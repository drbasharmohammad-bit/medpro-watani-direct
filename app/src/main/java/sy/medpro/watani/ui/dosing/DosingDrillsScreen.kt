package sy.medpro.watani.ui.dosing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import sy.medpro.watani.data.DosingDrillDto
import sy.medpro.watani.data.WataniRepository
import sy.medpro.watani.ui.drawer.DrawerScreenScaffold
import sy.medpro.watani.ui.skills.SkillsProgress
import sy.medpro.watani.ui.theme.*

/**
 * Dosing drills trainer (build 1.2.6) — Tools tile «مدرب الجرعات».
 * Content = dosing_drills.json (20 sourced drug/dose/route drills),
 * loaded exactly like osce.json: downloaded section first, manifest +
 * downloadSection on first use, bundled sample as the last fallback —
 * fully offline afterwards. A session walks the pack as scenario
 * cards with instant verdicts, the sourced explanation and the source
 * line; per-drill miss counts persist locally (SkillsProgress) and
 * feed the «أدويتي الضعيفة» review list. Session scoring is the pure
 * DosingScoring core.
 */
@Composable
fun DosingDrillsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var drills by remember { mutableStateOf<List<DosingDrillDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var inSession by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var sessionDrills by remember { mutableStateOf<List<DosingDrillDto>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var chosen by remember { mutableStateOf<Int?>(null) }
    var answers by remember { mutableStateOf<List<DrillAnswer>>(emptyList()) }

    suspend fun loadDrills() {
        isLoading = true
        errorMessage = null
        try {
            val repo = WataniRepository(context)
            var list = repo.getDosingDrills()
            if (list.isNullOrEmpty()) {
                val manifestRes = repo.fetchManifest()
                if (manifestRes.isSuccess) {
                    val entry = manifestRes.getOrNull()?.files?.get("dosing_drills.json")
                    if (entry != null) {
                        repo.downloadSection("dosing_drills.json", entry)
                        list = repo.getDosingDrills()
                    }
                }
            }
            if (list.isNullOrEmpty()) {
                list = repo.getBundledDosingDrills()
            }
            val usable = list?.filter {
                !it.options.isNullOrEmpty() && it.options!!.size >= 2 &&
                    it.correctIndex != null && it.correctIndex in it.options.indices
            } ?: emptyList()
            if (usable.isNotEmpty()) {
                drills = usable
            } else {
                errorMessage = "تعذر تحميل تدريبات الجرعات. يرجى التحقق من اتصال الإنترنت وإعادة المحاولة."
            }
        } catch (e: Exception) {
            errorMessage = "خطأ في الاتصال: ${e.localizedMessage ?: "تأكد من اتصالك بالإنترنت"}"
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadDrills() }

    fun startSession(pool: List<DosingDrillDto>) {
        sessionDrills = pool.shuffled()
        currentIndex = 0
        chosen = null
        answers = emptyList()
        finished = false
        inSession = true
    }

    fun goBack() {
        if (inSession) {
            inSession = false
            finished = false
        } else {
            onBack()
        }
    }
    BackHandler { goBack() }

    DrawerScreenScaffold(title = "مدرب الجرعات", onBack = { goBack() }) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            when {
                isLoading -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = MaroonPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("جاري تحميل تدريبات الجرعات...", fontWeight = FontWeight.Bold, color = MaroonPrimary)
                }
                drills.isEmpty() -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.PendingActions, contentDescription = null, tint = GoldAccentDark, modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("تعذر تحميل تدريبات الجرعات", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaroonPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "حدث خطأ أثناء تحميل التدريبات. يرجى إعادة المحاولة.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { coroutineScope.launch { loadDrills() } },
                        colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إعادة المحاولة", fontWeight = FontWeight.Bold)
                    }
                }
                finished -> DosingSessionResult(
                    score = DosingScoring.score(answers),
                    drillsById = drills.associateBy { it.id ?: "" },
                    onRestart = { startSession(drills) },
                    onHome = { inSession = false; finished = false }
                )
                inSession && sessionDrills.isNotEmpty() -> {
                    val drill = sessionDrills[currentIndex]
                    DosingDrillCard(
                        drill = drill,
                        position = currentIndex + 1,
                        total = sessionDrills.size,
                        chosen = chosen,
                        onChoose = { idx ->
                            if (chosen == null) {
                                chosen = idx
                                val correctIdx = drill.correctIndex ?: -1
                                answers = answers + DrillAnswer(drill.id ?: "", idx, correctIdx)
                                drill.id?.let {
                                    SkillsProgress.recordDrillAnswer(context, it, idx == correctIdx)
                                }
                            }
                        },
                        onNext = {
                            if (currentIndex >= sessionDrills.lastIndex) {
                                finished = true
                            } else {
                                currentIndex++
                                chosen = null
                            }
                        }
                    )
                }
                else -> DosingHome(
                    drills = drills,
                    onStartAll = { startSession(drills) },
                    onStartWeak = { weak ->
                        val pool = drills.filter { (it.id ?: "") in weak }
                        if (pool.isNotEmpty()) startSession(pool)
                    }
                )
            }
        }
    }
}

