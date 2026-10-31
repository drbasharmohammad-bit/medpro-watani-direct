package sy.medpro.watani.ui.welcome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.launch
import sy.medpro.watani.R
import sy.medpro.watani.ui.auth.GoogleSignInHelper

/**
 * Team 1 — welcome screen, the new entry point of the app.
 *
 * No sign-in is required to see it or to enter: [onEnter] opens the main
 * interface directly.
 *
 * The hero is the Med Pro logo animation (R.raw.welcome_logo, portrait
 * 720x1280, converted from the doctor's landscape video): it plays once
 * over the full screen and holds its final clean-logo frame behind the
 * title and buttons. It replaced the earlier scale/fade hero animation
 * (Oct 2026, the doctor's word: the old one was not nice). If playback
 * ever fails, the screen simply stays dark and fully functional — never
 * a black-screen dead end.
 *
 * The background colour (#1A1212) is the video's own edge colour, so the
 * letterbox bars (phones are slightly taller than 9:16) are invisible.
 */
@OptIn(UnstableApi::class)
@Composable
fun WelcomeScreen(
    onEnter: () -> Unit,
    onSignedIn: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSigningIn by remember { mutableStateOf(false) }
    var signInError by remember { mutableStateOf<String?>(null) }
    var signedInName by remember { mutableStateOf<String?>(null) }
    var videoError by remember { mutableStateOf(false) }

    val videoBg = Color(0xFF1A1212)
    val gold = Color(0xFFD4AF37)

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(
                MediaItem.fromUri(
                    "android.resource://${context.packageName}/${R.raw.welcome_logo}"
                )
            )
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    videoError = true
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
            .background(videoBg)
    ) {
        if (!videoError) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Med Pro - وطني",
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "نجاحك في الوطني يبدأ من هنا",
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "تصفح الأقسام المجانية مباشرة — بنك الأسئلة والمكتبة فقط يحتاجان كود تفعيل.",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onEnter,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = videoBg),
                shape = MaterialTheme.shapes.medium) {
                Text("الدخول إلى التطبيق", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (signedInName != null) {
                Text(
                    "تم تسجيل الدخول: $signedInName",
                    fontSize = 13.sp,
                    color = gold,
                    textAlign = TextAlign.Center
                )
            } else if (isSigningIn) {
                CircularProgressIndicator(color = gold, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("جاري تسجيل الدخول...", fontSize = 13.sp, color = Color.White)
            } else {
                OutlinedButton(border = BorderStroke(1.dp, Color.White),
                    onClick = {
                        signInError = null
                        isSigningIn = true
                        scope.launch {
                            val result = GoogleSignInHelper.signInWithGoogle(context)
                            isSigningIn = false
                            result.fold(
                                onSuccess = {
                                    signedInName =
                                        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName
                                            ?: "حساب Google"
                                    onSignedIn()
                                },
                                onFailure = { signInError = it.localizedMessage }
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    shape = MaterialTheme.shapes.medium) {
                    Text("تسجيل الدخول بحساب Google (اختياري)", fontSize = 14.sp)
                }
            }
            if (signInError != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    signInError!!,
                    color = Color(0xFFFF8A80),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
