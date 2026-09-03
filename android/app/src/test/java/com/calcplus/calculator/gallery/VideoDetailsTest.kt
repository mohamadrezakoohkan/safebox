package com.calcplus.calculator.gallery

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.calcplus.calculator.R
import com.calcplus.calculator.core.domain.model.MediaType
import com.calcplus.calculator.core.domain.model.Photo
import com.calcplus.calculator.feature.gallery.PhotoInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Details sheet's Duration row is video-only (decisions §8/§9), and its
 * value is the same string the grid badge shows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class VideoDetailsTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun photo(mediaType: String, durationMs: Long?) = Photo(
        id = "p",
        albumId = "a",
        fileName = "p.mp4",
        thumbFileName = "p.jpg",
        mimeType = if (mediaType == MediaType.VIDEO) "video/mp4" else "image/jpeg",
        width = 720,
        height = 1280,
        byteCount = 1_024,
        importedAt = 1_700_000_000_000,
        sortIndex = 0,
        mediaType = mediaType,
        durationMs = durationMs,
    )

    /** What `PhotoInfoSheet` passes: the duration only when the row is a video. */
    private fun info(photo: Photo) = PhotoInfo.from(
        context,
        photo,
        durationMs = if (photo.isVideo) photo.durationMs else null,
    )

    @Test
    fun aVideoGetsADurationRowRightAfterType() {
        val entries = info(photo(MediaType.VIDEO, 92_400)).entries
        assertEquals(
            listOf(
                R.string.photo_info_dimensions,
                R.string.photo_info_size,
                R.string.photo_info_type,
                R.string.photo_info_duration,
                R.string.photo_info_imported,
            ),
            entries.map { it.labelRes },
        )
        assertEquals("1:32", entries.single { it.labelRes == R.string.photo_info_duration }.value)
        // Type comes from the shared MIME table.
        assertEquals("MP4", entries.single { it.labelRes == R.string.photo_info_type }.value)
    }

    @Test
    fun aPhotoNeverGetsADurationRow() {
        val entries = info(photo(MediaType.PHOTO, null)).entries
        assertFalse(entries.any { it.labelRes == R.string.photo_info_duration })
        assertEquals(4, entries.size)
    }

    @Test
    fun aPhotoCarryingAStrayDurationStillGetsNoDurationRow() {
        // Belt and braces: the sheet gates on mediaType, not on the column.
        val entries = info(photo(MediaType.PHOTO, 5_000)).entries
        assertFalse(entries.any { it.labelRes == R.string.photo_info_duration })
    }

    @Test
    fun isVideoIsTheOneMediaTypeComparison() {
        assertTrue(photo(MediaType.VIDEO, 1).isVideo)
        assertFalse(photo(MediaType.PHOTO, null).isVideo)
    }
}
