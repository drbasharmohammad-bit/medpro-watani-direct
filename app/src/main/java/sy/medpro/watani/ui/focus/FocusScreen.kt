package sy.medpro.watani.ui.focus

import android.content.Context
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.data.FocusEvent
import sy.medpro.watani.data.FocusMode
import sy.medpro.watani.data.FocusPhase
import sy.medpro.watani.data.FocusTimerEngine
import sy.medpro.watani.data.FocusTimerState
import sy.medpro.watani.ui.theme.GoldAccent
import sy.medpro.watani.ui.theme.GoldAccentDark
import sy.medpro.watani.ui.theme.MaroonPrimary
import sy.medpro.watani.ui.theme.wataniDarkTheme

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

/** قطر حلقة العدّ بالوضع العمودي، وسقفها بالوضع الأفقي. */
private const val FOCUS_RING_MAX_DP = 248

/** أصغر قطر تبقى معه الحلقة وأرقامها مقروءة بالوضع الأفقي الضيّق. */
private const val FOCUS_RING_MIN_DP = 132

/** حجم أرقام العدّ عند القطر الأقصى؛ يتدرّج مع القطر الفعلي. */
private const val FOCUS_DIGITS_MAX_SP = 56f

/** ما يُحجز من ارتفاع اللوحة اليسرى فوق الحلقة لعنوان المرحلة والمادة. */
private const val FOCUS_RING_HEADER_RESERVE_DP = 76

/** وزن لوحة التحكّم الأفقية مقابل لوحة الحلقة (أوسع قليلًا لأزرارها). */
private const val FOCUS_CONTROLS_PANE_WEIGHT = 1.15f

/**
 * شاشة التركيز (ترقية 1.2.23 لمؤقت البومودورو القائم، وإعادة بناء
 * 1.2.46 البصرية والتخطيطية).
 *
 * الهوية: سطح عاجي من نظام Premium كباقي شاشات التطبيق — العنابي
 * للترويسة والأزرار فقط، والذهبي العتيق لحلقة التقدم ولمسات
 * الأرقام، بلا غمر لوني لكامل الشاشة (كانت الشاشة قبل 1.2.46
 * متدرّجة عنابية مشبعة على كاملها فكسرت تناسق الهوية). التخطيط
 * يتكيّف مع الاتجاه: عمودي مألوف بالطول، وبالعرض لوحتان — حلقة
 * العدّ بحجم يلائم الارتفاع المتاح فتُرى كاملة دائمًا، ولوحة
 * تحكّم قابلة للتمرير بجانبها، فلا يختفي شيء تحت الشريط السفلي
 * العائم ولا يُقصّ المحتوى (بلاغ الجهاز 23 نوفمبر 21:31).
 * كل منطق العدّ في FocusTimerEngine — ممنوع عدّ محلي هنا.
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
    val isBreak = state.phase == FocusPhase.BREAK
    val motivationVisible = showMotivation && state.lastEvent == FocusEvent.FOCUS_DONE

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (maxWidth > maxHeight) {
            // الوضع الأفقي: لوحتان جنبًا إلى جنب. الحلقة تُقاس من
            // الارتفاع المتاح فتظهر كاملة بأرقامها، والتحكّم
            // والإحصاءات بلوحة تمرير مستقلة — لا قصّ ولا تغطية.
            // (يُلتقط الارتفاع هنا بنطاق القيود نفسه: داخل لامبدا
            // الصف لا يعود مستقبِل القيود الضمني متاحًا.)
            val ringSize = (maxHeight - FOCUS_RING_HEADER_RESERVE_DP.dp)
                .coerceIn(FOCUS_RING_MIN_DP.dp, FOCUS_RING_MAX_DP.dp)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    FocusTimerHero(state = state, isBreak = isBreak, progress = phaseProgress, ringSize = ringSize)
                }
                Spacer(modifier = Modifier.width(28.dp))
                Column(
                    modifier = Modifier
                        .weight(FOCUS_CONTROLS_PANE_WEIGHT)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (motivationVisible) {
                        FocusMotivationBanner(state = state)
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                    if (state.isIdle) {
                        FocusIdleSettings(
                            state = state,
                            selectedSubject = selectedSubject,
                            onSubjectSelected = { selectedSubject = it },
                            startEarly = true,
                            onStart = {
                                showMotivation = false
                                FocusTimerEngine.startSession(context, selectedSubject)
                            }
                        )
                    } else {
                        FocusRunningControls(state = state, context = context)
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    FocusStats(state = state)
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FocusTimerHero(
                    state = state,
                    isBreak = isBreak,
                    progress = phaseProgress,
                    ringSize = FOCUS_RING_MAX_DP.dp
                )
                if (motivationVisible) {
                    Spacer(modifier = Modifier.height(16.dp))
                    FocusMotivationBanner(state = state)
                }
                Spacer(modifier = Modifier.height(24.dp))
                if (state.isIdle) {
                    FocusIdleSettings(
                        state = state,
                        selectedSubject = selectedSubject,
                        onSubjectSelected = { selectedSubject = it },
                        startEarly = false,
                        onStart = {
                            showMotivation = false
                            FocusTimerEngine.startSession(context, selectedSubject)
                        }
                    )
                } else {
                    FocusRunningControls(state = state, context = context)
                }
                Spacer(modifier = Modifier.height(28.dp))
                FocusStats(state = state)
                // حافة أمان سفلية: آخر بطاقة لا تلتصق بحافة منطقة
                // الشريط السفلي العائم مهما كان الاستضافة.
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * ترويسة المرحلة + حلقة العدّ: العنوان عنابي (ذهبي بالوضع الليلي)،
 * مسار الحلقة بلون سطحي دافئ، وتقدّمها بالذهبي العتيق — لمسة
 * الهوية الوحيدة الكبيرة بالشاشة. أرقام العدّ تتدرّج مع قطر
 * الحلقة الفعلي حتى تبقى متناسبة بالوضع الأفقي المصغّر.
 */
