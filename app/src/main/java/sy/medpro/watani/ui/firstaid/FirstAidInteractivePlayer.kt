package sy.medpro.watani.ui.firstaid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.data.FirstAidBranchingCaseDto
import sy.medpro.watani.data.FirstAidComparisonTableDto
import sy.medpro.watani.data.FirstAidInteractiveDto
import sy.medpro.watani.data.FirstAidOrderingDto
import sy.medpro.watani.ui.theme.NotoKufiFontFamily
import sy.medpro.watani.ui.theme.NotoSansArabicFontFamily

/**
 * Step-2 interactives player (merge phase 5): the three exercise
 * kinds of interactive.json — branching cases, ordering exercises and
 * comparison tables — rendered with the section design. Branching
 * options whose text was completed at packaging render in full (the
 * FA2-CARD-01 case: 3 text options at every step); a still-null text
 * is skipped rather than crashing (DTO contract).
 */
@Composable
fun FirstAidInteractivePlayer(
    interactive: FirstAidInteractiveDto,
    onClose: () -> Unit
) {
    val branching = interactive.branchingCases.orEmpty()
    val ordering = interactive.orderingExercises.orEmpty()
    val comparisons = interactive.comparisonTables.orEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(faBackground()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FaScreenHeader(
                title = "التفاعليات",
                subtitle = "حالات متفرعة وتمارين ترتيب وجداول مقارنة من نص الوحدة",
                onBack = onClose
            )
        }
        branching.forEach { case ->
            item { FaBranchingCase(case) }
        }
        ordering.forEach { exercise ->
            item { FaOrderingExercise(exercise) }
        }
        comparisons.forEach { table ->
            item { FaComparisonTable(table) }
        }
        if (branching.isEmpty() && ordering.isEmpty() && comparisons.isEmpty()) {
            item {
                Text(
                    "لا تفاعليات في هذه الوحدة.",
                    fontFamily = NotoSansArabicFontFamily, fontSize = 13.sp, color = faMuted()
                )
            }
        }
    }
}

/** Branching case: one step at a time; a wrong pick shows its own
 * feedback and lets the student retry, the correct pick shows the
 * step's feedback_correct and advances. */
@Composable
private fun FaBranchingCase(case: FirstAidBranchingCaseDto) {
    val steps = case.steps.orEmpty()
    var stepIndex by remember(case.id) { mutableIntStateOf(0) }
    var picked by remember(case.id) { mutableStateOf<Int?>(null) }
    var done by remember(case.id) { mutableStateOf(false) }
    FaCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FaChip("حالة متفرعة", faGoldTint(), faGoldText())
            Text(
                case.title ?: "حالة سريرية",
                fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 15.5.sp, color = faHeading()
            )
            if (!case.setting.isNullOrBlank()) {
                Text(
                    case.setting, fontFamily = NotoSansArabicFontFamily,
                    fontSize = 12.5.sp, lineHeight = 21.sp, color = faMuted()
                )
            }
            if (done || steps.isEmpty()) {
                Text(
                    "أكملت الحالة — كل الخطوات صحيحة.",
                    fontFamily = NotoSansArabicFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 13.sp, color = faGreenText()
                )
            } else {
            val step = steps[stepIndex]
            Text(
                "الخطوة ${step.step ?: stepIndex + 1} من ${steps.size}",
                fontFamily = NotoSansArabicFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp, color = faGoldText()
            )
            Text(
                step.prompt.orEmpty(),
                fontFamily = NotoSansArabicFontFamily,
                fontSize = 14.sp, lineHeight = 25.sp, color = faInk()
            )
            val options = step.options.orEmpty().filter { !it.text.isNullOrBlank() }
            options.forEachIndexed { i, option ->
                val isPicked = picked == i
                val border = when {
                    isPicked && option.correct == true -> faGreenText()
                    isPicked -> faRedText()
                    else -> faLine()
                }
                val bg = when {
                    isPicked && option.correct == true -> faGreenTint()
                    isPicked -> faRedTint()
                    else -> faNeutralOptionBg()
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(15.dp))
                        .background(bg)
                        .border(1.5.dp, border, RoundedCornerShape(15.dp))
                        .clickable { if (picked == null || options[picked!!].correct != true) picked = i }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        option.text.orEmpty(),
                        fontFamily = NotoSansArabicFontFamily,
                        fontSize = 13.3.sp, lineHeight = 22.sp, color = faInk()
                    )
                    if (isPicked && !option.feedback.isNullOrBlank()) {
                        Text(
                            option.feedback,
                            fontFamily = NotoSansArabicFontFamily,
                            fontSize = 12.3.sp, lineHeight = 21.sp,
                            color = if (option.correct == true) faGreenText() else faRedText()
                        )
                    }
                }
            }
            val pickedOption = picked?.let { options.getOrNull(it) }
            if (pickedOption?.correct == true) {
                if (!step.feedbackCorrect.isNullOrBlank()) {
                    Text(
                        step.feedbackCorrect,
                        fontFamily = NotoSansArabicFontFamily,
                        fontSize = 12.3.sp, lineHeight = 21.sp, color = faGreenText()
                    )
                }
                FaPrimaryButton(
                    text = if (stepIndex < steps.lastIndex) "الخطوة التالية" else "إنهاء الحالة",
                    onClick = {
                        if (stepIndex < steps.lastIndex) {
                            stepIndex++
                            picked = null
                        } else {
                            done = true
                        }
                    }
                )
            }
            }
        }
    }
}

