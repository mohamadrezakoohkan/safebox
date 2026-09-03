package com.calcplus.calculator.gallery

import com.calcplus.calculator.feature.gallery.PlaybackEvent
import com.calcplus.calculator.feature.gallery.PlaybackTeardown
import com.calcplus.calculator.feature.gallery.PlaybackTeardownPolicy
import com.calcplus.calculator.feature.gallery.VaultVideoPlayback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The disguise/containment rule for vault video (decisions §9), pinned as a pure
 * decision instead of trusted to a view's lifecycle. Mirrors the iOS
 * `PlaybackTeardownTests`.
 */
class PlaybackTeardownPolicyTest {
    /** Records what the policy did, so no Media3 player is needed. */
    private class RecordingPlayback : VaultVideoPlayback {
        var pauses = 0
        var releases = 0
        override fun pausePlayback() {
            pauses += 1
        }

        override fun releasePlayback() {
            releases += 1
        }
    }

    @Test
    fun everythingThatTakesTheVideoOffScreenReleasesIt() {
        val offScreen = listOf(
            PlaybackEvent.PAGE_CHANGED,
            PlaybackEvent.DISPOSED,
            PlaybackEvent.LIFECYCLE_STOPPED,
        )
        for (event in offScreen) {
            assertEquals(
                "$event must release the player",
                PlaybackTeardown.RELEASE,
                PlaybackTeardownPolicy.action(event),
            )
        }
    }

    @Test
    fun losingFocusPausesWithoutDroppingTheItem() {
        assertEquals(
            PlaybackTeardown.PAUSE,
            PlaybackTeardownPolicy.action(PlaybackEvent.LIFECYCLE_PAUSED),
        )
    }

    @Test
    fun comingBackDoesNothing() {
        assertEquals(
            PlaybackTeardown.NONE,
            PlaybackTeardownPolicy.action(PlaybackEvent.LIFECYCLE_STARTED),
        )
        assertEquals(
            PlaybackTeardown.NONE,
            PlaybackTeardownPolicy.action(PlaybackEvent.LIFECYCLE_RESUMED),
        )
    }

    @Test
    fun noEventEverStartsOrResumesPlayback() {
        // Invariant (1): play is a user action and only a user action, so the
        // policy's vocabulary contains nothing that could start playback.
        val actions = PlaybackEvent.entries.map { PlaybackTeardownPolicy.action(it) }
        assertTrue(actions.all { it in setOf(PlaybackTeardown.NONE, PlaybackTeardown.PAUSE, PlaybackTeardown.RELEASE) })
    }

    @Test
    fun applyDrivesThePlayerAccordingToTheDecision() {
        val playback = RecordingPlayback()

        assertEquals(
            PlaybackTeardown.NONE,
            PlaybackTeardownPolicy.apply(PlaybackEvent.LIFECYCLE_RESUMED, playback),
        )
        assertEquals(0, playback.pauses)
        assertEquals(0, playback.releases)

        PlaybackTeardownPolicy.apply(PlaybackEvent.LIFECYCLE_PAUSED, playback)
        assertEquals(1, playback.pauses)
        assertEquals(0, playback.releases)

        PlaybackTeardownPolicy.apply(PlaybackEvent.LIFECYCLE_STOPPED, playback)
        assertEquals(1, playback.releases)

        PlaybackTeardownPolicy.apply(PlaybackEvent.PAGE_CHANGED, playback)
        PlaybackTeardownPolicy.apply(PlaybackEvent.DISPOSED, playback)
        assertEquals(3, playback.releases)
    }

    @Test
    fun backgroundingReleasesTheVideoWithoutWaitingForTheLock() {
        // The vault locks on ON_STOP too, but the player must not depend on it:
        // nothing may still be decoding behind the recents cover.
        val playback = RecordingPlayback()
        PlaybackTeardownPolicy.apply(PlaybackEvent.LIFECYCLE_STOPPED, playback)
        assertEquals(1, playback.releases)
    }
}
