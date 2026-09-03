package com.calcplus.calculator.core.data

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import java.io.File

/**
 * Which frame of a video becomes its poster thumbnail: `min(1 s, duration / 2)`
 * (iteration-2-decisions §11). Pure, so the rule is pinned by a unit test and
 * stays identical to the iOS `PosterFrame`.
 */
object PosterFrame {
    /** The preferred offset for anything at least two seconds long. */
    const val PREFERRED_OFFSET_MS: Long = 1_000L

    /**
     * `min(1 s, duration / 2)` in milliseconds. A zero, negative or unknown
     * duration (a broken or single-frame file) yields the very first frame.
     */
    fun offsetMs(durationMs: Long): Long {
        if (durationMs <= 0L) return 0L
        return minOf(PREFERRED_OFFSET_MS, durationMs / 2L)
    }

    /** The same offset as microseconds, which is what `MediaMetadataRetriever` takes. */
    fun offsetMicros(durationMs: Long): Long = offsetMs(durationMs) * 1_000L
}

/**
 * Everything [PhotoFileStore] needs to file a video away, read once from the
 * copied original. Nothing here re-encodes the video — [poster] is the only
 * pixel data produced, and it becomes the same `<uuid>.jpg` thumbnail a photo
 * gets.
 *
 * [width] / [height] are **rotation-aware**: a portrait clip recorded by a
 * landscape sensor reports portrait dimensions, so the stored size matches its
 * own poster frame and the Details sheet does not contradict the picture.
 */
data class VideoMetadata(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val poster: Bitmap?,
)

/**
 * Reads duration, display size and one poster frame out of a video file.
 *
 * Injected into [PhotoFileStore] so the import path is testable: Robolectric has
 * no real `MediaMetadataRetriever` (it is a native decoder), so the unit tests
 * push real bytes through the store with a hand-written fake probe.
 */
interface MediaProbe {
    /** null when the file carries no readable video track. */
    fun probe(file: File): VideoMetadata?
}

/**
 * Production [MediaProbe]. `MediaMetadataRetriever` is a native decoder; every
 * accessor can return null or throw on a malformed file, so every read here is
 * defensive and a failure degrades to "not a video we can store".
 */
class MediaMetadataRetrieverProbe : MediaProbe {
    override fun probe(file: File): VideoMetadata? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationMs = retriever.longMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.coerceAtLeast(0L) ?: 0L
            val rawWidth = retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: 0
            val rawHeight = retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: 0
            if (rawWidth <= 0 || rawHeight <= 0) return null
            val rotation = retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) ?: 0
            val (width, height) = displaySize(rawWidth, rawHeight, rotation)

            val micros = PosterFrame.offsetMicros(durationMs)
            // CLOSEST honours the decided offset exactly; CLOSEST_SYNC is the
            // fallback for files whose decoder refuses a non-keyframe seek.
            val poster = retriever.frameOrNull(micros, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: retriever.frameOrNull(micros, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameOrNull(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

            VideoMetadata(durationMs = durationMs, width = width, height = height, poster = poster)
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // Releasing a retriever that never opened is not an import failure.
            }
        }
    }

    private fun MediaMetadataRetriever.longMetadata(key: Int): Long? =
        extractMetadata(key)?.toLongOrNull()

    private fun MediaMetadataRetriever.intMetadata(key: Int): Int? =
        extractMetadata(key)?.toIntOrNull()

    private fun MediaMetadataRetriever.frameOrNull(timeUs: Long, option: Int): Bitmap? = try {
        getFrameAtTime(timeUs, option)
    } catch (_: Exception) {
        null
    }

    companion object {
        /**
         * Applies the container's rotation metadata: a quarter-turn swaps the
         * axes, so a phone-held-upright clip reports portrait dimensions.
         * Mirrors the iOS `naturalSize.applying(preferredTransform)`.
         */
        fun displaySize(width: Int, height: Int, rotationDegrees: Int): Pair<Int, Int> {
            val normalized = ((rotationDegrees % 360) + 360) % 360
            return if (normalized == 90 || normalized == 270) height to width else width to height
        }
    }
}
