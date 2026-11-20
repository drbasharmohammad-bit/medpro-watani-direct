package sy.medpro.watani.ui.firstaid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import sy.medpro.watani.data.FirstAidBankQuestionDto
import sy.medpro.watani.data.FirstAidQuestionDto
import sy.medpro.watani.ui.theme.NotoKufiFontFamily
import sy.medpro.watani.ui.theme.NotoSansArabicFontFamily

/**
 * One normalized quiz item for the First Aid player (merge phase 5).
 * [correctIndex] == null is the printed-banks case: the item ships
 * with NO printed key, so the player shows it WITHOUT auto-grading —
 * no right/wrong colours, no score, no mistakes-bank record, no
 * guessing (merge plan §4.2 / phase-5 task: الـ13 تُعرض بلا تصحيح آلي).
 */
data class FaQuizItem(
    val stem: String,
    val options: List<String>,
    val correctIndex: Int?,
    val explanation: String?,
    val optionExplanations: List<String>?,
    val tag: String?,
    /** Index inside its source list (quiz/precheck/… of the unit, or
     * the question's position in its printed test) — the mistakes
     * bank's indexInFile. */
    val sourceIndex: Int
)

fun FirstAidQuestionDto.toFaQuizItem(sourceIndex: Int): FaQuizItem = FaQuizItem(
    stem = question.orEmpty(),
    options = options.orEmpty(),
    correctIndex = correctOptionId,
    explanation = explanation,
    optionExplanations = optionExplanations,
    tag = listOfNotNull(
        discipline?.takeIf { it.isNotBlank() },
        competency?.takeIf { it.isNotBlank() },
        if (vignette == true) "حالة سريرية" else null
    ).joinToString(" · ").ifBlank { null },
    sourceIndex = sourceIndex
)

fun FirstAidBankQuestionDto.toFaQuizItem(sourceIndex: Int): FaQuizItem = FaQuizItem(
    stem = stem.orEmpty(),
    options = options.orEmpty(),
    correctIndex = key?.trim()?.uppercase()?.firstOrNull()
        ?.let { it - 'A' }?.takeIf { it in (options.orEmpty().indices) },
    explanation = explanation,
    optionExplanations = null,
    tag = "سؤال ${n ?: sourceIndex + 1}",
    sourceIndex = sourceIndex
)

/** Exit-check duration: the pack's exitCheck is "timed-mini" with no
 * duration of its own — 5 minutes is the merge plan §3.3 UI constant
 * (one editable constant for the whole section). */
const val FA_EXIT_TIME_SECONDS = 5 * 60

/**
 * The 04-quiz player: instant feedback (correct/wrong colours + the
 * explanation + the per-option «لماذا» from option_explanations),
 * progress bar, optional countdown (exit check). Wrong answers are
 * reported through [onWrong] exactly once per item — the caller wires
 * MistakesBank.recordWrong + FirstAidSrs.enqueueQuizError there
 * (merge phase 6). [onFinish] fires once when the result screen is
 * reached, with (correct, keyedTotal).
 */
