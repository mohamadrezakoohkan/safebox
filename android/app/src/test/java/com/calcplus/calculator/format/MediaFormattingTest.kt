package com.calcplus.calculator.format

import com.calcplus.calculator.core.format.MediaFormatting
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure formatters behind the N2 Details sheet (iteration-2-decisions §8). File-size formatting
 * needs a Context and is covered under Robolectric in PhotoInfoTest.
 */
class MediaFormattingTest {

    // Dimensions: "W × H" with the multiplication sign, plain integers.

    @Test
    fun dimensionsUseMultiplicationSignAndUngroupedIntegers() {
        assertEquals("1920 × 1080", MediaFormatting.dimensions(1920, 1080))
        assertEquals("20 × 12", MediaFormatting.dimensions(20, 12))
    }

    // Type: the shared MIME→label table, else the uppercased subtype.

    @Test
    fun typeLabelMapsEveryDecidedSubtype() {
        val decided = mapOf(
            "image/jpeg" to "JPEG",
            "image/png" to "PNG",
            "image/heic" to "HEIC",
            "image/heif" to "HEIF",
            "image/gif" to "GIF",
            "image/webp" to "WEBP",
            "image/bmp" to "BMP",
            "video/mp4" to "MP4",
            "video/quicktime" to "MOV",
            "video/x-matroska" to "MKV",
            "video/webm" to "WEBM",
            "video/3gpp" to "3GP",
        )
        decided.forEach { (mime, label) -> assertEquals(mime, label, MediaFormatting.typeLabel(mime)) }
        // The table is exactly the decided twelve — nothing extra sneaks in on one platform.
        assertEquals(decided.size, MediaFormatting.MIME_LABELS.size)
    }

    @Test
    fun typeLabelFallsBackToUppercasedSubtype() {
        assertEquals("SVG+XML", MediaFormatting.typeLabel("image/svg+xml"))
        assertEquals("X-MSVIDEO", MediaFormatting.typeLabel("video/x-msvideo"))
    }

    @Test
    fun typeLabelIsCaseInsensitiveAndIgnoresParameters() {
        assertEquals("JPEG", MediaFormatting.typeLabel("IMAGE/JPEG"))
        assertEquals("MP4", MediaFormatting.typeLabel("video/mp4; codecs=\"avc1.42E01E\""))
        assertEquals("PNG", MediaFormatting.typeLabel(" image/png "))
    }

    @Test
    fun typeLabelHandlesDegenerateInput() {
        assertEquals("PNG", MediaFormatting.typeLabel("png")) // no slash: the whole value is the subtype
        assertEquals("", MediaFormatting.typeLabel(""))
        assertEquals("", MediaFormatting.typeLabel("image/"))
    }

    // Duration: m:ss under an hour, h:mm:ss from an hour up, rounded to the nearest whole second
    // (decisions §8 — identical to iOS MediaMetadataFormatter.duration).

    @Test
    fun durationUnderAnHourIsMinutesAndSeconds() {
        assertEquals("0:00", MediaFormatting.duration(0))
        assertEquals("0:05", MediaFormatting.duration(5_000))
        assertEquals("1:05", MediaFormatting.duration(65_000))
        assertEquals("59:59", MediaFormatting.duration(3_599_000))
    }

    @Test
    fun durationFromAnHourIsHoursMinutesAndSeconds() {
        assertEquals("1:00:00", MediaFormatting.duration(3_600_000))
        assertEquals("1:01:01", MediaFormatting.duration(3_661_000))
        assertEquals("10:00:00", MediaFormatting.duration(36_000_000))
    }

    @Test
    fun durationRoundsToTheNearestSecondHalfUp() {
        assertEquals("0:00", MediaFormatting.duration(499))
        assertEquals("0:01", MediaFormatting.duration(500))
        assertEquals("1:00", MediaFormatting.duration(59_600)) // the §8 example: 59.6 s → 1:00, never 0:59
        assertEquals("1:00", MediaFormatting.duration(59_999))
        assertEquals("1:00:00", MediaFormatting.duration(3_599_500)) // rounding may carry into h:mm:ss
    }

    @Test
    fun durationClampsNegativeToZero() {
        assertEquals("0:00", MediaFormatting.duration(-1_000))
    }

    // Imported: localized medium date + short time.

    private val instant: Long =
        ZonedDateTime.of(2026, 9, 2, 14, 5, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    /** JDK 20+ CLDR data puts a narrow no-break space before AM/PM; fold every space variant. */
    private fun plainSpaces(text: String) = text.replace(' ', ' ').replace(' ', ' ')

    @Test
    fun dateTimeIsMediumDateWithShortTime() {
        val text = plainSpaces(MediaFormatting.dateTime(instant, Locale.US, TimeZone.getTimeZone("UTC")))
        assertTrue(text, text.startsWith("Sep 2, 2026"))
        assertTrue(text, text.contains("2:05"))
        assertTrue(text, text.endsWith("PM"))
        assertTrue(text, !text.contains(":00 PM")) // short time: no seconds
    }

    @Test
    fun dateTimeHonoursLocaleAndTimeZone() {
        val newYork = plainSpaces(MediaFormatting.dateTime(instant, Locale.US, TimeZone.getTimeZone("America/New_York")))
        assertTrue(newYork, newYork.contains("10:05")) // 14:05Z is 10:05 EDT
        assertTrue(newYork, newYork.endsWith("AM"))

        val germany = plainSpaces(MediaFormatting.dateTime(instant, Locale.GERMANY, TimeZone.getTimeZone("UTC")))
        assertTrue(germany, germany.contains("02.09.2026"))
        assertTrue(germany, germany.contains("14:05"))
    }
}
