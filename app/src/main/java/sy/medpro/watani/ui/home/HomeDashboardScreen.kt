package sy.medpro.watani.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import sy.medpro.watani.data.FocusTimerEngine
import sy.medpro.watani.data.OfflineStudyVault
import sy.medpro.watani.data.SupportFavorites
import sy.medpro.watani.data.SupportMessages
import sy.medpro.watani.ui.theme.*

@Composable
fun HomeDashboardScreen(
    manifestVersion: String?,
    onOpenSupport: () -> Unit = {},
    onOpenFocusTimer: () -> Unit = {},
    onOpenReviewDue: () -> Unit = {}
) {
    val context = LocalContext.current
    var animateIn by remember { mutableStateOf(false) }
    // بطاقة الدعم: تقديم فهرس الرسالة اليومية بذاكرة الجلسة (يُصفَّر مع
    // إعادة إنشاء الشاشة، والأساس دائمًا فهرس اليوم الحقيقي).
    var supportOffset by remember { mutableStateOf(0) }
    val supportFavorites by SupportFavorites.favorites.collectAsState()

    LaunchedEffect(Unit) {
        OfflineStudyVault.ensureInitialized(context)
        FocusTimerEngine.init(context)
        SupportFavorites.init(context)
        animateIn = true
    }

    val stats by OfflineStudyVault.statsFlow.collectAsState()
    // No date anywhere (student, panel, manual) → no fake number: the
    // ring shows a dash until the student picks his exam date.
    val hasExamDate = stats.examDateMillis != null || stats.adminExamDateMillis != null || stats.examModeManual
    val days = stats.examDaysRemaining
    val totalAns = stats.totalAnswered
    val totalCor = stats.totalCorrect
    val accuracy = if (totalAns == 0) 0 else (totalCor * 100) / totalAns
    val progressVal = if (totalAns == 0) 0.05f else (totalCor.toFloat() / totalAns.toFloat()).coerceIn(0.05f, 1f)

    val ringProgress by animateFloatAsState(
        targetValue = if (animateIn) progressVal else 0f,
        animationSpec = tween(durationMillis = 900),
        label = "countdownRing"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Student-set offline exam countdown — its own section directly
        // under the top bar. The student picks the date; it ticks
        // locally (days / hours / minutes / seconds) with no network.
        item {
            ExamCountdownCard()
        }

        // بطاقة الدعم النفسي — أول عنصر بعد عدّاد الامتحان: رسالة اليوم
        // من بنك الدعم v2 بتدوير epochDay، مع تقديم يدوي وقلب مفضلة.
        item {
            val supportMessage = SupportMessages.all[
                (SupportMessages.dailyIndex() + supportOffset) % SupportMessages.all.size
            ]
            val supportIsFavorite = supportFavorites.contains(supportMessage.id)
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, RoseOutline),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth().clickable { onOpenSupport() }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaroonPrimary.copy(alpha = 0.1f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = SupportMessages.categoryTitle(supportMessage.categoryId),
                                color = MaroonPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Row {
                            IconButton(onClick = { supportOffset++ }) {
                                Icon(Icons.Default.Refresh, contentDescription = "رسالة أخرى", tint = MaroonPrimary)
                            }
                            IconButton(onClick = { SupportFavorites.toggle(context, supportMessage.id) }) {
                                Icon(
                                    imageVector = if (supportIsFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = if (supportIsFavorite) "إزالة من المفضلة" else "إضافة إلى المفضلة",
                                    tint = if (supportIsFavorite) Color(0xFFB71C1C) else MaroonPrimary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = supportMessage.text,
                        fontFamily = ThmanyahFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        lineHeight = 30.sp
                    )
                }
            }
        }

        item {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(MaroonPrimary, MaroonDark, MaroonDeep)
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Surface(
                                    color = GoldAccent.copy(alpha = 0.22f),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = "منصة التحضير السريري المعتمدة",
                                        color = GoldAccent,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                // Approved greeting (build 1.2.5): «د. <الاسم>»
                                // from the same profile store the gate writes
                                // and the profile screen edits; the generic
                                // fallback stays when no name exists anywhere.
                                val greetingName = sy.medpro.watani.data.ProfileNameStore.resolveName(
                                    sy.medpro.watani.data.ProfileNameStore.savedName(
                                        context,
                                        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                                    ),
                                    com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName
                                )
                                Text(
                                    text = sy.medpro.watani.data.ProfileNameStore.greetingFor(greetingName),
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "نجاحك في الوطني يبدأ من هنا",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(74.dp)
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawArc(
                                        color = Color.White.copy(alpha = 0.18f),
                                        startAngle = -90f,
                                        sweepAngle = 360f,
                                        useCenter = false,
                                        style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                    drawArc(
                                        color = GoldAccent,
                                        startAngle = -90f,
                                        sweepAngle = 360f * ringProgress,
                                        useCenter = false,
                                        style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(if (hasExamDate) "$days" else "—", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Text("يوماً", color = GoldAccent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Surface(
                            color = Color.White.copy(alpha = 0.12f),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (totalAns == 0) "جاهزية البنك: مبدئي" else "جاهزية $accuracy%",
                                    color = GoldAccent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }

        // بطاقة البومودورو: العدّاد الحي من محرك المؤقت المشترك نفسه.
        item {
            val focusAccent = Color(0xFF4527A0)
            val focusState by FocusTimerEngine.state.collectAsState()
            val focusTimeText = if (!focusState.isRunning && focusState.timeLeftSec == FocusTimerEngine.FULL_SESSION_SEC) {
                "25:00 — جاهز"
            } else {
                String.format("%02d:%02d", focusState.timeLeftSec / 60, focusState.timeLeftSec % 60)
            }
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, RoseOutline),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth().clickable { onOpenFocusTimer() }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = focusAccent.copy(alpha = 0.12f),
                        modifier = Modifier.size(50.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = focusAccent, modifier = Modifier.size(26.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("مؤقت التركيز", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(focusTimeText, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = focusAccent)
                        Text(
                            "جلسات اليوم: ${focusState.sessionsToday}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        )
                    }
                    Button(
                        onClick = { FocusTimerEngine.toggle(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = focusAccent),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(if (focusState.isRunning) "إيقاف مؤقت" else "بدء التركيز", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // بطاقة المراجعة المستحقة — شرطية: لا تُرسم عند صفر العناصر.
        if (stats.savedMistakes.size + stats.wrongFlashcardIds.size > 0) {
            item {
                val reviewAccent = Color(0xFFB71C1C)
                val reviewCount = stats.savedMistakes.size + stats.wrongFlashcardIds.size
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, RoseOutline),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FactCheck, contentDescription = null, tint = reviewAccent, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("مراجعة مستحقة", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = reviewAccent)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "لديك $reviewCount عنصرًا بانتظار المراجعة: ${stats.savedMistakes.size} من الأخطاء المحفوظة و${stats.wrongFlashcardIds.size} من البطاقات الخاطئة.",
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onOpenReviewDue,
                            colors = ButtonDefaults.buttonColors(containerColor = reviewAccent),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            Text("ابدأ المراجعة", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, RoseOutline),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CloudDone, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("حالة المزامنة وقاعدة البيانات", fontWeight = FontWeight.Bold, color = MaroonPrimary, fontSize = 14.sp)
                        Text(
                            // User-facing sync status only: the raw manifest
                            // version is an internal timestamp and must never
                            // be shown to users (it also renders mangled RTL).
                            text = "الفهرس متزامن مع الخادم الرسمي للبنك الوطني",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Student-set offline exam countdown (Nov 2026, doctor's order).
 *
 * The student picks his own exam date — nobody else's date (panel or
 * default) decides it. The date is stored in OfflineStudyVault prefs
 * (setExamDate, local midnight) and the countdown is pure local math
 * ticking every second: days / hours / minutes / seconds until that
 * day. No network call happens here at all.
 *
 * Target precedence mirrors the vault: the student's date first, the
 * admin panel date only as a fallback while the student has set none.
 * With no date anywhere, the card shows the picker prompt instead of
 * any made-up number.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamCountdownCard() {
    val context = LocalContext.current
    val stats by OfflineStudyVault.statsFlow.collectAsState()
    val target = stats.examDateMillis ?: stats.adminExamDateMillis
    var showPicker by remember { mutableStateOf(false) }

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(target) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = target?.let { localMidnight ->
                Instant.ofEpochMilli(localMidnight).atZone(ZoneId.systemDefault())
                    .toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { utcMidnight ->
                        val localDate = Instant.ofEpochMilli(utcMidnight)
                            .atZone(ZoneOffset.UTC).toLocalDate()
                        val localMidnight = localDate.atStartOfDay(ZoneId.systemDefault())
                            .toInstant().toEpochMilli()
                        OfflineStudyVault.setExamDate(context, localMidnight)
                    }
                    showPicker = false
                }) { Text("تأكيد") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("إلغاء") }
            }
        ) {
            DatePicker(state = pickerState, showModeToggle = false)
        }
    }

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, RoseOutline),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Event, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "العد التنازلي للامتحان الوطني",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaroonPrimary
                    )
                    Text(
                        "يُحفظ على جهازك ويعمل دون إنترنت",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            when {
                target == null -> {
                    Text(
                        "حدد تاريخ امتحانك ليبدأ العد التنازلي.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showPicker = true },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = MaroonDeep),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("تحديد تاريخ الامتحان", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                target - now <= 0L -> {
                    Text(
                        "حان موعد الامتحان! بالتوفيق يا طبيب المستقبل",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaroonPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showPicker = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("تغيير التاريخ", fontSize = 14.sp)
                    }
                }
                else -> {
                    val remaining = target - now
                    val days = remaining / 86_400_000L
                    val hours = (remaining % 86_400_000L) / 3_600_000L
                    val minutes = (remaining % 3_600_000L) / 60_000L
                    val seconds = (remaining % 60_000L) / 1_000L
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CountdownUnit(modifier = Modifier.weight(1f), value = "$days", label = "يوماً")
                        Text(":", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaroonPrimary)
                        CountdownUnit(modifier = Modifier.weight(1f), value = "%02d".format(hours), label = "ساعة")
                        Text(":", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaroonPrimary)
                        CountdownUnit(modifier = Modifier.weight(1f), value = "%02d".format(minutes), label = "دقيقة")
                        Text(":", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaroonPrimary)
                        CountdownUnit(modifier = Modifier.weight(1f), value = "%02d".format(seconds), label = "ثانية")
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    val dateText = Instant.ofEpochMilli(target).atZone(ZoneId.systemDefault())
                        .toLocalDate().toString()
                    Text(
                        "تاريخ الامتحان: $dateText",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showPicker = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("تغيير التاريخ", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CountdownUnit(modifier: Modifier = Modifier, value: String, label: String) {
    Surface(
        color = MaroonPrimary.copy(alpha = 0.08f),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 10.dp)
        ) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaroonPrimary)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
        }
    }
}
