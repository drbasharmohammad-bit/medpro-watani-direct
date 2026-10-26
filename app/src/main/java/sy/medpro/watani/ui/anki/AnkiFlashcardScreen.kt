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
import sy.medpro.watani.ui.theme.MaroonPrimary
import sy.medpro.watani.ui.theme.SuccessGreen
import sy.medpro.watani.ui.theme.ErrorRed

data class FlashcardItem(val question: String, val answer: String, val explanation: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnkiFlashcardScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val flashcards = remember {
        listOf(
            FlashcardItem(
                question = "ما هي العلامة المميزة في تخطيط القلب لاحتشاء العضلة القلبية حاد STEMI؟",
                answer = "ارتفاع قطعه ST (ST Elevation)",
                explanation = "يتميز احتشاء الحاد بارتفاع قطة ST بشكل مقعر للأعلى في المساري المقابلة."
            ),
            FlashcardItem(
                question = "ما هو العلاج الإسعافي الأول في نوبة الربو الحادة الشديدة؟",
                answer = "موسحات الشعب الهوائية سريعة التأثير (Salbutamol) + كورتيزون جهازي",
                explanation = "الإعطاء الفوري للموسعات الننشقة مع الستيروئيدات القشرية يقلل التقبض القصبي."
            ),
            FlashcardItem(
                question = "ما هو السبب الأكثر شيوعاً لقصور القلب الأيمن؟",
                answer = "قصور القلب الأيسر (Left-sided heart failure)",
                explanation = "السبب الأكثر شيوعاً لفشل البطين الأيمن هو فشل البطين الأيسر الذي يؤدي لاحتقان رئوي وارتفاع توتر رئوي."
            )
        )
    }

    var currentIndex by remember { mutableStateOf(0) }
    var showAnswer by remember { mutableStateOf(false) }

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
            LinearProgressIndicator(
                progress = { (currentIndex.toFloat() / flashcards.size.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = MaroonPrimary,
                trackColor = MaroonPrimary.copy(alpha = 0.2f),
            )

            if (currentIndex < flashcards.size) {
                val card = flashcards[currentIndex]
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
                            onClick = { showAnswer = false; currentIndex++ },
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("صعب / إعادة")
                        }
                        Button(
                            onClick = { 
                                sy.medpro.watani.data.OfflineStudyVault.toggleFlashcardMastered(context, currentIndex)
                                showAnswer = false
                                currentIndex++ 
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
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(Icons.Default.Style, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(64.dp))
                        Text("أتممت مراجعة جميع بطاقات Anki بنجاح!", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaroonPrimary, textAlign = TextAlign.Center)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { currentIndex = 0; showAnswer = false },
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
            }
        }
    }
}
