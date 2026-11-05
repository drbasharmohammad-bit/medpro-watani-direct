package sy.medpro.watani.ui.cases

import sy.medpro.watani.data.OfflineStudyVault

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.data.ClinicalCaseDto
import sy.medpro.watani.ui.drawer.DrawerScreenScaffold
import sy.medpro.watani.ui.theme.*

@Composable
fun CasesScreen(cases: List<ClinicalCaseDto>, onCaseClick: (ClinicalCaseDto) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredCases = cases.filter {
        it.title?.contains(searchQuery, ignoreCase = true) == true ||
        it.specialty?.contains(searchQuery, ignoreCase = true) == true ||
        it.presentation?.contains(searchQuery, ignoreCase = true) == true
    }

    DrawerScreenScaffold(title = "المريض الافتراضي والحالات السريرية (${cases.size} حالة)", onBack = {}) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("بحث في الحالات السريرية حسب الاختصاص أو القصة...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaroonPrimary) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredCases, key = { it.id ?: it.title ?: "" }) { caseItem ->
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        border = BorderStroke(1.dp, MaroonPrimary.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCaseClick(caseItem) }
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
                                        text = caseItem.specialty ?: "طب عام وباطنة",
                                        color = MaroonPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Surface(
                                    color = GoldAccent.copy(alpha = 0.2f),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = "+${caseItem.xpReward ?: 50} XP",
                                        color = GoldAccentDark,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = caseItem.title ?: "حالة سريرية إسعافية",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaroonPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = caseItem.presentation ?: "",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CasePlayerScreen(caseItem: ClinicalCaseDto, onBack: () -> Unit) {
    BackHandler { onBack() }
    val context = androidx.compose.ui.platform.LocalContext.current
    // Account binding: favorites live per account, never in one shared store.
    val favUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    val favPrefsName = if (favUid.isNullOrBlank()) "watani_favorites" else "watani_favorites_$favUid"
    val favPrefs = remember(favUid) { context.getSharedPreferences(favPrefsName, android.content.Context.MODE_PRIVATE) }
    var favList by remember(favUid) { mutableStateOf(favPrefs.getStringSet("fav_list", emptySet()) ?: emptySet()) }
    val cId = caseItem.id ?: caseItem.title ?: "حالة"
    val isFav = favList.contains(cId)

    var revealedHistory by remember { mutableStateOf(setOf<Int>()) }
    var revealedExams by remember { mutableStateOf(setOf<Int>()) }
    var revealedTests by remember { mutableStateOf(setOf<Int>()) }
    var selectedDiagnosis by remember { mutableStateOf<String?>(null) }
    var showExplanation by remember { mutableStateOf(false) }

    // ---- Virtual-patient upgrade (schema v2) state ----
    // A case is upgraded only when it declares schemaV2 and carries a
    // history bank; every other case follows the legacy flow untouched.
    val isV2 = caseItem.schemaV2 == true && !caseItem.historyBank.isNullOrEmpty()
    var askedQuestions by remember { mutableStateOf(setOf<String>()) }
    var earlyDiagnosis by remember { mutableStateOf(false) }
    var pendingDiagnosis by remember { mutableStateOf<String?>(null) }

    val correctDiagnosisText = caseItem.correctDiagnosis?.toString() ?: ""
    val diagnosisCorrectNow = selectedDiagnosis != null &&
        selectedDiagnosis!!.trim().equals(correctDiagnosisText.trim(), ignoreCase = true)
    val revealedExamIds = revealedExams.mapNotNull { caseItem.examinations.orEmpty().getOrNull(it)?.id }.toSet()
    val revealedTestIds = revealedTests.mapNotNull { caseItem.tests.orEmpty().getOrNull(it)?.id }.toSet()
    val v2Score = if (isV2 && selectedDiagnosis != null) {
        computeCaseScore(caseItem, askedQuestions, revealedExamIds, revealedTestIds, diagnosisCorrectNow)
    } else null

    // Soft diagnosis gate (v2 only): the diagnosis button is always
    // available, but choosing before asking at least one question in 3
    // different history domains asks for confirmation and flags the run
    // as an early diagnosis in the report. It never blocks.
    val chooseDiagnosis: (String) -> Unit = { option ->
        val askedDomains = caseItem.historyBank.orEmpty()
            .filter { it.id != null && askedQuestions.contains(it.id) }
            .mapNotNull { it.domain }
            .toSet()
        if (isV2 && !earlyDiagnosis && askedDomains.size < 3) {
            pendingDiagnosis = option
        } else {
            selectedDiagnosis = option
            showExplanation = true
        }
    }

    LaunchedEffect(showExplanation) {
        if (showExplanation) {
            // Upgraded cases earn the score-based share of the reward;
            // legacy cases keep the full-reward grant exactly as before.
            val xpToGrant = if (isV2) {
                v2Score?.xpEarned ?: (caseItem.xpReward ?: 25)
            } else {
                caseItem.xpReward ?: 25
            }
            OfflineStudyVault.recordCaseCompletion(context, xpToGrant)
        }
    }

    if (pendingDiagnosis != null) {
        AlertDialog(
            onDismissRequest = { pendingDiagnosis = null },
            title = { Text("تشخيص مبكر") },
            text = {
                Text("لم تسأل بعدُ سؤالًا واحدًا على الأقل في 3 مجالات مختلفة من القصة. التشخيص الآن ممكن، لكنه سيُسجَّل في تقريرك كتشخيص مبكر، وما لم تسأل عنه سيبقى غائبًا عن درجتك.")
            },
            confirmButton = {
                TextButton(onClick = {
                    earlyDiagnosis = true
                    selectedDiagnosis = pendingDiagnosis
                    showExplanation = true
                    pendingDiagnosis = null
                }) { Text("التشخيص الآن") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDiagnosis = null }) { Text("متابعة أخذ القصة") }
            }
        )
    }

    DrawerScreenScaffold(
        title = caseItem.title ?: "محاكي المريض الافتراضي",
        onBack = onBack
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaroonDeep),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(caseItem.specialty ?: "حالة سريرية", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("مكافأة الإنجاز: +${caseItem.xpReward ?: 50} XP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        val next = if (isFav) favList - cId else favList + cId
                                        favPrefs.edit().putStringSet("fav_list", next).apply()
                                        favList = next
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "إضافة للمفضلة",
                                        tint = if (isFav) Color.Red else Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isV2) "المقدّمة السريرية (Opening):" else "القصة السريرية والعرض البدئي (Presentation):",
                            fontWeight = FontWeight.Bold,
                            color = GoldAccent,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isV2) {
                                caseItem.openingVignette ?: caseItem.presentation ?: caseItem.legacyPresentation ?: "لا توجد قصة متاحة"
                            } else {
                                caseItem.presentation ?: "لا توجد قصة متاحة"
                            },
                            color = Color.White,
                            fontSize = 15.sp,
                            lineHeight = 23.sp
                        )
                    }
                }
            }

            // History bank (v2): the student interviews the patient.
            // Questions are grouped by the six history domains; question
            // titles are visible, answers stay hidden until the question
            // is asked (recorded in askedQuestions). The tier and the
            // red-flag flag are never shown to the student.
            if (isV2) {
                val bank = caseItem.historyBank.orEmpty()
                val domainOrder = listOf("hpi", "associated", "red_flags", "past_medical", "meds_allergy", "social")
                val domainTitles = mapOf(
                    "hpi" to "الشكاية الحالية وتطوّرها",
                    "associated" to "أعراض مرافقة ومراجعة أجهزة",
                    "red_flags" to "أعلام حمراء",
                    "past_medical" to "سوابق مرضية وجراحية",
                    "meds_allergy" to "أدوية وحساسية",
                    "social" to "اجتماعي وعادات"
                )
                val grouped = bank.groupBy { it.domain ?: "" }
                val orderedDomains = domainOrder.filter { grouped.containsKey(it) } +
                    grouped.keys.filter { it !in domainOrder }

                item {
                    Text("القصة المرضية — اسأل المريض بنفسك (History):", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                }
                orderedDomains.forEach { domain ->
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(domainTitles[domain] ?: domain, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GoldAccentDark)
                    }
                    items(grouped[domain].orEmpty(), key = { it.id ?: it.text ?: "" }) { question ->
                        val qid = question.id ?: ""
                        val isAsked = qid.isNotEmpty() && askedQuestions.contains(qid)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = qid.isNotEmpty()) { askedQuestions = askedQuestions + qid },
                            shape = MaterialTheme.shapes.small
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = question.text ?: "سؤال",
                                    fontSize = 14.sp,
                                    color = MaroonPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isAsked) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        color = MaroonPrimary.copy(alpha = 0.08f),
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "جواب المريض: ${question.answer ?: "لا توجد معلومات إضافية"}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("اضغط لطرح السؤال على المريض...", fontSize = 11.sp, color = GoldAccentDark)
                                }
                            }
                        }
                    }
                }
            }

            // History (القصة المرضية والسوابق) — legacy cases only
            if (!isV2 && !caseItem.history.isNullOrEmpty()) {
                item {
                    Text("القصة السريرية والسوابق الموجهة (History):", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                }
                items(caseItem.history.size) { idx ->
                    val item = caseItem.history[idx]
                    val isRevealed = revealedHistory.contains(idx)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { revealedHistory = revealedHistory + idx },
                        shape = MaterialTheme.shapes.small
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = item.text ?: "سؤال القصة ${idx + 1}",
                                fontSize = 14.sp,
                                color = MaroonPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            if (isRevealed) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    color = MaroonPrimary.copy(alpha = 0.08f),
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "النتيجة / الإجابة: ${item.answer ?: "لا توجد معلومات إضافية"}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("اضغط لكشف الجواب...", fontSize = 11.sp, color = GoldAccentDark)
                            }
                        }
                    }
                }
            }

            // Examinations (الفحص السريري) - C1: Two lines (line 1 = text, line 2 = finding)
            if (!caseItem.examinations.isNullOrEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("الفحص السريري العضوي (Physical Examinations):", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                }
                items(caseItem.examinations.size) { idx ->
                    val item = caseItem.examinations[idx]
                    val isRevealed = revealedExams.contains(idx)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { revealedExams = revealedExams + idx },
                        shape = MaterialTheme.shapes.small
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = item.text ?: "الفحص ${idx + 1}",
                                fontSize = 14.sp,
                                color = MaroonPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            if (isRevealed) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    color = SuccessGreen.copy(alpha = 0.1f),
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "الموجودات السريرية: ${item.finding ?: item.text ?: "طبيعي / لا يوجد شذوذ"}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SuccessGreen,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("اضغط لإجراء الفحص وكشف الموجودات...", fontSize = 11.sp, color = GoldAccentDark)
                            }
                        }
                    }
                }
            }

            // Tests (الاستقصاءات والتحاليل)
            if (!caseItem.tests.isNullOrEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("الاستقصاءات المخبرية والشعاعية (Diagnostic Tests):", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                }
                items(caseItem.tests.size) { idx ->
                    val item = caseItem.tests[idx]
                    val isRevealed = revealedTests.contains(idx)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { revealedTests = revealedTests + idx },
                        shape = MaterialTheme.shapes.small
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = item.text ?: "التحليل ${idx + 1}",
                                fontSize = 14.sp,
                                color = MaroonPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            if (isRevealed) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    color = MaroonPrimary.copy(alpha = 0.08f),
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "نتيجة التحليل / الصورة: ${item.result ?: "متوفر"}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("اضغط لطلب الاستقصاء وقراءة النتيجة...", fontSize = 11.sp, color = GoldAccentDark)
                            }
                        }
                    }
                }
            }

            // Diagnosis Options
            if (!caseItem.diagnosisOptions.isNullOrEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("التشخيص التفريقي والتشخيص النهائي (Diagnosis):", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                }
                items(caseItem.diagnosisOptions) { option ->
                    val isSelected = selectedDiagnosis == option
                    val correctDiag = caseItem.correctDiagnosis?.toString() ?: ""
                    val isCorrect = option.trim().equals(correctDiag.trim(), ignoreCase = true)
                    val borderColor = when {
                        showExplanation && isCorrect -> SuccessGreen
                        showExplanation && isSelected && !isCorrect -> ErrorRed
                        isSelected -> MaroonPrimary
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    }
                    val bg = when {
                        showExplanation && isCorrect -> SuccessGreen.copy(alpha = 0.1f)
                        showExplanation && isSelected && !isCorrect -> ErrorRed.copy(alpha = 0.1f)
                        else -> MaterialTheme.colorScheme.surface
                    }
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = bg,
                        border = BorderStroke(1.5.dp, borderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable(enabled = !showExplanation) {
                                chooseDiagnosis(option)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = MaroonPrimary)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = option,
                                modifier = Modifier.weight(1f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (showExplanation && isCorrect) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
                            } else if (showExplanation && isSelected && !isCorrect) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = ErrorRed)
                            }
                        }
                    }
                }
            }

            // C2: End-of-case Performance Report Card after Diagnosis answered
            if (showExplanation) {

                item {
                    val totalHistoryKey = caseItem.history.orEmpty().count { it.isKey == true }
                    val revealedHistoryKey = revealedHistory.count { idx -> caseItem.history.orEmpty().getOrNull(idx)?.isKey == true }
                    val historyPct = if (totalHistoryKey > 0) (revealedHistoryKey * 100) / totalHistoryKey else 100

                    val totalExamsKey = caseItem.examinations.orEmpty().count { it.isKey == true }
                    val revealedExamsKey = revealedExams.count { idx -> caseItem.examinations.orEmpty().getOrNull(idx)?.isKey == true }
                    val examsPct = if (totalExamsKey > 0) (revealedExamsKey * 100) / totalExamsKey else 100

                    val totalTestsKey = caseItem.tests.orEmpty().count { it.isKey == true }
                    val revealedTestsKey = revealedTests.count { idx -> caseItem.tests.orEmpty().getOrNull(idx)?.isKey == true }
                    val testsPct = if (totalTestsKey > 0) (revealedTestsKey * 100) / totalTestsKey else 100

                    val totalKeyAll = totalHistoryKey + totalExamsKey + totalTestsKey
                    val revealedKeyAll = revealedHistoryKey + revealedExamsKey + revealedTestsKey
                    val overallPct = if (totalKeyAll > 0) (revealedKeyAll * 100) / totalKeyAll else 100

                    // Id to display text for every possible revealer of a
                    // critical fact (history question, examination, test).
                    val revealerTexts = HashMap<String, String>()
                    caseItem.historyBank.orEmpty().forEach { q -> q.id?.let { revealerTexts[it] = q.text ?: it } }
                    caseItem.examinations.orEmpty().forEach { e -> e.id?.let { revealerTexts[it] = e.text ?: it } }
                    caseItem.tests.orEmpty().forEach { t -> t.id?.let { revealerTexts[it] = t.text ?: it } }

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (isV2 && v2Score != null) {
                            val score = v2Score
                            val fmtScore: (Double) -> String = { v -> if (v % 1.0 == 0.0) v.toInt().toString() else v.toString() }

                            // Composite Score Report Card (v2)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaroonDeep),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("تقرير أداء الحالة السريرية", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Surface(
                                            color = GoldAccent,
                                            shape = MaterialTheme.shapes.small
                                        ) {
                                            Text(
                                                text = "+${score.xpEarned} XP",
                                                color = MaroonDeep,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("الدرجة المركّبة: ${fmtScore(score.total)} / 100", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    if (score.historyMerged) {
                                        Text("• القصة — تغطية الجوهري (لا أسئلة أعلام حمراء موثّقة في هذه الحالة): ${fmtScore(score.historyEssentialPoints)} / 40 (${score.essentialAsked}/${score.essentialTotal})", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                                    } else {
                                        Text("• القصة — تغطية الجوهري: ${fmtScore(score.historyEssentialPoints)} / 25 (${score.essentialAsked}/${score.essentialTotal})", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                                        Text("• القصة — الأعلام الحمراء: ${fmtScore(score.historyRedFlagPoints)} / 15 (${score.redFlagAsked}/${score.redFlagTotal})", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                                    }
                                    if (score.distractorsAsked > 0) {
                                        Text("• كفاءة القصة: سألت ${score.distractorsAsked} سؤالًا مشتّتًا (المجاني منها: ${score.freeDistractors}) — الخصم من مكوّن القصة: ${fmtScore(score.distractorPenalty)} نقطة", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                                    }
                                    Text("• مكوّن القصة الإجمالي: ${fmtScore(score.historyTotal)} / 40", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                                    Text("• الفحص والاستقصاءات — تغطية العناصر الأساسية: ${fmtScore(score.examCoveragePoints)} / 15 (${score.examKeyRevealed}/${score.examKeyTotal})", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                                    Text(
                                        "• الفحص والاستقصاءات — الكفاءة: ${fmtScore(score.examEfficiencyPoints)} / 5" +
                                            if (score.examEfficiencyPenalty > 0) " (طُلب ${score.examDistractorsRequested} عنصرًا مشتّتًا — الخصم: ${fmtScore(score.examEfficiencyPenalty)})" else "",
                                        color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp
                                    )
                                    Text("• التشخيص: ${fmtScore(score.diagnosisPoints)} / 40 (${if (score.diagnosisCorrect) "صحيح" else "غير صحيح"})", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                                    if (earlyDiagnosis) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("⚠ تشخيص مبكر: شخّصت قبل سؤال سؤال واحد على الأقل في 3 مجالات مختلفة من القصة.", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                    if (score.missedFacts.isEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("كشفتَ كل الوقائع الحاسمة في هذه الحالة. أحسنت.", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }

                            // Safety alert: red-flag questions exist but none was asked
                            if (score.redFlagTotal > 0 && score.redFlagAsked == 0) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = ErrorRed),
                                    shape = MaterialTheme.shapes.large
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("⚠ تنبيه سلامة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "لم تسأل أيًّا من أسئلة الأعلام الحمراء في هذه الحالة (${score.redFlagTotal} سؤالًا متاحًا). استبعاد التشاخيص المهدِّدة للحياة بسؤال المريض جزء أساسي من التقييم الآمن.",
                                            color = Color.White, fontSize = 13.sp, lineHeight = 20.sp
                                        )
                                    }
                                }
                            }

                            // Missed critical facts — the core lesson of the report
                            if (score.missedFacts.isNotEmpty()) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = MaterialTheme.shapes.medium,
                                    border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.6f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("وقائع حاسمة لم تُكشف (${score.missedFacts.size}):", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaroonPrimary)
                                        score.missedFacts.forEach { fact ->
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("• ${fact.label ?: "واقعة حاسمة"}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            if (!fact.whyCritical.isNullOrEmpty()) {
                                                Text("لماذا هي حاسمة: ${fact.whyCritical}", fontSize = 12.sp)
                                            }
                                            if (fact.revealerIds.isNotEmpty()) {
                                                val revealerNames = fact.revealerIds.joinToString("، ") { revealerTexts[it] ?: it }
                                                Text("كان يكشفها: $revealerNames", fontSize = 12.sp, color = GoldAccentDark, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                        // Performance Report Summary Card (legacy)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaroonDeep),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("تقرير أداء الحالة السريرية", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Surface(
                                        color = GoldAccent,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            text = "+${caseItem.xpReward ?: 50} XP",
                                            color = MaroonDeep,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("كشف النقاط الحرجة (Key Items):", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("• القصة السريرية: $historyPct% ($revealedHistoryKey/$totalHistoryKey)", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                                Text("• الفحص العضوي: $examsPct% ($revealedExamsKey/$totalExamsKey)", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                                Text("• الاستقصاءات: $testsPct% ($revealedTestsKey/$totalTestsKey)", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("• الكفاءة الإجمالية لكشف النقاط المفتاحية: $overallPct%", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                        }

                        // Full Explanation Card
                        if (!caseItem.explanation.isNullOrEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("الشرح العلمي والتدبير السريري:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaroonPrimary)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(caseItem.explanation!!, fontSize = 14.sp, lineHeight = 21.sp)
                                }
                            }
                        }

                        // Learning Points Card
                        if (!caseItem.learningPoints.isNullOrEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("نقاط التعلم السريرية الأساسية (Learning Points):", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaroonPrimary)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    caseItem.learningPoints.forEach { lp ->
                                        Text("• $lp", fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp))
                                    }
                                }
                            }
                        }

                        // Full original story (v2 only): after finishing, the
                        // student compares what they uncovered against the
                        // complete record. Converted cases carry the text in
                        // legacyPresentation when presentation is absent.
                        if (isV2) {
                            val fullStory = caseItem.presentation ?: caseItem.legacyPresentation
                            if (!fullStory.isNullOrEmpty()) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = MaterialTheme.shapes.medium,
                                    border = BorderStroke(1.dp, MaroonPrimary.copy(alpha = 0.4f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("القصة الكاملة كما في السجل:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaroonPrimary)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(fullStory, fontSize = 14.sp, lineHeight = 21.sp)
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
