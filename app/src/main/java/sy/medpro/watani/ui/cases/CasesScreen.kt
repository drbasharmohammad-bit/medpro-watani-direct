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
    val favPrefs = remember { context.getSharedPreferences("watani_favorites", android.content.Context.MODE_PRIVATE) }
    var favList by remember { mutableStateOf(favPrefs.getStringSet("fav_list", emptySet()) ?: emptySet()) }
    val cId = caseItem.id ?: caseItem.title ?: "حالة"
    val isFav = favList.contains(cId)

    var revealedHistory by remember { mutableStateOf(setOf<Int>()) }
    var revealedExams by remember { mutableStateOf(setOf<Int>()) }
    var revealedTests by remember { mutableStateOf(setOf<Int>()) }
    var selectedDiagnosis by remember { mutableStateOf<String?>(null) }
    var showExplanation by remember { mutableStateOf(false) }

    LaunchedEffect(showExplanation) {
        if (showExplanation) {
            OfflineStudyVault.recordCaseCompletion(context, caseItem.xpReward ?: 25)
        }
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
                            text = "القصة السريرية والعرض البدئي (Presentation):",
                            fontWeight = FontWeight.Bold,
                            color = GoldAccent,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = caseItem.presentation ?: "لا توجد قصة متاحة",
                            color = Color.White,
                            fontSize = 15.sp,
                            lineHeight = 23.sp
                        )
                    }
                }
            }

            // History (القصة المرضية والسوابق)
            if (!caseItem.history.isNullOrEmpty()) {
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
                                selectedDiagnosis = option
                                showExplanation = true
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

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Performance Report Summary Card
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
                    }
                }
            }
        }
    }
}
