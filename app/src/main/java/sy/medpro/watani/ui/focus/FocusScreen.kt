package sy.medpro.watani.ui.focus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.data.FocusEvent
import sy.medpro.watani.data.FocusMode
import sy.medpro.watani.data.FocusPhase
import sy.medpro.watani.data.FocusTimerEngine
import sy.medpro.watani.ui.theme.GoldAccent
import sy.medpro.watani.ui.theme.MaroonDeep
import sy.medpro.watani.ui.theme.MaroonPrimary

/** المواد الـ27 الرسمية لوسم جلسة التركيز (نفس أسماء بنوك الأسئلة). */
val FOCUS_SUBJECTS: List<String> = listOf(
    "الأذنية", "الأطفال 1", "الأطفال 2", "التوليد", "الجراحة البولية",
    "الجراحة التجميلية", "الجراحة العامة", "الجراحة العصبية", "الجراحة العظمية",
    "الجلدية", "الخمجية", "الدموية", "الرثوية", "الصدرية", "الطب النفسي",
    "العصبية", "العينية", "الغدية والسكري", "القلبية", "الكلية والشوارد",
    "النسائية", "الهضمية والكبد", "جراحة الأطفال", "جراحة الأورام",
    "جراحة الأوعية", "جراحة الصدر", "جراحة القلب"
)

private val MOTIVATION_LINES = listOf(
    "جلسة أُنجزت — خطوة ثابتة نحو هدفك.",
    "أحسنت. الاستمرارية الهادئة أهم من الكمال.",
    "كل جلسة تركيز تقرّبك من يوم الامتحان بثقة أكبر.",
    "تركيزك اليوم هو ما سيتذكره الغد. واصل.",
    "جلسة بعد جلسة تُبنى الجاهزية الحقيقية."
)

