package com.calcplus.calculator.gallery

import android.content.Context
import android.graphics.Bitmap
import android.text.format.Formatter
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.calcplus.calculator.R
import com.calcplus.calculator.core.data.PhotoFileStore
import com.calcplus.calculator.core.data.PhotoRepositoryImpl
import com.calcplus.calculator.core.database.SafeBoxDatabase
import com.calcplus.calculator.core.database.entity.AlbumEntity
import com.calcplus.calculator.core.database.entity.PhotoEntity
import com.calcplus.calculator.core.format.MediaFormatting
import com.calcplus.calculator.feature.gallery.PhotoInfo
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * N2 Details sheet model: platform file-size formatting at every unit tier, the decided row
 * order, and — end to end through PhotoFileStore + Room + the repository — that the values shown
 * are the real stored values of an imported photo. Pinned to en-rUS for deterministic units.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class PhotoInfoTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    // File size: Formatter.formatShortFileSize (SI units) across bytes / kB / MB / GB.
    // Assertions check the number and the unit token rather than the exact separator, which
    // varies with the ICU data behind the platform formatter.

    @Test
    fun fileSizeInBytesStaysInBytes() {
        val text = MediaFormatting.fileSize(context, 512)
        assertTrue(text, text.startsWith("512"))
        assertFalse(text, text.contains("kB"))
        assertTrue(MediaFormatting.fileSize(context, 0), MediaFormatting.fileSize(context, 0).startsWith("0"))
    }

    @Test
    fun fileSizeInKilobytes() {
        val text = MediaFormatting.fileSize(context, 1_000)
        assertTrue(text, text.startsWith("1.0"))
        assertTrue(text, text.endsWith("kB"))
    }

    @Test
    fun fileSizeInMegabytes() {
        val text = MediaFormatting.fileSize(context, 1_500_000)
        assertTrue(text, text.startsWith("1.5"))
        assertTrue(text, text.endsWith("MB"))
    }

    @Test
    fun fileSizeInGigabytes() {
        val text = MediaFormatting.fileSize(context, 2_500_000_000)
        assertTrue(text, text.startsWith("2.5"))
        assertTrue(text, text.endsWith("GB"))
    }

    // Row order (decisions §8): Dimensions, File size, Type, [Duration], Imported.

    @Test
    fun entriesFollowTheDecidedOrderWithoutDuration() {
        val info = PhotoInfo(dimensions = "1 × 1", fileSize = "1 B", type = "PNG", imported = "now")
        assertEquals(
            listOf(
                R.string.photo_info_dimensions,
                R.string.photo_info_size,
                R.string.photo_info_type,
                R.string.photo_info_imported,
            ),
            info.entries.map { it.labelRes },
        )
        assertEquals(listOf("1 × 1", "1 B", "PNG", "now"), info.entries.map { it.value })
    }

    @Test
    fun durationRowSitsAfterTypeWhenPresent() {
        val info = PhotoInfo(dimensions = "1 × 1", fileSize = "1 B", type = "MP4", imported = "now", duration = "0:05")
        assertEquals(
            listOf(
                R.string.photo_info_dimensions,
                R.string.photo_info_size,
                R.string.photo_info_type,
                R.string.photo_info_duration,
                R.string.photo_info_imported,
            ),
            info.entries.map { it.labelRes },
        )
        assertEquals("0:05", info.entries[3].value)
    }

    // Real stored values: import a PNG through the file store, persist it through Room, read it
    // back through the repository (the pager's path), and format it the way the sheet does.

    private fun pngBytes(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFF3366CC.toInt())
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    @Test
    fun reflectsTheRealStoredValuesOfAnImportedPhoto() = runTest {
        val baseDir = Files.createTempDirectory("safebox-info").toFile()
        val fileStore = PhotoFileStore(baseDir)
        val db = Room.inMemoryDatabaseBuilder(context, SafeBoxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val png = pngBytes(width = 20, height = 12)
            val stored = fileStore.store({ ByteArrayInputStream(png) }, "image/png")!!
            val albumId = UUID.randomUUID().toString()
            db.albumDao().insert(AlbumEntity(albumId, "Trips", 0, 0))
            val importedAt = ZonedDateTime.of(2026, 9, 2, 14, 5, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
            db.photoDao().insert(
                PhotoEntity(
                    id = stored.id,
                    albumId = albumId,
                    fileName = stored.fileName,
                    thumbFileName = stored.thumbFileName,
                    mimeType = stored.mimeType,
                    width = stored.width,
                    height = stored.height,
                    byteCount = stored.byteCount,
                    importedAt = importedAt,
                    sortIndex = 0,
                )
            )

            val repository = PhotoRepositoryImpl(db, fileStore, context.contentResolver, this)
            val photo = repository.observePhotos(albumId).first().single()
            val info = PhotoInfo.from(context, photo)

            assertEquals("20 × 12", info.dimensions)
            assertEquals("PNG", info.type)
            // byteCount is the on-disk size of the byte-for-byte original, and that is what is shown.
            assertEquals(png.size.toLong(), photo.byteCount)
            assertEquals(fileStore.photoFile(photo.fileName).length(), photo.byteCount)
            assertEquals(Formatter.formatShortFileSize(context, png.size.toLong()), info.fileSize)
            assertEquals(MediaFormatting.dateTime(importedAt), info.imported)
            assertTrue(info.imported, info.imported.contains("2026"))
            assertNull(info.duration) // photos never show a Duration row
        } finally {
            db.close()
            baseDir.deleteRecursively()
        }
    }
}
