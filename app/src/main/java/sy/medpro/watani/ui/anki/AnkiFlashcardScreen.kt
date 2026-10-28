package sy.medpro.watani.ui.anki

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
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
import sy.medpro.watani.ui.theme.MaroonPrimary
import sy.medpro.watani.ui.theme.SuccessGreen
import sy.medpro.watani.ui.theme.ErrorRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnkiFlashcardScreen(onBack: () -> Unit) {
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

    var selectedSubject by remember { mutableStateOf(ANKI_ALL) }
    var selectedTopic by remember { mutableStateOf(ANKI_ALL) }

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
    var initialized by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(loadedSet, filteredCards) {
        if (loadedSet != null && !initialized) {
            queue = unmastered
            currentIndex = 0
            initialized = true
        }
    }

    // Changing the subject/topic filter starts a fresh session over the new queue.
    var lastSelection by remember { mutableStateOf(selectedSubject to selectedTopic) }
    LaunchedEffect(selectedSubject, selectedTopic) {
        if (lastSelection == selectedSubject to selectedTopic) return@LaunchedEffect
        lastSelection = selectedSubject to selectedTopic
        if (initialized) {
            queue = filteredCards.filter { it.id !in OfflineStudyVault.statsFlow.value.masteredFlashcardIds }
            currentIndex = 0
            showAnswer = false
            reviewedThisSession = 0
            newlyMasteredThisSession = 0
            finished = false
        }
    }

    fun rebuildSession() {
        val filterCards = allCards.filter { card ->
            (selectedSubject == ANKI_ALL || card.subject == selectedSubject) &&
                (selectedTopic == ANKI_ALL || card.topic == selectedTopic)
        }
        queue = filterCards.filter { it.id !in OfflineStudyVault.statsFlow.value.masteredFlashcardIds }
        currentIndex = 0
        showAnswer = false
        reviewedThisSession = 0
        newlyMasteredThisSession = 0
        finished = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("البطاقات الذكية - Anki", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = MaterialTheme.colorScheme.onPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaroonPrimary, titleContentColor = MaterialTheme.colorScheme.onPrimary)
            )
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
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("رجوع")
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
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("إعادة الجلسة")
                                }
                                Button(
                                    onClick = onBack,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.weight(1f).height(50.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("العودة للقائمة", color = MaterialTheme.colorScheme.onSurface)
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
                            modifier = Modifier.fillMaxSize().padding(24.dp),
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
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = {
                                    showAnswer = false
                                    queue = queue + card
                                    currentIndex++
                                    reviewedThisSession++
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("صعب / إعادة")
                            }
                            Button(
                                onClick = {
                                    OfflineStudyVault.addFlashcardMastered(context, card.id)
                                    showAnswer = false
                                    currentIndex++
                                    reviewedThisSession++
                                    newlyMasteredThisSession++
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("سهل / تثبيت")
                            }
                        }
                    } else {
                        Button(
                            onClick = { showAnswer = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("إظهار الإجابة", fontSize = 16.sp)
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
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(options) { option ->
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
