package com.calcplus.calculator.gallery

import android.graphics.BitmapFactory
import com.calcplus.calculator.core.data.PhotoFileStore
import com.calcplus.calculator.core.domain.model.MediaType
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * N3 video import at the file-store level (decisions §9): real bytes through the
 * real [PhotoFileStore], with only the native metadata reader faked.
 */
@RunWith(RobolectricTestRunner::class)
class VideoStoreTest {
    private lateinit var baseDir: File

    @Before
    fun setUp() {
        baseDir = Files.createTempDirectory("safebox-video-test").toFile()
    }

    /** Arbitrary, deterministic, definitely-not-an-image bytes. */
    private fun videoBytes(size: Int = 4_096): ByteArray =
        ByteArray(size) { (it * 31 % 251).toByte() }

    @Test
    fun videoImportStoresTheOriginalBytesAPosterThumbnailAndTheDuration() {
        val probe = FakeMediaProbe.succeeding(durationMs = 92_400, width = 1080, height = 1920)
        val store = PhotoFileStore(baseDir, probe)
        val data = videoBytes()

        val stored = store.store({ ByteArrayInputStream(data) }, "video/mp4")

        assertNotNull(stored)
        stored!!
        // Byte-for-byte original under the real extension — never re-encoded.
        assertTrue(stored.fileName.endsWith(".mp4"))
        assertArrayEquals(data, store.photoFile(stored.fileName).readBytes())
        assertEquals(data.size.toLong(), stored.byteCount)
        // Poster frame written as the SAME <uuid>.jpg a photo thumbnail uses.
        assertTrue(stored.thumbFileName.endsWith(".jpg"))
        assertEquals(stored.id + ".jpg", stored.thumbFileName)
        assertTrue(store.thumbFile(stored.thumbFileName).length() > 0)
        // Metadata from the probe, rotation already applied by it.
        assertEquals(MediaType.VIDEO, stored.mediaType)
        assertEquals(92_400L, stored.durationMs)
        assertEquals(1080, stored.width)
        assertEquals(1920, stored.height)
        assertEquals("video/mp4", stored.mimeType)
        // The probe read the COPIED file, not the source stream.
        assertEquals(listOf(store.photoFile(stored.fileName)), probe.probed)
    }

    @Test
    fun theExtensionTableIsTheDecidedOne() {
        val cases = mapOf(
            "video/mp4" to "mp4",
            "video/quicktime" to "mov",
            "video/3gpp" to "3gp",
            "video/webm" to "webm",
            "video/x-matroska" to "mkv",
            // Not in the table: the subtype itself, so the name stays truthful.
            "video/avc" to "avc",
            "video/MP2T" to "mp2t",
        )
        for ((mime, ext) in cases) {
            val store = PhotoFileStore(baseDir, FakeMediaProbe.succeeding(1_000))
            val stored = store.store({ ByteArrayInputStream(videoBytes(64)) }, mime)!!
            assertEquals("$mime → .$ext", ext, stored.fileName.substringAfterLast('.'))
            assertEquals(mime, stored.mimeType)
        }
    }

    @Test
    fun aMimeParameterAndOddCaseStillResolveToTheDecidedExtension() {
        val store = PhotoFileStore(baseDir, FakeMediaProbe.succeeding(1_000))
        val stored = store.store({ ByteArrayInputStream(videoBytes(64)) }, "VIDEO/QuickTime; codecs=avc1")!!
        assertEquals("mov", stored.fileName.substringAfterLast('.'))
    }

