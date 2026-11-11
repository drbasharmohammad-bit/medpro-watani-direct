package sy.medpro.watani.ui.mistakes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.data.MistakeEntry
import sy.medpro.watani.data.MistakesBank
import sy.medpro.watani.data.MistakesSrs
import sy.medpro.watani.data.getCorrectIndices
import sy.medpro.watani.ui.drawer.DrawerScreenScaffold
import sy.medpro.watani.ui.quiz.QuestionCard
import sy.medpro.watani.ui.theme.ErrorRed
import sy.medpro.watani.ui.theme.MaroonPrimary
import sy.medpro.watani.ui.theme.SuccessGreen

/** Arabic label for the surface a mistake came from. */
private fun bankLabel(bank: String): String = when (bank) {
    "questions" -> "البنك الوطني"
    "questions_en" -> "البنك الوطني (إنجليزي)"
    "dawrat_jami3at" -> "دورات الجامعات"
    "uworld" -> "UWorld"
    "daily" -> "التحدي اليومي"
    "simulator" -> "محاكاة الامتحان"
    else -> "اختبار"
}

private fun dueLabel(entry: MistakeEntry, now: Long): String {
    if (entry.mastered) return "تم إتقانه"
    val diff = entry.dueAt - now
    if (diff <= 0) return "مستحق للمراجعة الآن"
    val days = (diff + MistakesSrs.DAY_MS - 1) / MistakesSrs.DAY_MS
    return if (days <= 1L) "يستحق غدًا" else "يستحق بعد $days أيام"
}

