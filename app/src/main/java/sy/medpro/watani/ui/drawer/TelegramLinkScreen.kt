package sy.medpro.watani.ui.drawer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import sy.medpro.watani.data.TelegramLinkApi
import sy.medpro.watani.ui.theme.MaroonPrimary

/** حالات شاشة ربط التليجرام الأربع: تحميل / غير مربوط / مربوط / خطأ شبكي. */
private sealed interface TelegramLinkUiState {
    data object Loading : TelegramLinkUiState
    data object NotLinked : TelegramLinkUiState
    data class Linked(val username: String?) : TelegramLinkUiState
    data object Error : TelegramLinkUiState
}

/**
 * ربط التليجرام (بأمر الدكتور، 14 نوفمبر 2026): خيار جانبي اختياري كليًا
 * في اللوحة الجانبية فقط — ليس جزءًا من تسجيل الدخول ولا بوابة على أي
 * قسم. بربط حسابه ببوت وطني تصلك الطالبَ تقارير دورية عن دراسته
 * (أخطاؤه ومواضيع ضعفه وتقدّمه ونشاطه) ومعلومات ونصائح دراسية عبر
 * تليجرامه، وبإمكانه إلغاء الربط متى شاء من هنا، وبدونه يعمل التطبيق
 * كالمعتاد. الحالة تُقرأ عند فتح الشاشة وتُعاد قراءتها عند العودة
 * إليها (ON_RESUME) — فبعد ضغط «ابدأ» في البوت والعودة تظهر الحالة
 * الجديدة بلا أي خطوة إضافية.
 */
@Composable
fun TelegramLinkScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uiState by remember { mutableStateOf<TelegramLinkUiState>(TelegramLinkUiState.Loading) }
    var isLinking by remember { mutableStateOf(false) }
    var isUnlinking by remember { mutableStateOf(false) }
    var showUnlinkConfirm by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    fun loadStatus() {
        scope.launch {
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) {
                uiState = TelegramLinkUiState.Error
                return@launch
            }
            val status = TelegramLinkApi.fetchLinkStatus(user)
            uiState = when {
                status == null -> TelegramLinkUiState.Error
                status.linked -> TelegramLinkUiState.Linked(status.username)
                else -> TelegramLinkUiState.NotLinked
            }
        }
    }

    // قراءة عند الفتح + إعادة قراءة عند كل عودة للشاشة (ON_RESUME).
    LaunchedEffect(Unit) { loadStatus() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) loadStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun startLinking() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            uiState = TelegramLinkUiState.Error
            return
        }
        isLinking = true
        actionMessage = null
        scope.launch {
            val created = TelegramLinkApi.createLinkCode(user)
            isLinking = false
            if (created == null) {
                actionMessage = "تعذر إنشاء رمز الربط — تحقق من الاتصال وحاول مجددًا."
                return@launch
            }
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(created.deepLink)))
                actionMessage = "اضغط «ابدأ» (Start) في البوت ثم عُد إلى هنا — ستتحدث الحالة تلقائيًا عند عودتك."
            } catch (_: Exception) {
                actionMessage = "تعذر فتح تليجرام — ثبّت تطبيق تليجرام أو حدّثه ثم أعد المحاولة."
            }
        }
    }

    fun doUnlink() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            uiState = TelegramLinkUiState.Error
            return
        }
        isUnlinking = true
        actionMessage = null
        scope.launch {
            val ok = TelegramLinkApi.unlink(user)
            isUnlinking = false
            showUnlinkConfirm = false
            if (ok) {
                uiState = TelegramLinkUiState.NotLinked
            } else {
                actionMessage = "تعذر إلغاء الربط — تحقق من الاتصال وحاول مجددًا."
            }
        }
    }

    DrawerScreenScaffold(title = "ربط التليجرام", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Send, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("لماذا تربط تليجرامك؟", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaroonPrimary)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "بربط حسابك هنا ببوت «وطني» على تليجرام تصلك تقارير دورية عن دراستك: أخطاؤك، ومواضيع ضعفك، وتقدّمك ونشاطك — إضافة إلى معلومات ونصائح دراسية مفيدة، كلها على تليجرامك مباشرة.",
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "الربط اختياري تمامًا، وبإمكانك إلغاؤه متى شئت من هذه الشاشة. وبدونه يعمل التطبيق كالمعتاد بكل أقسامه.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        lineHeight = 20.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            when (val state = uiState) {
                TelegramLinkUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaroonPrimary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("جارٍ التحقق من حالة الربط…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                    }
                }
                TelegramLinkUiState.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "تعذر التحقق من حالة الربط — يبدو أن الاتصال غير متاح الآن.",
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    uiState = TelegramLinkUiState.Loading
                                    loadStatus()
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text("إعادة المحاولة", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                TelegramLinkUiState.NotLinked -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("الحالة: غير مربوط بعد", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("حسابك هنا غير مرتبط بأي حساب تليجرام حاليًا.", fontSize = 13.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { startLinking() },
                        enabled = !isLinking,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        if (isLinking) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Default.Send, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ربط الآن", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "سيفتح زر الربط بوت «وطني» على تليجرام: اضغط «ابدأ» (Start) في البوت ثم عُد إلى هنا.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        lineHeight = 20.sp
                    )
                    if (actionMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(actionMessage!!, fontSize = 13.sp, color = MaroonPrimary, lineHeight = 20.sp)
                    }
                }
                is TelegramLinkUiState.Linked -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(26.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("حسابك مربوط ✓", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaroonPrimary)
                            }
                            val linkedUsername = state.username
                            if (!linkedUsername.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                val shown = if (linkedUsername.startsWith("@")) linkedUsername else "@$linkedUsername"
                                Text("حساب التليجرام المرتبط: $shown", fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "ستصلك تقارير دراستك والنصائح على هذا الحساب. يمكنك إلغاء الربط متى شئت.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                lineHeight = 20.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    if (!showUnlinkConfirm) {
                        OutlinedButton(
                            onClick = { showUnlinkConfirm = true },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            border = BorderStroke(1.dp, MaroonPrimary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaroonPrimary),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text("إلغاء الربط", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("إلغاء الربط؟", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "لن تصلك تقارير الدراسة على تليجرام بعد الإلغاء، ويمكنك إعادة الربط في أي وقت.",
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { doUnlink() },
                                    enabled = !isUnlinking,
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    if (isUnlinking) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                                    } else {
                                        Text("تأكيد الإلغاء", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { showUnlinkConfirm = false },
                                    enabled = !isUnlinking,
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    border = BorderStroke(1.dp, MaroonPrimary),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaroonPrimary),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Text("تراجع", fontSize = 15.sp)
                                }
                            }
                        }
                    }
                    if (actionMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(actionMessage!!, fontSize = 13.sp, color = MaterialTheme.colorScheme.error, lineHeight = 20.sp)
                    }
                }
            }
        }
    }
}
