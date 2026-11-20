package sy.medpro.watani.ui.firstaid

import android.content.Context
import sy.medpro.watani.data.MistakesBank

/**
 * Merge phase 6 — the unified wiring of First Aid mistakes:
 *
 * Every wrong answer in ANY First Aid quiz surface (precheck /
 * training / exit check / printed banks / integration sessions) is
 * filed into the ONE personal mistakes bank via
 * [MistakesBank.recordWrong] with the plan §5.5 fields:
 * bank = "firstaid", file = the unit id (or the printed test's name),
 * questionId = null (the packs carry no per-question id), subject =
 * the unit's Arabic title, whyCorrect / whyWrong derived from the
 * pack's own option_explanations (keyed by option letter A, B, C…,
 * the mistakes-bank convention). The entry then shows in
 * MistakesBankScreen under the «First Aid - Med Pro» group and joins
 * its review sessions and SRS like every other bank.
 *
 * Additionally, a flashcard sharing the missed question's index in
 * the same unit is made due THE SAME DAY in [FirstAidSrs] — the
 * packs' own repetition rule (quiz_error_cards: "same-day").
 */
object FirstAidMistakeWiring {

    private val LETTERS = listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J")

    fun recordWrong(
        context: Context,
        file: String,
        subject: String,
        item: FaQuizItem,
        flashcardCount: Int = 0
    ) {
        val correctIndex = item.correctIndex ?: return
        if (item.stem.isBlank() || correctIndex !in item.options.indices) return
        val optionExplanations = item.optionExplanations.orEmpty()
        val whyCorrect = optionExplanations.getOrNull(correctIndex)
            ?.takeIf { it.isNotBlank() }
        val whyWrong = item.options.indices
            .filter { it != correctIndex }
            .mapNotNull { idx ->
                val text = optionExplanations.getOrNull(idx)?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                LETTERS.getOrNull(idx)?.let { it to text }
            }
            .toMap()
            .ifEmpty { null }
        MistakesBank.recordWrong(
            context = context,
            bank = "firstaid",
            file = file,
            questionId = null,
            indexInFile = item.sourceIndex,
            subject = subject,
            stem = item.stem,
            options = item.options,
            correctIndex = correctIndex,
            explanation = item.explanation.orEmpty(),
            whyCorrect = whyCorrect,
            whyWrong = whyWrong
        )
        // quiz_error_cards = "same-day": the flashcard at the missed
        // question's index (when the unit has one there) is due today.
        if (flashcardCount > 0 && item.sourceIndex in 0 until flashcardCount) {
            FirstAidSrs.enqueueQuizError(context, file, item.sourceIndex)
        }
    }
}