@Composable
fun FirstAidQuizPlayer(
    title: String,
    subtitle: String,
    items: List<FaQuizItem>,
    timedSeconds: Int? = null,
    onWrong: (FaQuizItem) -> Unit = {},
    onFinish: (correct: Int, keyedTotal: Int) -> Unit = { _, _ -> },
    onClose: () -> Unit
) {
    var index by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var correctCount by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    var remaining by remember { mutableIntStateOf(timedSeconds ?: 0) }

    val keyedTotal = remember(items) { items.count { it.correctIndex != null } }

    // Exit-check countdown: at zero the sitting ends as-is.
    LaunchedEffect(timedSeconds, finished) {
        if (timedSeconds == null || finished) return@LaunchedEffect
        while (remaining > 0 && !finished) {
            delay(1000)
            remaining--
        }
        if (remaining <= 0) finished = true
    }
    LaunchedEffect(finished) {
        if (finished) onFinish(correctCount, keyedTotal)
    }

    Column(modifier = Modifier.fillMaxSize().background(faBackground())) {
        FaScreenHeader(title = title, subtitle = subtitle, onBack = onClose)
        if (finished || items.isEmpty()) {
            FaQuizResult(
                correct = correctCount,
                keyedTotal = keyedTotal,
                total = items.size,
                timedOut = timedSeconds != null && remaining <= 0,
                onClose = onClose
            )
        } else {

        val item = items[index]
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FaProgressBar(
                fraction = (index + 1).toFloat() / items.size.toFloat(),
                modifier = Modifier.weight(1f)
            )
            Text(
                "السؤال ${index + 1} من ${items.size}",
                fontFamily = NotoSansArabicFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp, color = FaColors.Maroon
            )
            if (timedSeconds != null) {
                FaChip(
                    text = "%d:%02d".format(remaining / 60, remaining % 60),
                    bg = if (remaining <= 60) FaColors.RedTint else FaColors.GoldTint,
                    fg = if (remaining <= 60) FaColors.Red else FaColors.GoldText
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            item {
                FaCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
                        if (!item.tag.isNullOrBlank()) {
                            FaChip(item.tag, FaColors.GoldTint, FaColors.GoldText)
                        }
                        Text(
                            item.stem,
                            fontFamily = NotoSansArabicFontFamily,
                            fontSize = 15.sp, lineHeight = 29.sp, color = faInk(),
                            modifier = Modifier.padding(top = 9.dp)
                        )
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    item.options.forEachIndexed { optIndex, option ->
                        FaOptionRow(
                            letter = faOptionLetter(optIndex),
                            text = option,
                            state = optionState(item, selected, optIndex),
                            onClick = {
                                if (selected == null) {
                                    selected = optIndex
                                    val ci = item.correctIndex
                                    if (ci != null) {
                                        if (optIndex == ci) correctCount++ else onWrong(item)
                                    }
                                }
                            }
                        )
                    }
                }
            }
            if (selected != null) {
                item { FaFeedbackPanel(item, selected!!) }
                item {
                    Column {
                        FaPrimaryButton(
                            text = if (index < items.lastIndex) "السؤال التالي" else "إنهاء وعرض النتيجة",
                            onClick = {
                                if (index < items.lastIndex) {
                                    index++
                                    selected = null
                                } else {
                                    finished = true
                                }
                            }
                        )
                        if (item.correctIndex != null && selected != item.correctIndex) {
                            Text(
                                "أخطأت في هذا السؤال — حُفظ في بنك أخطائي وستجده في مراجعاتك.",
                                fontFamily = NotoSansArabicFontFamily,
                                fontSize = 11.3.sp, color = faMuted(),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }
        }
    }
}

private enum class FaOptionState { IDLE, CHOSEN, CORRECT, WRONG, REVEALED }

private fun optionState(item: FaQuizItem, selected: Int?, optIndex: Int): FaOptionState {
    if (selected == null) return FaOptionState.IDLE
    val ci = item.correctIndex ?: return if (optIndex == selected) FaOptionState.CHOSEN else FaOptionState.IDLE
    return when {
        optIndex == ci -> FaOptionState.CORRECT
        optIndex == selected -> FaOptionState.WRONG
        else -> FaOptionState.IDLE
    }
}

@Composable
private fun FaOptionRow(letter: String, text: String, state: FaOptionState, onClick: () -> Unit) {
    val (bg, borderColor, letterBg, letterFg) = when (state) {
        FaOptionState.CORRECT -> listOf(FaColors.GreenTint, Color(0xFFBFE0CF), FaColors.Green, Color.White)
        FaOptionState.WRONG -> listOf(FaColors.RedTint, Color(0xFFEFC9BF), FaColors.Red, Color.White)
        FaOptionState.CHOSEN -> listOf(FaColors.GoldTint, FaColors.Gold, FaColors.Gold, Color.White)
        else -> listOf(Color(0xFFFBF8F3), FaColors.Line, Color.White, faMuted())
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(bg)
            .border(1.5.dp, borderColor, RoundedCornerShape(15.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.5.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(letterBg)
                .border(1.5.dp, borderColor, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                letter, fontFamily = NotoSansArabicFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp, color = letterFg
            )
        }
        Text(
            text, fontFamily = NotoSansArabicFontFamily,
            fontSize = 13.3.sp, lineHeight = 22.5.sp, color = faInk(),
            modifier = Modifier.weight(1f).padding(top = 2.dp)
        )
        if (state == FaOptionState.CORRECT || state == FaOptionState.WRONG) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (state == FaOptionState.CORRECT) FaColors.Green else FaColors.Red),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (state == FaOptionState.CORRECT) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

/** The design's feedback card: gold gradient panel with «لماذا الصح»
 * and «لماذا الخطأ» rows sourced from option_explanations, plus the
 * general explanation. Unkeyed items get an honest no-grading note. */
@Composable
private fun FaFeedbackPanel(item: FaQuizItem, selected: Int) {
    val ci = item.correctIndex
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(19.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFFFFDF6), Color(0xFFFBF4E2))))
            .border(1.dp, Color(0xFFEDDFC0), RoundedCornerShape(19.dp))
            .padding(horizontal = 15.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            when {
                ci == null -> "سؤال بلا مفتاح مطبوع"
                selected == ci -> "إجابة صحيحة"
                else -> "إجابة غير صحيحة"
            },
            fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.ExtraBold,
            fontSize = 12.8.sp,
            color = when {
                ci == null -> FaColors.GoldDeepText
                selected == ci -> FaColors.Green
                else -> FaColors.Red
            }
        )
        if (ci == null) {
            Text(
                "هذا السؤال من البنوك المطبوعة بلا مفتاح مطبوع — يُعرض كما هو بلا تصحيح آلي وبلا تخمين.",
                fontFamily = NotoSansArabicFontFamily,
                fontSize = 12.3.sp, lineHeight = 22.sp, color = Color(0xFF5C4A33)
            )
        }
        val whyCorrect = ci?.let { item.optionExplanations?.getOrNull(it) }
        if (!whyCorrect.isNullOrBlank()) {
            FaFeedbackRow(correct = true, label = "لماذا الصح:", text = stripWhyPrefix(whyCorrect))
        }
        if (ci != null && selected != ci) {
            val whyWrong = item.optionExplanations?.getOrNull(selected)
            if (!whyWrong.isNullOrBlank()) {
                FaFeedbackRow(correct = false, label = "لماذا اختيارك خطأ:", text = stripWhyPrefix(whyWrong))
            }
        }
        if (!item.explanation.isNullOrBlank() && ci != null) {
            Text(
                item.explanation,
                fontFamily = NotoSansArabicFontFamily,
                fontSize = 12.3.sp, lineHeight = 22.sp, color = Color(0xFF5C4A33)
            )
        } else if (ci == null && !item.explanation.isNullOrBlank()) {
            Text(
                item.explanation,
                fontFamily = NotoSansArabicFontFamily,
                fontSize = 12.3.sp, lineHeight = 22.sp, color = Color(0xFF5C4A33)
            )
        }
    }
}

@Composable
private fun FaFeedbackRow(correct: Boolean, label: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (correct) FaColors.Green else FaColors.Red),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (correct) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp)
            )
        }
        Text(
            text, fontFamily = NotoSansArabicFontFamily,
            fontSize = 12.3.sp, lineHeight = 22.sp, color = Color(0xFF5C4A33),
            modifier = Modifier.weight(1f)
        )
    }
}

