package sy.medpro.watani.ui.home
import sy.medpro.watani.ui.library.libGoldTint

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import sy.medpro.watani.data.DailyGoalEngine
import sy.medpro.watani.data.DailyGoalMath
import sy.medpro.watani.data.DailyGoalStore
import sy.medpro.watani.data.DayGoalsMath
import sy.medpro.watani.data.DayGoalsStore
import sy.medpro.watani.data.FocusTimerEngine
import sy.medpro.watani.data.OfflineStudyVault
import sy.medpro.watani.data.SupportFavorites
import sy.medpro.watani.data.SupportMessages
import sy.medpro.watani.ui.share.shareTipAsImage
import androidx.compose.ui.draw.shadow
import sy.medpro.watani.ui.library.LibAppLogo
import sy.medpro.watani.ui.library.LibCard
import sy.medpro.watani.ui.library.LibColors
import sy.medpro.watani.ui.library.LibGoldBar
import sy.medpro.watani.ui.library.libBackground
import sy.medpro.watani.ui.library.libHeading
import sy.medpro.watani.ui.library.libMuted
import sy.medpro.watani.ui.theme.*

// Legacy home hub: one long declarative composition, kept as a
// single function by design (same scoped suppression as HomeScreen).
@Suppress("LongMethod")
@Composable
fun HomeDashboardScreen(
    manifestVersion: String?,
    onOpenSupport: () -> Unit = {},
    onOpenFocusTimer: () -> Unit = {},
    onOpenReviewDue: () -> Unit = {},
    onOpenTips: () -> Unit = {}
) {
    val context = LocalContext.current
    // بطاقة الدعم: تقديم فهرس الرسالة اليومية بذاكرة الجلسة (يُصفَّر مع
    // إعادة إنشاء الشاشة، والأساس دائمًا فهرس اليوم الحقيقي).
    var supportOffset by remember { mutableStateOf(0) }
    val supportFavorites by SupportFavorites.favorites.collectAsState()

    LaunchedEffect(Unit) {
        OfflineStudyVault.ensureInitialized(context)
        FocusTimerEngine.init(context)
        SupportFavorites.init(context)
        // نشاط التطبيق يحدّث الويدجت بأرقام اليوم (1.2.23).
        sy.medpro.watani.ui.widget.Widgets.updateAll(context)
        // وبطاقة العدّاد الدائمة على القفل معها (1.2.30).
        sy.medpro.watani.notify.StudyNotifications.refreshCountdownCard(context)
    }

    val stats by OfflineStudyVault.statsFlow.collectAsState()
    // Readiness for the hero bar: with no answers yet the bar keeps
    // its small seed value and the percent label shows a dash.
    val totalAns = stats.totalAnswered
    val totalCor = stats.totalCorrect
    val accuracy = if (totalAns == 0) 0 else (totalCor * 100) / totalAns
    val progressVal = if (totalAns == 0) 0.05f else (totalCor.toFloat() / totalAns.toFloat()).coerceIn(0.05f, 1f)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(libBackground())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Slim header of the approved home model (01): logo +
        // app name. (The header search button was removed
        // with the global-search feature, 1.2.45; the brand word
        // and the slogan left the header in 1.2.49.)
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LibAppLogo()
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Med Pro",
                        fontFamily = NotoKufiFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = libHeading()
                    )
                }
            }
        }

        // Greeting of the model: small warm line, then the big
        // Kufi heading. The name comes from the same profile store
        // the gate writes and the profile screen edits.
        item {
            val greetingName = sy.medpro.watani.data.ProfileNameStore.resolveName(
                sy.medpro.watani.data.ProfileNameStore.savedName(
                    context,
                    com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                ),
                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName
            )
            Column {
                Text(
                    text = sy.medpro.watani.data.ProfileNameStore.greetingFor(greetingName),
                    fontSize = 13.sp,
                    color = libMuted()
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "دراستك اليوم… خطوة أقرب للنجاح",
                    fontFamily = NotoKufiFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = libHeading()
                )
            }
        }

        item {
            ExamCountdownCard()
        }

        item {
            // Readiness — compact card (1.2.51, doctor's device
            // report: the hero block shouted over the whole home).
            // SAME content as the old hero, proportional size:
            // the continue state («تابع من حيث توقفت» + play), the
            // answered-question count, and the progress bar with
            // its percent. The decorative gold chip is gone — one
            // accent moment per card, not three competing ones.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(20.dp), clip = false)
                    .clip(RoundedCornerShape(20.dp))
                    .background(brush = LibColors.MaroonBrush)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "تابع من حيث توقفت",
                                color = LibColors.GoldSoft,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "جاهزيتك للامتحان الوطني",
                                fontFamily = NotoKufiFontFamily,
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (totalAns == 0) {
                                    "ابدأ أول جلسة اليوم — خطوة أقرب للنجاح"
                                } else {
                                    "أجبت عن $totalAns سؤالًا حتى الآن — واصل التقدّم"
                                },
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(LibColors.GoldBrush),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = LibColors.MaroonDeep,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LibGoldBar(
                            fraction = progressVal,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (totalAns == 0) "—" else "$accuracy%",
                            color = LibColors.GoldSoft,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }


        item {
            MyDayGoalsCard()
        }

        item {
            // «زوادة الطالب» (1.2.51): home entry card — tips from
            // fellow students, per subject and section.
            LibCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onOpenTips() }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = libGoldTint(),
                        modifier = Modifier.size(50.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = LibColors.Maroon,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "زوادة الطالب",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = LibColors.Maroon
                        )
                        Text(
                            "نصائح زملائك في كل مادة — الأشيع، النقاط المهمة، ربطات الذاكرة وفخاخ الامتحان. أضف زوادتك أنت أيضًا.",
                            fontSize = 12.sp,
                            color = libMuted()
                        )
                    }
                }
            }
        }


        item {
            val supportMessage = SupportMessages.all[
                (SupportMessages.dailyIndex() + supportOffset) % SupportMessages.all.size
            ]
            val supportIsFavorite = supportFavorites.contains(supportMessage.id)
            LibCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onOpenSupport() }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = libGoldTint(),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = SupportMessages.categoryTitle(supportMessage.categoryId),
                                color = LibColors.Maroon,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Row {
                            IconButton(onClick = { shareTipAsImage(context, supportMessage) }) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = "مشاركة الرسالة كصورة",
                                    tint = LibColors.Maroon
                                )
                            }
                            IconButton(onClick = { supportOffset++ }) {
                                Icon(Icons.Default.Refresh, contentDescription = "رسالة أخرى", tint = LibColors.Maroon)
                            }
                            IconButton(onClick = { SupportFavorites.toggle(context, supportMessage.id) }) {
                                Icon(
                                    imageVector = if (supportIsFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = if (supportIsFavorite) "إزالة من المفضلة" else "إضافة إلى المفضلة",
                                    tint = if (supportIsFavorite) LibColors.Maroon else LibColors.Maroon
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = supportMessage.text,
                        fontFamily = ThmanyahFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        lineHeight = 26.sp
                    )
                }
            }
        }

        item {
            val focusAccent = LibColors.Maroon
            val focusState by FocusTimerEngine.state.collectAsState()
            val focusTimeText = if (focusState.isIdle) {
                String.format("%02d:%02d", focusState.timeLeftSec / 60, focusState.timeLeftSec % 60) + " — جاهز"
            } else {
                String.format("%02d:%02d", focusState.timeLeftSec / 60, focusState.timeLeftSec % 60)
            }
            LibCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onOpenFocusTimer() }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = libGoldTint(),
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
                            "جلسات اليوم: ${focusState.sessionsToday} · تركيز اليوم: ${focusState.minutesToday} دقيقة",
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

        if (stats.savedMistakes.size + stats.wrongFlashcardIds.size > 0) {
            item {
                val reviewAccent = LibColors.Maroon
                val reviewCount = stats.savedMistakes.size + stats.wrongFlashcardIds.size
                LibCard(modifier = Modifier.fillMaxWidth()) {
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
            LibCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CloudDone, contentDescription = null,
                        tint = LibColors.Green, modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "حالة المزامنة وقاعدة البيانات",
                            fontWeight = FontWeight.Bold, color = LibColors.Maroon, fontSize = 14.sp
                        )
                        Text(
                            // User-facing sync status only: the raw manifest
                            // version is an internal timestamp and must never
                            // be shown to users (it also renders mangled RTL).
                            text = "الفهرس متزامن مع الخادم الرسمي للبنك",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
        // Student-set offline exam countdown — its own section directly
        // under the top bar. The student picks the date; it ticks
        // locally (days / hours / minutes / seconds) with no network.
        // بطاقة «أهدافي اليوم» (1.2.24): هدف الأسئلة برقم يختاره
        // الطالب بنفسه + أهدافه النصية لليوم، وسطر تركيز اليوم من
        // محرك المؤقت نفسه (لا قسم مكرر). تنعكس على الويدجت مباشرة.
        // بطاقة الدعم النفسي — أول عنصر بعد عدّاد الامتحان: رسالة اليوم
        // من بنك الدعم v2 بتدوير epochDay، مع تقديم يدوي وقلب مفضلة.
        // بطاقة البومودورو: العدّاد الحي من محرك المؤقت المشترك نفسه.
        // بطاقة المراجعة المستحقة — شرطية: لا تُرسم عند صفر العناصر.
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

    LibCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (target != null) {
                    // Model (01) exam tile: the exam's day-of-month on
                    // an ivory tile, e.g. «٢٢» for 22 December 2026.
                    val examDay = Instant.ofEpochMilli(target).atZone(ZoneId.systemDefault())
                        .toLocalDate().dayOfMonth
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(libGoldTint()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            sy.medpro.watani.data.CountdownDigits.unit(examDay.toLong()),
                            fontFamily = NotoKufiFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = LibColors.MaroonDeep
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                } else {
                    Icon(
                        Icons.Default.Event, contentDescription = null,
                        tint = LibColors.Maroon, modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "العد التنازلي للامتحان الوطني",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = LibColors.Maroon
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LibColors.Gold, contentColor = LibColors.MaroonDeep
                        ),
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
                        color = LibColors.Maroon
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
                        CountdownUnit(modifier = Modifier.weight(1f), value = sy.medpro.watani.data.CountdownDigits.unit(days), label = "يوماً")
                        Text(":", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = LibColors.Maroon)
                        CountdownUnit(modifier = Modifier.weight(1f), value = sy.medpro.watani.data.CountdownDigits.unit(hours, 2), label = "ساعة")
                        Text(":", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = LibColors.Maroon)
                        CountdownUnit(modifier = Modifier.weight(1f), value = sy.medpro.watani.data.CountdownDigits.unit(minutes, 2), label = "دقيقة")
                        Text(":", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = LibColors.Maroon)
                        CountdownUnit(modifier = Modifier.weight(1f), value = sy.medpro.watani.data.CountdownDigits.unit(seconds, 2), label = "ثانية")
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    val dateText = Instant.ofEpochMilli(target).atZone(ZoneId.systemDefault())
                        .toLocalDate().format(
                            java.time.format.DateTimeFormatter.ofPattern(
                                "d MMMM yyyy", java.util.Locale("ar")
                            )
                        )
                    Text(
                        "تاريخ الامتحان: ${sy.medpro.watani.data.CountdownDigits.eastern(dateText)}",
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

/**
 * بطاقة «أهدافي اليوم» (1.2.24 — تحل محل بطاقة الهدف الذكي من
 * 1.2.23 بأمر الدكتور بعد تجربته على جهازه).
 *
 * الهدف هنا قرار الطالب وحده:
 * - هدف الأسئلة رقم يضبطه بنفسه (DailyGoalStore)؛ الرقم التلقائي
 *   (المتبقي ÷ الأيام) يظهر فقط كاقتراح اختياري داخل حوار الضبط
 *   ولا يُفرض ولا يُعرض كهدف بلا اختياره.
 * - أهداف نصية حرة لليوم (DayGoalsStore): يكتب مادة يدرسها أو أي
 *   نص، يضيف ويحذف ويعلّم منجزًا — وتخصّ اليوم الحالي فقط.
 * كل تعديل يبثّ تحديثًا فوريًا لعائلة الويدجتات فتنعكس أهدافه
 * على شاشة هاتفه الرئيسية مباشرة. أسفل البطاقة سطر تركيز اليوم
 * من محرك المؤقت — بجانب الأهداف دون قسم مكرر بالرئيسية.
 * العرض محصور بسقف 640dp ومتوسّط أفقيًا: على التابلت لا تتمدد
 * البطاقة بعرض الشاشة كله فتبقى مقروءة بلا فراغات مشوّهة.
 */
@Composable
private fun MyDayGoalsCard() {
    val context = LocalContext.current
    val stats by OfflineStudyVault.statsFlow.collectAsState()
    val focusState by FocusTimerEngine.state.collectAsState()
    var refresh by remember { mutableStateOf(0) }
    val snapshot = remember(stats.totalAnswered, stats.answeredToday, refresh) {
        DailyGoalEngine.snapshot(context)
    }
    val items = remember(refresh) { DayGoalsStore.todayItems(context) }
    var showTargetDialog by remember { mutableStateOf(false) }
    var newGoalText by remember { mutableStateOf("") }

    fun afterChange() {
        refresh++
        sy.medpro.watani.ui.widget.Widgets.updateAll(context)
        sy.medpro.watani.notify.StudyNotifications.refreshCountdownCard(context)
    }

    if (showTargetDialog) {
        var input by remember { mutableStateOf((snapshot.manualGoal ?: "").toString()) }
        AlertDialog(
            onDismissRequest = { showTargetDialog = false },
            title = { Text("هدف الأسئلة اليومي") },
            text = {
                Column {
                    Text(
                        "اختر بنفسك عدد الأسئلة الذي تريد إنجازه كل يوم. الهدف قرارك — لن يفرض التطبيق عليك أي رقم.",
                        fontSize = 13.sp
                    )
                    if (snapshot.hasBank && snapshot.autoGoal > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "اقتراح اختياري من التطبيق: ${snapshot.autoGoal} سؤالًا في اليوم (المتبقي ${snapshot.remaining} سؤالًا على ${snapshot.daysLeft} يومًا حتى الامتحان).",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        )
                        TextButton(onClick = { input = snapshot.autoGoal.toString() }) {
                            Text("استخدام الاقتراح")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.filter { ch -> ch.isDigit() }.take(4) },
                        label = { Text("هدفي اليومي (سؤال)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    input.toIntOrNull()?.let { DailyGoalStore.setManualGoal(context, it) }
                    afterChange()
                    showTargetDialog = false
                }) { Text("حفظ الهدف") }
            },
            dismissButton = {
                Row {
                    if (snapshot.isManual) {
                        TextButton(onClick = {
                            DailyGoalStore.setManualGoal(context, null)
                            afterChange()
                            showTargetDialog = false
                        }) { Text("إزالة الهدف") }
                    }
                    TextButton(onClick = { showTargetDialog = false }) { Text("إلغاء") }
                }
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        LibCard(modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(76.dp)) {
                        val fraction = if (snapshot.hasStudentGoal) {
                            DailyGoalMath.progressFraction(snapshot.doneToday, snapshot.goal)
                        } else 0f
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawArc(
                                color = LibColors.Maroon.copy(alpha = 0.15f),
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = LibColors.Maroon,
                                startAngle = -90f,
                                sweepAngle = 360f * fraction,
                                useCenter = false,
                                style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${snapshot.doneToday}",
                                fontWeight = FontWeight.Bold, fontSize = 17.sp, color = LibColors.Maroon
                            )
                            Text(
                                if (snapshot.hasStudentGoal) "من ${snapshot.goal}" else "اليوم",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("أهدافي اليوم", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = LibColors.Maroon)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when {
                                !snapshot.hasStudentGoal ->
                                    "لم تضبط هدف أسئلة بعد. اضبط رقمك بنفسك — عدد الأسئلة الذي تنجزه كل يوم قرارك أنت."
                                snapshot.doneToday >= snapshot.goal ->
                                    "هدف الأسئلة: ${snapshot.goal} — أنجزته كاملًا اليوم، أحسنت."
                                else ->
                                    "هدف الأسئلة: ${snapshot.goal} سؤالًا في اليوم — أنجزت ${snapshot.doneToday} حتى الآن."
                            },
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showTargetDialog = true },
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text(if (snapshot.hasStudentGoal) "تعديل هدف الأسئلة" else "ضبط هدف الأسئلة", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "أهداف اليوم المكتوبة",
                        fontWeight = FontWeight.Bold, fontSize = 13.sp, color = LibColors.Maroon,
                        modifier = Modifier.weight(1f)
                    )
                    if (items.isNotEmpty()) {
                        Text(
                            "أنجزت ${DayGoalsMath.doneCount(items)} من ${items.size}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                if (items.isEmpty()) {
                    Text(
                        "لا أهداف مكتوبة لليوم — اكتب مادة تدرسها اليوم أو أي هدف تريد إنجازه.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                items.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = item.done,
                            onCheckedChange = {
                                DayGoalsStore.saveItems(context, DayGoalsMath.toggleItem(items, item.id))
                                afterChange()
                            }
                        )
                        Text(
                            item.text,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                            textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (item.done) 0.55f else 0.9f)
                        )
                        IconButton(onClick = {
                            DayGoalsStore.saveItems(context, DayGoalsMath.removeItem(items, item.id))
                            afterChange()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف الهدف", tint = LibColors.Maroon)
                        }
                    }
                }
                if (items.size < DayGoalsMath.MAX_ITEMS) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = newGoalText,
                            onValueChange = { newGoalText = it.take(90) },
                            label = { Text("هدف جديد — مادة أو مهمة اليوم") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val updated = DayGoalsMath.addItem(items, newGoalText, DayGoalsStore.nextId(context))
                                if (updated.size > items.size) {
                                    DayGoalsStore.saveItems(context, updated)
                                    newGoalText = ""
                                    afterChange()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = LibColors.Maroon),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text("إضافة", fontSize = 13.sp)
                        }
                    }
                } else {
                    Text(
                        "وصلت للحد الأقصى لأهداف اليوم (${DayGoalsMath.MAX_ITEMS}).",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Timer, contentDescription = null,
                        tint = LibColors.Maroon, modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "تركيز اليوم: ${focusState.minutesToday} دقيقة · ${focusState.sessionsToday} جلسة",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CountdownUnit(modifier: Modifier = Modifier, value: String, label: String) {
    Surface(
        color = libGoldTint(),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 10.dp)
        ) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = LibColors.Maroon)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
        }
    }
}
