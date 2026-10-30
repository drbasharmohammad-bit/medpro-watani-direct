package sy.medpro.watani.ui.library

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import sy.medpro.watani.ui.theme.MaroonPrimary
import java.io.File

/**
 * Video player with toggleable Arabic SRT subtitles (Media3 ExoPlayer).
 *
 * Pairing rule: the subtitle belongs to THIS video only. It arrives either
 * as [subtitleFile] (the locally downloaded side-car for a downloaded video)
 * or as [subtitleUrl] (streamed alongside a streamed video). A subtitle is
 * never shown for the wrong video and never invented: when neither is
 * available, playback simply has no subtitles and no toggle is shown.
 *
 * Offline: a downloaded video plays from its local file and its local SRT
 * file, with no network call during playback. The toggle only enables or
 * disables the text track; it never restarts the video.
 */
@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    videoFile: File?,
    title: String,
    onBack: () -> Unit,
    videoFileUrl: String? = null,
    subtitleFile: File? = null,
    subtitleUrl: String? = null
) {
    val context = LocalContext.current
    var showControls by remember { mutableStateOf(true) }

    val videoUri: Uri? = when {
        videoFile != null -> Uri.fromFile(videoFile)
        !videoFileUrl.isNullOrBlank() -> Uri.parse(videoFileUrl)
        else -> null
    }
    // Local side-car wins for downloaded videos (offline); otherwise the URL.
    val subtitleUri: Uri? = when {
        subtitleFile != null && subtitleFile.exists() -> Uri.fromFile(subtitleFile)
        !subtitleUrl.isNullOrBlank() -> Uri.parse(subtitleUrl)
        else -> null
    }
    val hasSubtitles = subtitleUri != null
    var subtitlesEnabled by remember { mutableStateOf(hasSubtitles) }
    // A playback failure must surface as a message, never as a silent
    // black screen or an unexplained exit (the HLS pilot failure mode).
    var playerError by remember { mutableStateOf<String?>(null) }
    // Fullscreen (tablets + landscape watching): hides the system bars and
    // the app bars; a floating exit button + Back are the way back, and
    // leaving the player always restores the system bars.
    var isFullscreen by remember { mutableStateOf(false) }
    fun setSystemBarsVisible(visible: Boolean) {
        val activity = context as? android.app.Activity ?: return
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        if (visible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    DisposableEffect(Unit) {
        onDispose { setSystemBarsVisible(true) }
    }
    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
        setSystemBarsVisible(true)
        showControls = true
    }

    val player = remember(videoUri, subtitleUri) {
        ExoPlayer.Builder(context).build().apply {
            if (videoUri != null) {
                val itemBuilder = MediaItem.Builder().setUri(videoUri)
                if (subtitleUri != null) {
                    itemBuilder.setSubtitleConfigurations(
                        listOf(
                            MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                                .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                                .setLanguage("ar")
                                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                .build()
                        )
                    )
                }
                setMediaItem(itemBuilder.build())
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        playerError = error.message ?: "تعذر تشغيل الفيديو"
                    }
                })
                prepare()
                playWhenReady = true
            }
        }
    }

    // Toggling subtitles only flips the text track; playback continues.
    LaunchedEffect(subtitlesEnabled, hasSubtitles) {
        if (hasSubtitles) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled)
                .build()
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                }
            },
            update = { view -> view.player = player }
        )

        if (showControls) {
            TopAppBar(
                title = { Text(title, maxLines = 1, color = Color.White) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaroonPrimary)
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(MaroonPrimary.copy(alpha = 0.9f))
                    .padding(16.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasSubtitles) {
                        TextButton(onClick = { subtitlesEnabled = !subtitlesEnabled }) {
                            Icon(
                                if (subtitlesEnabled) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                                null,
                                tint = Color.White
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (subtitlesEnabled) "إيقاف الترجمة" else "تشغيل الترجمة",
                                color = Color.White
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                    }
                    TextButton(onClick = {
                        isFullscreen = true
                        setSystemBarsVisible(false)
                        showControls = false
                    }) {
                        Text("ملء الشاشة", color = Color.White)
                    }
                    Spacer(Modifier.width(16.dp))
                    IconButton(onClick = { showControls = false }) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                }
            }
        }
        if (isFullscreen) {
            Box(
                Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.TopStart
            ) {
                Button(
                    onClick = {
                        isFullscreen = false
                        setSystemBarsVisible(true)
                        showControls = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f))
                ) {
                    Text("خروج من ملء الشاشة", color = Color.White)
                }
            }
        }
        if (playerError != null) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "تعذر تشغيل الفيديو\n$playerError",
                    color = Color.White,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }
    }
}
