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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sy.medpro.watani.data.MistakeKeys
import sy.medpro.watani.data.MistakesBank
import sy.medpro.watani.data.OfflineStudyVault
import sy.medpro.watani.data.QuestionDto
import sy.medpro.watani.data.WataniGatewaySync
import sy.medpro.watani.data.getCorrectIndices
import sy.medpro.watani.ui.SlimSubBar
import sy.medpro.watani.ui.reference.QuickMedicalReferenceModal
import sy.medpro.watani.ui.explanation.ExplanationSection
import sy.medpro.watani.ui.explanation.explanationContentOf
import sy.medpro.watani.ui.explanation.ExplanationUiState
import sy.medpro.watani.ui.explanation.normalizeExplanationKey
import sy.medpro.watani.ui.explanation.rememberExplanationState
import sy.medpro.watani.ui.theme.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuizScreen(
    title: String,
    questions: List<QuestionDto>,
    onBack: () -> Unit,
    sourcePath: String? = null,
    subjectName: String? = null,
    ltr: Boolean = false
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        OfflineStudyVault.ensureInitialized(context)
    }
    // F-14: in-progress answers survive process death / activity
    // recreation via rememberSaveable (the quiz is keyed by its
    // question set: a different quiz instance starts clean because
    // the caller composes a fresh screen state per quiz). Previously
    // plain remember{} lost every answer when Android killed the
    // process mid-quiz.
    var selectedAnswers by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(mapOf<Int, Int>()) }
    var ruledOutMap by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(mapOf<Int, Set<Int>>()) }
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
                        WataniGatewaySync.ensureAndSyncUser(context, user)
                    } catch (_: Exception) {}
                }
            }
        }
        while (true) {
            delay(1000)
            activeQuestionTimerSeconds++
        }
    }

    // Points sync on quiz exit (Nov 2026 leaderboard fix): the old code
    // synced only when every question was answered, so a partial quiz
    // never moved the leaderboard until the next cold start or full
    // quiz. Leaving a quiz with answers now pushes the current totals
    // too (same idempotent absolute set, same swallowed errors).
    val latestAnswers by rememberUpdatedState(selectedAnswers)
    DisposableEffect(Unit) {
        onDispose {
            if (latestAnswers.isNotEmpty()) {
                val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                if (user != null) {
                    val appContext = context.applicationContext
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            WataniGatewaySync.ensureAndSyncUser(appContext, user)
                        } catch (_: Exception) {}
                    }
                }
            }
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
                // Slim chrome (1.2.7): 48dp sub-bar; same title lines and
                // the same two action buttons, just tighter type.
                SlimSubBar(
                    onBack = onBack,
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
                    }
                ) {
                    Column {
                        Text("اختبار: $title", fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                        Text(
                            text = "عدد الأسئلة: ${questions.size} • وضع التدريب السريري المتقدم",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                            maxLines = 1
                        )
                    }
                }
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

                                    // FlowRow, not Row (1.2.5 layout-fix
                                    // class): the first chip's label is
                                    // long — a plain Row squeezes chip
                                    // widths on narrow screens.
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            val cardSubjectLabel = sy.medpro.watani.data.ShareLinks.subjectLabel(
                                subjectName, sourcePath, title
                            )

                            QuestionCard(
                                questionNumber = index + 1,
                                question = question,
                                options = options,
                                ltr = ltr,
                                selectedOption = selectedOption,
                                correctIndices = correctIndices,
                                isAnswered = isAnswered,
                                ruledOutIndices = ruledOutSet,
                                highlightKeywords = highlightKeywords,
                                timeSpentSeconds = timeTaken,
                                subjectLabel = cardSubjectLabel,
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
                                        // F-08: an empty correctIndices means the
                                        // question's key is invalid — the tap is
                                        // shown as selected above, but the question
                                        // is never graded, scored, or recorded.
                                        if (correctIndices.isNotEmpty()) {
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
                                            // بنك أخطائي (1.2.5): the SAME central
                                            // grading point also files a wrong
                                            // answer — with its full re-askable
                                            // payload — into the personal
                                            // mistakes bank for spaced review.
                                            // This one hook covers every bank
                                            // that renders through QuizScreen
                                            // (questions/, questions_en/,
                                            // dawrat_jami3at/, uworld/). The
                                            // bank is local-only; grading and
                                            // scoring above are untouched.
                                            if (!isCor) {
                                                val (mbBank, mbFile) = MistakeKeys.bankOf(sourcePath, title)
                                                MistakesBank.recordWrong(
                                                    context = context,
                                                    bank = mbBank,
                                                    file = mbFile,
                                                    questionId = question.id,
                                                    indexInFile = index,
                                                    subject = subjectName ?: title,
                                                    stem = question.text ?: "",
                                                    options = options,
                                                    correctIndex = correctIndices.first(),
                                                    explanation = question.explanation ?: "",
                                                    whyCorrect = question.whyCorrect,
                                                    whyWrong = question.whyWrong,
                                                    summary = question.summary,
                                                    ltr = ltr
                                                )
                                            }
                                        }
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

@OptIn(ExperimentalMaterial3Api::class)
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
    ltr: Boolean = false,
    timeSpentSeconds: Int? = null,
    subjectLabel: String = "",
    onToggleRuleOut: (Int) -> Unit = {},
    onOptionSelected: (Int) -> Unit
) {
    val context = LocalContext.current
    val shareScope = rememberCoroutineScope()
    // Account binding: favorites live per account, never in one shared store.
    val favUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    val favPrefsName = if (favUid.isNullOrBlank()) "watani_favorites" else "watani_favorites_$favUid"
    val favPrefs = remember(favUid) { context.getSharedPreferences(favPrefsName, android.content.Context.MODE_PRIVATE) }
    var favList by remember(favUid) { mutableStateOf(favPrefs.getStringSet("fav_list", emptySet()) ?: emptySet()) }
    val qId = question.id ?: question.text ?: "سؤال $questionNumber"
    val qLookupKey = normalizeExplanationKey(question.id)
    // Unified explanation rule (Nov 2026): a question with a non-blank
    // embedded explanation (shown below from the file itself) never
    // consults the separate explanation layer — the lookup is disabled,
    // so the retired layer can no longer produce a false "failed to
    // load" state or a retry button under an explanation already shown.
    // Structured explanation embedded on the question object itself
    // (build 1.2.3 content waves: why_correct/why_wrong/summary added
    // to section files, dawrat_jami3at first). When present it IS the
    // explanation — the separate layer lookup stays disabled, exactly
    // like the embedded-text rule above.
    val structuredContent = remember(question.id, question.summary, question.whyCorrect, question.whyWrong) {
        explanationContentOf(question.summary, question.whyCorrect, question.whyWrong)
    }
    val explanationState = rememberExplanationState(
        qLookupKey,
        enabled = question.explanation.isNullOrBlank() && structuredContent == null
    )
    // «إظهار الشرح» (doctor's order, build 1.2.3): after answering,
    // NOTHING of the explanation shows automatically — a single button
    // reveals all of it (embedded text, structured fields, layer
    // content) inline in this card's natural scroll column: no text
    // clipping, no capped height, no nested scroll.
    var showExplanation by remember(question.id) { mutableStateOf(false) }
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
                            shareScope.launch(Dispatchers.IO) {
                                shareQuestionAsImage(context, question, subjectLabel)
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "مشاركة السؤال",
                            tint = MaroonPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
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

            // English-bank mode (questions_en/): stems and options render
            // LTR; the card chrome and the (Arabic) explanation below
            // stay RTL.
            CompositionLocalProvider(LocalLayoutDirection provides if (ltr) LayoutDirection.Ltr else LocalLayoutDirection.current) {
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
            }

            AnimatedVisibility(
                visible = isAnswered,
                enter = expandVertically(animationSpec = tween(280)) + fadeIn(),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    val layerPresent = explanationState as? ExplanationUiState.Present
                    val hasExplanation = !question.explanation.isNullOrBlank() ||
                        structuredContent != null || layerPresent != null
                    if (hasExplanation && !showExplanation) {
                        // «إظهار الشرح» — the only explanation element
                        // shown until the student taps it. A question
                        // with NO explanation never reaches this branch
                        // (hasExplanation false): no button, and the
                        // card below keeps its fallback line as before.
                        OutlinedButton(
                            onClick = { showExplanation = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaroonPrimary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaroonPrimary)
                        ) {
                            Text("إظهار الشرح", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
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
                    if (showExplanation) {
                        structuredContent?.let { ExplanationSection(content = it) }
                        layerPresent?.let { ExplanationSection(content = it.content) }
                    }
                    }
                    when (val ex = explanationState) {
                        // Present is rendered above, behind the
                        // «إظهار الشرح» button; only the load states
                        // remain here.
                        is ExplanationUiState.Present -> Unit
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
                        is ExplanationUiState.Failed -> Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "تعذر تحميل الشرح — تحقق من الاتصال وحاول مجددًا.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(border = BorderStroke(1.dp, MaroonPrimary), shape = MaterialTheme.shapes.medium, onClick = ex.retry) {
                                Text("إعادة تحميل الشرح", fontSize = 13.sp)
                            }
                        }
                        ExplanationUiState.Absent, ExplanationUiState.Idle -> Unit
                    }
                }
            }
        }
    }

}