/** option_explanations entries are prefixed «صحيح:» / «خاطئ:» in the
 * packs; the feedback icons already carry that verdict. */
private fun stripWhyPrefix(raw: String): String =
    raw.trim().removePrefix("صحيح:").removePrefix("خاطئ:").trim()

@Composable
private fun FaQuizResult(
    correct: Int,
    keyedTotal: Int,
    total: Int,
    timedOut: Boolean,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FaCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "انتهت الجلسة",
                    fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 17.sp, color = faHeading()
                )
                if (timedOut) {
                    Text(
                        "انتهى الوقت المخصص — سُجّلت نتيجتك كما هي.",
                        fontFamily = NotoSansArabicFontFamily,
                        fontSize = 12.5.sp, color = FaColors.Red
                    )
                }
                if (keyedTotal > 0) {
                    val pct = (correct * 100) / keyedTotal
                    Text(
                        "$correct من $keyedTotal إجابة صحيحة ($pct%)",
                        fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp, color = FaColors.Maroon
                    )
                    FaProgressBar(fraction = correct.toFloat() / keyedTotal.toFloat())
                }
                Text(
                    "عدد الأسئلة المعروضة: $total",
                    fontFamily = NotoSansArabicFontFamily,
                    fontSize = 12.sp, color = faMuted()
                )
            }
        }
        FaPrimaryButton(text = "عودة", onClick = onClose)
    }
}