/** Ordering exercise: tap shuffled steps in the right order, tap a
 * chosen step to remove it, then check against correct_order. */
@Composable
private fun FaOrderingExercise(exercise: FirstAidOrderingDto) {
    val shuffled = remember(exercise.id) { exercise.shuffledSteps.orEmpty() }
    val correct = exercise.correctOrder.orEmpty()
    var chosen by remember(exercise.id) { mutableStateOf<List<String>>(emptyList()) }
    var verdict by remember(exercise.id) { mutableStateOf<Boolean?>(null) }
    FaCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FaChip("تمرين ترتيب", faGoldTint(), faGoldText())
            Text(
                exercise.title ?: "رتّب الخطوات",
                fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 15.5.sp, color = faHeading()
            )
            if (!exercise.instruction.isNullOrBlank()) {
                Text(
                    exercise.instruction, fontFamily = NotoSansArabicFontFamily,
                    fontSize = 12.5.sp, lineHeight = 21.sp, color = faMuted()
                )
            }
            if (chosen.isNotEmpty()) {
                Text(
                    "ترتيبك الحالي (انقر خطوة لإزالتها):",
                    fontFamily = NotoSansArabicFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 12.sp, color = faAccentText()
                )
                chosen.forEachIndexed { i, stepText ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(faChipCodeBg())
                            .clickable { chosen = chosen - stepText; verdict = null }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Text(
                            "${i + 1}.", fontFamily = NotoSansArabicFontFamily,
                            fontWeight = FontWeight.Bold, fontSize = 13.sp, color = faAccentText()
                        )
                        Text(
                            stepText, fontFamily = NotoSansArabicFontFamily,
                            fontSize = 13.sp, lineHeight = 21.sp, color = faInk()
                        )
                    }
                }
            }
            val remaining = shuffled.filter { it !in chosen }
            if (remaining.isNotEmpty()) {
                Text(
                    "اختر الخطوة التالية:",
                    fontFamily = NotoSansArabicFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 12.sp, color = faAccentText()
                )
                remaining.forEach { stepText ->
                    Box2StepOption(stepText) {
                        chosen = chosen + stepText
                        verdict = null
                    }
                }
            }
            if (chosen.size == correct.size && correct.isNotEmpty()) {
                FaPrimaryButton(
                    text = "تحقق من الترتيب",
                    onClick = { verdict = chosen == correct }
                )
            }
            when (verdict) {
                true -> Text(
                    "ترتيب صحيح — أحسنت.",
                    fontFamily = NotoSansArabicFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 13.sp, color = faGreenText()
                )
                false -> Text(
                    "الترتيب غير صحيح — راجع الخطوات وحاول مجددًا.",
                    fontFamily = NotoSansArabicFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 13.sp, color = faRedText()
                )
                null -> Unit
            }
        }
    }
}

@Composable
private fun Box2StepOption(text: String, onClick: () -> Unit) {
    Text(
        text,
        fontFamily = NotoSansArabicFontFamily, fontSize = 13.sp, lineHeight = 21.sp,
        color = faInk(),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(faNeutralOptionBg())
            .border(1.5.dp, faLine(), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp)
    )
}

/** Comparison table from interactive.json, rendered with the same
 * RTL table as the reference renderer. */
@Composable
private fun FaComparisonTable(table: FirstAidComparisonTableDto) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FaChip("جدول مقارنة", faGoldTint(), faGoldText())
            Text(
                table.title.orEmpty(),
                fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 14.5.sp, color = faHeading()
            )
        }
        val header = table.columns.orEmpty().map { listOf(FaInlineSpan(it)) }
        val rows = table.rows.orEmpty().map { row ->
            row.cells.orEmpty().map { listOf(FaInlineSpan(it)) }
        }
        if (header.isNotEmpty()) {
            FaMdTable(header = header, rows = rows)
        }
        if (!table.note.isNullOrBlank()) {
            Text(
                table.note, fontFamily = NotoSansArabicFontFamily,
                fontSize = 11.5.sp, lineHeight = 18.sp, color = faMuted()
            )
        }
    }
}
