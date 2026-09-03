package com.calcplus.calculator.feature.gallery

import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.calcplus.calculator.R
import java.io.File

/**
 * Owns the one [ExoPlayer] a video page may have. Kept out of the composable so
 * [PlaybackTeardownPolicy] can drive it through the plain [VaultVideoPlayback]
 * interface (the tests drive a fake through the same interface).
 *
 * Nothing here ever calls `play()`: [prepare] loads the item paused, and the
 * user starts it from the controller. That is invariant (1) of the policy.
 */
@OptIn(UnstableApi::class)
class VaultVideoPlaybackBox(private val player: ExoPlayer) : VaultVideoPlayback {
    private var hasItem = false

    val exoPlayer: ExoPlayer get() = player

    /** Loads [file] paused. Idempotent — a recomposition must not restart it. */
    fun prepare(file: File) {
        if (hasItem) return
        player.setMediaItem(MediaItem.fromUri(file.toUri()))
        player.playWhenReady = false
        player.prepare()
        hasItem = true
    }

    override fun pausePlayback() {
        player.playWhenReady = false
        player.pause()
    }

    override fun releasePlayback() {
        pausePlayback()
        player.clearMediaItems()
        hasItem = false
    }

    /** End of life: after this the box is unusable. */
    fun destroy() {
        releasePlayback()
        player.release()
    }
}

/**
 * One video page of the pager (decisions §9).
 *
 * Containment rules, all of them deliberate:
 * - The player exists **only for the current page** — [isCurrent] false renders
 *   a black page and holds no decoder at all, so swiping away tears the
 *   previous page's player down through [DisposableEffect].
 * - `ON_STOP` (backgrounded — the vault locks on the same signal) and `ON_PAUSE`
 *   go through [PlaybackTeardownPolicy], never through ad-hoc lifecycle code.
 * - No Picture-in-Picture anywhere: the manifest declares no
 *   `android:supportsPictureInPicture`, nothing calls
 *   `enterPictureInPictureMode`, and the player takes **no audio focus**
 *   (`handleAudioFocus = false`) and sets no wake mode, so the system has no
 *   reason to treat this as background-capable playback.
 * - The surface is a `TextureView` (see `res/layout/vault_player_view.xml`), so
 *   the activity's unconditional FLAG_SECURE covers the video.
 */
@OptIn(UnstableApi::class)
@Composable
fun VaultVideoPage(file: File, isCurrent: Boolean, modifier: Modifier = Modifier) {
    if (!isCurrent) {
        // No player off-page: nothing to release later, nothing decoding behind
        // the visible page.
        Box(modifier.fillMaxSize().background(Color.Black))
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val playback = remember(file.path) {
        val player = ExoPlayer.Builder(context).build().apply {
            // Media playback, but WITHOUT audio focus: a vault video must never
            // duck or pause other apps, and must never be treated as a
            // background-audio session.
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ false,
            )
            setWakeMode(C.WAKE_MODE_NONE)
            repeatMode = Player.REPEAT_MODE_OFF
        }
        VaultVideoPlaybackBox(player)
    }

    DisposableEffect(playback, file.path) {
        playback.prepare(file)
        onDispose {
            // Page change, back, delete, lock — all of them land here.
            PlaybackTeardownPolicy.apply(PlaybackEvent.DISPOSED, playback)
            playback.destroy()
        }
    }

    DisposableEffect(lifecycleOwner, playback) {
        val observer = LifecycleEventObserver { _, event ->
            val playbackEvent = when (event) {
                Lifecycle.Event.ON_PAUSE -> PlaybackEvent.LIFECYCLE_PAUSED
                Lifecycle.Event.ON_STOP -> PlaybackEvent.LIFECYCLE_STOPPED
                Lifecycle.Event.ON_START -> PlaybackEvent.LIFECYCLE_STARTED
                Lifecycle.Event.ON_RESUME -> PlaybackEvent.LIFECYCLE_RESUMED
                else -> null
            }
            if (playbackEvent != null) PlaybackTeardownPolicy.apply(playbackEvent, playback)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        modifier = modifier.fillMaxSize().background(Color.Black),
        factory = { viewContext ->
            // Inflated from XML so `surface_type="texture_view"` is declarative
            // and reviewable: a SurfaceView would be composited outside the
            // window, where FLAG_SECURE does not reliably cover it.
            val view = LayoutInflater.from(viewContext)
                .inflate(R.layout.vault_player_view, null) as PlayerView
            view.useController = true
            view.setShowNextButton(false)
            view.setShowPreviousButton(false)
            view
        },
        update = { view -> view.player = playback.exoPlayer },
        onRelease = { view -> view.player = null },
    )
}
