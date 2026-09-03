package com.calcplus.calculator.gallery

import android.graphics.Bitmap
import com.calcplus.calculator.core.data.MediaProbe
import com.calcplus.calculator.core.data.VideoMetadata
import java.io.File

/**
 * Stand-in for [com.calcplus.calculator.core.data.MediaMetadataRetrieverProbe].
 *
 * Robolectric has no `MediaMetadataRetriever` (it is a native decoder), so the
 * import tests push **real bytes** through the real [PhotoFileStore] and swap
 * only the metadata reader. Everything the store does with the bytes — the
 * byte-for-byte copy, the extension, the thumbnail write, the failure cleanup —
 * is the production code path.
 */
class FakeMediaProbe(
    private var result: VideoMetadata?,
    /**
     * Mints the poster for each probe. `MediaMetadataRetriever.getFrameAtTime`
     * hands back a NEW bitmap per call and the store takes ownership of it (it
     * recycles the frame once the poster JPEG is written), so a fake that
     * reused one bitmap would hand the second video of a batch a recycled one.
     */
    private val posterFactory: (() -> Bitmap)? = null,
) : MediaProbe {
    /** Every file the store asked about, in order. */
    val probed = mutableListOf<File>()

    /** The poster handed to the store by the most recent probe, if any. */
    var lastPoster: Bitmap? = null
        private set

    override fun probe(file: File): VideoMetadata? {
        probed += file
        val metadata = result ?: return null
        val poster = posterFactory?.invoke()
        lastPoster = poster
        return if (posterFactory == null) metadata else metadata.copy(poster = poster)
    }

    /** Makes every subsequent probe fail, as an unreadable video would. */
    fun failFromNowOn() {
        result = null
    }

    companion object {
        /** A probe that reports [durationMs] and hands back a real poster bitmap. */
        fun succeeding(
            durationMs: Long,
            width: Int = 320,
            height: Int = 180,
        ): FakeMediaProbe = FakeMediaProbe(
            result = VideoMetadata(
                durationMs = durationMs,
                width = width,
                height = height,
                poster = null,
            ),
            posterFactory = { poster(width, height) },
        )

        /** A probe that reports a video with no readable frame. */
        fun withoutPoster(durationMs: Long = 5_000): FakeMediaProbe = FakeMediaProbe(
            VideoMetadata(durationMs = durationMs, width = 320, height = 180, poster = null)
        )

        fun failing(): FakeMediaProbe = FakeMediaProbe(null)

        fun poster(width: Int, height: Int): Bitmap =
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                eraseColor(0xFF2266AA.toInt())
            }
    }
}
