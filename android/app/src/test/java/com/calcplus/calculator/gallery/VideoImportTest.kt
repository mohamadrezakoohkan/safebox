package com.calcplus.calculator.gallery

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.calcplus.calculator.core.data.AlbumRepositoryImpl
import com.calcplus.calculator.core.data.PhotoFileStore
import com.calcplus.calculator.core.data.PhotoRepositoryImpl
import com.calcplus.calculator.core.database.SafeBoxDatabase
import com.calcplus.calculator.core.database.entity.AlbumEntity
import com.calcplus.calculator.core.domain.model.MediaType
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * N3 video support through the real repository (decisions §9): a mixed
 * photo/video import, the media-type-agnostic ordering, purge deleting both
 * files of a video, and the `video_import_failed` signal.
 *
 * `runBlocking` (not `runTest`) throughout: `PhotoRepositoryImpl.import` launches
 * on `Dispatchers.IO`, which no test scheduler controls, so these tests wait on
 * real time with a generous deadline.
 */
@RunWith(RobolectricTestRunner::class)
class VideoImportTest {
    private lateinit var context: Context
    private lateinit var db: SafeBoxDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var probe: FakeMediaProbe
    private lateinit var fileStore: PhotoFileStore
    private lateinit var photos: PhotoRepositoryImpl
    private lateinit var albums: AlbumRepositoryImpl
    private val albumId = "album-1"

    private var clock = 5_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        Robolectric.setupContentProvider(
            FakeMediaContentProvider::class.java,
            FakeMediaContentProvider.AUTHORITY,
        )
        db = Room.inMemoryDatabaseBuilder(context, SafeBoxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        probe = FakeMediaProbe.succeeding(durationMs = 12_345, width = 720, height = 1280)
        fileStore = PhotoFileStore(File(context.filesDir, "video-${UUID.randomUUID()}"), probe)
        photos = PhotoRepositoryImpl(db, fileStore, context.contentResolver, scope) { clock }
        albums = AlbumRepositoryImpl(db, fileStore) { clock }
        runBlocking {
            db.albumDao().insert(
                AlbumEntity(id = albumId, name = "Mixed", createdAt = 1, sortIndex = 0)
            )
        }
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
        FakeMediaContentProvider.clear()
    }

    private fun videoBytes(seed: Int, size: Int = 2_048): ByteArray =
        ByteArray(size) { ((it + seed) * 37 % 251).toByte() }

