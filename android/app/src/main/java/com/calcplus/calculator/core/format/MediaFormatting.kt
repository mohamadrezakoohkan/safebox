package com.calcplus.calculator.core.format

import android.content.Context
import android.text.format.Formatter
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Display formatting for photo/video metadata (iteration-2-decisions §8).
 *
 * Used by the pager's Details sheet (N2) and, once N3 lands, by the grid's duration badge.
 * Every function is deterministic given its arguments; the locale-sensitive ones take the
 * locale / time zone explicitly (defaulting to the device's) so tests can pin them.
 */
object MediaFormatting {
    /**
     * MIME subtype → user-facing label. Decided cross-platform (identical on iOS); any subtype
     * not listed is shown as its uppercased form.
     */
    val MIME_LABELS: Map<String, String> = mapOf(
        "jpeg" to "JPEG",
        "png" to "PNG",
        "heic" to "HEIC",
        "heif" to "HEIF",
        "gif" to "GIF",
        "webp" to "WEBP",
        "bmp" to "BMP",
        "mp4" to "MP4",
        "quicktime" to "MOV",
        "x-matroska" to "MKV",
        "webm" to "WEBM",
        "3gpp" to "3GP",
    )

    /** "W × H" — the multiplication sign (U+00D7) between plain, ungrouped integers. */
    fun dimensions(width: Int, height: Int): String = "$width × $height"

    /**
     * Type label for a MIME type: the subtype (after the slash, before any ";" parameter),
     * lower-cased and mapped through [MIME_LABELS]; unknown subtypes are uppercased as-is.
     * A value with no slash is treated as a bare subtype; an empty subtype yields "".
     */
    fun typeLabel(mimeType: String): String {
        val subtype = mimeType
            .substringAfter('/', mimeType)
            .substringBefore(';')
            .trim()
            .lowercase(Locale.ROOT)
        if (subtype.isEmpty()) return ""
        return MIME_LABELS[subtype] ?: subtype.uppercase(Locale.ROOT)
    }

    /**
     * Media duration rounded to the nearest whole second (half up, so a 59.6 s clip reads "1:00",
     * not "0:59"): "m:ss" under one hour, "h:mm:ss" from one hour. Negative input is clamped to
     * "0:00". Same rule as iOS `MediaMetadataFormatter.duration` (decisions §8). N3 uses this for
     * both the grid badge and the sheet.
     */
    fun duration(durationMs: Long): String {
        val clamped = durationMs.coerceAtLeast(0L)
        val totalSeconds = clamped / 1_000L + (if (clamped % 1_000L >= 500L) 1L else 0L)
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }

    /** Localized, human-readable byte count via the platform formatter (SI units, short form). */
    fun fileSize(context: Context, byteCount: Long): String =
        Formatter.formatShortFileSize(context, byteCount)

    /** Localized medium date + short time, e.g. "Sep 2, 2026, 2:05 PM" in en-US. */
    fun dateTime(
        epochMillis: Long,
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): String {
        val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
        format.timeZone = timeZone
        return format.format(Date(epochMillis))
    }
}