/**
 * «بنك أخطائي» — the personal mistakes bank screen (build 1.2.5).
 * Stats header, per-subject filter chips, the active deck, the
 * mastered list, and the «درّبني الآن» spaced-review session which
 * re-asks items through the SAME shared QuestionCard as every quiz
 * (including the «إظهار الشرح» reveal rule from 1.2.3). Fully local.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MistakesBankScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val changes by MistakesBank.changes.collectAsState()
    var entries by remember { mutableStateOf(MistakesBank.entries(context)) }
    LaunchedEffect(changes) { entries = MistakesBank.entries(context) }

    var subjectFilter by remember { mutableStateOf<String?>(null) }
    var showMastered by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    var sessionItems by remember { mutableStateOf<List<MistakeEntry>?>(null) }
    var sessionAnswers by remember { mutableStateOf(mapOf<String, Int>()) }

    val now = System.currentTimeMillis()
    val active = remember(entries) { entries.filter { !it.mastered } }
    val mastered = remember(entries) { entries.filter { it.mastered } }
    val dueCount = remember(entries, now) { active.count { MistakesSrs.isDue(it, now) } }
    val subjects = remember(active) {
        active.groupBy { it.subject }.entries
            .map { it.key to it.value.size }
            .sortedByDescending { it.second }
    }
    val visibleDeck = remember(active, subjectFilter) {
        val scoped = if (subjectFilter == null) active else active.filter { it.subject == subjectFilter }
        scoped.sortedWith(compareBy({ it.dueAt }, { -it.lastWrongAt }))
    }

    fun startSession(items: List<MistakeEntry>) {
        if (items.isEmpty()) return
        sessionItems = items
        sessionAnswers = emptyMap()
    }

    DrawerScreenScaffold(title = "بنك أخطائي", onBack = onBack) { padding ->
        val items = sessionItems
        if (items != null) {
            // ------------------------- Review session -------------------------
            val answeredCount = items.count { sessionAnswers.containsKey(it.key) }
            val correctCount = items.count { entry ->
                val sel = sessionAnswers[entry.key] ?: return@count false
                getCorrectIndices(entry.toDto(), entry.options).contains(sel)
            }
            val finished = answeredCount == items.size && items.isNotEmpty()
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaroonPrimary.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (finished) "انتهت الجلسة: $correctCount صحيحة من ${items.size}"
                                else "جلسة تدريب الأخطاء: أجبت عن $answeredCount من ${items.size}",
                                fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaroonPrimary
                            )
                            TextButton(onClick = { sessionItems = null }) {
                                Text(if (finished) "عودة للبنك" else "إنهاء الجلسة", fontSize = 13.sp)
                            }
                        }
                        if (finished) {
                            Text(
                                "الأخطاء المتكررة عادت لجدولتها بعد يوم، والصحيحة تقدّمت بفواصل أطول (3 ثم 7 ثم 21 يومًا) حتى الإتقان.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    itemsIndexed(items, key = { _, e -> e.key }) { index, entry ->
                        val dto = remember(entry.key) { entry.toDto() }
                        val correctIndices = remember(entry.key) { getCorrectIndices(dto, entry.options) }
                        val selected = sessionAnswers[entry.key]
                        QuestionCard(
                            questionNumber = index + 1,
                            question = dto,
                            options = entry.options,
                            ltr = entry.ltr,
                            selectedOption = selected,
                            correctIndices = correctIndices,
                            isAnswered = selected != null,
                            subjectLabel = entry.subject,
                            onOptionSelected = { optIdx ->
                                if (selected == null && correctIndices.isNotEmpty()) {
                                    sessionAnswers = sessionAnswers + (entry.key to optIdx)
                                    MistakesBank.recordReviewResult(
                                        context = context,
                                        key = entry.key,
                                        correct = correctIndices.contains(optIdx)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        } else {
            // ----------------------------- Deck -----------------------------
            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(shape = CircleShape, color = MaroonPrimary.copy(alpha = 0.12f), modifier = Modifier.size(96.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(48.dp))
                            }
                        }
                        Text("لا توجد أخطاء مسجلة حالياً.", fontWeight = FontWeight.Bold, fontSize = 17.sp, textAlign = TextAlign.Center)
                        Text(
                            "أي سؤال تخطئ فيه بأي بنك (الوطني، الإنجليزي، دورات الجامعات، UWorld، التحدي اليومي، المحاكاة) سيُحفظ هنا تلقائياً لتراجعه بجدولة متباعدة حتى تتقنه — كل ذلك على جهازك وبدون إنترنت.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.height(48.dp)
                        ) { Text("عودة") }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaroonPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                StatColumn("أخطاء نشطة", active.size.toString())
                                StatColumn("مستحقة اليوم", dueCount.toString())
                                StatColumn("أتقنتها", mastered.size.toString())
                            }
                        }
                    }
                    item {
                        Button(
                            onClick = {
                                startSession(
                                    MistakesBank.sessionQueue(
                                        context = context,
                                        subject = subjectFilter
                                    )
                                )
                            },
                            enabled = active.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("درّبني الآن", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                    if (subjects.isNotEmpty()) {
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    FilterChip(
                                        selected = subjectFilter == null,
                                        onClick = { subjectFilter = null },
                                        label = { Text("الكل (${active.size})", fontSize = 12.sp) }
                                    )
                                }
                                items(subjects, key = { it.first }) { (subject, count) ->
                                    FilterChip(
                                        selected = subjectFilter == subject,
                                        onClick = { subjectFilter = if (subjectFilter == subject) null else subject },
                                        label = { Text("$subject ($count)", fontSize = 12.sp) }
                                    )
                                }
                            }
                        }
                    }
                    items(visibleDeck, key = { it.key }) { entry ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaroonPrimary.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth().clickable { startSession(listOf(entry)) }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    entry.stem,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${entry.subject} • ${bankLabel(entry.bank)}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        dueLabel(entry, now),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (MistakesSrs.isDue(entry, now)) ErrorRed else MaroonPrimary
                                    )
                                }
                                if (entry.wrongCount > 1) {
                                    Text(
                                        "أخطأت في هذا السؤال ${entry.wrongCount} مرات",
                                        fontSize = 11.sp,
                                        color = ErrorRed
                                    )
                                }
                            }
                        }
                    }
                    if (mastered.isNotEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth().clickable { showMastered = !showMastered }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "أتقنتها (${mastered.size}) — اضغط ${if (showMastered) "للإخفاء" else "للعرض"}",
                                        fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SuccessGreen
                                    )
                                }
                            }
                        }
                        if (showMastered) {
                            items(mastered, key = { "m_${it.key}" }) { entry ->
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            entry.stem,
                                            fontSize = 13.sp,
                                            lineHeight = 19.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${entry.subject} • ${bankLabel(entry.bank)}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item {
                        TextButton(
                            onClick = { showClearDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("مسح بنك الأخطاء", color = ErrorRed, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("مسح بنك الأخطاء", fontWeight = FontWeight.Bold) },
            text = { Text("سيتم حذف كل الأخطاء المسجلة وجدولتها نهائياً من هذا الجهاز. لا يمكن التراجع عن هذا الإجراء.") },
            confirmButton = {
                TextButton(onClick = {
                    MistakesBank.clear(context)
                    showClearDialog = false
                    subjectFilter = null
                }) { Text("مسح الكل", color = ErrorRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        )
        Text(
            label,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            fontSize = 12.sp
        )
    }
}
