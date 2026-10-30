package sy.medpro.watani.ui.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import sy.medpro.watani.R
import sy.medpro.watani.ui.auth.GoogleSignInHelper
import sy.medpro.watani.ui.theme.MaroonPrimary

/**
 * Team 1 — welcome screen, the new entry point of the app.
 *
 * No sign-in is required to see it or to enter: [onEnter] opens the main
 * interface directly. The hero image is the app icon's own photo
 * (R.drawable.ic_launcher_artwork — the doctor's own image, used as-is,
 * never redesigned).
 *
 * Crash fix (30 Oct 2026): the first version loaded R.mipmap.ic_launcher
 * via painterResource. On API 26+ that ID resolves to the ADAPTIVE icon
 * (mipmap-anydpi-v26/ic_launcher.xml), which Compose cannot draw, so the
 * app died on the very first frame (grey window, then exit). The artwork
 * raster loads safely on every API level; the dark box behind it
 * reproduces the icon's own background colour (#2D1015).
 */
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaroonPrimary)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // App icon's own photo (raster artwork, never the adaptive
                // mipmap — see the KDoc above). The dark box reproduces the
                // icon's own background colour.
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(androidx.compose.ui.graphics.Color(0xFF2D1015)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_artwork),
                        contentDescription = "أيقونة تطبيق Med Pro - وطني",
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Med Pro - وطني",
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = MaroonPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "نجاحك في الوطني يبدأ من هنا",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "تصفح الأقسام المجانية مباشرة — بنك الأسئلة والمكتبة فقط يحتاجان كود تفعيل.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onEnter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                    shape = MaterialTheme.shapes.medium) {
                    Text("الدخول إلى التطبيق", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (signedInName != null) {
                    Text(
                        "تم تسجيل الدخول: $signedInName",
                        fontSize = 13.sp,
                        color = MaroonPrimary,
                        textAlign = TextAlign.Center
                    )
                } else if (isSigningIn) {
                    CircularProgressIndicator(color = MaroonPrimary, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("جاري تسجيل الدخول...", fontSize = 13.sp)
                } else {
                    OutlinedButton(border = BorderStroke(1.dp, MaroonPrimary),
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
                        shape = MaterialTheme.shapes.medium) {
                        Text("تسجيل الدخول بحساب Google (اختياري)", fontSize = 14.sp)
                    }
                }
                if (signInError != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        signInError!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
