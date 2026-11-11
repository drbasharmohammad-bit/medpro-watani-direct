package sy.medpro.watani.ui.anki

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.data.OfflineStudyVault
import sy.medpro.watani.ui.SlimSubBar
import sy.medpro.watani.ui.icons.SubjectIcon
import sy.medpro.watani.ui.icons.subjectIconRes
import sy.medpro.watani.ui.theme.MaroonPrimary
import sy.medpro.watani.ui.theme.SuccessGreen
import sy.medpro.watani.ui.theme.ErrorRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnkiFlashcardScreen(onBack: () -> Unit, wrongOnly: Boolean = false) {
    val context = androidx.compose.ui.platform.LocalContext.current
    OfflineStudyVault.ensureInitialized(context)
    val stats by OfflineStudyVault.statsFlow.collectAsState()

    var set by remember { mutableStateOf<AnkiSet?>(null) }

    LaunchedEffect(Unit) {
        set = AnkiRepository(context.applicationContext).load()
    }

    val loadedSet = set
    val subjects = loadedSet?.subjects.orEmpty()
    val allCards = remember(loadedSet) { loadedSet?.cards.orEmpty() }

    // SRS layer (local-only scheduling): due ids/counts recompute
    // whenever a grade lands (AnkiSrs.changes) or the set reloads.
    val srsChanges by AnkiSrs.changes.collectAsState()
    val dueIds = remember(allCards, srsChanges) {
        if (allCards.isEmpty()) emptyList()
        else AnkiSrs.dueIds(context, allCards.map { it.id })
    }
    val dueCountBySubject = remember(subjects, srsChanges) {
        subjects.associate { s ->
            s.name to AnkiSrs.dueCount(context, s.topics.flatMap { it.cards }.map { it.id })
        }
    }

    var selectedSubject by remember { mutableStateOf(ANKI_ALL) }
    var selectedTopic by remember { mutableStateOf(ANKI_ALL) }
    var wrongOnlyMode by remember { mutableStateOf(wrongOnly) }
    var dueOnlyMode by remember { mutableStateOf(false) }
    var sessionStarted by remember { mutableStateOf(false) }

    val topicsForSubject = remember(subjects, selectedSubject) {
        subjects.firstOrNull { it.name == selectedSubject }?.topics.orEmpty()
    }

    val filteredCards = remember(allCards, selectedSubject, selectedTopic) {
        allCards.filter { card ->
            (selectedSubject == ANKI_ALL || card.subject == selectedSubject) &&
                (selectedTopic == ANKI_ALL || card.topic == selectedTopic)
        }
    }

    val mastered = stats.masteredFlashcardIds
    val unmastered = remember(filteredCards, mastered) { filteredCards.filter { it.id !in mastered } }

    var queue by remember { mutableStateOf<List<AnkiCard>>(emptyList()) }
    var currentIndex by remember { mutableStateOf(0) }
    var showAnswer by remember { mutableStateOf(false) }
    var reviewedThisSession by remember { mutableStateOf(0) }
    var newlyMasteredThisSession by remember { mutableStateOf(0) }
    var finished by remember { mutableStateOf(false) }

    fun buildQueueFor(subject: String, topic: String, wrong: Boolean, due: Boolean = false): List<AnkiCard> {
        val masteredIds = OfflineStudyVault.statsFlow.value.masteredFlashcardIds
        val wrongIds = OfflineStudyVault.statsFlow.value.wrongFlashcardIds
        if (due) {
            val dueNow = AnkiSrs.dueIds(context, allCards.map { it.id }).toSet()
            return allCards.filter { card ->
                card.id in dueNow &&
                    (subject == ANKI_ALL || card.subject == subject) &&
                    (topic == ANKI_ALL || card.topic == topic)
            }.sortedBy { AnkiSrs.state(context, it.id)?.dueAtMillis ?: Long.MAX_VALUE }
        }
        return if (wrong) {
            allCards.filter { it.id in wrongIds && it.id !in masteredIds }
        } else {
            // SRS scheduling: a reviewed card leaves the general queue
            // until its due date arrives (vault mastery still removes
            // it outright, exactly as before).
            val srsStates = AnkiSrs.states(context, allCards.map { it.id })
            val now = System.currentTimeMillis()
            allCards.filter { card ->
                (subject == ANKI_ALL || card.subject == subject) &&
                    (topic == ANKI_ALL || card.topic == topic) &&
                    card.id !in masteredIds &&
                    (srsStates[card.id] == null || srsStates[card.id]!!.dueAtMillis <= now)
            }
        }
    }

    fun startSession(subject: String, topic: String, wrong: Boolean, due: Boolean = false) {
        selectedSubject = subject
        selectedTopic = topic
        wrongOnlyMode = wrong
        dueOnlyMode = due
        queue = buildQueueFor(subject, topic, wrong, due)
        currentIndex = 0
        showAnswer = false
        reviewedThisSession = 0
        newlyMasteredThisSession = 0
        finished = false
        sessionStarted = true
    }

    fun backToPicker() {
        sessionStarted = false
        wrongOnlyMode = false
        dueOnlyMode = false
        queue = emptyList()
        currentIndex = 0
        showAnswer = false
        reviewedThisSession = 0
        newlyMasteredThisSession = 0
        finished = false
    }

    // Wrong-only entry (e.g. from the mistakes screen) starts directly; otherwise
    // the subject picker is the default state — never a mixed all-subjects session.
    LaunchedEffect(loadedSet) {
        if (loadedSet != null && wrongOnly && !sessionStarted) {
            startSession(ANKI_ALL, ANKI_ALL, true)
        }
    }

    // Changing the subject/topic filter inside a session starts a fresh session.
    var lastSelection by remember { mutableStateOf(selectedSubject to selectedTopic) }
    LaunchedEffect(selectedSubject, selectedTopic) {
        if (lastSelection == selectedSubject to selectedTopic) return@LaunchedEffect
        lastSelection = selectedSubject to selectedTopic
        if (sessionStarted && !wrongOnlyMode && !dueOnlyMode) {
            queue = buildQueueFor(selectedSubject, selectedTopic, false)
            currentIndex = 0
            showAnswer = false
            reviewedThisSession = 0
            newlyMasteredThisSession = 0
            finished = false
        }
    }

    fun rebuildSession() {
        queue = buildQueueFor(selectedSubject, selectedTopic, wrongOnlyMode, dueOnlyMode)
        currentIndex = 0
        showAnswer = false
        reviewedThisSession = 0
        newlyMasteredThisSession = 0
        finished = false
    }

    Scaffold(
        topBar = {
            // Slim chrome (1.2.7): 48dp sub-bar instead of the 64dp block.
            SlimSubBar(onBack = onBack) {
                Text("البطاقات الذكية - Anki", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            if (loadedSet == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaroonPrimary)
                }
            } else if (!sessionStarted) {
                AnkiSubjectPicker(
                    subjects = subjects,
                    allCards = allCards,
                    mastered = mastered,
                    wrongCount = stats.wrongFlashcardIds.size,
                    dueCount = dueIds.size,
                    dueCountBySubject = dueCountBySubject,
                    onSubjectSelected = { name -> startSession(name, ANKI_ALL, false) },
                    onMixedSelected = { startSession(ANKI_ALL, ANKI_ALL, false) },
                    onWrongSelected = { startSession(ANKI_ALL, ANKI_ALL, true) },
                    onDueSelected = { startSession(ANKI_ALL, ANKI_ALL, false, due = true) }
                )
            } else {
                if (wrongOnlyMode) {
                    Text(
                        text = "مراجعة البطاقات الخاطئة",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ErrorRed,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                } else if (dueOnlyMode) {
                    Text(
                        text = "مراجعة مستحقات اليوم",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaroonPrimary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                } else {
                    AnkiFilterBar(
                        subjects = subjects.map { it.name },
                        topics = topicsForSubject.map { it.name },
                        selectedSubject = selectedSubject,
                        selectedTopic = selectedTopic,
                        onSubjectSelected = { name ->
                            selectedSubject = name
                            selectedTopic = ANKI_ALL
                        },
                        onTopicSelected = { selectedTopic = it }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (allCards.isEmpty()) {
                    AnkiEmptyState(
                        title = "لا توجد بطاقات متاحة.",
                        message = "لم يتم تحميل مجموعة البطاقات بعد. تحقق من الاتصال بالإنترنت، أو ستظهر البطاقات المحفوظة عند توفرها."
                    )
                } else if (unmastered.isEmpty() && !finished) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(Icons.Default.Style, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(64.dp))
                            Text("لقد أتقنت جميع البطاقات المتاحة.", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaroonPrimary, textAlign = TextAlign.Center)
                            Button(
                                onClick = onBack,
                                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                                modifier = Modifier.height(50.dp),
                                shape = MaterialTheme.shapes.medium) {
                                Text("رجوع")
                            }
                        }
                    }
                } else if (queue.isEmpty()) {
                    // SRS: everything in scope is scheduled for later —
                    // that is not "all mastered", so say so plainly.
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(Icons.Default.Style, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(64.dp))
                            Text("لا توجد بطاقات مستحقة الآن في هذا النطاق.", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaroonPrimary, textAlign = TextAlign.Center)
                            Text("البطاقات التي راجعتها ستعود إليك في مواعيدها المجدولة حسب تقييمك لها.", fontSize = 14.sp, textAlign = TextAlign.Center)
                            Button(
                                onClick = { backToPicker() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                                modifier = Modifier.height(50.dp),
                                shape = MaterialTheme.shapes.medium) {
                                Text("اختيار مادة أخرى")
                            }
                        }
                    }
                } else if (finished || currentIndex >= queue.size) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(Icons.Default.Style, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(64.dp))
                            Text("أتممت مراجعة جميع بطاقات Anki بنجاح!", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaroonPrimary, textAlign = TextAlign.Center)
                            Text("راجعت في هذه الجلسة: $reviewedThisSession", fontSize = 15.sp)
                            Text("أتقنتها في هذه الجلسة: $newlyMasteredThisSession", fontSize = 15.sp, color = SuccessGreen)
                            Text("بطاقات متبقية في القائمة: ${(queue.size - currentIndex).coerceAtLeast(0)}", fontSize = 15.sp)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = { rebuildSession() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                                    modifier = Modifier.weight(1f).height(50.dp),
                                    shape = MaterialTheme.shapes.medium) {
                                    Text("إعادة الجلسة")
                                }
                                Button(
                                    onClick = { backToPicker() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.weight(1f).height(50.dp),
                                    shape = MaterialTheme.shapes.medium) {
                                    Text("اختيار مادة أخرى", color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                } else {
                    val card = queue[currentIndex.coerceAtMost(queue.lastIndex)]
                    LinearProgressIndicator(
                        progress = { if (queue.isEmpty()) 0f else (currentIndex.toFloat() / queue.size.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = MaroonPrimary,
                        trackColor = MaroonPrimary.copy(alpha = 0.2f),
                    )
                    Text(
                        text = "${card.subject} — ${card.topic}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        textAlign = TextAlign.Center
                    )
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 16.dp).clickable { showAnswer = !showAnswer }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp)
                                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (!showAnswer) "السؤال (انقر لعرض الإجابة):" else "الإجابة النموذجية:",
                                fontWeight = FontWeight.Bold,
                                color = MaroonPrimary,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (!showAnswer) card.front else card.back,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            AnimatedVisibility(visible = showAnswer) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    card.src?.let { src ->
                                        Spacer(modifier = Modifier.height(16.dp))
                                        HorizontalDivider()
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            "المصدر: $src",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (showAnswer) {
                        // SRS grading (Anki-style, Nov 2026): every grade
                        // schedules this card's next due date in the local
                        // SRS store. The vault lists keep their old meaning
                        // for the other screens: إعادة = wrong (+unmaster),
                        // صعب/جيد = clear wrong, سهل = mastered.
                        fun gradeCurrent(grade: AnkiGrade) {
                            AnkiSrs.grade(context, card.id, grade)
                            when (grade) {
                                AnkiGrade.AGAIN -> {
                                    OfflineStudyVault.addFlashcardWrong(context, card.id)
                                    if (OfflineStudyVault.statsFlow.value.masteredFlashcardIds.contains(card.id)) {
                                        OfflineStudyVault.toggleFlashcardMastered(context, card.id)
                                    }
                                    queue = queue + card
                                }
                                AnkiGrade.HARD, AnkiGrade.GOOD ->
                                    OfflineStudyVault.removeFlashcardWrong(context, card.id)
                                AnkiGrade.EASY -> {
                                    OfflineStudyVault.addFlashcardMastered(context, card.id)
                                    newlyMasteredThisSession++
                                }
                            }
                            showAnswer = false
                            currentIndex++
                            reviewedThisSession++
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { gradeCurrent(AnkiGrade.AGAIN) },
                                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = MaterialTheme.shapes.medium) {
                                Text("إعادة", fontSize = 13.sp)
                            }
                            Button(
                                onClick = { gradeCurrent(AnkiGrade.HARD) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = MaterialTheme.shapes.medium) {
                                Text("صعب", fontSize = 13.sp)
                            }
                            Button(
                                onClick = { gradeCurrent(AnkiGrade.GOOD) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = MaterialTheme.shapes.medium) {
                                Text("جيد", fontSize = 13.sp)
                            }
                            Button(
                                onClick = { gradeCurrent(AnkiGrade.EASY) },
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = MaterialTheme.shapes.medium) {
                                Text("سهل", fontSize = 13.sp)
                            }
                        }
                    } else {
                        Button(
                            onClick = { showAnswer = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = MaterialTheme.shapes.medium) {
                            Text("إظهار الإجابة", fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnkiSubjectPicker(
    subjects: List<AnkiSubject>,
    allCards: List<AnkiCard>,
    mastered: Set<Int>,
    wrongCount: Int,
    dueCount: Int,
    dueCountBySubject: Map<String, Int>,
    onSubjectSelected: (String) -> Unit,
    onMixedSelected: () -> Unit,
    onWrongSelected: () -> Unit,
    onDueSelected: () -> Unit
) {
    val totalCards = allCards.size
    val totalUnmastered = allCards.count { it.id !in mastered }
    Column(modifier = Modifier.fillMaxSize()) {
        Text("اختر مادة للمراجعة", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaroonPrimary)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "اختر مادة لمراجعة بطاقاتها فقط، أو راجع بطاقاتك الخاطئة المحفوظة.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(
                    onClick = { if (dueCount > 0) onDueSelected() },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (dueCount > 0) MaroonPrimary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("مستحق اليوم", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                            Text(
                                if (dueCount > 0) "$dueCount بطاقة حان موعد مراجعتها المجدولة" else "لا بطاقات مستحقة اليوم — راجع لاحقًا",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        if (dueCount > 0) {
                            Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            items(subjects) { subject ->
                val subjectCards = subject.topics.flatMap { it.cards }
                val unmasteredCount = subjectCards.count { it.id !in mastered }
                val subjectDue = dueCountBySubject[subject.name] ?: 0
                Card(
                    onClick = { onSubjectSelected(subject.name) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (subjectIconRes(subject.name) != null) {
                            SubjectIcon(subject.name, Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(subject.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                            Text(
                                "${subjectCards.size} بطاقة • $unmasteredCount غير متقنة" +
                                    if (subjectDue > 0) " • $subjectDue مستحقة اليوم" else "",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(16.dp))
                    }
                }
            }
            item {
                Card(
                    onClick = onMixedSelected,
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaroonPrimary.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("الكل (مختلط)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                            Text(
                                "$totalCards بطاقة • $totalUnmastered غير متقنة",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(16.dp))
                    }
                }
            }
            item {
                Card(
                    onClick = { if (wrongCount > 0) onWrongSelected() },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (wrongCount > 0) ErrorRed.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("راجع بطاقاتي الخاطئة", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ErrorRed)
                            Text(
                                if (wrongCount > 0) "$wrongCount بطاقة خاطئة بانتظار المراجعة" else "لا توجد بطاقات خاطئة حاليًا",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        if (wrongCount > 0) {
                            Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnkiFilterBar(
    subjects: List<String>,
    topics: List<String>,
    selectedSubject: String,
    selectedTopic: String,
    onSubjectSelected: (String) -> Unit,
    onTopicSelected: (String) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AnkiDropdown(
            label = "المادة",
            options = listOf(ANKI_ALL) + subjects,
            selected = selectedSubject,
            onSelected = onSubjectSelected,
            modifier = Modifier.weight(1f)
        )
        AnkiDropdown(
            label = "الموضوع",
            options = listOf(ANKI_ALL) + topics,
            selected = selectedTopic,
            onSelected = onTopicSelected,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnkiDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedCard(
            onClick = { expanded = true },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text(selected, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaroonPrimary)
            }
        }
        // Plain scrollable Column, not a LazyColumn: a lazy list inside a
        // dropdown menu is the fragile combination on this exact path
        // (subject selection) — the menu measures its popup content
        // loosely, and a lazy list inside it is the one construct that
        // can blow up on some devices. A fixed-height scroll column
        // cannot.
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, fontSize = 14.sp) },
                        onClick = {
                            expanded = false
                            onSelected(option)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AnkiEmptyState(title: String, message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(Icons.Default.Style, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(64.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
            Text(message, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}
