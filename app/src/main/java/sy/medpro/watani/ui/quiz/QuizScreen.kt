package sy.medpro.watani.ui.quiz

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sy.medpro.watani.data.OfflineStudyVault
import sy.medpro.watani.data.QuestionDto
import sy.medpro.watani.data.WataniFirestoreSync
import sy.medpro.watani.data.getCorrectIndices
import sy.medpro.watani.ui.reference.QuickMedicalReferenceModal
import sy.medpro.watani.ui.explanation.ExplanationSection
import sy.medpro.watani.ui.explanation.ExplanationUiState
import sy.medpro.watani.ui.explanation.normalizeExplanationKey
import sy.medpro.watani.ui.explanation.rememberExplanationState
import sy.medpro.watani.ui.theme.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    title: String,
    questions: List<QuestionDto>,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        OfflineStudyVault.ensureInitialized(context)
    }
    var selectedAnswers by remember { mutableStateOf(mapOf<Int, Int>()) }
    var ruledOutMap by remember { mutableStateOf(mapOf<Int, Set<Int>>()) }
    var questionTimes by remember { mutableStateOf(mapOf<Int, Int>()) }
    var activeQuestionTimerSeconds by remember { mutableStateOf(0) }
    var highlightKeywords by remember { mutableStateOf(true) }
    var showReferenceSheet by remember { mutableStateOf(false) }
    var score by remember { mutableStateOf(0) }

    LaunchedEffect(selectedAnswers.size) {
        activeQuestionTimerSeconds = 0
        if (questions.isNotEmpty() && selectedAnswers.size == questions.size) {
            val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (user != null) {
                scope.launch(Dispatchers.IO) {
                    try {
                        WataniFirestoreSync.ensureAndSyncUser(context, user)
                    } catch (_: Exception) {}
                }
            }
        }
        while (true) {
            delay(1000)
            activeQuestionTimerSeconds++
        }
    }

    val averageSeconds = remember(questionTimes) {
        if (questionTimes.isEmpty()) 0 else questionTimes.values.sum() / questionTimes.size
    }
    val accuracyPercent = remember(selectedAnswers, score) {
        if (selectedAnswers.isEmpty()) 0 else (score * 100) / selectedAnswers.size
    }
    val pacerColor by animateColorAsState(
        targetValue = when {
            activeQuestionTimerSeconds < 50 -> SuccessGreen
            activeQuestionTimerSeconds <= 75 -> WarningAmber
            else -> ErrorRed
        },
        label = "pacerColor"
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("اختبار: $title", fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
                            Text(
                                text = "عدد الأسئلة: ${questions.size} • وضع التدريب السريري المتقدم",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { highlightKeywords = !highlightKeywords }) {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = "تظليل الدلالات السريرية",
                                tint = if (highlightKeywords) GoldAccent else MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        IconButton(onClick = { showReferenceSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.Science,
                                contentDescription = "القيم المخبرية والخطوط العلاجية",
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
            if (showReferenceSheet) {
                QuickMedicalReferenceModal(onDismiss = { showReferenceSheet = false })
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (questions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("لا توجد أسئلة متاحة في هذا القسم.", color = MaterialTheme.colorScheme.onBackground)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                                border = BorderStroke(1.dp, MaroonPrimary.copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Timer, contentDescription = null, tint = pacerColor, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "مؤقت السؤال: ${activeQuestionTimerSeconds}ث / 75ث",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = pacerColor
                                            )
                                        }
                                        Surface(
                                            color = MaroonPrimary.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "متوسط السرعة: ${averageSeconds}ث/سؤال",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaroonPrimary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "المُنجز: ${selectedAnswers.size} / ${questions.size}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "الدقة: $accuracyPercent% ($score صحيحة)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (accuracyPercent >= 60) SuccessGreen else MaroonPrimary
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        AssistChip(
                                            onClick = { highlightKeywords = !highlightKeywords },
                                            label = { Text(if (highlightKeywords) "تظليل الكلمات المفتاحية: مفعل" else "تفعيل تظليل الكلمات المفتاحية", fontSize = 11.sp) },
                                            leadingIcon = {
                                                Icon(Icons.Default.Highlight, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaroonPrimary)
                                            }
                                        )
                                        AssistChip(
                                            onClick = { showReferenceSheet = true },
                                            label = { Text("القيم المخبرية", fontSize = 11.sp) },
                                            leadingIcon = {
                                                Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaroonPrimary)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        itemsIndexed(questions) { index, question ->
                            val options = question.options ?: emptyList()
                            val selectedOption = selectedAnswers[index]
                            val isAnswered = selectedOption != null
                            val correctIndices = getCorrectIndices(question, options)
                            val ruledOutSet = ruledOutMap[index] ?: emptySet()
                            val timeTaken = questionTimes[index]

                            QuestionCard(
                                questionNumber = index + 1,
                                question = question,
                                options = options,
                                selectedOption = selectedOption,
                                correctIndices = correctIndices,
                                isAnswered = isAnswered,
                                ruledOutIndices = ruledOutSet,
                                highlightKeywords = highlightKeywords,
                                timeSpentSeconds = timeTaken,
                                onToggleRuleOut = { optIdx ->
                                    if (!isAnswered) {
                                        val currentSet = ruledOutMap[index] ?: emptySet()
                                        val updatedSet = if (currentSet.contains(optIdx)) currentSet - optIdx else currentSet + optIdx
                                        ruledOutMap = ruledOutMap.toMutableMap().apply { put(index, updatedSet) }
                                    }
                                },
                                onOptionSelected = { optionIndex ->
                                    if (!isAnswered) {
                                        val newAnswers = selectedAnswers.toMutableMap()
                                        newAnswers[index] = optionIndex
                                        selectedAnswers = newAnswers
                                        questionTimes = questionTimes.toMutableMap().apply {
                                            put(index, activeQuestionTimerSeconds.coerceAtLeast(1))
                                        }
                                        val isCor = correctIndices.contains(optionIndex)
                                        if (isCor) {
                                            score++
                                        }
                                        val correctText = options.getOrNull(correctIndices.firstOrNull() ?: 0) ?: ""
                                        val selectedText = options.getOrNull(optionIndex) ?: ""
                                        OfflineStudyVault.recordQuestionAttempt(
                                            context = context,
                                            subjectTitle = title,
                                            questionId = question.id ?: "${title}_${index + 1}",
                                            questionText = question.text ?: "",
                                            selectedOptionText = selectedText,
                                            correctOptionText = correctText,
                                            explanation = question.explanation ?: "",
                                            isCorrect = isCor
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuestionCard(
    questionNumber: Int,
    question: QuestionDto,
    options: List<String>,
    selectedOption: Int?,
    correctIndices: List<Int>,
    isAnswered: Boolean,
    ruledOutIndices: Set<Int> = emptySet(),
    highlightKeywords: Boolean = true,
    timeSpentSeconds: Int? = null,
    onToggleRuleOut: (Int) -> Unit = {},
    onOptionSelected: (Int) -> Unit
) {
    val context = LocalContext.current
    val favPrefs = remember { context.getSharedPreferences("watani_favorites", android.content.Context.MODE_PRIVATE) }
    var favList by remember { mutableStateOf(favPrefs.getStringSet("fav_list", emptySet()) ?: emptySet()) }
    val qId = question.id ?: question.text ?: "سؤال $questionNumber"
    val qLookupKey = normalizeExplanationKey(question.id)
    val explanationState = rememberExplanationState(qLookupKey)
    val isFav = favList.contains(qId)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaroonPrimary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "سؤال $questionNumber",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val next = if (isFav) favList - qId else favList + qId
                            favPrefs.edit().putStringSet("fav_list", next).apply()
                            favList = next
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "إضافة للمفضلة",
                            tint = if (isFav) Color.Red else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    if (timeSpentSeconds != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "زمن الحل: ${timeSpentSeconds}ث",
                                fontSize = 11.sp,
                                color = MaroonPrimary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    if (!question.session.isNullOrEmpty()) {
                        Surface(
                            color = MaroonPrimary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = question.session,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaroonPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val rawStem = question.text ?: ""
            val styledStem = remember(rawStem, highlightKeywords) {
                if (highlightKeywords) buildHighlightedClinicalStem(rawStem)
                else AnnotatedString(rawStem)
            }

            Text(
                text = styledStem,
                fontSize = 16.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(14.dp))

            options.forEachIndexed { optIndex, optionText ->
                val isSelected = selectedOption == optIndex
                val isCorrectOpt = correctIndices.contains(optIndex)
                val isRuledOut = ruledOutIndices.contains(optIndex) && !isAnswered


                val borderColor = when {
                    isAnswered && isCorrectOpt -> SuccessGreen
                    isAnswered && isSelected && !isCorrectOpt -> ErrorRed
                    isSelected -> MaroonPrimary
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
                }

                val backgroundColor = when {
                    isAnswered && isCorrectOpt -> SuccessGreen.copy(alpha = 0.10f)
                    isAnswered && isSelected && !isCorrectOpt -> ErrorRed.copy(alpha = 0.10f)
                    else -> MaterialTheme.colorScheme.surface
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = backgroundColor,
                    border = BorderStroke(1.5.dp, borderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .alpha(if (isRuledOut) 0.45f else 1f)
                        .clickable(enabled = !isAnswered) { onOptionSelected(optIndex) }
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = MaroonPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = optionText,
                                modifier = Modifier.weight(1f),
                                fontSize = 15.sp,
                                style = LocalTextStyle.current.copy(
                                    textDecoration = if (isRuledOut) TextDecoration.LineThrough else TextDecoration.None
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (!isAnswered) {
                                IconButton(
                                    onClick = { onToggleRuleOut(optIndex) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isRuledOut) Icons.Default.Undo else Icons.Default.StrikethroughS,
                                        contentDescription = "شطب واستبعاد الخيار",
                                        tint = if (isRuledOut) MaroonPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else {
                                if (isCorrectOpt) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
                                } else if (isSelected) {
                                    Icon(Icons.Default.Error, contentDescription = null, tint = ErrorRed)
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isAnswered,
                enter = expandVertically(animationSpec = tween(280)) + fadeIn(),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaroonPrimary.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "الشرح السريري والتوضيح المرجعي:",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaroonPrimary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (!question.explanation.isNullOrEmpty()) question.explanation else "الإجابة المعتمدة موضحة باللون الأخضر وفق مفتاح التصحيح الرسمي.",
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    when (val ex = explanationState) {
                        is ExplanationUiState.Present -> ExplanationSection(content = ex.content)
                        ExplanationUiState.Loading -> Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
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
        }
    }
}



private fun buildHighlightedClinicalStem(text: String): AnnotatedString {
    val clinicalPatterns = Regex(
        "(\\d+[./]?\\d*\\s*(?:سنة|عاماً|عام|أشهر|يوم|ساعة|ملم زئبقي|ملغ/دل|غ/دل|mEq/L|mg/dL|mmHg|°C|%)|" +
            "ألم|حاد|مزمن|إيلام|ترفع حروري|حمى|يرقان|صدمة|هبوط ضغط|تسرع قلب|ضيق نفس|تشنج|اختلاج|نزف|حامل|رضيع|طفل)"
    )
    return buildAnnotatedString {
        append(text)
        clinicalPatterns.findAll(text).forEach { match ->
            addStyle(
                style = SpanStyle(
                    background = Color(0xFFFFF3CD),
                    color = Color(0xFF52131C),
                    fontWeight = FontWeight.Bold
                ),
                start = match.range.first,
                end = match.range.last + 1
            )
        }
    }
}
