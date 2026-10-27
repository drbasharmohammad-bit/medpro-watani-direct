package sy.medpro.watani.ui.anki

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.data.*
import sy.medpro.watani.ui.theme.MaroonPrimary
import sy.medpro.watani.ui.theme.SuccessGreen
import sy.medpro.watani.ui.theme.ErrorRed
import java.io.File

data class FlashcardItem(val id: Int, val question: String, val answer: String, val explanation: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnkiFlashcardScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    OfflineStudyVault.ensureInitialized(context)
    val stats by OfflineStudyVault.statsFlow.collectAsState()

    var derived by remember { mutableStateOf<List<FlashcardItem>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val dir = File(context.filesDir, "sections_cache")
        val list = mutableListOf<FlashcardItem>()
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".json") && !file.name.contains("cases") && !file.name.contains("guide") && !file.name.contains("osce")) {
                    try {
                        val text = file.readText(Charsets.UTF_8)
                        val moshi = com.squareup.moshi.Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
                        val env = moshi.adapter(SubjectEnvelope::class.java).fromJson(text)
                        env?.topics?.forEach { topic ->
                            topic.questions?.forEach { q ->
                                val qText = q.text
                                val options = q.options
                                val correctIndices = getCorrectIndices(q, options)
                                val correctAns = correctIndices.firstOrNull()?.let { options?.getOrNull(it) } ?: q.explanation
                                if (!qText.isNullOrBlank() && !correctAns.isNullOrBlank()) {
                                    list.add(
                                        FlashcardItem(
                                            id = (qText + "|" + correctAns).hashCode(),
                                            question = qText,
                                            answer = correctAns,
                                            explanation = q.explanation ?: ""
                                        )
                                    )
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
        derived = list
        loaded = true
    }

    val mastered = stats.masteredFlashcardIds
    val unmastered = remember(derived, mastered) { derived.filter { it.id !in mastered } }
    var queue by remember { mutableStateOf<List<FlashcardItem>>(emptyList()) }
    var currentIndex by remember { mutableStateOf(0) }
    var showAnswer by remember { mutableStateOf(false) }
    var reviewedThisSession by remember { mutableStateOf(0) }
    var newlyMasteredThisSession by remember { mutableStateOf(0) }
    var initialized by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(loaded) {
        if (loaded && !initialized) {
            queue = unmastered
            currentIndex = 0
            initialized = true
        }
    }

    fun rebuildSession() {
        queue = derived.filter { it.id !in OfflineStudyVault.statsFlow.value.masteredFlashcardIds }
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
            if (!loaded) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaroonPrimary)
                }
            } else if (derived.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(Icons.Default.Style, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(64.dp))
                        Text("لا توجد بطاقات متاحة.", fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
                        Text(
                            "يرجى تحميل أقسام من الشاشة الرئيسية لتوليد البطاقات من بنوك الأسئلة.",
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (unmastered.isEmpty() && !finished) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                LinearProgressIndicator(
                    progress = { if (queue.isEmpty()) 0f else (currentIndex.toFloat() / queue.size.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = MaroonPrimary,
                    trackColor = MaroonPrimary.copy(alpha = 0.2f),
                )

                val card = queue[currentIndex]
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 24.dp).clickable { showAnswer = !showAnswer }
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
                            text = if (!showAnswer) card.question else card.answer,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        AnimatedVisibility(visible = showAnswer) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(card.explanation, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), textAlign = TextAlign.Center)
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