/** Image-only share: renders the spec card, saves it to cacheDir and
 * shares it via FileProvider. The ONLY accompanying text is the
 * channel URL (ShareLinks.FALLBACK_URL) — the question text itself
 * never leaves the app as text. On any rendering/IO failure nothing
 * is shared at all: there is deliberately no text fallback. */
private fun shareQuestionAsImage(
    context: android.content.Context,
    question: QuestionDto,
    subjectLabel: String
) {
    try {
        val bitmap = sy.medpro.watani.ui.share.ShareCardRenderer.render(context, question, subjectLabel)
        val file = sy.medpro.watani.ui.share.ShareCardRenderer.saveToCache(context, bitmap)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_TEXT, sy.medpro.watani.data.ShareLinks.FALLBACK_URL)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            android.content.Intent.createChooser(intent, "مشاركة السؤال").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
    } catch (_: Exception) {
        // F-15: the failure is VISIBLE now (the old code swallowed it
        // and the share tap looked dead). Still no text fallback:
        // image-only sharing is the doctor's rule — nothing is shared
        // on failure, the user is simply told.
        try {
            android.widget.Toast.makeText(context, "تعذّرت مشاركة السؤال — أعد المحاولة.", android.widget.Toast.LENGTH_SHORT).show()
        } catch (_: Exception) { }
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
                    // Keyword highlight: rose-ivory FeaturedContainer (was pale yellow) — no-yellow rule.
                    background = FeaturedContainer,
                    color = Color(0xFF52131C),
                    fontWeight = FontWeight.Bold
                ),
                start = match.range.first,
                end = match.range.last + 1
            )
        }
    }
}