    /** A picker-shaped URI whose type is [mimeType] and whose bytes are [bytes]. */
    private fun pickerUri(name: String, mimeType: String?, bytes: ByteArray): Uri {
        val uri = FakeMediaContentProvider.uri(name, mimeType)
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(bytes))
        return uri
    }

    private suspend fun awaitPhotoRows(count: Int) {
        withTimeout(20_000) {
            while (db.photoDao().allPhotos().size < count) delay(20)
        }
        // The import coroutine writes the progress reset after the last row.
        withTimeout(20_000) {
            while (photos.importProgress.value.isActive) delay(20)
        }
    }

    @Test
    fun aVideoImportsWithItsOwnBytesPosterAndDuration() = runBlocking {
        val bytes = videoBytes(seed = 1)
        photos.import(albumId, listOf(pickerUri("clip.mp4", "video/mp4", bytes)))
        awaitPhotoRows(1)

        val row = db.photoDao().allPhotos().single()
        assertEquals(MediaType.VIDEO, row.mediaType)
        assertEquals(12_345L, row.durationMs)
        assertEquals("video/mp4", row.mimeType)
        assertEquals(720, row.width)
        assertEquals(1280, row.height)
        assertTrue(row.fileName.endsWith(".mp4"))
        assertTrue(row.thumbFileName.endsWith(".jpg"))
        // The stored original is byte-for-byte what the picker handed over.
        assertArrayEquals(bytes, fileStore.photoFile(row.fileName).readBytes())
        assertEquals(bytes.size.toLong(), row.byteCount)
        assertTrue(fileStore.thumbFile(row.thumbFileName).length() > 0)
    }

    @Test
    fun aMixedAlbumListsAndOrdersByInsertionRegardlessOfMediaType() = runBlocking {
        photos.import(
            albumId,
            listOf(
                pickerUri("a.png", "image/png", TestImages.pngBytes()),
                pickerUri("b.mp4", "video/mp4", videoBytes(seed = 2)),
                pickerUri("c.mov", "video/quicktime", videoBytes(seed = 3)),
                pickerUri("d.png", "image/png", TestImages.pngBytes(30, 30)),
            ),
        )
        awaitPhotoRows(4)

        val listed = photos.observePhotos(albumId).first()
        assertEquals(4, listed.size)
        // sortIndex is assigned in import order and the query is media-type
        // agnostic, so photos and videos interleave exactly as they arrived.
        assertEquals(listOf(0, 1, 2, 3), listed.map { it.sortIndex })
        assertEquals(
            listOf(MediaType.PHOTO, MediaType.VIDEO, MediaType.VIDEO, MediaType.PHOTO),
            listed.map { it.mediaType },
        )
        assertEquals(listOf(false, true, true, false), listed.map { it.isVideo })
        // Extensions follow the media type, not the position.
        assertTrue(listed[1].fileName.endsWith(".mp4"))
        assertTrue(listed[2].fileName.endsWith(".mov"))
        // Only videos carry a duration.
        assertNull(listed[0].durationMs)
        assertEquals(12_345L, listed[1].durationMs)
        assertNull(listed[3].durationMs)
    }

    @Test
    fun deletingAVideoKeepsItsFilesUntilPurgeAndThenRemovesBoth() = runBlocking {
        photos.import(albumId, listOf(pickerUri("clip.mp4", "video/mp4", videoBytes(seed = 4))))
        awaitPhotoRows(1)
        val row = db.photoDao().allPhotos().single()
        val original = fileStore.photoFile(row.fileName)
        val poster = fileStore.thumbFile(row.thumbFileName)

        // P3 soft delete: hidden, bytes untouched (identical for photos and videos).
        photos.deletePhotos(listOf(row.id))
        assertTrue(photos.observePhotos(albumId).first().isEmpty())
        assertTrue(original.exists())
        assertTrue(poster.exists())

        // Purge: the row AND both files.
        photos.purge(listOf(row.id))
        assertTrue(db.photoDao().allPhotos().isEmpty())
        assertFalse(original.exists())
        assertFalse(poster.exists())
    }

    @Test
    fun theOrphanSweepNeverTouchesATrashedVideosBytes() = runBlocking {
        photos.import(albumId, listOf(pickerUri("clip.mp4", "video/mp4", videoBytes(seed = 5))))
        awaitPhotoRows(1)
        val row = db.photoDao().allPhotos().single()
        photos.deletePhotos(listOf(row.id))

        photos.sweepOrphans()

        assertTrue(fileStore.photoFile(row.fileName).exists())
        assertTrue(fileStore.thumbFile(row.thumbFileName).exists())
    }

    @Test
    fun purgingAnAlbumRemovesItsVideosFilesToo() = runBlocking {
        photos.import(
            albumId,
            listOf(
                pickerUri("a.mp4", "video/mp4", videoBytes(seed = 6)),
                pickerUri("b.png", "image/png", TestImages.pngBytes()),
            ),
        )
        awaitPhotoRows(2)
        val rows = db.photoDao().allPhotos()

        albums.purge(listOf(albumId))

        assertTrue(db.photoDao().allPhotos().isEmpty())
        for (row in rows) {
            assertFalse(fileStore.photoFile(row.fileName).exists())
            assertFalse(fileStore.thumbFile(row.thumbFileName).exists())
        }
    }

    @Test
    fun aVideoThatCannotBeReadIsReportedAndLeavesNothingBehind() = runBlocking {
        probe.failFromNowOn()
        val subscribed = CompletableDeferred<Unit>()
        val reported = Channel<Int>(Channel.UNLIMITED)
        val collector = scope.launch {
            photos.videoImportFailures
                .onSubscription { subscribed.complete(Unit) }
                .collect { reported.trySend(it) }
        }
        subscribed.await()

        photos.import(
            albumId,
            listOf(
                pickerUri("broken.mp4", "video/mp4", videoBytes(seed = 7)),
                pickerUri("ok.png", "image/png", TestImages.pngBytes()),
            ),
        )

        val count = withTimeout(20_000) { reported.receive() }
        assertEquals(1, count)
        // The photo of the same batch still landed; the video left no row and no file.
        val rows = db.photoDao().allPhotos()
        assertEquals(1, rows.size)
        assertEquals(MediaType.PHOTO, rows.single().mediaType)
        assertEquals(1, fileStore.photosDir.listFiles().orEmpty().size)
        assertEquals(1, fileStore.thumbsDir.listFiles().orEmpty().size)
        collector.cancel()
    }

    @Test
    fun aVideoWithNoMimeTypeIsStillReportedAsAFailedVideo() = runBlocking {
        // Nothing said "video/…", so the item took the store's image path and
        // failed the bitmap decode. Classifying only by `getType` would drop it
        // in total silence; the file name is what saves the notice.
        val subscribed = CompletableDeferred<Unit>()
        val reported = Channel<Int>(Channel.UNLIMITED)
        val collector = scope.launch {
            photos.videoImportFailures
                .onSubscription { subscribed.complete(Unit) }
                .collect { reported.trySend(it) }
        }
        subscribed.await()

        // Empty bytes are how a store failure is forced here: Robolectric's
        // BitmapFactory shadow does not really decode, so arbitrary bytes would
        // "import" as a 100×100 image instead of failing the way a real device
        // fails a video pushed down the image path.
        photos.import(
            albumId,
            listOf(
                pickerUri("holiday.MOV", null, ByteArray(0)),
                pickerUri("clip.mp4", "application/octet-stream", ByteArray(0)),
            ),
        )

        assertEquals(2, withTimeout(20_000) { reported.receive() })
        assertTrue(db.photoDao().allPhotos().isEmpty())
        assertTrue(fileStore.photosDir.listFiles().orEmpty().isEmpty())
        assertTrue(fileStore.thumbsDir.listFiles().orEmpty().isEmpty())
        collector.cancel()
    }

    @Test
    fun aPhotoThatFailsToDecodeIsNeverReportedAsAVideo() = runBlocking {
        // The other half of the same rule: a broken image must stay silent, even
        // when its name would look like a video to a laxer check.
        val subscribed = CompletableDeferred<Unit>()
        val reported = Channel<Int>(Channel.UNLIMITED)
        val collector = scope.launch {
            photos.videoImportFailures
                .onSubscription { subscribed.complete(Unit) }
                .collect { reported.trySend(it) }
        }
        subscribed.await()

        photos.import(
            albumId,
            listOf(
                pickerUri("broken.png", "image/png", ByteArray(0)),
                pickerUri("trick.mp4", "image/jpeg", ByteArray(0)),
                pickerUri("ok.png", "image/png", TestImages.pngBytes()),
            ),
        )
        awaitPhotoRows(1)

        assertNull(reported.tryReceive().getOrNull())
        assertEquals(1, db.photoDao().allPhotos().size)
        collector.cancel()
    }

    @Test
    fun anImportWithNoFailedVideoReportsNothing() = runBlocking {
        val subscribed = CompletableDeferred<Unit>()
        val reported = Channel<Int>(Channel.UNLIMITED)
        val collector = scope.launch {
            photos.videoImportFailures
                .onSubscription { subscribed.complete(Unit) }
                .collect { reported.trySend(it) }
        }
        subscribed.await()

        photos.import(albumId, listOf(pickerUri("ok.mp4", "video/mp4", videoBytes(seed = 8))))
        awaitPhotoRows(1)

        assertNull(reported.tryReceive().getOrNull())
        collector.cancel()
    }
}