/**
 * شاشة التركيز الغامرة (ترقية 1.2.23 لمؤقت البومودورو القائم).
 *
 * سطح عنابي كامل بلا تشتيت: حلقة عدّ كبيرة، اختيار النمط والمادة
 * قبل البدء، إبقاء الشاشة مضيئة اختياري أثناء الجلسة، وسطر ختامي
 * محفّز عند اكتمال الجلسة. كل منطق العدّ في FocusTimerEngine —
 * ممنوع عدّ محلي هنا.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FocusScreen() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { FocusTimerEngine.init(context) }
    val state by FocusTimerEngine.state.collectAsState()

    var selectedSubject by remember { mutableStateOf(state.subject) }
    LaunchedEffect(state.subject) {
        if (selectedSubject == null) selectedSubject = state.subject
    }
    var showMotivation by remember { mutableStateOf(false) }
    LaunchedEffect(state.lastEventAt) {
        if (state.lastEvent == FocusEvent.FOCUS_DONE && state.lastEventAt > 0L) {
            showMotivation = true
        }
    }

    // إبقاء الشاشة مضيئة أثناء جلسة جارية حين فعّلها الطالب.
    val view = LocalView.current
    val keepOn = state.keepScreenOn && state.isRunning
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    val phaseProgress = if (state.phaseTotalSec > 0) {
        1f - (state.timeLeftSec.toFloat() / state.phaseTotalSec.toFloat())
    } else 0f
    val minutes = state.timeLeftSec / 60
    val seconds = state.timeLeftSec % 60
    val isBreak = state.phase == FocusPhase.BREAK

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(MaroonDeep, MaroonPrimary, MaroonDeep))
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isBreak) "وقت الاستراحة" else "جلسة تركيز",
            color = GoldAccent,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
        if (state.subject != null && (state.isRunning || isBreak)) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = state.subject!!,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(248.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    color = Color.White.copy(alpha = 0.16f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = GoldAccent,
                    startAngle = -90f,
                    sweepAngle = 360f * phaseProgress.coerceIn(0f, 1f),
                    useCenter = false,
                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format("%02d:%02d", minutes, seconds),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 56.sp
                )
                Text(
                    text = when (state.mode) {
                        FocusMode.CLASSIC -> "كلاسيك ${state.mode.focusMin}/${state.mode.breakMin}"
                        FocusMode.DEEP -> "تركيز عميق ${state.mode.focusMin}/${state.mode.breakMin}"
                        FocusMode.CUSTOM -> "مخصص ${state.customFocusMin}/${state.customBreakMin}"
                    },
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }

        if (showMotivation && state.lastEvent == FocusEvent.FOCUS_DONE) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                color = Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = MOTIVATION_LINES[state.sessionsToday % MOTIVATION_LINES.size],
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (state.isIdle) {
            // إعداد ما قبل البدء: النمط، المدد المخصصة، المادة، الإضاءة.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.mode == FocusMode.CLASSIC,
                    onClick = { FocusTimerEngine.setMode(context, FocusMode.CLASSIC) },
                    label = { Text("كلاسيك 25/5") },
                    colors = focusChipColors()
                )
                FilterChip(
                    selected = state.mode == FocusMode.DEEP,
                    onClick = { FocusTimerEngine.setMode(context, FocusMode.DEEP) },
                    label = { Text("عميق 50/10") },
                    colors = focusChipColors()
                )
                FilterChip(
                    selected = state.mode == FocusMode.CUSTOM,
                    onClick = { FocusTimerEngine.setMode(context, FocusMode.CUSTOM) },
                    label = { Text("مخصص") },
                    colors = focusChipColors()
                )
            }

            if (state.mode == FocusMode.CUSTOM) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DurationStepper(
                        label = "دقائق التركيز",
                        value = state.customFocusMin,
                        onMinus = { FocusTimerEngine.setCustomDurations(context, state.customFocusMin - 5, state.customBreakMin) },
                        onPlus = { FocusTimerEngine.setCustomDurations(context, state.customFocusMin + 5, state.customBreakMin) }
                    )
                    DurationStepper(
                        label = "دقائق الاستراحة",
                        value = state.customBreakMin,
                        onMinus = { FocusTimerEngine.setCustomDurations(context, state.customFocusMin, state.customBreakMin - 1) },
                        onPlus = { FocusTimerEngine.setCustomDurations(context, state.customFocusMin, state.customBreakMin + 1) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                "ماذا ستدرس في هذه الجلسة؟",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedSubject == null,
                    onClick = { selectedSubject = null },
                    label = { Text("دراسة عامة") },
                    colors = focusChipColors()
                )
                FOCUS_SUBJECTS.forEach { subject ->
                    FilterChip(
                        selected = selectedSubject == subject,
                        onClick = { selectedSubject = subject },
                        label = { Text(subject) },
                        colors = focusChipColors()
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("إبقاء الشاشة مضيئة أثناء الجلسة", color = Color.White, fontSize = 13.sp)
                Switch(
                    checked = state.keepScreenOn,
                    onCheckedChange = { FocusTimerEngine.setKeepScreenOn(context, it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = GoldAccent,
                        checkedTrackColor = GoldAccent.copy(alpha = 0.4f),
                        uncheckedThumbColor = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    showMotivation = false
                    FocusTimerEngine.startSession(context, selectedSubject)
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = MaroonDeep),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text("بدء جلسة التركيز", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        } else {
            // أثناء الجلسة/الاستراحة: تحكم نظيف فقط.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { FocusTimerEngine.toggle(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = MaroonDeep),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(52.dp)
                ) {
                    Text(
                        if (state.isRunning) "إيقاف مؤقت" else "استئناف",
                        fontWeight = FontWeight.Bold, fontSize = 15.sp
                    )
                }
                if (isBreak) {
                    OutlinedButton(
                        onClick = { FocusTimerEngine.skipBreak(context) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text("تخطي الاستراحة", color = Color.White, fontSize = 15.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = { FocusTimerEngine.finishSession(context) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text("إنهاء الجلسة", color = Color.White, fontSize = 15.sp)
                    }
                }
            }
            if (state.isRunning) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (isBreak) "استرخِ — الاستراحة جزء من الخطة." else "أغلق كل شيء آخر، وركّز على مادتك فقط.",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        Surface(
            color = Color.White.copy(alpha = 0.10f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${state.minutesToday}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("دقيقة اليوم", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${state.sessionsToday}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("جلسة اليوم", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${state.minutesWeek}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("دقيقة بالأسبوع", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                }
            }
        }
        if (state.subjectMinutesToday.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            val top = state.subjectMinutesToday.maxByOrNull { it.value }
            if (top != null) {
                Text(
                    text = "أكثر مادة تركيزًا اليوم: ${top.key} (${top.value} دقيقة)",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun focusChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color.White.copy(alpha = 0.10f),
    labelColor = Color.White,
    selectedContainerColor = GoldAccent,
    selectedLabelColor = MaroonDeep
)

@Composable
private fun DurationStepper(label: String, value: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconButton(onClick = onMinus) {
                Icon(Icons.Default.Remove, contentDescription = "إنقاص", tint = Color.White)
            }
            Text("$value", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            IconButton(onClick = onPlus) {
                Icon(Icons.Default.Add, contentDescription = "زيادة", tint = Color.White)
            }
        }
    }
}
