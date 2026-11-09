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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import sy.medpro.watani.data.ClinicalCaseDto
import sy.medpro.watani.ui.drawer.DrawerScreenScaffold
import sy.medpro.watani.ui.icons.SubjectIcon
import sy.medpro.watani.ui.icons.subjectIconRes
import sy.medpro.watani.ui.theme.*

// A case plays the upgraded (v2) flow only when it carries the full
// v2 payload: the schema flag, a non-empty history bank, AND an
// opening vignette. Requiring the vignette keeps a malformed future
// record from silently opening with the full presentation text; such
// a record degrades to the legacy player instead.
private fun ClinicalCaseDto.isUpgradedCase(): Boolean =
    schemaV2 == true && !historyBank.isNullOrEmpty() && !openingVignette.isNullOrBlank()

// Text shown for a case in lists and search: upgraded cases preview
// their opening vignette only — never the full presentation, which
// would hand the student the whole story before playing.
private fun ClinicalCaseDto.listPreviewText(): String =
    if (isUpgradedCase()) openingVignette ?: "" else presentation ?: ""

@Composable
fun CasesScreen(cases: List<ClinicalCaseDto>, onCaseClick: (ClinicalCaseDto) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredCases = cases.filter {
        it.title?.contains(searchQuery, ignoreCase = true) == true ||
        it.specialty?.contains(searchQuery, ignoreCase = true) == true ||
        it.listPreviewText().contains(searchQuery, ignoreCase = true)
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
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (subjectIconRes(caseItem.specialty) != null) {
                                            SubjectIcon(caseItem.specialty, Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = caseItem.specialty ?: "طب عام وباطنة",
                                            color = MaroonPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
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
                                text = caseItem.listPreviewText(),
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

/** Player chrome localization: Arabic (default) or German. */
private fun localized(de: Boolean, ar: String, deu: String): String = if (de) deu else ar

@Composable
fun CasePlayerScreen(caseItem: ClinicalCaseDto, onBack: () -> Unit, german: Boolean = false) {
    // Fix 1.2.2: inside a German case EVERYTHING is German and LTR —
    // direction and every chrome label (doctor's device report: the
    // list was fine, the player interior stayed Arabic/RTL). The
    // Arabic path keeps the old behavior exactly.
    if (german) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            CasePlayerContent(caseItem, onBack, german = true)
        }
    } else {
        CasePlayerContent(caseItem, onBack, german = false)
    }
}

@Composable
private fun CasePlayerContent(caseItem: ClinicalCaseDto, onBack: () -> Unit, german: Boolean) {
    val de = german
    BackHandler { onBack() }
    val context = androidx.compose.ui.platform.LocalContext.current
    // Account binding: favorites live per account, never in one shared store.
    val favUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    val favPrefsName = if (favUid.isNullOrBlank()) "watani_favorites" else "watani_favorites_$favUid"
    val favPrefs = remember(favUid) { context.getSharedPreferences(favPrefsName, android.content.Context.MODE_PRIVATE) }
    var favList by remember(favUid) { mutableStateOf(favPrefs.getStringSet("fav_list", emptySet()) ?: emptySet()) }
    val cId = caseItem.id ?: caseItem.title ?: localized(de, "حالة", "Fall")
    val isFav = favList.contains(cId)

    var revealedHistory by remember { mutableStateOf(setOf<Int>()) }
    var revealedExams by remember { mutableStateOf(setOf<Int>()) }
    var revealedTests by remember { mutableStateOf(setOf<Int>()) }
    var selectedDiagnosis by remember { mutableStateOf<String?>(null) }
    var showExplanation by remember { mutableStateOf(false) }

    // ---- Virtual-patient upgrade (schema v2) state ----
    // A case is upgraded only when isUpgradedCase() holds: schemaV2
    // declared, a non-empty history bank, and an opening vignette;
    // every other case follows the legacy flow untouched.
    val isV2 = caseItem.isUpgradedCase()
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
            title = { Text(localized(de, "تشخيص مبكر", "Frühe Diagnose")) },
            text = {
                Text(localized(de, "لم تسأل بعدُ سؤالًا واحدًا على الأقل في 3 مجالات مختلفة من القصة. التشخيص الآن ممكن، لكنه سيُسجَّل في تقريرك كتشخيص مبكر، وما لم تسأل عنه سيبقى غائبًا عن درجتك.", "Sie haben noch nicht in mindestens 3 verschiedenen Anamnese-Bereichen je eine Frage gestellt. Eine Diagnose ist jetzt möglich, wird aber in Ihrem Bericht als frühe Diagnose vermerkt, und nicht Erfragtes fehlt in Ihrer Bewertung."))
            },
            confirmButton = {
                TextButton(onClick = {
                    earlyDiagnosis = true
                    selectedDiagnosis = pendingDiagnosis
                    showExplanation = true
                    pendingDiagnosis = null
                }) { Text(localized(de, "التشخيص الآن", "Jetzt diagnostizieren")) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDiagnosis = null }) { Text(localized(de, "متابعة أخذ القصة", "Anamnese fortsetzen")) }
            }
        )
    }

    DrawerScreenScaffold(
        title = caseItem.title ?: localized(de, "محاكي المريض الافتراضي", "Virtueller Patient"),
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
                            Text(caseItem.specialty ?: localized(de, "حالة سريرية", "Klinischer Fall"), color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(localized(de, "مكافأة الإنجاز: +${caseItem.xpReward ?: 50} XP", "Abschlussbelohnung: +${caseItem.xpReward ?: 50} XP"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                                        contentDescription = localized(de, "إضافة للمفضلة", "Zu Favoriten hinzufügen"),
                                        tint = if (isFav) Color.Red else Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isV2) localized(de, "المقدّمة السريرية (Opening):", "Einführung (Opening):") else localized(de, "القصة السريرية والعرض البدئي (Presentation):", "Krankengeschichte und Präsentation (Presentation):"),
                            fontWeight = FontWeight.Bold,
                            color = GoldAccent,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isV2) {
                                caseItem.openingVignette ?: caseItem.presentation ?: caseItem.legacyPresentation ?: localized(de, "لا توجد قصة متاحة", "Keine Geschichte verfügbar")
                            } else {
                                caseItem.presentation ?: localized(de, "لا توجد قصة متاحة", "Keine Geschichte verfügbar")
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
                    "hpi" to localized(de, "الشكاية الحالية وتطوّرها", "Aktuelle Beschwerden und Verlauf"),
                    "associated" to localized(de, "أعراض مرافقة ومراجعة أجهزة", "Begleitsymptome und Organsysteme"),
                    "red_flags" to localized(de, "أعلام حمراء", "Red Flags (Warnzeichen)"),
                    "past_medical" to localized(de, "سوابق مرضية وجراحية", "Vorerkrankungen und Operationen"),
                    "meds_allergy" to localized(de, "أدوية وحساسية", "Medikamente und Allergien"),
                    "social" to localized(de, "اجتماعي وعادات", "Soziales und Gewohnheiten")
                )
                val grouped = bank.groupBy { it.domain ?: "" }
                val orderedDomains = domainOrder.filter { grouped.containsKey(it) } +
                    grouped.keys.filter { it !in domainOrder }

                item {
                    Text(localized(de, "القصة المرضية — اسأل المريض بنفسك (History):", "Anamnese — Befragen Sie den Patienten selbst (History):"), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
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
                                .clickable(enabled = qid.isNotEmpty() && !(isV2 && showExplanation)) { askedQuestions = askedQuestions + qid },
                            shape = MaterialTheme.shapes.small
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = question.text ?: localized(de, "سؤال", "Frage"),
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
                                            text = localized(de, "جواب المريض: ", "Antwort des Patienten: ") + (question.answer ?: localized(de, "لا توجد معلومات إضافية", "Keine weiteren Angaben")),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(localized(de, "اضغط لطرح السؤال على المريض...", "Tippen, um die Frage zu stellen..."), fontSize = 11.sp, color = GoldAccentDark)
                                }
                            }
                        }
                    }
                }
            }

            // History (القصة المرضية والسوابق) — legacy cases only
            if (!isV2 && !caseItem.history.isNullOrEmpty()) {
                item {
                    Text(localized(de, "القصة السريرية والسوابق الموجهة (History):", "Krankengeschichte und Vorgeschichte (History):"), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
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
                                text = item.text ?: localized(de, "سؤال القصة ${idx + 1}", "Anamnesefrage ${idx + 1}"),
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
                                        text = localized(de, "النتيجة / الإجابة: ", "Ergebnis / Antwort: ") + (item.answer ?: localized(de, "لا توجد معلومات إضافية", "Keine weiteren Angaben")),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(localized(de, "اضغط لكشف الجواب...", "Tippen, um die Antwort zu zeigen..."), fontSize = 11.sp, color = GoldAccentDark)
                            }
                        }
                    }
                }
            }

            // Examinations (الفحص السريري) - C1: Two lines (line 1 = text, line 2 = finding)
            if (!caseItem.examinations.isNullOrEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(localized(de, "الفحص السريري العضوي (Physical Examinations):", "Körperliche Untersuchung (Physical Examinations):"), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                }
                items(caseItem.examinations.size) { idx ->
                    val item = caseItem.examinations[idx]
                    val isRevealed = revealedExams.contains(idx)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !(isV2 && showExplanation)) { revealedExams = revealedExams + idx },
                        shape = MaterialTheme.shapes.small
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = item.text ?: localized(de, "الفحص ${idx + 1}", "Untersuchung ${idx + 1}"),
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
                                        text = localized(de, "الموجودات السريرية: ", "Klinische Befunde: ") + (item.finding ?: item.text ?: localized(de, "طبيعي / لا يوجد شذوذ", "Unauffällig / keine Abweichung")),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SuccessGreen,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(localized(de, "اضغط لإجراء الفحص وكشف الموجودات...", "Tippen, um zu untersuchen und die Befunde zu zeigen..."), fontSize = 11.sp, color = GoldAccentDark)
                            }
                        }
                    }
                }
            }

            // Tests (الاستقصاءات والتحاليل)
            if (!caseItem.tests.isNullOrEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(localized(de, "الاستقصاءات المخبرية والشعاعية (Diagnostic Tests):", "Labor und Bildgebung (Diagnostic Tests):"), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                }
                items(caseItem.tests.size) { idx ->
                    val item = caseItem.tests[idx]
                    val isRevealed = revealedTests.contains(idx)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !(isV2 && showExplanation)) { revealedTests = revealedTests + idx },
                        shape = MaterialTheme.shapes.small
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = item.text ?: localized(de, "التحليل ${idx + 1}", "Test ${idx + 1}"),
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
                                        text = localized(de, "نتيجة التحليل / الصورة: ", "Test-/Bildergebnis: ") + (item.result ?: localized(de, "متوفر", "Verfügbar")),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(localized(de, "اضغط لطلب الاستقصاء وقراءة النتيجة...", "Tippen, um den Test anzufordern und das Ergebnis zu lesen..."), fontSize = 11.sp, color = GoldAccentDark)
                            }
                        }
                    }
                }
            }

            // Diagnosis Options
            if (!caseItem.diagnosisOptions.isNullOrEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(localized(de, "التشخيص التفريقي والتشخيص النهائي (Diagnosis):", "Differenzialdiagnose und Enddiagnose (Diagnosis):"), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
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
                                        Text(localized(de, "تقرير أداء الحالة السريرية", "Leistungsbericht zum Fall"), color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                                    Text(localized(de, "الدرجة المركّبة: ${fmtScore(score.total)} / 100", "Gesamtpunktzahl: ${fmtScore(score.total)} / 100"), color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    if (score.historyMerged) {
                                        Text(localized(de, "• القصة — تغطية الجوهري (لا أسئلة أعلام حمراء موثّقة في هذه الحالة): ${fmtScore(score.historyEssentialPoints)} / 40 (${score.essentialAsked}/${score.essentialTotal})", "• Anamnese — Wesentliches abgedeckt (keine dokumentierten Red-Flag-Fragen in diesem Fall): ${fmtScore(score.historyEssentialPoints)} / 40 (${score.essentialAsked}/${score.essentialTotal})"), color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                                    } else {
                                        Text(localized(de, "• القصة — تغطية الجوهري: ${fmtScore(score.historyEssentialPoints)} / 25 (${score.essentialAsked}/${score.essentialTotal})", "• Anamnese — Wesentliches: ${fmtScore(score.historyEssentialPoints)} / 25 (${score.essentialAsked}/${score.essentialTotal})"), color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                                        Text(localized(de, "• القصة — الأعلام الحمراء: ${fmtScore(score.historyRedFlagPoints)} / 15 (${score.redFlagAsked}/${score.redFlagTotal})", "• Anamnese — Red Flags: ${fmtScore(score.historyRedFlagPoints)} / 15 (${score.redFlagAsked}/${score.redFlagTotal})"), color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                                    }
                                    if (score.distractorsAsked > 0) {
                                        Text(localized(de, "• كفاءة القصة: سألت ${score.distractorsAsked} سؤالًا مشتّتًا (المجاني منها: ${score.freeDistractors}) — الخصم من مكوّن القصة: ${fmtScore(score.distractorPenalty)} نقطة", "• Anamnese-Effizienz: ${score.distractorsAsked} ablenkende Fragen gestellt (davon frei: ${score.freeDistractors}) — Abzug von der Anamnese-Komponente: ${fmtScore(score.distractorPenalty)} Punkte"), color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                                    }
                                    Text(localized(de, "• مكوّن القصة الإجمالي: ${fmtScore(score.historyTotal)} / 40", "• Anamnese gesamt: ${fmtScore(score.historyTotal)} / 40"), color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                                    Text(localized(de, "• الفحص والاستقصاءات — تغطية العناصر الأساسية: ${fmtScore(score.examCoveragePoints)} / 15 (${score.examKeyRevealed}/${score.examKeyTotal})", "• Untersuchung und Tests — Kernelemente abgedeckt: ${fmtScore(score.examCoveragePoints)} / 15 (${score.examKeyRevealed}/${score.examKeyTotal})"), color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                                    Text(
                                        localized(de, "• الفحص والاستقصاءات — الكفاءة: ${fmtScore(score.examEfficiencyPoints)} / 5", "• Untersuchung und Tests — Effizienz: ${fmtScore(score.examEfficiencyPoints)} / 5") +
                                            if (score.examEfficiencyPenalty > 0) localized(de, " (طُلب ${score.examDistractorsRequested} عنصرًا مشتّتًا — الخصم: ${fmtScore(score.examEfficiencyPenalty)})", " (${score.examDistractorsRequested} ablenkende Elemente angefordert — Abzug: ${fmtScore(score.examEfficiencyPenalty)})") else "",
                                        color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp
                                    )
                                    Text(localized(de, "• التشخيص: ${fmtScore(score.diagnosisPoints)} / 40 (${if (score.diagnosisCorrect) "صحيح" else "غير صحيح"})", "• Diagnose: ${fmtScore(score.diagnosisPoints)} / 40 (${if (score.diagnosisCorrect) "Richtig" else "Falsch"})"), color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                                    if (earlyDiagnosis) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(localized(de, "⚠ تشخيص مبكر: شخّصت قبل سؤال سؤال واحد على الأقل في 3 مجالات مختلفة من القصة.", "⚠ Frühe Diagnose: Sie haben diagnostiziert, bevor Sie in mindestens 3 Anamnese-Bereichen je mindestens eine Frage gestellt hatten."), color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                    if (score.missedFacts.isEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(localized(de, "كشفتَ كل الوقائع الحاسمة في هذه الحالة. أحسنت.", "Sie haben alle entscheidenden Fakten dieses Falls aufgedeckt. Gut gemacht."), color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }

                            // Safety alert: red-flag questions exist but none was asked.
                            // Documented interpretation: design section 5 ties this
                            // alert to cases whose options include a life-threatening
                            // diagnosis, but the data carries no such field; the
                            // adopted trigger is the broader factual one (red flags
                            // present and none asked), as specified for this build.
                            if (score.redFlagTotal > 0 && score.redFlagAsked == 0) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = ErrorRed),
                                    shape = MaterialTheme.shapes.large
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(localized(de, "⚠ تنبيه سلامة", "⚠ Sicherheitshinweis"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            localized(de, "لم تسأل أيًّا من أسئلة الأعلام الحمراء في هذه الحالة (${score.redFlagTotal} سؤالًا متاحًا). استبعاد التشاخيص المهدِّدة للحياة بسؤال المريض جزء أساسي من التقييم الآمن.", "Sie haben keine der Red-Flag-Fragen in diesem Fall gestellt (${score.redFlagTotal} Fragen verfügbar). Lebensbedrohliche Diagnosen durch gezielte Befragung auszuschließen, ist ein wesentlicher Teil der sicheren Beurteilung."),
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
                                        Text(localized(de, "وقائع حاسمة لم تُكشف (${score.missedFacts.size}):", "Nicht aufgedeckte entscheidende Fakten (${score.missedFacts.size}):"), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaroonPrimary)
                                        score.missedFacts.forEach { fact ->
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("• ${fact.label ?: localized(de, "واقعة حاسمة", "Entscheidender Fakt")}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            if (!fact.whyCritical.isNullOrEmpty()) {
                                                Text(localized(de, "لماذا هي حاسمة: ${fact.whyCritical}", "Warum entscheidend: ${fact.whyCritical}"), fontSize = 12.sp)
                                            }
                                            if (fact.revealerIds.isNotEmpty()) {
                                                val revealerNames = fact.revealerIds.joinToString(localized(de, "، ", ", ")) { revealerTexts[it] ?: it }
                                                Text(localized(de, "كان يكشفها: $revealerNames", "Aufzudecken durch: $revealerNames"), fontSize = 12.sp, color = GoldAccentDark, fontWeight = FontWeight.SemiBold)
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
                                    Text(localized(de, "تقرير أداء الحالة السريرية", "Leistungsbericht zum Fall"), color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                                Text(localized(de, "كشف النقاط الحرجة (Key Items):", "Aufdeckung der Kernelemente (Key Items):"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(localized(de, "• القصة السريرية: $historyPct% ($revealedHistoryKey/$totalHistoryKey)", "• Anamnese: $historyPct% ($revealedHistoryKey/$totalHistoryKey)"), color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                                Text(localized(de, "• الفحص العضوي: $examsPct% ($revealedExamsKey/$totalExamsKey)", "• Körperliche Untersuchung: $examsPct% ($revealedExamsKey/$totalExamsKey)"), color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                                Text(localized(de, "• الاستقصاءات: $testsPct% ($revealedTestsKey/$totalTestsKey)", "• Tests: $testsPct% ($revealedTestsKey/$totalTestsKey)"), color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(localized(de, "• الكفاءة الإجمالية لكشف النقاط المفتاحية: $overallPct%", "• Gesamteffizienz der Kernelemente: $overallPct%"), color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                                    Text(localized(de, "الشرح العلمي والتدبير السريري:", "Wissenschaftliche Erklärung und klinisches Management:"), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaroonPrimary)
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
                                    Text(localized(de, "نقاط التعلم السريرية الأساسية (Learning Points):", "Klinische Lernpunkte (Learning Points):"), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaroonPrimary)
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
                                        Text(localized(de, "القصة الكاملة كما في السجل:", "Die vollständige Geschichte laut Akte:"), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaroonPrimary)
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
