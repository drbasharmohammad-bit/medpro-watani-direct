package sy.medpro.watani.ui.osce

/**
 * Pure scoring for the OSCE procedures ordering trainer (build 1.2.6).
 * No Android imports — unit-tested off-device in UnifiedFixesTest.
 *
 * The student arranges the shuffled checklist steps; [studentOrders]
 * is the sequence of step `order` values in the positions the student
 * placed them (position 0 first). [correctOrders] is the true
 * sequence (position 0 first). A position is exact when both agree.
 * Critical steps (patient-safety steps flagged in the content pack)
 * that do NOT land on their exact position are reported separately —
 * the UI flags them explicitly as «خطوة حرجة».
 */
data class OrderingScore(
    val totalSteps: Int,
    val exactPositions: Int,
    val percent: Int,
    /** Per-position verdict, same length as the student's sequence. */
    val positionCorrect: List<Boolean>,
    /** `order` values of critical steps placed at a wrong position. */
    val missedCriticalOrders: List<Int>
)

object ProcedureScoring {

    /**
     * @param correctOrders true order-value sequence, position 0 first.
     * @param studentOrders the student's placed order-value sequence.
     * @param criticalOrders the set of order values flagged critical.
     */
    fun score(
        correctOrders: List<Int>,
        studentOrders: List<Int>,
        criticalOrders: Set<Int>
    ): OrderingScore {
        val total = correctOrders.size
        val positionCorrect = correctOrders.indices.map { i ->
            studentOrders.getOrNull(i) == correctOrders[i]
        }
        val exact = positionCorrect.count { it }
        val percent = if (total > 0) exact * 100 / total else 0
        val missedCritical = correctOrders.indices
            .filter { i -> !positionCorrect[i] && correctOrders[i] in criticalOrders }
            .map { correctOrders[it] }
        return OrderingScore(
            totalSteps = total,
            exactPositions = exact,
            percent = percent,
            positionCorrect = positionCorrect,
            missedCriticalOrders = missedCritical
        )
    }

    /** True when the student's sequence is a full permutation of the checklist. */
    fun isCompleteArrangement(correctOrders: List<Int>, studentOrders: List<Int>): Boolean =
        studentOrders.size == correctOrders.size &&
            studentOrders.toSet() == correctOrders.toSet()
}
