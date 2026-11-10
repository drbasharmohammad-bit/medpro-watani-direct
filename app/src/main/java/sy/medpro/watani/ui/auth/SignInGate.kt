package sy.medpro.watani.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import sy.medpro.watani.ui.theme.MaroonPrimary

/**
 * Entry gate again (build 1.2.3, doctor's order 11 Nov 2026 — supersedes
 * the Team 1 "no sign-in wall" note): MainActivity shows this screen
 * after the splash whenever there is NO locally persisted Google
 * session. A saved session never reaches it — the gate checks local
 * session presence only, never the network — so daily offline entry
 * for signed-in users is untouched. After sign-in, free sections open
 * at once; Questions/Library keep their activation gate as before.
 * The Google flow delegates to [GoogleSignInHelper], the same helper
 * used by the profile screen and the activation prompt.
 */
@Composable
fun SignInGate(onSignedIn: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun signInWithGoogle() {
        scope.launch {
            isLoading = true
            errorMessage = null
            val result = GoogleSignInHelper.signInWithGoogle(context)
            isLoading = false
            result.fold(
                onSuccess = { onSignedIn() },
                onFailure = { errorMessage = it.localizedMessage }
            )
        }
    }

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
                Surface(
                    shape = RoundedCornerShape(50.dp),
                    color = MaroonPrimary.copy(alpha = 0.1f),
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.MedicalServices,
                            contentDescription = null,
                            tint = MaroonPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Med Pro - وطني",
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = MaroonPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "نجاحك في الوطني يبدأ من هنا",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                if (errorMessage != null) {
                    Text(
                        errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (isLoading) {
                    CircularProgressIndicator(color = MaroonPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("جاري تسجيل الدخول...", fontSize = 13.sp)
                } else {
                    Button(
                        onClick = { signInWithGoogle() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                        shape = MaterialTheme.shapes.medium) {
                        Text("Sign in with Google (تسجيل الدخول بواسطة جوجل)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
