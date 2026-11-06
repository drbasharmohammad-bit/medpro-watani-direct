package sy.medpro.watani.ui.ddx

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import sy.medpro.watani.data.*
import sy.medpro.watani.ui.theme.MaroonPrimary

// ---------------------------------------------------------------------------
// First-attempt progress record (SharedPreferences).
// Only the FIRST attempt of each case is stored; replays are practice and
// never replace it (design doc, closing step). Stored per case id:
// the student's probability ranking, the danger set, the A1 points and
// the total score of that first attempt, and a timestamp. Records saved
// before the full scoring existed have no total and stay readable.
// ---------------------------------------------------------------------------

private const val DDx_PREFS_NAME = "watani_ddx_progress"
private const val DDx_PREFS_KEY_RECORDS = "first_attempts_json"

data class DdxAttemptRecord(
    val caseId: String,
    val rankingLabels: List<String>,
    val rankingEn: List<String>,
    val mnmLabels: List<String>,
    val mnmEn: List<String>,
    val a1Points: Int,
    val total: Int? = null,
    val timestamp: Long
)

private fun readDdxAttemptRecords(context: Context): Map<String, DdxAttemptRecord> {
    return try {
        val prefs = context.getSharedPreferences(DDx_PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(DDx_PREFS_KEY_RECORDS, null) ?: return emptyMap()
        val root = JSONObject(raw)
        val result = mutableMapOf<String, DdxAttemptRecord>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val caseId = keys.next()
            val obj = root.optJSONObject(caseId) ?: continue
            fun stringList(name: String): List<String> {
                val arr = obj.optJSONArray(name) ?: return emptyList()
                return (0 until arr.length()).map { arr.optString(it) }
            }
            result[caseId] = DdxAttemptRecord(
                caseId = caseId,
                rankingLabels = stringList("rankingLabels"),
                rankingEn = stringList("rankingEn"),
                mnmLabels = stringList("mnmLabels"),
                mnmEn = stringList("mnmEn"),
                a1Points = obj.optInt("a1", 0),
                // Records saved before the full scoring existed carry
                // no total; they stay readable and show A1 only.
                total = if (obj.has("total") && !obj.isNull("total")) {
                    obj.optInt("total")
                } else {
                    null
                },
                timestamp = obj.optLong("ts", 0L)
            )
        }
        result
    } catch (_: Exception) {
        emptyMap()
    }
}

private fun saveDdxFirstAttempt(context: Context, record: DdxAttemptRecord) {
    try {
        val prefs = context.getSharedPreferences(DDx_PREFS_NAME, Context.MODE_PRIVATE)
        val existing = readDdxAttemptRecords(context)
        if (existing.containsKey(record.caseId)) return // first attempt only
        val rawRecords = prefs.getString(DDx_PREFS_KEY_RECORDS, null)
        val root = if (rawRecords == null) {
            JSONObject()
        } else {
            try {
                JSONObject(rawRecords)
            } catch (_: Exception) {
                // Corrupt stored JSON: abort this save instead of
                // rebuilding from an empty root and wiping every other
                // case's first-attempt record.
                return
            }
        }
        val obj = JSONObject()
            .put("rankingLabels", JSONArray(record.rankingLabels))
            .put("rankingEn", JSONArray(record.rankingEn))
            .put("mnmLabels", JSONArray(record.mnmLabels))
            .put("mnmEn", JSONArray(record.mnmEn))
            .put("a1", record.a1Points)
            .put("ts", record.timestamp)
        record.total?.let { obj.put("total", it) }
        root.put(record.caseId, obj)
        prefs.edit().putString(DDx_PREFS_KEY_RECORDS, root.toString()).apply()
    } catch (_: Exception) {
        // Progress persistence is best-effort; a failed save never blocks play.
    }
}

private fun ddxDifficultyLabel(difficulty: String?): String = when (difficulty) {
    "core" -> "أساسي"
    "advanced" -> "متقدّم"
    else -> difficulty ?: ""
}

