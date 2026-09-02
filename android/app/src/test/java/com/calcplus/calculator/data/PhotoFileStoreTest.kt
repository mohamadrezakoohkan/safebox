package com.calcplus.calculator.data

import android.graphics.Bitmap
import com.calcplus.calculator.core.data.PhotoFileStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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

@RunWith(RobolectricTestRunner::class)
class PhotoFileStoreTest {
    private lateinit var baseDir: File
    private lateinit var store: PhotoFileStore

    @Before
    fun setUp() {
        baseDir = Files.createTempDirectory("safebox-test").toFile()
        store = PhotoFileStore(baseDir)
    }

    private fun pngBytes(width: Int = 20, height: Int = 12): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFFFF8800.toInt())
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    @Test
    fun storePreservesOriginalBytesAndRealExtension() {
        val data = pngBytes()
        val stored = store.store({ ByteArrayInputStream(data) }, "image/png")
        assertNotNull(stored)
        stored!!
        assertTrue(stored.fileName.endsWith(".png")) // real extension
        assertEquals("image/png", stored.mimeType)
        assertEquals(20, stored.width)
        assertEquals(12, stored.height)
        // Byte-for-byte original — never re-encoded.
        assertArrayEquals(data, store.photoFile(stored.fileName).readBytes())
        // Thumbnail generated alongside.
        assertTrue(store.thumbFile(stored.thumbFileName).length() > 0)
    }

    @Test
    fun unreadableSourceLeavesNoPartialFiles() {
        // Empty stream: the copy produces a 0-byte file → import fails atomically.
        // (Truly undecodable bytes can't be simulated under Robolectric's
        // lenient BitmapFactory shadow.)
        val stored = store.store({ ByteArrayInputStream(ByteArray(0)) }, "image/png")
        assertNull(stored)
        assertTrue(store.photosDir.listFiles().orEmpty().isEmpty())
        assertTrue(store.thumbsDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun deleteRemovesBothFiles() {
        val stored = store.store({ ByteArrayInputStream(pngBytes()) }, "image/png")!!
        store.delete(stored.fileName, stored.thumbFileName)
        assertFalse(store.photoFile(stored.fileName).exists())
        assertFalse(store.thumbFile(stored.thumbFileName).exists())
    }

    @Test
    fun orphanSweepRemovesUnreferencedFiles() {
        val kept = store.store({ ByteArrayInputStream(pngBytes()) }, "image/png")!!
        val orphan = store.store({ ByteArrayInputStream(pngBytes()) }, "image/png")!!

        store.sweepOrphans(setOf(kept.fileName), setOf(kept.thumbFileName))

        assertTrue(store.photoFile(kept.fileName).exists())
        assertTrue(store.thumbFile(kept.thumbFileName).exists())
        assertFalse(store.photoFile(orphan.fileName).exists())
        assertFalse(store.thumbFile(orphan.thumbFileName).exists())
    }

    @Test
    fun largeImageGetsDownsampledThumbnail() {
        val stored = store.store({ ByteArrayInputStream(pngBytes(width = 1400, height = 700)) }, "image/png")!!
        assertEquals(1400, stored.width)
        assertEquals(700, stored.height)
        val thumb = android.graphics.BitmapFactory.decodeFile(store.thumbFile(stored.thumbFileName).path)
        assertTrue(thumb.width <= PhotoFileStore.THUMBNAIL_MAX_PIXEL)
        assertTrue(thumb.height <= PhotoFileStore.THUMBNAIL_MAX_PIXEL)
    }
}
