package sy.medpro.watani.ui.library

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import sy.medpro.watani.data.EncryptedHlsDataSource
import sy.medpro.watani.data.GatewayHlsDataSource
import sy.medpro.watani.data.playbackErrorMessage
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
 *
 * Hardening (build 1.2.31, doctor's device report): a playback error
 * shows a friendly Arabic sentence with «إعادة المحاولة» (re-prepare
 * from the saved position) and «رجوع» — never a raw exception text
 * on a dead black screen; buffering shows a spinner instead of
 * reading as a hang; the watch position is snapshotted (saveable,
 * per video) and resumed on return instead of restarting from
 * zero; leaving the app pauses playback and returning resumes it
 * only if it was playing; transient IO load errors get extra
 * retries before surfacing.
 */
@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    videoFile: File?,
    title: String,
    onBack: () -> Unit,
    videoFileUrl: String? = null,
    subtitleFile: File? = null,
    subtitleUrl: String? = null,
    onFullscreenChange: ((Boolean) -> Unit)? = null
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
    // 1.2.31: the error is kept as its media3 CODE and rendered as a
    // friendly Arabic sentence (playbackErrorMessage) with a retry
    // action — the raw exception text never reaches the student.
    var playerErrorCode by remember { mutableStateOf<Int?>(null) }
    // Buffering visibility (1.2.31): while the player is loading or
    // re-buffering, a spinner says so — a black frame alone reads
    // as a hang («عم يعلق»).
    var isBuffering by remember { mutableStateOf(false) }
    // Resume state (1.2.31): the last watched position and whether
    // playback was running, kept in saveable state per video. The
    // player is recreated whenever this screen is re-entered (and
    // after process death) — before this, every return restarted
    // the video from zero.
    var savedPositionMs by rememberSaveable(videoUri) { mutableLongStateOf(0L) }
    var savedPlayWhenReady by rememberSaveable(videoUri) { mutableStateOf(true) }
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
        onDispose { setSystemBarsVisible(true); onFullscreenChange?.invoke(false) }
    }
    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
        setSystemBarsVisible(true)
        showControls = true
    }
    // True fullscreen needs the main Scaffold bars hidden too.
    LaunchedEffect(isFullscreen) {
        onFullscreenChange?.invoke(isFullscreen)
    }

    val player = remember(videoUri, subtitleUri) {
        // Phase-3 (§4.2 + §6): THE DataSource chain for all playback —
        // the single touch point in this screen, its external contract
        // is unchanged:
        //  - file:// (session playlist temps, SRT temps) is served by
        //    DefaultDataSource itself;
        //  - watani-enc:/// segments of a downloaded tree are decrypted
        //    in memory by EncryptedHlsDataSource;
        //  - http(s):// streaming (playlists, segments, subtitle URL)
        //    goes through GatewayHlsDataSource, which rebuilds every
        //    request via WorkerActivationApi.contentRequestForUrl with
        //    the activation token — no direct fetch from the public
        //    origin remains in any playback path.
        val gatewayFactory = GatewayHlsDataSource.Factory(context)
        val encFactory = EncryptedHlsDataSource.Factory(context, gatewayFactory)
        val dataSourceFactory = DefaultDataSource.Factory(context, encFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)
            // 1.2.31: transient IO failures (a dropped connection
            // mid-segment on a weak network) get more load retries
            // before the player gives up and reports an error.
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(5))
        ExoPlayer.Builder(context).setMediaSourceFactory(mediaSourceFactory).build().apply {
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
                        playerErrorCode = error.errorCode
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        isBuffering = playbackState == Player.STATE_BUFFERING ||
                            playbackState == Player.STATE_IDLE
                        if (playbackState == Player.STATE_READY) {
                            // A successful (re)load clears any error
                            // the retry path recovered from.
                            playerErrorCode = null
                        }
                        if (playbackState == Player.STATE_ENDED) {
                            // A finished video restarts from the top
                            // next time — never "resume" into the end.
                            savedPositionMs = 0L
                        }
                    }
                })
                // Resume (1.2.31): return to where the student left
                // this video instead of restarting from zero.
                if (savedPositionMs > 0L) seekTo(savedPositionMs)
                prepare()
                playWhenReady = savedPlayWhenReady
            }
        }
    }

    // Position snapshot (1.2.31): while this player lives, refresh
    // the saved position every couple of seconds so a later return
    // (or a process death the saveable state survives) resumes
    // within seconds of where playback actually stopped.
    LaunchedEffect(player) {
        while (true) {
            delay(2_000)
            if (player.playbackState != Player.STATE_ENDED) {
                val pos = player.currentPosition
                if (pos > 0L) savedPositionMs = pos
            }
            savedPlayWhenReady = player.playWhenReady
        }
    }

    // Background behaviour (1.2.31): leaving the app pauses the
    // video (it used to keep playing audio in the background);
    // coming back resumes only if it was playing when left.
    var autoResume by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (player.playWhenReady) {
                        autoResume = true
                        player.playWhenReady = false
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (autoResume) {
                        autoResume = false
                        player.playWhenReady = true
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
        onDispose {
            // Last-chance snapshot before release (1.2.31): leaving
            // the player keeps the exact stop point for the return.
            if (player.playbackState != Player.STATE_ENDED) {
                val pos = player.currentPosition
                if (pos > 0L) savedPositionMs = pos
            }
            savedPlayWhenReady = player.playWhenReady || autoResume
            player.release()
        }
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
                title = { Text(title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = Color.White) },
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
        // Buffering indicator (1.2.31): loading/re-buffering is
        // shown, never mistaken for a hang.
        if (isBuffering && playerErrorCode == null && videoUri != null) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
        // Error surface (1.2.31): a friendly sentence, a RETRY that
        // re-prepares from the saved position, and a way back —
        // before this, an error was a dead text on a black screen
        // (and with the controls hidden, not even a visible exit).
        val errorCode = playerErrorCode
        if (errorCode != null || videoUri == null) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        if (videoUri == null) "تعذّر العثور على ملف الفيديو."
                        else playbackErrorMessage(errorCode ?: 0),
                        color = Color.White
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.Center) {
                        if (videoUri != null) {
                            Button(
                                onClick = {
                                    playerErrorCode = null
                                    val resumeAt = maxOf(savedPositionMs, player.currentPosition)
                                    if (resumeAt > 0L) player.seekTo(resumeAt)
                                    player.prepare()
                                    player.playWhenReady = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
                            ) {
                                Text("إعادة المحاولة", color = Color.White)
                            }
                            Spacer(Modifier.width(12.dp))
                        }
                        OutlinedButton(onClick = onBack) {
                            Text("رجوع", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
