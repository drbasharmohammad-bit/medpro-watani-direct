package sy.medpro.watani.ui.activation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import sy.medpro.watani.data.WorkerActivationApi
import sy.medpro.watani.ui.auth.GoogleSignInHelper
import sy.medpro.watani.ui.theme.MaroonPrimary

/**
 * Team 1 — activation prompt shown when a non-activated user opens the
 * Questions section or the Library.
 *
 * Flow: Google sign-in (only if not signed in) -> code entry ->
 * POST {WORKER_BASE}/v1/activate -> on success the caller unlocks the
 * requested section. On failure the EXACT server error text is shown
 * (WorkerActivationApi returns the `error` field verbatim).
 *
 * If a previous token merely expired, one silent refresh is attempted
 * first; only when it fails is the code asked for again.
 */
@Composable
fun ActivationPromptDialog(
    onDismiss: () -> Unit,
    onActivated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser) }
    var code by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    // Silent refresh for an expired token before asking for the code again.
    LaunchedEffect(user) {
        val u = user ?: return@LaunchedEffect
        if (WorkerActivationApi.hasExpiredToken(context)) {
            isLoading = true
            message = "جاري تجديد التفعيل..."
            isError = false
            val refreshed = WorkerActivationApi.tryRefresh(context)
            isLoading = false
            if (refreshed) {
                onActivated()
            } else {
                message = "انتهى التفعيل السابق — أدخل كود تفعيل لتجديده."
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("تفعيل مطلوب", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "هذا القسم (بنك الأسئلة أو المكتبة) يحتاج كود تفعيل. باقي أقسام التطبيق مجانية.",
                    fontSize = 14.sp
                )
                if (user == null) {
                    Text(
                        "سجّل الدخول بحساب Google أولاً، ثم أدخل كود التفعيل.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Button(
                        onClick = {
                            isLoading = true
                            message = null
                            isError = false
                            scope.launch {
                                val result = GoogleSignInHelper.signInWithGoogle(context)
                                isLoading = false
                                result.fold(
                                    onSuccess = {
                                        user = FirebaseAuth.getInstance().currentUser
                                        if (WorkerActivationApi.isActivated(context)) {
                                            onActivated()
                                        }
                                    },
                                    onFailure = {
                                        message = it.localizedMessage
                                        isError = true
                                    }
                                )
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("تسجيل الدخول بحساب Google")
                    }
                } else {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("كود التفعيل") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Button(
                        onClick = {
                            val trimmed = code.trim()
                            if (trimmed.isBlank()) {
                                message = "يرجى إدخال كود تفعيل صالح أولاً."
                                isError = true
                                return@Button
                            }
                            val currentUser = user ?: return@Button
                            isLoading = true
                            message = "جاري التحقق من الكود عبر الخادم..."
                            isError = false
                            scope.launch {
                                val (success, msg) = WorkerActivationApi.activate(context, currentUser, trimmed)
                                isLoading = false
                                if (success) {
                                    onActivated()
                                } else {
                                    // Exact server error text, shown as returned.
                                    message = msg
                                    isError = true
                                }
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("التحقق والتفعيل", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (message != null) {
                    Text(
                        message!!,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Start
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("إلغاء")
            }
        }
    )
}
