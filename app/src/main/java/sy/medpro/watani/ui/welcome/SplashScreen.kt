package sy.medpro.watani.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import sy.medpro.watani.R

/**
 * Video splash — every cold start (Nov 2026, doctor's order).
 *
 * Replaces the old first-run-only welcome screen (abolished): no
 * buttons, no sign-in prompt, no texts over the video — the video
 * itself is the whole screen. Tap skips, the end advances, and any
 * playback error advances too, so the student can never be stuck on
 * a black screen. Sign-in still happens where it is needed (the
 * activation prompt on restricted sections), never as a gate here.
 *
 * Seamless colour: the box colour (#1A1212) is the video's own edge
 * colour, and the PlayerView ZOOMs (crop) so video pixels reach every
 * screen edge with no letterbox bars on any aspect ratio. The launch
 * window behind it uses the artwork tile's exact colour (#2D1015),
 * so the whole cold-start sequence stays in one dark family.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSplashScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var doneCalled by remember { mutableStateOf(false) }
    fun finish() {
        if (!doneCalled) {
            doneCalled = true
            onDone()
        }
    }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(
                MediaItem.fromUri(
                    "android.resource://${context.packageName}/${R.raw.welcome_logo}"
                )
            )
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) finish()
                }

                override fun onPlayerError(error: PlaybackException) {
                    finish()
                }
            })
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1212))
            .clickable { finish() }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
