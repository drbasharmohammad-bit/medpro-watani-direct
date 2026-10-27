package sy.medpro.watani.ui.simulator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import sy.medpro.watani.data.OfflineStudyVault
import sy.medpro.watani.data.SubjectEnvelope
import sy.medpro.watani.data.getCorrectIndices
import sy.medpro.watani.ui.reference.QuickMedicalReferenceModal
import sy.medpro.watani.ui.explanation.ExplanationSection
import sy.medpro.watani.ui.explanation.ExplanationUiState
import sy.medpro.watani.ui.explanation.normalizeExplanationKey
import sy.medpro.watani.ui.explanation.rememberExplanationState
import sy.medpro.watani.ui.theme.*
import java.io.File

private data class SimQuestion(
    val id: Int,
    val specialty: String,
    val stem: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String,
    val natId: String? = null
)

private val sampleExamQuestions = listOf(
    SimQuestion(
        1,
        "الباطنة القلبية",
        "مريض 62 عاماً مدخن ومرض سكري راجع الإسعاف بألم صدري عاصر خلف القص منذ ساعتين مع تعرق غزير. تخطيط القلب أظهر ارتفاع القطعة ST في المساري II, III, aVF مع هبوط ضغط (85/55 ملم زئبقي) واحتقان وداجيين وإصغاء رئتين صافٍ. ما الخطوة العلاجية الإسعافية الأولى لرفع الضغط؟",
        listOf(
            "إعطاء السوائل الوريدية (Normal Saline 0.9%) بحذر",
            "إعطاء النتروغليسرين تحت اللسان فوراً",
            "إعطاء الفوروسيميد وريدياً",
            "إعطاء حاصرات بيتا الوريدية بجرعة عالية"
        ),
        0,
        "احتشاء سفلي (II, III, aVF) مترافق بالثلاثية السريرية لاحتشاء البطين الأيمن (هبوط ضغط + ارتفاع JVP + رئتين صافيتين). البطين الأيمن يعتمد على الحمل القبلي (Preload) لذلك يعالج بالسوائل الوريدية وتُمنع النترات والمدرات لأنها تسبب وهطاً دورانياً."
    ),
    SimQuestion(
        2,
        "الجراحة العامة",
        "مريضة 44 عاماً بدينة راجعت بألم في المراق الأيمن ينتشر للكتف الأيمن مع ترفع حروري (38.8°C) واصفرار صلبتين وتخليط ذهني وهبوط ضغط (80/50 ملم زئبقي). ما الإجراء النوعي الإسعافي لتصريف الطرق الصفراوية بعد الإنعاش؟",
        listOf(
            "تصوير وتصريف الطرق الصفراوية بالتنظير الراجع (ERCP)",
            "استئصال المرارة التنظيري الفوري",
            "تفتيت الحصيات بالأمواج الصادمة من خارج الجسم",
            "المراقبة المحافظة بالصادات الفموية فقط"
        ),
        0,
        "خماسية رينولدز (ثلاثية شاركو + صدمة + تبدل وعي) تشخص التهاب الطرق الصفراوية الصاعد الوخيم، والـ ERCP هو الإجراء المعياري الأول لفك الانسداد وتصريف القيح."
    ),
    SimQuestion(
        3,
        "طب الأطفال",
        "رضيع بعمر 4 أسابيع أُحضر بشكوى إقياءات نافورية غير صفراوية تزداد تدريجياً بعد كل رضاعة، مع بقاء الرضيع جائعاً. ما الاضطراب الشاردي والاستقلابي الكلاسيكي المتوقع؟",
        listOf(
            "قلاء استقلابي ناقص الكلور وناقص البوتاسيوم",
            "حماض استقلابي مرتفع الفجوة الأنيونية",
            "حماض استقلابي مفرط الكلور",
            "قلاء تنفسي مع فرط بوتاسيوم الدم"
        ),
        0,
        "في تضيق البواب الضخامي الوليدي يفقد الرضيع حمض كلور الماء المعدي (HCl) بالإقياءات المتكررة مع تفعيل الألدوستيرون الثانوي للتجفاف، مما يسبب قلاءً استقلابياً ناقص الكلور وناقص البوتاسيوم."
    ),
    SimQuestion(
        4,
        "النسائية والتوليد",
        "حامل في الأسبوع 33 راجعت بنزف تناسلي أحمر قانئ غزير غير مؤلم تماماً بدأ أثناء النوم. الرحم لينة غير متقبضة ودقات قلب الجنين 145/د. ما الإجراء المحظور قطعياً في هذه اللحظة؟",
        listOf(
            "إجراء المس المهبلي الإصبعي لفحص عنق الرحم",
            "إجراء الإيكو لتحديد موقع المشيمة",
            "فتح خطين وريديين وسحب زمرة وتصالب",
            "مراقبة تخطيط قلب الجنين (CTG)"
        ),
        0,
        "النزف الأحمر القانئ غير المؤلم في الثلث الثالث يوجه للمشيمة المنزاحة (Placenta Previa)، ويُمنع المس المهبلي منعاً باتاً قبل نفي المشيمة المنزاحة بالإيكو لتجنب النزف الصاعق."
    ),
    SimQuestion(
        5,
        "الباطنة الصدرية",
        "مريض 70 عاماً راجع بذات رئة مكتسبة من المجتمع، وبدء تخليط ذهني حديث، وتسرع تنفس (34/د)، وتوتر شرياني 85/55 ملم زئبقي، ويوريا الدم 52 ملغ/دل. كم يبلغ حرز CURB-65 وما مكان التدبير المعتمد؟",
        listOf(
            "حرز 5 من 5 — قبول في العناية المشددة (ICU)",
            "حرز 1 من 5 — علاج خارج المشفى بصاد فموي",
            "حرز 2 من 5 — مراقبة منزلية",
            "حرز 0 من 5 — لا يحتاج صادات"
        ),
        0,
        "معايير CURB-65 الخمسة متحققة جميعاً (تخليط ذهني، يوريا > 42 ملغ/دل أو BUN > 19، تنفس ≥ 30، ضغط < 90/60، عمر ≥ 65) مما يستوجب القبول في العناية المشددة."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamSimulatorScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var mode by remember { mutableStateOf("SETUP") } // SETUP, RUNNING, REPORT
    var selectedSpecialty by remember { mutableStateOf("شامل - جميع الاختصاصات") }
    var timerMinutes by remember { mutableStateOf(15) }
    var remainingSeconds by remember { mutableStateOf(15 * 60) }
    var currentIndex by remember { mutableStateOf(0) }
    var answers by remember { mutableStateOf(mapOf<Int, Int>()) }
    var ruledOut by remember { mutableStateOf(mapOf<Int, Set<Int>>()) }
    var flaggedQuestions by remember { mutableStateOf(setOf<Int>()) }
    var showLabModal by remember { mutableStateOf(false) }
    var activeQuestions by remember { mutableStateOf(sampleExamQuestions) }

    LaunchedEffect(mode) {
        if (mode == "RUNNING") {
            remainingSeconds = timerMinutes * 60
            while (remainingSeconds > 0 && mode == "RUNNING") {
                delay(1000)
                remainingSeconds--
            }
            if (remainingSeconds == 0 && mode == "RUNNING") {
                mode = "REPORT"
            }
        } else if (mode == "REPORT") {
            OfflineStudyVault.ensureInitialized(context)
            activeQuestions.forEachIndexed { idx, q ->
                val userAns = answers[idx]
                val isCor = userAns == q.correctIndex
                val selText = q.options.getOrNull(userAns ?: -1) ?: ""
                val corText = q.options.getOrNull(q.correctIndex) ?: ""
                OfflineStudyVault.recordQuestionAttempt(
                    context = context,
                    subjectTitle = q.specialty,
                    questionId = "sim_${q.id}_$idx",
                    questionText = q.stem,
                    selectedOptionText = selText,
                    correctOptionText = corText,
                    explanation = q.explanation,
                    isCorrect = isCor
                )
            }
        }
    }

    if (showLabModal) {
        QuickMedicalReferenceModal(onDismiss = { showLabModal = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("محاكاة الامتحان الوطني الموحد", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        if (mode == "RUNNING") {
                            val mm = remainingSeconds / 60
                            val ss = remainingSeconds % 60
                            Text(
                                text = String.format("الزمن المتبقي: %02d:%02d • سؤال %d من %d", mm, ss, currentIndex + 1, activeQuestions.size),
                                fontSize = 11.sp,
                                color = GoldAccent
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (mode == "SETUP") onBack() else mode = "SETUP"
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                actions = {
                    if (mode == "RUNNING") {
                        IconButton(onClick = { showLabModal = true }) {
                            Icon(Icons.Default.Science, contentDescription = "القيم المخبرية", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaroonPrimary, titleContentColor = MaterialTheme.colorScheme.onPrimary)
            )
        }
    ) { padding ->
        when (mode) {
            "SETUP" -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaroonDeep),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("بيئة محاكاة الامتحان الطبي الموحد", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                    Text("توقيت معياري • شطب الخيارات • القيم المخبرية • تقرير تحليلي", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("تخصيص جلسة المحاكاة", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaroonPrimary)

                            Text("نطاق الاختصاصات:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            val specs = listOf("شامل - جميع الاختصاصات", "الباطنة والجراحة", "الأطفال والنسائية")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                specs.forEach { sp ->
                                    FilterChip(
                                        selected = selectedSpecialty == sp,
                                        onClick = { selectedSpecialty = sp },
                                        label = { Text(sp, fontSize = 11.sp) }
                                    )
                                }
                            }

                            Text("الزمن المخصص للجلسة:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                listOf(10, 15, 30).forEach { mins ->
                                    FilterChip(
                                        selected = timerMinutes == mins,
                                        onClick = { timerMinutes = mins },
                                        label = { Text("$mins دقيقة", fontSize = 12.sp) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val dir = File(context.filesDir, "sections_cache")
                                    val list = mutableListOf<SimQuestion>()
                                    if (dir.exists() && dir.isDirectory) {
                                        dir.listFiles()?.forEach { file ->
                                            if (file.name.endsWith(".json") && !file.name.contains("cases") && !file.name.contains("guide") && !file.name.contains("osce")) {
                                                try {
                                                    val text = file.readText(Charsets.UTF_8)
                                                    val moshi = com.squareup.moshi.Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
                                                    val env = moshi.adapter(SubjectEnvelope::class.java).fromJson(text)
                                                    val subjName = env?.subjectName ?: file.name.substringBefore(".json")
                                                    env?.topics?.forEach { topic ->
                                                        topic.questions?.forEach { q ->
                                                            val qSpec = subjName
                                                            val opts = q.options ?: emptyList()
                                                            val correctIdx = getCorrectIndices(q, opts).firstOrNull() ?: 0
                                                            if (opts.isNotEmpty()) {
                                                                list.add(
                                                                    SimQuestion(
                                                                        id = list.size + 1,
                                                                        specialty = qSpec,
                                                                        stem = q.text ?: "",
                                                                        options = opts,
                                                                        correctIndex = correctIdx,
                                                                        explanation = q.explanation ?: "",
                                                                        natId = q.id
                                                                    )
                                                                )
                                                            }
                                                        }
                                                    }
                                                } catch (_: Exception) {}
                                            }
                                        }
                                    }
                                    val filtered = when (selectedSpecialty) {
                                        "الباطنة والجراحة" -> list.filter { it.specialty.contains("باطنة", true) || it.specialty.contains("جراحة", true) }
                                        "الأطفال والنسائية" -> list.filter { it.specialty.contains("أطفال", true) || it.specialty.contains("نسائية", true) }
                                        else -> list
                                    }
                                    activeQuestions = if (filtered.isNotEmpty()) filtered.shuffled().take(15) else sampleExamQuestions

                                    answers = emptyMap()
                                    ruledOut = emptyMap()
                                    flaggedQuestions = emptySet()
                                    currentIndex = 0
                                    mode = "RUNNING"
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("بدء الامتحان التجريبي الآن", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            "RUNNING" -> {
                val q = activeQuestions[currentIndex]
                val selectedOpt = answers[currentIndex]
                val ruledOutSet = ruledOut[currentIndex] ?: emptySet()
                val isFlagged = flaggedQuestions.contains(currentIndex)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(16.dp)
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(activeQuestions) { idx, _ ->
                            val isCurrent = idx == currentIndex
                            val isAns = answers.containsKey(idx)
                            val isFlag = flaggedQuestions.contains(idx)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = when {
                                    isCurrent -> MaroonPrimary
                                    isFlag -> WarningAmber
                                    isAns -> SuccessGreen.copy(alpha = 0.2f)
                                    else -> MaterialTheme.colorScheme.surface
                                },
                                border = BorderStroke(1.dp, MaroonPrimary.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .size(38.dp)
                                    .clickable { currentIndex = idx }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${idx + 1}",
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent || isFlag) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(color = MaroonPrimary.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                                        Text(
                                            q.specialty,
                                            color = MaroonPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    AssistChip(
                                        onClick = {
                                            flaggedQuestions = if (isFlagged) flaggedQuestions - currentIndex else flaggedQuestions + currentIndex
                                        },
                                        label = { Text(if (isFlagged) "مُعلّم للمراجعة ★" else "تعليم السؤال", fontSize = 11.sp) }
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(q.stem, fontSize = 16.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            itemsIndexed(q.options) { optIdx, optText ->
                                val isSel = selectedOpt == optIdx
                                val isRule = ruledOutSet.contains(optIdx)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSel) MaroonPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.5.dp, if (isSel) MaroonPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .alpha(if (isRule) 0.45f else 1f)
                                        .clickable {
                                            answers = answers.toMutableMap().apply { put(currentIndex, optIdx) }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(selected = isSel, onClick = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = optText,
                                            modifier = Modifier.weight(1f),
                                            fontSize = 14.sp,
                                            style = LocalTextStyle.current.copy(
                                                textDecoration = if (isRule) TextDecoration.LineThrough else TextDecoration.None
                                            )
                                        )
                                        IconButton(
                                            onClick = {
                                                val nextSet = if (isRule) ruledOutSet - optIdx else ruledOutSet + optIdx
                                                ruledOut = ruledOut.toMutableMap().apply { put(currentIndex, nextSet) }
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isRule) Icons.Default.Undo else Icons.Default.StrikethroughS,
                                                contentDescription = "شطب",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { if (currentIndex > 0) currentIndex-- },
                            enabled = currentIndex > 0,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("السؤال السابق")
                        }
                        if (currentIndex < activeQuestions.lastIndex) {
                            Button(
                                onClick = { currentIndex++ },
                                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("السؤال التالي")
                            }
                        } else {
                            Button(
                                onClick = { mode = "REPORT" },
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("إنهاء والتصحيح")
                            }
                        }
                    }
                }
            }

            "REPORT" -> {
                val correctCount = activeQuestions.indices.count { idx ->
                    answers[idx] == activeQuestions[idx].correctIndex
                }
                val percent = if (activeQuestions.isEmpty()) 0 else (correctCount * 100) / activeQuestions.size

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaroonDeep),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("تقرير نتيجة المحاكاة الوطنية", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("$percent%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 38.sp)
                                Text(
                                    "الإجابات الصحيحة: $correctCount من ${activeQuestions.size}",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    itemsIndexed(activeQuestions) { idx, q ->
                        val userAns = answers[idx]
                        val isCorrect = userAns == q.correctIndex
                        val simExplanationState = rememberExplanationState(normalizeExplanationKey(q.natId))
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, if (isCorrect) SuccessGreen else ErrorRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    "سؤال ${idx + 1} (${q.specialty}) — ${if (isCorrect) "إجابة صحيحة ✓" else "إجابة خاطئة ✗"}",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCorrect) SuccessGreen else ErrorRed,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("الجواب المعتمد: ${q.options[q.correctIndex]}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(q.explanation, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                            }
                            when (val ex = simExplanationState) {
                                is ExplanationUiState.Present -> ExplanationSection(content = ex.content, modifier = Modifier.padding(horizontal = 14.dp))
                                ExplanationUiState.Loading -> Box(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaroonPrimary
                                    )
                                }
                                ExplanationUiState.Absent, ExplanationUiState.Idle -> Unit
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = { mode = "SETUP" },
                            colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("بدء جلسة محاكاة جديدة")
                        }
                    }
                }
            }
        }
    }
}
