package sy.medpro.watani.ui.dosing

/**
 * Pure scoring for the dosing-drills sessions (build 1.2.6).
 * No Android imports — unit-tested off-device in UnifiedFixesTest.
 */

/** One answered drill inside a session. */
data class DrillAnswer(
    val drillId: String,
    val chosenIndex: Int,
    val correctIndex: Int
) {
    val isCorrect: Boolean get() = chosenIndex == correctIndex
}

/** Aggregate result of a finished session. */
data class DosingSessionScore(
    val total: Int,
    val correct: Int,
    val percent: Int,
    /** Ids of the drills answered wrongly, in session order. */
    val missedDrillIds: List<String>
)

object DosingScoring {

    fun score(answers: List<DrillAnswer>): DosingSessionScore {
        val total = answers.size
        val correct = answers.count { it.isCorrect }
        val percent = if (total > 0) correct * 100 / total else 0
        return DosingSessionScore(
            total = total,
            correct = correct,
            percent = percent,
            missedDrillIds = answers.filter { !it.isCorrect }.map { it.drillId }
        )
    }
}