@Composable
private fun DosingHome(
    drills: List<DosingDrillDto>,
    onStartAll: () -> Unit,
    onStartWeak: (Set<String>) -> Unit
) {
    val context = LocalContext.current
    val changes by SkillsProgress.changes.collectAsState()
    val weakIds = remember(changes, drills) {
        SkillsProgress.weakDrillIds(context, drills.mapNotNull { it.id })
    }
    val weakDrills = remember(weakIds, drills) { drills.filter { (it.id ?: "") in weakIds } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaroonDeep),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("مدرب الجرعات والأدوية", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "سيناريوهات سريرية قصيرة: اختر الدواء والجرعة والطريق الصحيحين. كل جرعة موثّقة بمصدرها (لصاقة رسمية أو إرشاد معتمد) يظهر مع الشرح.",
                        color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp
                    )
                }
            }
        }
        item {
            Button(
                onClick = onStartAll,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("بدء جلسة كاملة (${drills.size} تدريبًا)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
        if (weakDrills.isNotEmpty()) {
            item {
                Text("أدويتي الضعيفة — أخطأت فيها من قبل:", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
            }
            items(weakDrills, key = { it.id ?: it.drug ?: "" }) { drill ->
                val misses = remember(changes, drill.id) {
                    drill.id?.let { SkillsProgress.drillMissCount(context, it) } ?: 0
                }
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.06f)),
                    border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(drill.drug ?: "", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaroonPrimary)
                            Text(drill.focusAr ?: "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                        Text("أخطأت $misses", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ErrorRed)
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { onStartWeak(weakIds.toSet()) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("مراجعة الأدوية الضعيفة فقط", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun formatWeight(weightKg: Double?): String {
    if (weightKg == null) return ""
    return if (weightKg % 1.0 == 0.0) weightKg.toInt().toString() else weightKg.toString()
}

@Composable
private fun DosingDrillCard(
    drill: DosingDrillDto,
    position: Int,
    total: Int,
    chosen: Int?,
    onChoose: (Int) -> Unit,
    onNext: () -> Unit
) {
    val options = drill.options ?: emptyList()
    val correctIndex = drill.correctIndex ?: -1
    val answered = chosen != null

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(drill.drug ?: "", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaroonPrimary)
                Surface(color = MaroonPrimary.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        "تدريب $position من $total",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaroonPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            LinearProgressIndicator(
                progress = { position.toFloat() / total.coerceAtLeast(1) },
                color = MaroonPrimary,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("الحالة:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaroonPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(drill.scenarioAr ?: "", fontSize = 14.sp, lineHeight = 22.sp)
                    val patient = drill.patient
                    if (patient != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val sexAr = when (patient.sex) { "M" -> "ذكر"; "F" -> "أنثى"; else -> patient.sex ?: "" }
                        val parts = listOfNotNull(
                            patient.age?.let { "العمر: $it سنة" },
                            sexAr.takeIf { it.isNotBlank() }?.let { "الجنس: $it" },
                            formatWeight(patient.weightKg).takeIf { it.isNotBlank() }?.let { "الوزن: $it كغ" }
                        )
                        if (parts.isNotEmpty()) {
                            Text(parts.joinToString(" • "), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                        }
                    }
                }
            }
        }
        if (!drill.focusAr.isNullOrBlank()) {
            item {
                Text(drill.focusAr, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
            }
        }
        items(options.size) { idx ->
            val isChosen = chosen == idx
            val isCorrect = idx == correctIndex
            val container = when {
                !answered -> MaterialTheme.colorScheme.surface
                isCorrect -> SuccessGreen.copy(alpha = 0.1f)
                isChosen -> ErrorRed.copy(alpha = 0.08f)
                else -> MaterialTheme.colorScheme.surface
            }
            val border = when {
                !answered -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                isCorrect -> BorderStroke(1.dp, SuccessGreen)
                isChosen -> BorderStroke(1.dp, ErrorRed)
                else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            }
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = container),
                border = border,
                modifier = Modifier.fillMaxWidth().clickable(enabled = !answered) { onChoose(idx) }
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(options[idx], fontSize = 13.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    if (answered && isCorrect) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                    } else if (answered && isChosen) {
                        Icon(Icons.Default.Cancel, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        if (answered) {
            item {
                val wasCorrect = chosen == correctIndex
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (wasCorrect) SuccessGreen.copy(alpha = 0.08f) else ErrorRed.copy(alpha = 0.06f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            if (wasCorrect) "إجابة صحيحة ✓" else "إجابة خاطئة — الصواب: ${options.getOrNull(correctIndex) ?: ""}",
                            fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            color = if (wasCorrect) SuccessGreen else ErrorRed
                        )
                        if (!drill.explanationAr.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(drill.explanationAr, fontSize = 13.sp, lineHeight = 20.sp)
                        }
                        val src = drill.source
                        if (src != null && (!src.name.isNullOrBlank() || !src.detail.isNullOrBlank())) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "المصدر: ${src.name ?: ""}" + if (!src.detail.isNullOrBlank()) " — ${src.detail}" else "",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(if (position >= total) "إنهاء الجلسة ورؤية النتيجة" else "التدريب التالي", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun DosingSessionResult(
    score: DosingSessionScore,
    drillsById: Map<String, DosingDrillDto>,
    onRestart: () -> Unit,
    onHome: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaroonDeep),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("نتيجة جلسة الجرعات", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${score.percent}%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 42.sp)
                    Text(
                        "أجبت صحيحًا عن ${score.correct} من ${score.total} تدريبًا",
                        color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp
                    )
                }
            }
        }
        if (score.missedDrillIds.isNotEmpty()) {
            item {
                Text("أدوية أخطأت فيها بهذه الجلسة:", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
            }
            items(score.missedDrillIds, key = { it }) { id ->
                val drill = drillsById[id]
                Surface(
                    color = ErrorRed.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(drill?.drug ?: id, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            if (!drill?.focusAr.isNullOrBlank()) {
                                Text(drill.focusAr, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
        } else if (score.total > 0) {
            item {
                Text("جلسة كاملة بلا أخطاء — أحسنت.", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = SuccessGreen)
            }
        }
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onRestart,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("جلسة جديدة", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onHome,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("العودة لبداية المدرب", fontWeight = FontWeight.Bold)
            }
        }
    }
}