private fun ddxSexLabel(sex: String?): String = when (sex) {
    "male" -> "ذكر"
    "female" -> "أنثى"
    else -> sex ?: ""
}

// ---------------------------------------------------------------------------
// Screen root: self-contained hosting and loading, mirroring
// AuscultationTrainerScreen (fetchManifest, manifest entry for ddx.json,
// downloadSection with progress, then getDdxEnvelope).
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DdxTrainerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ddxEnvelope by remember { mutableStateOf<DdxEnvelope?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var statusText by remember { mutableStateOf("جاري تحميل حزمة حالات التشخيص التفريقي...") }
    var selectedCaseId by remember { mutableStateOf<String?>(null) }
    var attemptRecords by remember { mutableStateOf<Map<String, DdxAttemptRecord>>(emptyMap()) }

    var searchQuery by remember { mutableStateOf("") }
    var systemFilter by remember { mutableStateOf<String?>(null) }
    var difficultyFilter by remember { mutableStateOf<String?>(null) }

    val loadPack: () -> Unit = {
        scope.launch {
            isLoading = true
            val repo = WataniRepository(context)
            var env = repo.getDdxEnvelope()
            if (env == null) {
                val manifestRes = repo.fetchManifest()
                if (manifestRes.isSuccess) {
                    val entry = manifestRes.getOrNull()?.files?.get("ddx.json")
                    if (entry != null) {
                        repo.downloadSection("ddx.json", entry) { prog -> downloadProgress = prog }
                        env = repo.getDdxEnvelope()
                    }
                }
            }
            ddxEnvelope = env
            attemptRecords = readDdxAttemptRecords(context)
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadPack()
    }

    val cases = ddxEnvelope?.cases.orEmpty()
    val selectedCase = cases.firstOrNull { it.id != null && it.id == selectedCaseId }

    // Diagnosis dictionary of the whole bank: every reference diagnosis,
    // de-duplicated on the diagnosis_en identity key.
    val dictionary = remember(cases) {
        val byKey = linkedMapOf<String, DdxDictionaryEntry>()
        cases.forEach { c ->
            c.differentialReference.orEmpty().forEach { ref ->
                val en = ref.diagnosisEn?.trim().orEmpty()
                if (en.isNotEmpty() && !byKey.containsKey(en.lowercase())) {
                    byKey[en.lowercase()] = DdxDictionaryEntry(
                        diagnosisAr = ref.diagnosisAr ?: en,
                        diagnosisEn = en
                    )
                }
            }
        }
        byKey.values.toList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        selectedCase?.titleAr ?: "مدرّب التشخيص التفريقي",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedCase != null) selectedCaseId = null else onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaroonPrimary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = MaroonPrimary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(statusText, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = MaroonPrimary
                        )
                    }
                }
                selectedCase != null -> {
                    DdxCasePlay(
                        case = selectedCase,
                        dictionary = dictionary,
                        hasFirstAttempt = attemptRecords.containsKey(selectedCase.id),
                        onFirstAttemptSaved = { attemptRecords = readDdxAttemptRecords(context) },
                        onExitToList = { selectedCaseId = null }
                    )
                }
                cases.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = MaroonPrimary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("تعذر تحميل حزمة الحالات.", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            shape = MaterialTheme.shapes.medium,
                            onClick = { loadPack() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
                        ) {
                            Text("إعادة المحاولة")
                        }
                    }
                }
                else -> {
                    val systems = remember(cases) {
                        cases.mapNotNull { it.systemAr }.filter { it.isNotBlank() }.distinct()
                    }
                    val filtered = cases.filter { c ->
                        (systemFilter == null || c.systemAr == systemFilter) &&
                            (difficultyFilter == null || c.difficulty == difficultyFilter) &&
                            (searchQuery.isBlank() ||
                                (c.titleAr ?: "").contains(searchQuery, ignoreCase = true) ||
                                (c.chiefComplaintAr ?: "").contains(searchQuery, ignoreCase = true) ||
                                (c.specialtyAr ?: "").contains(searchQuery, ignoreCase = true))
                    }
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("بحث بعنوان الحالة أو الشكاية أو الاختصاص") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = systemFilter == null,
                                onClick = { systemFilter = null },
                                label = { Text("كل الأجهزة") }
                            )
                            systems.forEach { system ->
                                FilterChip(
                                    selected = systemFilter == system,
                                    onClick = { systemFilter = if (systemFilter == system) null else system },
                                    label = { Text(system) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = difficultyFilter == null,
                                onClick = { difficultyFilter = null },
                                label = { Text("كل المستويات") }
                            )
                            FilterChip(
                                selected = difficultyFilter == "core",
                                onClick = { difficultyFilter = if (difficultyFilter == "core") null else "core" },
                                label = { Text("أساسي") }
                            )
                            FilterChip(
                                selected = difficultyFilter == "advanced",
                                onClick = { difficultyFilter = if (difficultyFilter == "advanced") null else "advanced" },
                                label = { Text("متقدّم") }
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        if (filtered.isEmpty()) {
                            Text(
                                "لا حالات مطابقة للتصفية الحالية.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filtered, key = { it.id ?: (it.titleAr ?: "") }) { c ->
                                    val record = attemptRecords[c.id]
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                            .clickable { selectedCaseId = c.id }
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                c.chiefComplaintAr ?: c.titleAr ?: "حالة تفريقية",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                            if (!c.titleAr.isNullOrBlank() && c.titleAr != c.chiefComplaintAr) {
                                                Text(c.titleAr, fontSize = 13.sp)
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                listOfNotNull(c.systemAr, c.specialtyAr)
                                                    .joinToString(" • "),
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.AccessTime,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    "${ddxDifficultyLabel(c.difficulty)} • ≈ ${c.estimatedMinutes ?: 0} دقيقة",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                )
                                            }
                                            if (record != null) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Default.CheckCircle,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaroonPrimary
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        if (record.total != null) {
                                                            "المحاولة الأولى محفوظة (الدرجة: ${record.total}/100) — إعادة اللعب تدريب"
                                                        } else {
                                                            "المحاولة الأولى محفوظة (أ1: ${record.a1Points}/30) — إعادة اللعب تدريب"
                                                        },
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaroonPrimary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Dictionary picker: free typing with autocomplete suggestions from the
// bank dictionary. Picking a suggestion fixes the diagnosis identity
// (diagnosis_en). With allowFreeText, unpicked text is accepted as a
// free entry, displayed as typed and scored 0 points / 0 penalty.
// ---------------------------------------------------------------------------

@Composable
private fun DdxDiagnosisPicker(
    dictionary: List<DdxDictionaryEntry>,
    excludedKeys: Set<String>,
    allowFreeText: Boolean,
    fieldLabel: String,
    onPick: (DdxListEntry) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val suggestions = remember(query, dictionary, excludedKeys) {
        val q = query.trim()
        if (q.isEmpty()) {
            emptyList()
        } else {
            dictionary.filter { entry ->
                ddxMatchKey(entry.diagnosisEn) !in excludedKeys &&
                    (entry.diagnosisAr.contains(q, ignoreCase = true) ||
                        entry.diagnosisEn.contains(q, ignoreCase = true))
            }.take(8)
        }
    }
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(fieldLabel) },
            placeholder = { Text("اكتب اسم تشخيص…") },
            singleLine = true
        )
        suggestions.forEach { entry ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable {
                        onPick(
                            DdxListEntry(
                                keyEn = entry.diagnosisEn,
                                labelAr = entry.diagnosisAr,
                                labelEn = entry.diagnosisEn,
                                isFreeText = false
                            )
                        )
                        query = ""
                    }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = MaroonPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(entry.diagnosisAr, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(
                        entry.diagnosisEn,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
        if (allowFreeText && query.isNotBlank() && dictionary.none {
                it.diagnosisAr.trim() == query.trim() ||
                    ddxMatchKey(it.diagnosisEn) == ddxMatchKey(query)
            }
        ) {
            TextButton(onClick = {
                val text = query.trim()
                if (text.isNotEmpty()) {
                    onPick(
                        DdxListEntry(
                            keyEn = null,
                            labelAr = text,
                            labelEn = null,
                            isFreeText = true
                        )
                    )
                    query = ""
                }
            }) {
                Text("إضافة «${query.trim()}» كنص حر (بلا مطابقة مرجعية)")
            }
        }
    }
}

@Composable
private fun DdxWorkingRow(
    index: Int,
    entry: DdxListEntry,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: (() -> Unit)?,
    mnmChecked: Boolean?,
    onMnmToggle: ((Boolean) -> Unit)?
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${index + 1}.", fontWeight = FontWeight.Bold, color = MaroonPrimary)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.labelAr, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (!entry.labelEn.isNullOrBlank()) {
                    Text(
                        entry.labelEn,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                if (entry.isFreeText) {
                    Text(
                        "نص حر — بلا مطابقة مرجعية",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            if (mnmChecked != null && onMnmToggle != null) {
                Checkbox(checked = mnmChecked, onCheckedChange = onMnmToggle)
            }
            IconButton(onClick = onMoveUp, enabled = !isFirst) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "رفع")
            }
            IconButton(onClick = onMoveDown, enabled = !isLast) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "خفض")
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف")
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// One case: presentation, staged reveal, working list, commit, reveal.
// Phases: 0 = staged play, 1 = commit decision, 2 = reveal (locked).
// ---------------------------------------------------------------------------

@Composable
private fun DdxCasePlay(
    case: DdxCaseDto,
    dictionary: List<DdxDictionaryEntry>,
    hasFirstAttempt: Boolean,
    onFirstAttemptSaved: () -> Unit,
    onExitToList: () -> Unit
) {
    val context = LocalContext.current
    val caseId = case.id ?: ""
    val stages = remember(caseId) {
        case.stagedData.orEmpty().sortedBy { it.stage ?: Int.MAX_VALUE }
    }

    var phase by remember(caseId) { mutableIntStateOf(0) }
    var revealedCount by remember(caseId) { mutableIntStateOf(0) }
    var working by remember(caseId) { mutableStateOf<List<DdxListEntry>>(emptyList()) }
    var mnmSelected by remember(caseId) { mutableStateOf<Set<DdxListEntry>>(emptySet()) }
    var dangerExtras by remember(caseId) { mutableStateOf<List<DdxListEntry>>(emptyList()) }
    var breakdown by remember(caseId) { mutableStateOf<DdxScoreBreakdown?>(null) }
    var practiceRun by remember(caseId) { mutableStateOf(false) }

    val allRevealed = revealedCount >= stages.size
    val workingKeys = working.mapNotNull { it.keyEn?.let { en -> ddxMatchKey(en) } }.toSet()
    val dangerRanks = remember(caseId) {
        ddxReferenceMustNotMiss(case).mapNotNull { it.rank }.toSet()
    }

    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (index in working.indices && target in working.indices) {
            val mutable = working.toMutableList()
            val item = mutable.removeAt(index)
            mutable.add(target, item)
            working = mutable
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Presentation card: the only data visible before any stage.
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("بطاقة التقديم", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                val pres = case.presentation
                Text(
                    listOfNotNull(
                        pres?.age?.let { "العمر: $it سنة" },
                        ddxSexLabel(pres?.sex).takeIf { it.isNotBlank() }?.let { "الجنس: $it" },
                        pres?.settingAr?.let { "السياق: $it" }
                    ).joinToString(" • "),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(pres?.chiefComplaintTextAr ?: case.chiefComplaintAr ?: "", fontSize = 15.sp)
                if (hasFirstAttempt && phase != 2) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "هذه إعادة لعب — تُوسم «تدريب» ولا تستبدل محاولتك الأولى المحفوظة.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaroonPrimary
                    )
                }
            }
        }

        if (phase == 2) {
            // ------------------------- Reveal -------------------------
            val score = breakdown
            val fin = case.finalDiagnosis
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("التشخيص النهائي", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(fin?.diagnosisAr ?: "", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    if (!fin?.diagnosisEn.isNullOrBlank()) {
                        Text(fin.diagnosisEn, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("الدليل الحاسم:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(fin?.decisiveEvidenceAr ?: "", fontSize = 14.sp)
                }
            }

            Text("الترتيب المرجعي الكامل", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaroonPrimary)
            case.differentialReference.orEmpty()
                .sortedBy { it.rank ?: Int.MAX_VALUE }
                .forEach { ref ->
                    val placement = score?.referencePlacements
                        ?.firstOrNull { ddxMatchKey(it.diagnosisEn) == ddxMatchKey(ref.diagnosisEn) }
                    val isDanger = ref.rank != null && ref.rank in dangerRanks
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${ref.rank ?: "—"}.",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaroonPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(ref.diagnosisAr ?: "", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    if (!ref.diagnosisEn.isNullOrBlank()) {
                                        Text(ref.diagnosisEn, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    }
                                }
                                if (isDanger) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("لا يُفوَّت", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaroonPrimary)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                if (placement?.studentPosition != null) "موضعك لهذا التشخيص: ${placement.studentPosition}"
                                else "موضعك لهذا التشخيص: غائب عن قائمتك",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("سبب الموضع:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaroonPrimary)
                            Text(ref.placementReasonAr ?: "", fontSize = 13.sp)
                            if (!ref.supportingAr.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("مؤيّد:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaroonPrimary)
                                ref.supportingAr.forEach { item ->
                                    Text("• $item", fontSize = 13.sp)
                                }
                            }
                            if (!ref.refutingAr.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("مفنّد / مضعّف:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaroonPrimary)
                                ref.refutingAr.forEach { item ->
                                    Text("• $item", fontSize = 13.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("الفحص الحاسم:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaroonPrimary)
                            Text(ref.decisiveTestAr ?: "", fontSize = 13.sp)
                        }
                    }
                }

            if (score != null && score.mustNotMissStatus.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("مجموعة «لا يُفوَّت» المرجعية", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        score.mustNotMissStatus.forEach { status ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (status.markedByStudent) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (status.markedByStudent) MaroonPrimary else MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "${status.diagnosisAr ?: status.diagnosisEn ?: ""} — ${if (status.markedByStudent) "مُعلَّم في مجموعتك" else "غير مُعلَّم في مجموعتك"}",
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }

            if (!case.redFlagsAr.isNullOrEmpty()) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("الأعلام الحمراء", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        case.redFlagsAr.forEach { flag ->
                            Text("• $flag", fontSize = 13.sp)
                        }
                    }
                }
            }

            if (!case.teachingSummaryAr.isNullOrBlank()) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("الخلاصة التعليمية", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(case.teachingSummaryAr, fontSize = 14.sp)
                    }
                }
            }

            // Scoring comes last in the reveal sequence (design section
            // 2, step 4): final diagnosis, reference ranking, danger
            // set, red flags, teaching summary, then the score.
            if (score != null) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("نتيجة التنقيط", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                        if (practiceRun) {
                            Text("محاولة تدريبية — لم تُستبدل نتيجة المحاولة الأولى.", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "الدرجة الكلية: ${score.total}/100",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaroonPrimary
                        )
                        Text(
                            score.bandText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (score.hasMissWarning) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                DDX_MISS_WARNING,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "موضع التشخيص الأرجح (أ1): ${score.a1Points}/30",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            if (score.topStudentPosition != null) {
                                "وضعتَ التشخيص الأرجح «${score.topDiagnosisAr ?: score.topDiagnosisEn ?: ""}» في الموضع ${score.topStudentPosition} من قائمتك."
                            } else {
                                "التشخيص الأرجح «${score.topDiagnosisAr ?: score.topDiagnosisEn ?: ""}» كان غائبًا عن قائمتك."
                            },
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "قرب باقي الترتيب (أ2): ${ddxFormatPoints(score.a2Points)}/20",
                            fontSize = 14.sp
                        )
                        Text(
                            "بُعد الخطر «لا يُفوَّت» (ب): ${ddxFormatPoints(score.riskPoints)}/30",
                            fontSize = 14.sp
                        )
                        Text(
                            "الاتساع (ج): ${ddxFormatPoints(score.breadthPoints)}/20" +
                                if (score.disciplineFactor < 1.0) {
                                    " (خُفّض بعامل انضباط لتجاوز حدّ الإفراط)"
                                } else {
                                    ""
                                },
                            fontSize = 14.sp
                        )
                        if (score.penaltyPoints > 0) {
                            Text(
                                "عقوبة إغفال خطر قاتل بالكامل: −${score.penaltyPoints}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (score.extraStudentCount > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "عناصر إضافية في قائمتك خارج الترتيب المرجعي: ${score.extraStudentCount}" +
                                    if (score.extraStudentCount <= 3) {
                                        " (ضمن حدّ الإفراط: بلا نقاط وبلا عقوبة)."
                                    } else {
                                        " (تجاوزت حدّ الإفراط (3) فخُفّض مكوّن الاتساع بعامل الانضباط)."
                                    },
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Button(
                shape = MaterialTheme.shapes.medium,
                onClick = {
                    // Replay: fresh run, marked practice when a first
                    // attempt already exists for this case.
                    phase = 0
                    revealedCount = 0
                    working = emptyList()
                    mnmSelected = emptySet()
                    dangerExtras = emptyList()
                    breakdown = null
                    practiceRun = false
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
            ) {
                Text("إعادة اللعب (تدريب)", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onExitToList,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("عودة إلى قائمة الحالات", fontWeight = FontWeight.Bold)
            }
        } else {
            // ------------------- Staged play / commit -------------------
            // Revealed stages stay visible; reveal is strictly in order.
            stages.take(revealedCount).forEach { stage ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "المرحلة ${stage.stage ?: ""}: ${stage.titleAr ?: ""}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaroonPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        stage.itemsAr.orEmpty().forEach { item ->
                            Text("• $item", fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }

            if (!allRevealed && phase == 0) {
                val nextStage = stages.getOrNull(revealedCount)
                Button(
                    shape = MaterialTheme.shapes.medium,
                    onClick = { revealedCount += 1 },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
                ) {
                    Text(
                        "فتح المرحلة التالية: ${nextStage?.titleAr ?: ""}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            // Working list editor (in the commit phase the same list is
            // the final probability ranking, with danger checkboxes).
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        when {
                            phase == 1 -> "الالتزام النهائي — (أ) قائمة الأرجح احتماليةً"
                            revealedCount == 0 -> "المسودة الأولى (اختيارية — بلا تنقيط، يمكن تخطّيها بفتح المرحلة الأولى)"
                            else -> "القائمة العاملة — حدّثها بعد كل مرحلة"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaroonPrimary
                    )
                    if (phase == 1) {
                        Text(
                            "رتّب القائمة من الأرجح إلى الأقل ترجيحًا، وعلّم بمربع «لا يُفوَّت» كل تشخيص مهدِّد إن فُوّت.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    if (phase == 0 && revealedCount == 0) {
                        Text(
                            "أضف 2–4 تشخيصات تخطر لك من الشكاية وحدها قبل رؤية أي معطيات — هذه المسودة للتدريب الذهني ولا تُنقَّط.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    if (working.isEmpty()) {
                        Text(
                            "القائمة فارغة — أضف تشخيصات من المنتقي بالأسفل.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    } else {
                        working.forEachIndexed { index, entry ->
                            DdxWorkingRow(
                                index = index,
                                entry = entry,
                                isFirst = index == 0,
                                isLast = index == working.lastIndex,
                                onMoveUp = { move(index, -1) },
                                onMoveDown = { move(index, +1) },
                                onRemove = if (phase == 0) {
                                    {
                                        working = working.filterIndexed { i, _ -> i != index }
                                        mnmSelected = mnmSelected - entry
                                    }
                                } else null,
                                mnmChecked = if (phase == 1) entry in mnmSelected else null,
                                onMnmToggle = if (phase == 1) {
                                    { checked ->
                                        mnmSelected = if (checked) mnmSelected + entry else mnmSelected - entry
                                    }
                                } else null
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    if (phase == 0) {
                        DdxDiagnosisPicker(
                            dictionary = dictionary,
                            excludedKeys = workingKeys,
                            allowFreeText = true,
                            fieldLabel = "إضافة تشخيص",
                            onPick = { entry -> working = working + entry }
                        )
                    }
                }
            }

            if (phase == 0 && stages.isNotEmpty() && allRevealed) {
                Button(
                    shape = MaterialTheme.shapes.medium,
                    onClick = { phase = 1 },
                    enabled = working.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("الالتزام النهائي", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                if (working.isEmpty()) {
                    Text(
                        "أضف تشخيصًا واحدًا على الأقل إلى القائمة قبل الالتزام.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            if (phase == 1) {
                // Danger-set direct additions from the dictionary.
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "(ب) مجموعة «لا يُفوَّت» — إضافات مباشرة",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaroonPrimary
                        )
                        Text(
                            "تشخيص مهدِّد ليس في قائمتك الاحتمالية؟ أضفه هنا إلى مجموعة الخطر مباشرة.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        dangerExtras.forEachIndexed { extraIndex, extra ->
                            DdxWorkingRow(
                                index = extraIndex,
                                entry = extra,
                                isFirst = true,
                                isLast = true,
                                onMoveUp = {},
                                onMoveDown = {},
                                onRemove = { dangerExtras = dangerExtras - extra },
                                mnmChecked = null,
                                onMnmToggle = null
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        val extrasKeys = dangerExtras.mapNotNull {
                            it.keyEn?.let { en -> ddxMatchKey(en) }
                        }.toSet()
                        val mnmKeys = mnmSelected.mapNotNull {
                            it.keyEn?.let { en -> ddxMatchKey(en) }
                        }.toSet()
                        DdxDiagnosisPicker(
                            dictionary = dictionary,
                            excludedKeys = workingKeys + extrasKeys + mnmKeys,
                            allowFreeText = false,
                            fieldLabel = "إضافة إلى مجموعة الخطر",
                            onPick = { entry -> dangerExtras = dangerExtras + entry }
                        )
                    }
                }

                Button(
                    shape = MaterialTheme.shapes.medium,
                    onClick = {
                        // The ranking mirrors the student's list slot for
                        // slot: a null entry is a free-text item, which
                        // still occupies its position for A1 (design 4.2).
                        val rankingEn: List<String?> = working.map { it.keyEn }
                        // The danger commitment mirrors the student's
                        // flags entry for entry: a null entry is a
                        // free-text flag, which still counts toward
                        // |D| in scoring (design section 14.2).
                        val mnmEn: List<String?> = mnmSelected.map { it.keyEn } +
                            dangerExtras.map { it.keyEn }
                        val result = scoreDdxCommit(case, rankingEn, mnmEn)
                        val wasPractice = hasFirstAttempt
                        if (!wasPractice) {
                            saveDdxFirstAttempt(
                                context,
                                DdxAttemptRecord(
                                    caseId = caseId,
                                    rankingLabels = working.map { it.labelAr },
                                    rankingEn = rankingEn.map { it ?: "" },
                                    mnmLabels = (mnmSelected.map { it.labelAr } +
                                        dangerExtras.map { it.labelAr }).distinct(),
                                    mnmEn = mnmEn.map { it ?: "" },
                                    a1Points = result.a1Points,
                                    total = result.total,
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                            onFirstAttemptSaved()
                        }
                        breakdown = result
                        practiceRun = wasPractice
                        phase = 2
                    },
                    enabled = working.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تأكيد الالتزام وعرض الكشف", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Text(
                    "بعد التأكيد يُقفل كل شيء ولا يمكن تعديل القائمتين.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
