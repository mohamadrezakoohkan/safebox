package com.calcplus.calculator.feature.gallery

/**
 * Anything that owns a running vault video and can be stopped. The pager's
 * ExoPlayer wrapper conforms in production; the tests conform a recording fake,
 * so the containment rules below are exercised without Media3 or a real
 * surface. Android twin of the iOS `VaultVideoPlayback`.
 */
interface VaultVideoPlayback {
    /** Stop playing, keep the loaded item (and its position). */
    fun pausePlayback()

    /** Stop playing AND drop the item, so no decoder and no surface survive. */
    fun releasePlayback()
}

/** Every reason vault video playback is reconsidered. */
enum class PlaybackEvent {
    /** The pager swiped to a different page: this page is no longer the current one. */
    PAGE_CHANGED,

    /** The player composable left the composition (back, a delete, a lock). */
    DISPOSED,

    /**
     * `Lifecycle.Event.ON_PAUSE` — the activity lost focus but is still visible
     * (a system dialog, a split-screen handoff, the power menu). Audible
     * playback must not continue, but the app has not gone away, so the item is
     * kept and the user can resume deliberately.
     */
    LIFECYCLE_PAUSED,

    /**
     * `Lifecycle.Event.ON_STOP` — the app is backgrounded. The vault locks on
     * this too, but the player must not wait for the lock to reach it.
     */
    LIFECYCLE_STOPPED,

    /** `Lifecycle.Event.ON_START`. */
    LIFECYCLE_STARTED,

    /** `Lifecycle.Event.ON_RESUME`. */
    LIFECYCLE_RESUMED,
}

/** What the player must do about an event. */
enum class PlaybackTeardown {
    /** Leave the player alone. Only ever the answer for "the app came back". */
    NONE,
    PAUSE,

    /** Pause AND drop the item. */
    RELEASE,
}

/**
 * The containment rule for vault video (decisions §9), as a pure function so it
 * can be pinned by a unit test rather than trusted to a view's lifecycle.
 * Mirrors the iOS `PlaybackTeardownPolicy`.
 *
 * Two invariants the tests enforce:
 * 1. **No event ever starts or resumes playback.** Play is a user action, and
 *    only a user action — [PlaybackTeardown.NONE] means "do nothing", never
 *    "resume".
 * 2. **Everything that takes the video off screen releases it** — nothing is
 *    left decoding behind the recents cover, behind another page, or after a
 *    lock. Picture-in-Picture is impossible by construction (the manifest
 *    declares no `supportsPictureInPicture` and nothing calls
 *    `enterPictureInPictureMode`), so a released player cannot escape the app
 *    either.
 */
object PlaybackTeardownPolicy {
    fun action(event: PlaybackEvent): PlaybackTeardown = when (event) {
        PlaybackEvent.PAGE_CHANGED,
        PlaybackEvent.DISPOSED,
        PlaybackEvent.LIFECYCLE_STOPPED,
        -> PlaybackTeardown.RELEASE

        PlaybackEvent.LIFECYCLE_PAUSED -> PlaybackTeardown.PAUSE

        PlaybackEvent.LIFECYCLE_STARTED,
        PlaybackEvent.LIFECYCLE_RESUMED,
        -> PlaybackTeardown.NONE
    }

    /**
     * Applies the decided action. Returns it, so a caller (or a test) can see
     * what happened without reaching into the player.
     */
    fun apply(event: PlaybackEvent, playback: VaultVideoPlayback): PlaybackTeardown {
        val action = action(event)
        when (action) {
            PlaybackTeardown.NONE -> Unit
            PlaybackTeardown.PAUSE -> playback.pausePlayback()
            PlaybackTeardown.RELEASE -> playback.releasePlayback()
        }
        return action
    }
}
