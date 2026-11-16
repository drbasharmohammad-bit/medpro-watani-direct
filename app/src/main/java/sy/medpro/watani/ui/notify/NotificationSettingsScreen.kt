package sy.medpro.watani.ui.notify

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import sy.medpro.watani.data.ReminderSettings
import sy.medpro.watani.notify.ReminderScheduler
import sy.medpro.watani.notify.StudyNotifications
import sy.medpro.watani.ui.drawer.DrawerScreenScaffold
import sy.medpro.watani.ui.theme.MaroonPrimary
import sy.medpro.watani.ui.theme.RoseOutline

/**
 * شاشة «الإشعارات والتذكير» (بناء 1.2.23).
 *
 * تفعيل أي تنبيه لأول مرة يمرّ بإذن صريح: شرح داخل التطبيق أولًا،
 * ثم طلب إذن إشعارات النظام (POST_NOTIFICATIONS على أندرويد 13+)
 * بموافقة الطالب. بلا إذن يبقى المفتاح مطفأً ولا يُعاد الإلحاح —
 * إعادة المحاولة بيد الطالب فقط. كل تنبيه بمفتاح مستقل، والوقت
 * واحد يختاره الطالب.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var config by remember { mutableStateOf(ReminderSettings.config(context)) }
    var showTimePicker by remember { mutableStateOf(false) }
    var deniedNote by remember { mutableStateOf(false) }

    // أيّ مفتاح ينتظر نتيجة الإذن: "study" أو "mistakes".
    var pendingToggle by remember { mutableStateOf<String?>(null) }
    var showExplainDialog by remember { mutableStateOf(false) }

    fun notificationsGranted(): Boolean {
        if (!StudyNotifications.canPost(context)) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun applyToggle(kind: String, enabled: Boolean) {
        when (kind) {
            "study" -> ReminderSettings.setStudyEnabled(context, enabled)
            "mistakes" -> ReminderSettings.setMistakesEnabled(context, enabled)
        }
        config = ReminderSettings.config(context)
        ReminderScheduler.rescheduleAll(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val kind = pendingToggle
        pendingToggle = null
        if (granted && kind != null) {
            applyToggle(kind, true)
            deniedNote = false
        } else {
            deniedNote = true
        }
    }

    fun onToggle(kind: String, wantOn: Boolean) {
        if (!wantOn) {
            applyToggle(kind, false)
            return
        }
        if (notificationsGranted()) {
            applyToggle(kind, true)
            deniedNote = false
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // شرح أولًا، ثم طلب النظام — مرة واحدة لكل محاولة طالب.
            pendingToggle = kind
            showExplainDialog = true
        } else {
            // أندرويد أقدم: لا إذن تشغيل، لكن إشعارات التطبيق قد تكون
            // معطلة من إعدادات النظام — لا نفترض النجاح.
            deniedNote = true
        }
    }

    if (showExplainDialog) {
        AlertDialog(
            onDismissRequest = {
                showExplainDialog = false
                pendingToggle = null
            },
            title = { Text("إذن الإشعارات") },
            text = {
                Text(
                    "ليصلك التذكير على شاشة هاتفك حتى والتطبيق مغلق، يحتاج التطبيق إذن الإشعارات من النظام. " +
                        "الإشعارات محلية من جهازك فقط (تذكير الدراسة ومستحقّات مراجعة الأخطاء)، ويمكنك إيقافها متى شئت من هنا."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showExplainDialog = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) { Text("متابعة") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExplainDialog = false
                    pendingToggle = null
                }) { Text("ليس الآن") }
            }
        )
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = config.hour,
            initialMinute = config.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("وقت التذكير اليومي") },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    ReminderSettings.setTime(context, pickerState.hour, pickerState.minute)
                    config = ReminderSettings.config(context)
                    ReminderScheduler.rescheduleAll(context)
                    showTimePicker = false
                }) { Text("حفظ") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("إلغاء") }
            }
        )
    }

    DrawerScreenScaffold(title = "الإشعارات والتذكير", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, RoseOutline),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("تذكير الدراسة اليومي", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaroonPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "إشعار يصلك على هاتفك كل يوم في الوقت الذي تختاره — حتى والتطبيق مغلق ودون إنترنت.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(if (config.studyEnabled) "مفعّل" else "معطّل", fontSize = 14.sp)
                        Switch(
                            checked = config.studyEnabled,
                            onCheckedChange = { onToggle("study", it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaroonPrimary)
                        )
                    }
                }
            }

            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, RoseOutline),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("تنبيه مراجعة الأخطاء", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaroonPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "إشعار يومي واحد بعدد أسئلة «بنك أخطائي» التي حان وقت مراجعتها — لا يُرسل إن لم يوجد مستحقّات.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(if (config.mistakesEnabled) "مفعّل" else "معطّل", fontSize = 14.sp)
                        Switch(
                            checked = config.mistakesEnabled,
                            onCheckedChange = { onToggle("mistakes", it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaroonPrimary)
                        )
                    }
                }
            }

            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, RoseOutline),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("وقت التذكير", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaroonPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "الساعة ${String.format("%02d:%02d", config.hour, config.minute)} — تنطبق على التنبيهين معًا.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("تغيير الوقت", fontSize = 14.sp)
                    }
                }
            }

            if (deniedNote) {
                Text(
                    "إشعارات التطبيق غير مفعّلة من النظام بعد — فعّلها من إعدادات هاتفك (التطبيقات ← وطني ← الإشعارات) ثم أعد تشغيل المفتاح هنا.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
