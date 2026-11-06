package sy.medpro.watani.ui.home

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import sy.medpro.watani.data.OfflineStudyVault
import sy.medpro.watani.data.QuestionDto
import sy.medpro.watani.data.WataniRepository
import sy.medpro.watani.data.WorkerActivationApi
import sy.medpro.watani.data.getCorrectIndices
import java.time.LocalDate

private const val DAILY_PREFS = "watani_daily_question"
private const val KEY_DATE = "date"
private const val KEY_SELECTED = "selected_index"
private const val KEY_WAS_CORRECT = "was_correct"

private val DailyQuestionAccent = Color(0xFF6A1B9A)

/**
 * بطاقة «سؤال اليوم» بالرئيسية.
 *
 * التحميل: أول قيمة غير فارغة من downloadedSectionsFlow، ثم تجميع
 * أسئلة كل قسم محمّل (topics + sessions) واختيار سؤال اليوم بفهرس
 * epochDay % total — ثابت لكل اليوم ولكل الأجهزة التي حمّلت نفس
 * الأقسام. بلا أي قسم محمّل لا تُرسم البطاقة إطلاقًا.
 *
 * غير مفعّل: السؤال والخيارات ظاهرة، والنقر لا يكشف شيئًا ولا يسجّل
 * أي إحصائية — يعرض دعوة للتفعيل فقط. مفعّل: أول اختيار يُحفظ
 * (تاريخ/مختار/صحّة) ويُسجَّل بـrecordQuestionAttempt مرة واحدة فقط
 * بنفس وسائط QuizScreen، وإعادة الدخول بنفس اليوم تعرض الحالة المحلولة.
 */
@Composable
fun DailyQuestionCard(onOpenActivation: () -> Unit) {
    val context = LocalContext.current
    var dailyPair by remember { mutableStateOf<Pair<String, QuestionDto>?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val repo = WataniRepository(context)
            val sections = repo.downloadedSectionsFlow.first { it.isNotEmpty() }
            val all = mutableListOf<Pair<String, QuestionDto>>()
            for (section in sections) {
                val envelope = repo.getSubjectEnvelope(section.sectionPath) ?: continue
                val title = envelope.subjectName?.takeIf { it.isNotBlank() } ?: section.sectionPath
                envelope.topics.orEmpty().forEach { topic ->
                    topic.questions.orEmpty().forEach { q ->
                        if (!q.text.isNullOrBlank() && !q.options.isNullOrEmpty()) all.add(title to q)
                    }
                }
                envelope.sessions.orEmpty().forEach { session ->
                    session.questions.orEmpty().forEach { q ->
                        if (!q.text.isNullOrBlank() && !q.options.isNullOrEmpty()) all.add(title to q)
                    }
                }
            }
            if (all.isNotEmpty()) {
                val index = (LocalDate.now().toEpochDay() % all.size).toInt()
                dailyPair = all[index]
            }
        } catch (_: Exception) {
        }
        loaded = true
    }

    if (!loaded) return
    val (sectionTitle, question) = dailyPair ?: return
    val options = question.options ?: emptyList()
    if (options.isEmpty()) return
    val correctIndices = getCorrectIndices(question, options)
    val correctIndex = correctIndices.firstOrNull() ?: 0

    val prefs = remember { context.getSharedPreferences(DAILY_PREFS, Context.MODE_PRIVATE) }
    val today = remember { LocalDate.now().toString() }
    var selectedIndex by remember {
        mutableStateOf(
            if (prefs.getString(KEY_DATE, null) == today) {
                prefs.getInt(KEY_SELECTED, -1).takeIf { it >= 0 }
            } else {
                null
            }
        )
    }
    var showActivationHint by remember { mutableStateOf(false) }
    val solved = selectedIndex != null

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, DailyQuestionAccent.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.HelpOutline, contentDescription = null, tint = DailyQuestionAccent, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("سؤال اليوم", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DailyQuestionAccent)
                Spacer(modifier = Modifier.width(8.dp))
                Text(sectionTitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(question.text ?: "", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 21.sp)
            Spacer(modifier = Modifier.height(10.dp))
            options.forEachIndexed { idx, option ->
                val isSelected = selectedIndex == idx
                val bg = when {
                    solved && idx == correctIndex -> MaterialTheme.colorScheme.primaryContainer
                    solved && isSelected && idx != correctIndex -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable {
                        if (solved) return@clickable
                        if (!WorkerActivationApi.isActivated(context)) {
                            // غير مفعّل: لا كشف ولا تسجيل إطلاقًا.
                            showActivationHint = true
                        } else {
                            selectedIndex = idx
                            val isCorrect = correctIndices.contains(idx)
                            prefs.edit()
                                .putString(KEY_DATE, today)
                                .putInt(KEY_SELECTED, idx)
                                .putBoolean(KEY_WAS_CORRECT, isCorrect)
                                .apply()
                            OfflineStudyVault.recordQuestionAttempt(
                                context = context,
                                subjectTitle = sectionTitle,
                                questionId = question.id ?: "${sectionTitle}_daily",
                                questionText = question.text ?: "",
                                selectedOptionText = options.getOrNull(idx) ?: "",
                                correctOptionText = options.getOrNull(correctIndex) ?: "",
                                explanation = question.explanation ?: "",
                                isCorrect = isCorrect
                            )
                        }
                    },
                    colors = CardDefaults.cardColors(containerColor = bg)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = isSelected, onClick = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(option, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            if (showActivationHint && !solved) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "الإجابة والشرح متاحان بعد تفعيل التطبيق.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onOpenActivation,
                    colors = ButtonDefaults.buttonColors(containerColor = DailyQuestionAccent),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Text("فعّل التطبيق لمعرفة الإجابة والشرح", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            if (solved) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (correctIndices.contains(selectedIndex!!)) "إجابة صحيحة!" else "إجابة غير صحيحة. الجواب الصحيح: ${options.getOrNull(correctIndex) ?: ""}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = DailyQuestionAccent
                )
                if (!question.explanation.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("الشرح: ${question.explanation}", fontSize = 13.sp, lineHeight = 20.sp)
                }
            }
        }
    }
}