@Composable
private fun FocusTimerHero(state: FocusTimerState, isBreak: Boolean, progress: Float, ringSize: Dp) {
    val titleColor = if (wataniDarkTheme()) GoldAccent else MaroonPrimary
    val minutes = state.timeLeftSec / 60
    val seconds = state.timeLeftSec % 60
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (isBreak) "وقت الاستراحة" else "جلسة تركيز",
            color = titleColor,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
        if (state.subject != null && (state.isRunning || isBreak)) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = state.subject!!,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(ringSize)) {
            val trackColor = MaterialTheme.colorScheme.surfaceVariant
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                drawArc(color = trackColor, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke)
                drawArc(
                    color = GoldAccent,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    style = stroke
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format("%02d:%02d", minutes, seconds),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = (FOCUS_DIGITS_MAX_SP * (ringSize.value / FOCUS_RING_MAX_DP)).sp
                )
                Text(
                    text = when (state.mode) {
                        FocusMode.CLASSIC -> "كلاسيك ${state.mode.focusMin}/${state.mode.breakMin}"
                        FocusMode.DEEP -> "تركيز عميق ${state.mode.focusMin}/${state.mode.breakMin}"
                        FocusMode.CUSTOM -> "مخصص ${state.customFocusMin}/${state.customBreakMin}"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
    }
}

/** بطاقة بيضاء نظيفة بسطر التحفيز عند اكتمال جلسة. */
@Composable
private fun FocusMotivationBanner(state: FocusTimerState) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 2.dp
    ) {
        Text(
            text = MOTIVATION_LINES[state.sessionsToday % MOTIVATION_LINES.size],
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

/** اختيار النمط + عدّادا المدد عند النمط المخصص. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FocusModePicker(state: FocusTimerState, context: Context) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
    }
}

/**
 * إعداد ما قبل البدء كاملًا: النمط، مادة الجلسة، الإضاءة، وزر
 * البدء العنابي. بالطول يأتي البدء أخيرًا بعد اكتمال الإعداد،
 * وبالعرض ([startEarly]) يتقدّم زر البدء وكتلة الإضاءة فوق قائمة
 * المواد الطويلة حتى يظهر زر البدء في اللوحة القصيرة بلا تمرير.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FocusIdleSettings(
    state: FocusTimerState,
    selectedSubject: String?,
    onSubjectSelected: (String?) -> Unit,
    startEarly: Boolean,
    onStart: () -> Unit
) {
    val context = LocalContext.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FocusModePicker(state = state, context = context)
        if (startEarly) {
            Spacer(modifier = Modifier.height(18.dp))
            FocusStartBlock(state = state, context = context, onStart = onStart)
            Spacer(modifier = Modifier.height(18.dp))
        }
        Text(
            "ماذا ستدرس في هذه الجلسة؟",
            color = MaterialTheme.colorScheme.onBackground,
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
                onClick = { onSubjectSelected(null) },
                label = { Text("دراسة عامة") },
                colors = focusChipColors()
            )
            FOCUS_SUBJECTS.forEach { subject ->
                FilterChip(
                    selected = selectedSubject == subject,
                    onClick = { onSubjectSelected(subject) },
                    label = { Text(subject) },
                    colors = focusChipColors()
                )
            }
        }
        if (!startEarly) {
            Spacer(modifier = Modifier.height(18.dp))
            FocusStartBlock(state = state, context = context, onStart = onStart)
        }
    }
}

/** مفتاح إبقاء الشاشة مضيئة + زر بدء الجلسة (عنابي، نص أبيض). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FocusStartBlock(state: FocusTimerState, context: Context, onStart: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "إبقاء الشاشة مضيئة أثناء الجلسة",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp
            )
            Switch(
                checked = state.keepScreenOn,
                onCheckedChange = { FocusTimerEngine.setKeepScreenOn(context, it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onStart,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            Text("بدء جلسة التركيز", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

/** تحكّم أثناء الجلسة/الاستراحة: إيقاف/استئناف وتخطٍّ أو إنهاء، بلا أي إعداد. */
@Composable
private fun FocusRunningControls(state: FocusTimerState, context: Context) {
    val isBreak = state.phase == FocusPhase.BREAK
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { FocusTimerEngine.toggle(context) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(52.dp)
            ) {
                Text(
                    if (state.isRunning) "إيقاف مؤقت" else "استئناف",
                    fontWeight = FontWeight.Bold, fontSize = 15.sp
                )
            }
            OutlinedButton(
                onClick = {
                    if (isBreak) FocusTimerEngine.skipBreak(context)
                    else FocusTimerEngine.finishSession(context)
                },
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.height(52.dp)
            ) {
                Text(if (isBreak) "تخطي الاستراحة" else "إنهاء الجلسة", fontSize = 15.sp)
            }
        }
        if (state.isRunning) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (isBreak) "استرخِ — الاستراحة جزء من الخطة." else "أغلق كل شيء آخر، وركّز على مادتك فقط.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * بطاقة إحصاءات اليوم/الأسبوع: سطح أبيض نظيف بظل خفيف، أرقامها
 * بالذهبي العتيق (داكنه بالوضع الفاتح لقراءة مريحة) وتسمياتها
 * رمادية دافئة — بلا أي غمر لوني.
 */
@Composable
private fun FocusStats(state: FocusTimerState) {
    val numberColor = if (wataniDarkTheme()) GoldAccent else GoldAccentDark
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${state.minutesToday}", color = numberColor, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("دقيقة اليوم", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${state.sessionsToday}", color = numberColor, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("جلسة اليوم", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${state.minutesWeek}", color = numberColor, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("دقيقة بالأسبوع", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
            }
        }
        if (state.subjectMinutesToday.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            val top = state.subjectMinutesToday.maxByOrNull { it.value }
            if (top != null) {
                Text(
                    text = "أكثر مادة تركيزًا اليوم: ${top.key} (${top.value} دقيقة)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun focusChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surface,
    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
)

@Composable
private fun DurationStepper(label: String, value: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconButton(onClick = onMinus) {
                Icon(Icons.Default.Remove, contentDescription = "إنقاص", tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                "$value",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            IconButton(onClick = onPlus) {
                Icon(Icons.Default.Add, contentDescription = "زيادة", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