    @Test
    fun aVideoWithNoReadableFrameLeavesNoFiles() {
        // A poster is mandatory: a video with no thumbnail would be a hole in
        // the grid (iOS raises StoreError.unreadableVideo for the same reason).
        val store = PhotoFileStore(baseDir, FakeMediaProbe.withoutPoster())
        assertNull(store.store({ ByteArrayInputStream(videoBytes()) }, "video/mp4"))
        assertTrue(store.photosDir.listFiles().orEmpty().isEmpty())
        assertTrue(store.thumbsDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun anUnprobeableVideoLeavesNoFiles() {
        val store = PhotoFileStore(baseDir, FakeMediaProbe.failing())
        assertNull(store.store({ ByteArrayInputStream(videoBytes()) }, "video/mp4"))
        assertTrue(store.photosDir.listFiles().orEmpty().isEmpty())
        assertTrue(store.thumbsDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun anEmptyVideoStreamFailsBeforeTheProbeIsEvenAsked() {
        val probe = FakeMediaProbe.succeeding(1_000)
        val store = PhotoFileStore(baseDir, probe)
        assertNull(store.store({ ByteArrayInputStream(ByteArray(0)) }, "video/mp4"))
        assertTrue(probe.probed.isEmpty())
        assertTrue(store.photosDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun aLargePosterIsDownscaledLikeAPhotoThumbnail() {
        val store = PhotoFileStore(baseDir, FakeMediaProbe.succeeding(3_000, width = 1400, height = 700))
        val stored = store.store({ ByteArrayInputStream(videoBytes()) }, "video/mp4")!!
        val thumb = BitmapFactory.decodeFile(store.thumbFile(stored.thumbFileName).path)
        assertTrue(thumb.width <= PhotoFileStore.THUMBNAIL_MAX_PIXEL)
        assertTrue(thumb.height <= PhotoFileStore.THUMBNAIL_MAX_PIXEL)
    }

    @Test
    fun deletingAVideoRemovesTheOriginalAndThePoster() {
        val store = PhotoFileStore(baseDir, FakeMediaProbe.succeeding(1_000))
        val stored = store.store({ ByteArrayInputStream(videoBytes()) }, "video/mp4")!!
        store.delete(stored.fileName, stored.thumbFileName)
        assertFalse(store.photoFile(stored.fileName).exists())
        assertFalse(store.thumbFile(stored.thumbFileName).exists())
    }

    @Test
    fun mimeClassificationIsCaseInsensitiveAndPhotosAreUnaffected() {
        assertTrue(PhotoFileStore.isVideoMime("video/mp4"))
        assertTrue(PhotoFileStore.isVideoMime("VIDEO/MP4"))
        assertFalse(PhotoFileStore.isVideoMime("image/jpeg"))
        assertFalse(PhotoFileStore.isVideoMime(null))
        assertFalse(PhotoFileStore.isVideoMime("application/octet-stream"))
    }

    @Test
    fun thePosterBitmapIsRecycledOnceItHasBeenWritten() {
        // A poster already ≤ THUMBNAIL_MAX_PIXEL is written without a scaled
        // copy, so if the store did not recycle the frame the probe handed it,
        // a whole ARGB_8888 bitmap would be left to the GC on every import.
        val small = FakeMediaProbe.succeeding(1_000, width = 320, height = 180)
        val store = PhotoFileStore(baseDir, small)
        assertNotNull(store.store({ ByteArrayInputStream(videoBytes()) }, "video/mp4"))
        assertTrue("the unscaled poster must be recycled", small.lastPoster!!.isRecycled)

        // …and when a scaled copy IS made, the source is still recycled exactly
        // once (the copy is recycled separately inside the writer).
        val large = FakeMediaProbe.succeeding(1_000, width = 1400, height = 700)
        val scalingStore = PhotoFileStore(baseDir, large)
        val stored = scalingStore.store({ ByteArrayInputStream(videoBytes()) }, "video/mp4")!!
        assertTrue(large.lastPoster!!.isRecycled)
        assertTrue(scalingStore.thumbFile(stored.thumbFileName).length() > 0)
    }

    @Test
    fun aFailedVideoIsRecognisedEvenWhenTheMimeTypeDoesNotSayVideo() {
        // The picker may hand a clip over with a missing or generic MIME type;
        // it then takes the image path, fails the decode, and would otherwise be
        // dropped with no `video_import_failed` notice at all.
        assertTrue(PhotoFileStore.looksLikeVideo("video/mp4", null))
        assertTrue(PhotoFileStore.looksLikeVideo(null, "clip.MP4"))
        assertTrue(PhotoFileStore.looksLikeVideo("application/octet-stream", "holiday.mov"))
        assertTrue(PhotoFileStore.looksLikeVideo(null, "content://media/external/video/a.mkv"))
        assertTrue(PhotoFileStore.looksLikeVideo("", "a.webm"))
        // A declared image is never re-classified — a corrupt JPEG stays silent.
        assertFalse(PhotoFileStore.looksLikeVideo("image/jpeg", "trick.mp4"))
        assertFalse(PhotoFileStore.looksLikeVideo("image/png", null))
        // Nothing to go on: no MIME, no extension (a media-store picker URI).
        assertFalse(PhotoFileStore.looksLikeVideo(null, "1000000034"))
        assertFalse(PhotoFileStore.looksLikeVideo(null, null))
        assertFalse(PhotoFileStore.looksLikeVideo("application/pdf", "report.pdf"))
    }

    @Test
    fun aPhotoStillStoresAsAPhotoWithNoDuration() {
        // The photo path must be untouched by N3: the probe is never consulted.
        val probe = FakeMediaProbe.succeeding(1_000)
        val store = PhotoFileStore(baseDir, probe)
        val png = TestImages.pngBytes(20, 12)
        val stored = store.store({ ByteArrayInputStream(png) }, "image/png")!!
        assertEquals(MediaType.PHOTO, stored.mediaType)
        assertNull(stored.durationMs)
        assertArrayEquals(png, store.photoFile(stored.fileName).readBytes())
        assertTrue(probe.probed.isEmpty())
    }
}
