package sy.medpro.watani.ui.reverse

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import sy.medpro.watani.data.*
import sy.medpro.watani.ui.theme.MaroonPrimary

// Local progress for «اقلبها»: best score per round + intruder streak.
// Display-only gamification; the rounds themselves are 100% offline once
// the reverse.json section is downloaded.
private const val REVERSE_PREFS = "reverse_trainer"
private const val KEY_STREAK = "intruder_streak"
private const val KEY_BEST_STREAK = "intruder_best_streak"

private fun bestScoreKey(roundId: String) = "best_score_$roundId"
private fun bestMaxKey(roundId: String) = "best_max_$roundId"

private fun loadBestScores(context: Context, rounds: List<ReverseRoundDto>): Map<String, Pair<Int, Int>> {
    val prefs = context.getSharedPreferences(REVERSE_PREFS, Context.MODE_PRIVATE)
    val result = mutableMapOf<String, Pair<Int, Int>>()
    rounds.forEach { round ->
        val id = round.id ?: return@forEach
        if (prefs.contains(bestScoreKey(id))) {
            result[id] = prefs.getInt(bestScoreKey(id), 0) to prefs.getInt(bestMaxKey(id), 0)
        }
    }
    return result
}

private fun saveBestScore(context: Context, roundId: String, score: Int, max: Int) {
    val prefs = context.getSharedPreferences(REVERSE_PREFS, Context.MODE_PRIVATE)
    val previous = if (prefs.contains(bestScoreKey(roundId))) prefs.getInt(bestScoreKey(roundId), 0) else -1
    if (score > previous) {
        prefs.edit()
            .putInt(bestScoreKey(roundId), score)
            .putInt(bestMaxKey(roundId), max)
            .apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReverseTrainerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reverseEnvelope by remember { mutableStateOf<ReverseEnvelope?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var statusText by remember { mutableStateOf("جاري تحميل حزمة «اقلبها»...") }

    // Navigation: null = round list, otherwise the round being played.
    var currentRound by remember { mutableStateOf<ReverseRoundDto?>(null) }
    var roundAttempt by remember { mutableIntStateOf(0) }

    // Filters
    var systemFilter by remember { mutableStateOf<String?>(null) }
    var patternFilter by remember { mutableStateOf<String?>(null) }
    var difficultyFilter by remember { mutableStateOf<Int?>(null) }

    // Local progress
    var bestScores by remember { mutableStateOf<Map<String, Pair<Int, Int>>>(emptyMap()) }
    var intruderStreak by remember { mutableIntStateOf(0) }
    var intruderBestStreak by remember { mutableIntStateOf(0) }

    val loadPack: () -> Unit = {
        scope.launch {
            isLoading = true
            val repo = WataniRepository(context)
            var env = repo.getReverseEnvelope()
            if (env == null) {
                val manifestRes = repo.fetchManifest()
                if (manifestRes.isSuccess) {
                    val entry = manifestRes.getOrNull()?.files?.get("reverse.json")
                    if (entry != null) {
                        repo.downloadSection("reverse.json", entry) { prog -> downloadProgress = prog }
                        env = repo.getReverseEnvelope()
                    }
                }
            }
            reverseEnvelope = env
            val rounds = env?.rounds.orEmpty()
            bestScores = loadBestScores(context, rounds)
            val prefs = context.getSharedPreferences(REVERSE_PREFS, Context.MODE_PRIVATE)
            intruderStreak = prefs.getInt(KEY_STREAK, 0)
            intruderBestStreak = prefs.getInt(KEY_BEST_STREAK, 0)
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadPack()
    }

    val onRoundFinished: (ReverseRoundDto, Int, Int) -> Unit = { round, score, max ->
        val id = round.id
        if (id != null) {
            saveBestScore(context, id, score, max)
            bestScores = bestScores + (id to ((bestScores[id]?.let { maxOf(it.first, score) } ?: score) to max))
        }
        if (round.pattern == ReversePatterns.INTRUDER) {
            val prefs = context.getSharedPreferences(REVERSE_PREFS, Context.MODE_PRIVATE)
            intruderStreak = if (score >= INTRUDER_HIT_POINTS) intruderStreak + 1 else 0
            if (intruderStreak > intruderBestStreak) intruderBestStreak = intruderStreak
            prefs.edit()
                .putInt(KEY_STREAK, intruderStreak)
                .putInt(KEY_BEST_STREAK, intruderBestStreak)
                .apply()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        currentRound?.let { patternLabel(it.pattern) } ?: "اقلبها — التدرّب العكسي",
                        fontWeight = FontWeight.Bold, fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (currentRound != null) currentRound = null else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaroonPrimary, titleContentColor = MaterialTheme.colorScheme.onPrimary)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            when {
                isLoading -> {
                    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaroonPrimary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(statusText, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth().height(8.dp), color = MaroonPrimary)
                    }
                }
                reverseEnvelope?.rounds.isNullOrEmpty() -> {
                    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("تعذر تحميل حزمة «اقلبها».", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(shape = MaterialTheme.shapes.medium, onClick = { loadPack() }, colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)) {
                            Text("إعادة المحاولة")
                        }
                    }
                }
                currentRound != null -> {
                    val round = currentRound!!
                    key(round.id, roundAttempt) {
                        ReverseRoundPlay(
                            round = round,
                            onFinished = onRoundFinished,
                            onExit = { currentRound = null },
                            onReplay = { roundAttempt++ }
                        )
                    }
                }
                else -> {
                    val rounds = reverseEnvelope!!.rounds!!
                    ReverseRoundList(
                        rounds = rounds,
                        bestScores = bestScores,
                        intruderStreak = intruderStreak,
                        intruderBestStreak = intruderBestStreak,
                        systemFilter = systemFilter,
                        patternFilter = patternFilter,
                        difficultyFilter = difficultyFilter,
                        onSystemFilter = { systemFilter = it },
                        onPatternFilter = { patternFilter = it },
                        onDifficultyFilter = { difficultyFilter = it },
                        onPick = { picked ->
                            currentRound = picked
                            roundAttempt++
                        },
                        onMixed = {
                            val pool = rounds.filter { r ->
                                (systemFilter == null || r.systemAr == systemFilter) &&
                                    (difficultyFilter == null || r.difficulty == difficultyFilter)
                            }
                            val picked = (pool.ifEmpty { rounds }).random()
                            currentRound = picked
                            roundAttempt++
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReverseRoundList(
    rounds: List<ReverseRoundDto>,
    bestScores: Map<String, Pair<Int, Int>>,
    intruderStreak: Int,
    intruderBestStreak: Int,
    systemFilter: String?,
    patternFilter: String?,
    difficultyFilter: Int?,
    onSystemFilter: (String?) -> Unit,
    onPatternFilter: (String?) -> Unit,
    onDifficultyFilter: (Int?) -> Unit,
    onPick: (ReverseRoundDto) -> Unit,
    onMixed: () -> Unit
) {
    val systems = remember(rounds) { rounds.mapNotNull { it.systemAr }.distinct().sorted() }
    val filtered = rounds.filter { r ->
        (systemFilter == null || r.systemAr == systemFilter) &&
            (patternFilter == null || r.pattern == patternFilter) &&
            (difficultyFilter == null || r.difficulty == difficultyFilter)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "يُعرض التشخيص جاهزًا — ومهمّتك أن تبني صورته السريرية بنفسك: ما الذي يجب أن تجده عند هذا المريض، وما الذي ينقض التشخيص؟",
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "سلسلة «اطرِد الدخيل» الحالية: $intruderStreak • أفضل سلسلة: $intruderBestStreak",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaroonPrimary
                    )
                }
            }
        }
        item {
            Button(
                shape = MaterialTheme.shapes.medium,
                onClick = onMixed,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
            ) {
                Text("🎲 جولة مختلطة عشوائية", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        item {
            Text("النمط", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                FilterChip(selected = patternFilter == null, onClick = { onPatternFilter(null) }, label = { Text("الكل") })
                FilterChip(selected = patternFilter == ReversePatterns.BUILD, onClick = { onPatternFilter(ReversePatterns.BUILD) }, label = { Text("ركّب الصورة") })
                FilterChip(selected = patternFilter == ReversePatterns.INTRUDER, onClick = { onPatternFilter(ReversePatterns.INTRUDER) }, label = { Text("اطرِد الدخيل") })
                FilterChip(selected = patternFilter == ReversePatterns.COMPLETE, onClick = { onPatternFilter(ReversePatterns.COMPLETE) }, label = { Text("أكمل الناقص") })
            }
        }
        item {
            Text("الصعوبة", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                FilterChip(selected = difficultyFilter == null, onClick = { onDifficultyFilter(null) }, label = { Text("الكل") })
                listOf(1, 2, 3).forEach { level ->
                    FilterChip(
                        selected = difficultyFilter == level,
                        onClick = { onDifficultyFilter(level) },
                        label = { Text(difficultyLabel(level)) }
                    )
                }
            }
        }
        item {
            Text("الجهاز / الاختصاص", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            ) {
                FilterChip(selected = systemFilter == null, onClick = { onSystemFilter(null) }, label = { Text("الكل") })
                systems.forEach { system ->
                    FilterChip(
                        selected = systemFilter == system,
                        onClick = { onSystemFilter(system) },
                        label = { Text(system) }
                    )
                }
            }
        }
        item {
            Text("الجولات (${filtered.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
        }
        if (filtered.isEmpty()) {
            item {
                Text("لا جولات مطابقة للمرشّحات الحالية.", fontSize = 14.sp)
            }
        }
        items(filtered, key = { it.id ?: "" }) { round ->
            val best = round.id?.let { bestScores[it] }
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().clickable { onPick(round) }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(round.diagnosis ?: "", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        if (best != null && best.second > 0 && best.first >= best.second) {
                            Icon(Icons.Default.Check, contentDescription = "مكتملة", tint = MaroonPrimary)
                        }
                    }
                    round.diagnosisEn?.let {
                        Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "${patternLabel(round.pattern)} • ${round.systemAr ?: ""} • ${difficultyLabel(round.difficulty)}",
                        fontSize = 13.sp, color = MaroonPrimary, fontWeight = FontWeight.Medium
                    )
                    round.pairRival?.let {
                        Text("الشبيه المستهدف: $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                    if (best != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("أفضل نتيجة: ${best.first}/${best.second}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReverseRoundPlay(
    round: ReverseRoundDto,
    onFinished: (ReverseRoundDto, Int, Int) -> Unit,
    onExit: () -> Unit,
    onReplay: () -> Unit
) {
    when (round.pattern) {
        ReversePatterns.INTRUDER -> IntruderPlay(round, onFinished, onExit, onReplay)
        ReversePatterns.COMPLETE -> CompletePlay(round, onFinished, onExit, onReplay)
        else -> BuildPlay(round, onFinished, onExit, onReplay)
    }
}

@Composable
private fun DiagnosisHeader(round: ReverseRoundDto, instruction: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("التشخيص", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(round.diagnosis ?: "", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaroonPrimary)
            round.diagnosisEn?.let {
                Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            round.context?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "الشبيه المستهدف: ${round.pairRival ?: "—"} • ${difficultyLabel(round.difficulty)}",
                fontSize = 12.sp, fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(instruction, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ResultCard(score: Int, max: Int, rating: String?, round: ReverseRoundDto) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("نتيجتك: $score / $max", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaroonPrimary)
            if (rating != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(rating, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            round.completePicture?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text("الصورة المرجعية الكاملة", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(it, fontSize = 14.sp)
            }
            round.keyRule?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text("قاعدة التمييز", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(it, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun RoundEndButtons(onExit: () -> Unit, onReplay: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            shape = MaterialTheme.shapes.medium,
            onClick = onReplay,
            modifier = Modifier.weight(1f).height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
        ) { Text("إعادة الجولة", fontWeight = FontWeight.Bold) }
        OutlinedButton(
            shape = MaterialTheme.shapes.medium,
            onClick = onExit,
            modifier = Modifier.weight(1f).height(48.dp)
        ) { Text("جولة أخرى", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun BuildPlay(
    round: ReverseRoundDto,
    onFinished: (ReverseRoundDto, Int, Int) -> Unit,
    onExit: () -> Unit,
    onReplay: () -> Unit
) {
    val features = round.features.orEmpty()
    // Fixed shuffled order for this round (seeded by the round id).
    val displayOrder = remember(round.id) { seededShuffledIndices(round.id ?: "build", features.size) }
    var selected by remember(round.id) { mutableStateOf<Set<Int>>(emptySet()) }
    var result by remember(round.id) { mutableStateOf<BuildResult?>(null) }
    val finished = result != null

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DiagnosisHeader(round, "اختر كل السمات المنتمية لهذا التشخيص، واترك سمات الشبيه.")
        displayOrder.forEach { index ->
            val feature = features[index]
            val isSelected = index in selected
            val bg = when {
                finished && feature.isBelonging() && isSelected -> MaterialTheme.colorScheme.primaryContainer
                finished && feature.isIntruder() && isSelected -> MaterialTheme.colorScheme.errorContainer
                finished && feature.isBelonging() && !isSelected -> MaterialTheme.colorScheme.tertiaryContainer
                isSelected -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = bg),
                modifier = Modifier.fillMaxWidth().clickable(enabled = !finished) {
                    selected = if (isSelected) selected - index else selected + index
                }
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Checkbox(checked = isSelected, onCheckedChange = if (finished) null else { checked ->
                        selected = if (checked) selected + index else selected - index
                    })
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(feature.text ?: "", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        if (finished) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val status = when {
                                feature.isBelonging() && isSelected -> "✓ أصبتها — تنتمي للتشخيص"
                                feature.isBelonging() -> "فاتتك — تنتمي للتشخيص"
                                isSelected -> "✗ دخيلة اخترتها — تنتمي إلى: ${feature.belongsTo ?: "الشبيه"}"
                                else -> "✓ تركتها — دخيلة تنتمي إلى: ${feature.belongsTo ?: "الشبيه"}"
                            }
                            Text(status, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaroonPrimary)
                            feature.why?.let {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(it, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
        if (!finished) {
            Button(
                shape = MaterialTheme.shapes.medium,
                onClick = {
                    val scored = scoreBuild(round, selected)
                    result = scored
                    onFinished(round, scored.score, scored.maxScore)
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
            ) { Text("إنهاء الجولة وعرض النتيجة", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        } else {
            val scored = result!!
            if (scored.missedDiscriminating.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaroonPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("فجوات حاسمة — سمات مميِّزة فاتتك", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        scored.missedDiscriminating.forEach { feature ->
                            Text("• ${feature.text ?: ""}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            feature.why?.let { Text(it, fontSize = 13.sp) }
                        }
                    }
                }
            }
            ResultCard(scored.score, scored.maxScore, scored.rating, round)
            RoundEndButtons(onExit, onReplay)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun IntruderPlay(
    round: ReverseRoundDto,
    onFinished: (ReverseRoundDto, Int, Int) -> Unit,
    onExit: () -> Unit,
    onReplay: () -> Unit
) {
    val features = round.features.orEmpty()
    val displayOrder = remember(round.id) { seededShuffledIndices((round.id ?: "intruder") + ":intruder", features.size) }
    var picked by remember(round.id) { mutableStateOf<Int?>(null) }
    var finished by remember(round.id) { mutableStateOf(false) }
    var score by remember(round.id) { mutableIntStateOf(0) }
    val correctIndex = remember(round.id) { intruderIndex(round) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DiagnosisHeader(round, "أربع سمات تنتمي لهذا التشخيص وواحدة دخيلة من الشبيه — حدّد الدخيلة.")
        displayOrder.forEach { index ->
            val feature = features[index]
            val isPicked = picked == index
            val bg = when {
                finished && index == correctIndex -> MaterialTheme.colorScheme.primaryContainer
                finished && isPicked && index != correctIndex -> MaterialTheme.colorScheme.errorContainer
                isPicked -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = bg),
                modifier = Modifier.fillMaxWidth().clickable(enabled = !finished) { picked = index }
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    RadioButton(selected = isPicked, onClick = { if (!finished) picked = index })
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(feature.text ?: "", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        if (finished) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val status = when {
                                index == correctIndex -> "✓ هذه هي الدخيلة — تنتمي إلى: ${feature.belongsTo ?: "الشبيه"}"
                                else -> "تنتمي للتشخيص"
                            }
                            Text(status, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaroonPrimary)
                            feature.why?.let {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(it, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
        if (!finished) {
            Button(
                shape = MaterialTheme.shapes.medium,
                onClick = {
                    picked?.let { pickedIndex ->
                        score = scoreIntruder(round, pickedIndex)
                        finished = true
                        onFinished(round, score, intruderMaxScore())
                    }
                },
                enabled = picked != null,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
            ) { Text("تأكيد الاختيار", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        } else {
            ResultCard(score, intruderMaxScore(), if (score > 0) "أصبت الدخيل 🎯" else "فاتك الدخيل — راجع الشروح", round)
            RoundEndButtons(onExit, onReplay)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun CompletePlay(
    round: ReverseRoundDto,
    onFinished: (ReverseRoundDto, Int, Int) -> Unit,
    onExit: () -> Unit,
    onReplay: () -> Unit
) {
    val options = round.options.orEmpty()
    val displayOrder = remember(round.id) { seededShuffledIndices((round.id ?: "complete") + ":complete", options.size) }
    var picked by remember(round.id) { mutableStateOf<Int?>(null) }
    var finished by remember(round.id) { mutableStateOf(false) }
    var score by remember(round.id) { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DiagnosisHeader(round, "الصورة أدناه شبه مكتملة — اختر السمة الحاسمة الناقصة.")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("السمات المعروضة", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                round.presentedFeatures.orEmpty().forEach { presented ->
                    Text("• ${presented.text ?: ""}", fontSize = 14.sp)
                    if (finished) {
                        presented.why?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)) }
                    }
                }
                round.missingSlot?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("المحور الناقص: $it", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaroonPrimary)
                }
            }
        }
        displayOrder.forEach { index ->
            val option = options[index]
            val isPicked = picked == index
            val bg = when {
                finished && option.verdict == ReverseVerdicts.KEY -> MaterialTheme.colorScheme.primaryContainer
                finished && isPicked && option.verdict != ReverseVerdicts.KEY -> MaterialTheme.colorScheme.errorContainer
                isPicked -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = bg),
                modifier = Modifier.fillMaxWidth().clickable(enabled = !finished) { picked = index }
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    RadioButton(selected = isPicked, onClick = { if (!finished) picked = index })
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(option.text ?: "", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        if (finished) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val verdictLabel = when (option.verdict) {
                                ReverseVerdicts.KEY -> "✓ السمة الحاسمة (+5)"
                                ReverseVerdicts.TRUE_WEAK -> "صحيحة لكن غير حاسمة (+2)"
                                else -> "سمة الشبيه (0)"
                            }
                            Text(verdictLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaroonPrimary)
                            option.why?.let {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(it, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
        if (!finished) {
            Button(
                shape = MaterialTheme.shapes.medium,
                onClick = {
                    picked?.let { pickedIndex ->
                        score = scoreComplete(round, pickedIndex)
                        finished = true
                        onFinished(round, score, completeMaxScore())
                    }
                },
                enabled = picked != null,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
            ) { Text("تأكيد الاختيار", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        } else {
            val rating = when (score) {
                COMPLETE_KEY_POINTS -> "أمسكت السمة الحاسمة 🎯"
                COMPLETE_TRUE_WEAK_POINTS -> "صحيحة طبيًا لكنها ليست الحاسمة"
                else -> "هذه سمة الشبيه — راجع قاعدة التمييز"
            }
            ResultCard(score, completeMaxScore(), rating, round)
            RoundEndButtons(onExit, onReplay)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
