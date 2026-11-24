package sy.medpro.watani.ui.notify

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import sy.medpro.watani.notify.FocusSessionService
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
 *
 * توسيع 1.2.30 (ميزات شاشة القفل): بطاقة العدّاد الدائمة، سؤال
 * اليوم الصباحي بوقته المستقل، وقت مستقل لتنبيه الأخطاء، شريط
 * التركيز الحي، والمنبّه القوي بملء الشاشة مع إرشاد إذنه — كلها
 * بنفس النمط: مفتاح مستقل معطّل افتراضيًا ونفس بوابة الإذن.
 *
 * تنظيم 1.2.31: الشاشة صارت بقسمين معنونين — «التذكير اليومي»
 * (المفتاح + وقته) ثم «الظهور على شاشة القفل» (الميزات الخمس
 * مجموعة تحت اسمها) — بعد شكوى الدكتور أنه لم يجد الميزات أصلًا.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var config by remember { mutableStateOf(ReminderSettings.config(context)) }
    var deniedNote by remember { mutableStateOf(false) }

    // أيّ مفتاح ينتظر نتيجة الإذن (قيمة من مفاتيح applyToggle).
    var pendingToggle by remember { mutableStateOf<String?>(null) }
    var showExplainDialog by remember { mutableStateOf(false) }

    // منتقي الوقت المفتوح الآن: "study" أو "mistakes" أو "question".
    var pickerTarget by remember { mutableStateOf<String?>(null) }

    fun fmtTime(hour: Int, minute: Int): String = String.format("%02d:%02d", hour, minute)

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
            "countdown" -> {
                ReminderSettings.setCountdownCardEnabled(context, enabled)
                // البطاقة تظهر/تختفي فورًا مع مفتاحها.
                if (enabled) StudyNotifications.postCountdownCard(context)
                else StudyNotifications.cancelCountdownCard(context)
            }
            "question" -> ReminderSettings.setQuestionEnabled(context, enabled)
            "focusbar" -> {
                ReminderSettings.setFocusBarEnabled(context, enabled)
                // جلسة جارية الآن تلحقها الخدمة أو تتركها فورًا.
                FocusSessionService.sync(context)
            }
            "strong" -> ReminderSettings.setStrongAlarmEnabled(context, enabled)
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
                        "الإشعارات محلية من جهازك فقط (تذكير الدراسة ومستحقّات مراجعة الأخطاء وميزات شاشة القفل)، ويمكنك إيقافها متى شئت من هنا."
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

    val target = pickerTarget
    if (target != null) {
        val (initHour, initMinute, pickerTitle) = when (target) {
            "mistakes" -> Triple(config.mistakesHour, config.mistakesMinute, "وقت تنبيه مراجعة الأخطاء")
            "question" -> Triple(config.questionHour, config.questionMinute, "وقت سؤال اليوم")
            else -> Triple(config.hour, config.minute, "وقت التذكير اليومي")
        }
        val pickerState = rememberTimePickerState(
            initialHour = initHour,
            initialMinute = initMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { pickerTarget = null },
            title = { Text(pickerTitle) },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    when (target) {
                        "mistakes" -> ReminderSettings.setMistakesTime(context, pickerState.hour, pickerState.minute)
                        "question" -> ReminderSettings.setQuestionTime(context, pickerState.hour, pickerState.minute)
                        else -> ReminderSettings.setTime(context, pickerState.hour, pickerState.minute)
                    }
                    config = ReminderSettings.config(context)
                    ReminderScheduler.rescheduleAll(context)
                    pickerTarget = null
                }) { Text("حفظ") }
            },
            dismissButton = {
                TextButton(onClick = { pickerTarget = null }) { Text("إلغاء") }
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
            // ---------------- قسم: التذكير اليومي ----------------
            // 1.2.31: قسمان بعناوين صريحة — قبل هذا كانت ميزات شاشة
            // القفل مبعثرة بين التذكيرات بلا عنوان يجمعها، فلم يجدها
            // الدكتور أصلًا («وين أشوفها بالتطبيق»).
            SectionHeader(
                title = "التذكير اليومي",
                subtitle = "الإشعار الأساسي الذي يصلك كل يوم في وقت الدراسة."
            )

            // ---------------- تذكير الدراسة اليومي ----------------
            ToggleCard(
                title = "تذكير الدراسة اليومي",
                description = "إشعار يصلك على هاتفك كل يوم في الوقت الذي تختاره — حتى والتطبيق مغلق ودون إنترنت.",
                enabled = config.studyEnabled,
                onToggle = { onToggle("study", it) }
            )

            // ---------------- وقت تذكير الدراسة ----------------
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, RoseOutline),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("وقت تذكير الدراسة", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaroonPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "الساعة ${fmtTime(config.hour, config.minute)} — وقت تذكير الدراسة اليومي (والمنبّه القوي معه). لتنبيه الأخطاء وسؤال اليوم وقتاهما داخل بطاقتيهما بقسم «الظهور على شاشة القفل».",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { pickerTarget = "study" },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("تغيير الوقت", fontSize = 14.sp)
                    }
                }
            }

            // ---------------- قسم: الظهور على شاشة القفل ----------------
            SectionHeader(
                title = "الظهور على شاشة القفل",
                subtitle = "ميزات تجعل التطبيق حاضرًا على شاشة قفل هاتفك — كل واحدة بمفتاحها ووقتها، وكلها معطّلة حتى تفعّلها بنفسك."
            )

            // ---------------- المنبّه القوي بملء الشاشة ----------------
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, RoseOutline),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("منبّه الدراسة القوي (ملء الشاشة)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaroonPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "بدل الإشعار العادي: في وقت تذكير الدراسة تضيء شاشة كاملة فوق القفل «حان وقت الدراسة» مع هدف اليوم وزر بدء أو تأجيل عشر دقائق. يعمل مع تذكير الدراسة اليومي حين يكون مفعّلًا.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(if (config.strongAlarmEnabled) "مفعّل" else "معطّل", fontSize = 14.sp)
                        Switch(
                            checked = config.strongAlarmEnabled,
                            onCheckedChange = { onToggle("strong", it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaroonPrimary)
                        )
                    }
                    if (config.strongAlarmEnabled &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                        !StudyNotifications.canUseFullScreenIntent(context)
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "ينقصه إذن «إشعارات ملء الشاشة» من نظام هاتفك — بدونه يصلك التذكير إشعارًا عاديًا. فعّله من إعدادات النظام:",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    // Sweep fix L-5: the failure used to be
                                    // swallowed, leaving a seemingly dead
                                    // button; mirror the Toast feedback the
                                    // share flows already use.
                                    android.widget.Toast.makeText(
                                        context,
                                        "تعذّرت فتح إعدادات النظام — أعد المحاولة.",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text("فتح إعدادات ملء الشاشة", fontSize = 14.sp)
                        }
                    }
                }
            }

            // ---------------- تنبيه مراجعة الأخطاء ----------------
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
                        "إشعار يومي واحد بعدد أسئلة «بنك أخطائي» التي حان وقت مراجعتها — لا يُرسل إن لم يوجد مستحقّات. زره «ابدأ المراجعة» يفتح جلسة المراجعة مباشرة.",
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
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("وقته: الساعة ${fmtTime(config.mistakesHour, config.mistakesMinute)}", fontSize = 13.sp)
                        TextButton(onClick = { pickerTarget = "mistakes" }) { Text("تغيير") }
                    }
                }
            }

            // ---------------- سؤال اليوم صباحًا ----------------
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, RoseOutline),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("سؤال اليوم صباحًا", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaroonPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "كل صباح يصلك سؤال اليوم على شاشة القفل بنصّه ومادته، والنقر عليه يفتح شاشة «التحدي اليومي» لحلّه فورًا. يحتاج بنوكًا محمّلة على جهازك.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(if (config.questionEnabled) "مفعّل" else "معطّل", fontSize = 14.sp)
                        Switch(
                            checked = config.questionEnabled,
                            onCheckedChange = { onToggle("question", it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaroonPrimary)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("وقته: الساعة ${fmtTime(config.questionHour, config.questionMinute)}", fontSize = 13.sp)
                        TextButton(onClick = { pickerTarget = "question" }) { Text("تغيير") }
                    }
                }
            }

            // ---------------- بطاقة العدّاد الدائمة ----------------
            ToggleCard(
                title = "بطاقة العدّاد على شاشة القفل",
                description = "بطاقة ثابتة صامتة أعلى إشعارات القفل: كم بقي على الامتحان الوطني وشريط تقدم هدف اليوم — تتحدث وحدها عند منتصف الليل وبعد جلساتك وعند فتح التطبيق.",
                enabled = config.countdownCardEnabled,
                onToggle = { onToggle("countdown", it) }
            )

            // ---------------- شريط التركيز الحي ----------------
            ToggleCard(
                title = "شريط التركيز على شاشة القفل",
                description = "أثناء جلسة التركيز (البومودورو) يعيش عدّادها على شاشة القفل مع زرّي إيقاف مؤقت وإنهاء، والجلسة لا تموت إذا أُغلق التطبيق.",
                enabled = config.focusBarEnabled,
                onToggle = { onToggle("focusbar", it) }
            )

            if (deniedNote) {
                Text(
                    "إشعارات التطبيق غير مفعّلة من النظام بعد — فعّلها من إعدادات هاتفك (التطبيقات ← Med Pro ← الإشعارات) ثم أعد تشغيل المفتاح هنا.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * عنوان قسم صريح داخل الشاشة (بناء 1.2.31): «التذكير اليومي» ثم
 * «الظهور على شاشة القفل» — حتى يجد الطالب ميزات القفل مجموعةً
 * تحت اسمها بدل بعثرتها بين بطاقات التذكير.
 */
@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaroonPrimary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            subtitle,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

/** بطاقة مفتاح بسيطة بنمط شاشة الإشعارات نفسه (عنوان + وصف + مفتاح). */
@Composable
private fun ToggleCard(
    title: String,
    description: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, RoseOutline),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaroonPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                description,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(if (enabled) "مفعّل" else "معطّل", fontSize = 14.sp)
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = MaroonPrimary)
                )
            }
        }
    }
}
