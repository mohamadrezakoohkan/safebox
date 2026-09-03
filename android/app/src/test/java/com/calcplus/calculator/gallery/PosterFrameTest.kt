package com.calcplus.calculator.gallery

import com.calcplus.calculator.core.data.MediaMetadataRetrieverProbe
import com.calcplus.calculator.core.data.PosterFrame
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The video poster rule as a pure function (decisions §11: `min(1 s, duration / 2)`)
 * and the rotation-aware display size. Both are shared cross-platform: the iOS
 * `PosterFrame.offsetSeconds` and the `naturalSize.applying(preferredTransform)`
 * must agree with these numbers.
 */
class PosterFrameTest {
    @Test
    fun shortClipsTakeTheirMidpointFrame() {
        assertEquals(0L, PosterFrame.offsetMs(0))
        assertEquals(100L, PosterFrame.offsetMs(200))
        assertEquals(250L, PosterFrame.offsetMs(500))
        assertEquals(999L, PosterFrame.offsetMs(1_999)) // integer halving, still below 1 s
    }

    @Test
    fun clipsOfTwoSecondsAndUpTakeTheOneSecondFrame() {
        assertEquals(1_000L, PosterFrame.offsetMs(2_000))
        assertEquals(1_000L, PosterFrame.offsetMs(2_001))
        assertEquals(1_000L, PosterFrame.offsetMs(60_000))
        assertEquals(1_000L, PosterFrame.offsetMs(3_600_000))
    }

    @Test
    fun anUnknownOrNegativeDurationTakesTheFirstFrame() {
        assertEquals(0L, PosterFrame.offsetMs(-1))
        assertEquals(0L, PosterFrame.offsetMs(-3_600_000))
    }

    @Test
    fun theOffsetIsAlsoOfferedInMicrosecondsForMediaMetadataRetriever() {
        assertEquals(1_000_000L, PosterFrame.offsetMicros(2_000))
        assertEquals(100_000L, PosterFrame.offsetMicros(200))
        assertEquals(0L, PosterFrame.offsetMicros(0))
    }

    @Test
    fun theOneSecondPreferenceIsTheSharedConstant() {
        assertEquals(1_000L, PosterFrame.PREFERRED_OFFSET_MS)
    }

    @Test
    fun aQuarterTurnSwapsTheReportedDimensions() {
        // A landscape sensor recording a phone held upright: 1920×1080 with a
        // 90° rotation must be reported (and thumbnailed) as 1080×1920.
        assertEquals(1080 to 1920, MediaMetadataRetrieverProbe.displaySize(1920, 1080, 90))
        assertEquals(1080 to 1920, MediaMetadataRetrieverProbe.displaySize(1920, 1080, 270))
    }

    @Test
    fun halfTurnsAndNoRotationKeepTheDimensions() {
        assertEquals(1920 to 1080, MediaMetadataRetrieverProbe.displaySize(1920, 1080, 0))
        assertEquals(1920 to 1080, MediaMetadataRetrieverProbe.displaySize(1920, 1080, 180))
        assertEquals(1920 to 1080, MediaMetadataRetrieverProbe.displaySize(1920, 1080, 360))
    }

    @Test
    fun outOfRangeAndNegativeRotationsAreNormalizedFirst() {
        assertEquals(1080 to 1920, MediaMetadataRetrieverProbe.displaySize(1920, 1080, -90))
        assertEquals(1080 to 1920, MediaMetadataRetrieverProbe.displaySize(1920, 1080, 450))
        assertEquals(1920 to 1080, MediaMetadataRetrieverProbe.displaySize(1920, 1080, -180))
    }
}
