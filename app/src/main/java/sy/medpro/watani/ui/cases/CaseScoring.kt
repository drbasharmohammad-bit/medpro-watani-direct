package sy.medpro.watani.ui.cases

import sy.medpro.watani.data.ClinicalCaseDto
import kotlin.math.round

/**
 * A critical fact the student never uncovered, with everything the report
 * card needs to teach from the miss: what it was, why it mattered, and the
 * ids of the questions, examinations or tests that would have revealed it.
 */
data class MissedCriticalFact(
    val factId: String?,
    val label: String?,
    val whyCritical: String?,
    val revealerIds: List<String>
)

/**
 * Full breakdown of the composite case score (out of 100), line by line,
 * so the report card can show every component and every deduction.
 *
 * Components:
 * - History (40): essential coverage 25 plus red-flag coverage 15, or a
 *   single merged 40 when the case has no red-flag questions at all.
 * - Examinations and tests (20): key-item coverage 15 plus efficiency 5.
 * - Diagnosis (40): correct is always 40, wrong is 0.
 */
data class CaseScore(
    val historyEssentialPoints: Double,
    val historyRedFlagPoints: Double,
    val historyMerged: Boolean,
    val essentialAsked: Int,
    val essentialTotal: Int,
    val redFlagAsked: Int,
    val redFlagTotal: Int,
    val distractorsAsked: Int,
    val freeDistractors: Int,
    val distractorPenalty: Double,
    val historyTotal: Double,
    val examKeyRevealed: Int,
    val examKeyTotal: Int,
    val examCoveragePoints: Double,
    val examDistractorsRequested: Int,
    val examEfficiencyPenalty: Double,
    val examEfficiencyPoints: Double,
    val examTotal: Double,
    val diagnosisPoints: Double,
    val diagnosisCorrect: Boolean,
    val total: Double,
    val xpBase: Int,
    val xpFraction: Double,
    val xpEarned: Int,
    val missedFacts: List<MissedCriticalFact>
)

// Effective tier of an examination or test element: the explicit tier when
// present, otherwise derived from isKey (key when isKey is true, supportive
// otherwise). A derived tier is therefore never "distractor".
private fun effectiveExamTier(tier: String?, isKey: Boolean?): String =
    tier?.takeIf { it.isNotBlank() } ?: if (isKey == true) "key" else "supportive"

// Coverage ratio with the documented zero-total rule: when a category has
// no items at all there is nothing to miss, so its ratio counts as full
// (1.0). This mirrors the legacy report, which shows 100 percent for a
// section with zero key items.
private fun coverageRatio(done: Int, total: Int): Double =
    if (total <= 0) 1.0 else done.toDouble() / total.toDouble()

/**
 * Pure composite scoring for an upgraded (schema v2) virtual-patient case.
 *
 * [asked] holds the ids of history-bank questions the student asked.
 * [revealedExams] and [revealedTests] hold the ids of examination and test
 * elements whose result the student revealed. [diagnosisCorrect] is the
 * caller's verdict on the single final diagnosis choice.
 *
 * Rules (design section 5 as amended by the v2 appendix, verified against
 * the scoring simulation):
 * - History (40): when the bank contains red-flag questions, essential
 *   non-red coverage earns 25 and red-flag coverage earns 15. When the
 *   bank has no red-flag question at all, the 15 is merged into the
 *   essential component: 40 times essential coverage.
 * - Distractor deduction, taken from the history component only and never
 *   pushing it below zero, capped at -4: the free allowance is 1 asked
 *   distractor when the bank holds 2 to 3 distractors in total, and 2
 *   otherwise (the base rule of two free distractors; a bank with a single
 *   distractor therefore exempts that one). Deduction is
 *   min(4, max(0, askedDistractors - free)).
 * - Examinations and tests (20): 15 times the share of key elements
 *   revealed across both sections combined, where "key" is the
 *   element's effective tier (its documented tier when present,
 *   otherwise derived from isKey) — the approved conversion's tier
 *   corrections are scoring decisions, so the tier, not the raw
 *   legacy isKey flag, rules coverage. Plus an efficiency 5 that
 *   loses 1 point per requested distractor-tier element after the
 *   first, capped at -5.
 * - Diagnosis (40): correct always earns the full 40. The "blind correct"
 *   reduction is suspended by the v2 appendix and is NOT applied here.
 * - Critical facts: a fact counts as revealed when its revealLogic is
 *   "all" and every id in revealedBy was asked or revealed, or when its
 *   logic is anything else (including absent) and at least one revealer
 *   id was asked or revealed. A fact with an empty revealedBy can never
 *   count as revealed under either logic. Every unrevealed fact is
 *   returned in [CaseScore.missedFacts].
 * - XP: the score maps to a share of the case xpReward (default 25 when
 *   absent, matching the legacy grant): 85 and above earns 100 percent,
 *   70 to 84 earns 75 percent, 50 to 69 earns 50 percent, below 50 earns
 *   25 percent, rounded half-to-even (kotlin.math.round, i.e.
 *   Math.rint): 25 percent of 50 is 12.5 and becomes 12. Do NOT use
 *   roundToInt here: its ties go toward positive infinity, so 12.5
 *   would become 13 and the documented simulation would fail.
 */
fun computeCaseScore(
    c: ClinicalCaseDto,
    asked: Set<String>,
    revealedExams: Set<String>,
    revealedTests: Set<String>,
    diagnosisCorrect: Boolean
): CaseScore {
    val bank = c.historyBank.orEmpty()

    // ---- History (40) ----
    val redFlags = bank.filter { it.isRedFlag == true }
    val essentialNonRed = bank.filter { it.tier == "essential" && it.isRedFlag != true }
    val redFlagAsked = redFlags.count { it.id != null && asked.contains(it.id) }
    val essentialAsked = essentialNonRed.count { it.id != null && asked.contains(it.id) }
    val merged = redFlags.isEmpty()
    val essentialPoints: Double
    val redFlagPoints: Double
    val historyBase: Double
    if (merged) {
        essentialPoints = 40.0 * coverageRatio(essentialAsked, essentialNonRed.size)
        redFlagPoints = 0.0
        historyBase = essentialPoints
    } else {
        essentialPoints = 25.0 * coverageRatio(essentialAsked, essentialNonRed.size)
        redFlagPoints = 15.0 * coverageRatio(redFlagAsked, redFlags.size)
        historyBase = essentialPoints + redFlagPoints
    }

    val totalDistractors = bank.count { it.tier == "distractor" }
    val freeDistractors = if (totalDistractors in 2..3) 1 else 2
    val distractorsAsked = bank.count { it.tier == "distractor" && it.id != null && asked.contains(it.id) }
    val distractorPenalty = minOf(4, maxOf(0, distractorsAsked - freeDistractors)).toDouble()
    val historyTotal = maxOf(0.0, historyBase - distractorPenalty)

    // ---- Examinations and tests (20) ----
    val exams = c.examinations.orEmpty()
    val tests = c.tests.orEmpty()
    val examKeyTotal = exams.count { effectiveExamTier(it.tier, it.isKey) == "key" } +
        tests.count { effectiveExamTier(it.tier, it.isKey) == "key" }
    val examKeyRevealed =
        exams.count { effectiveExamTier(it.tier, it.isKey) == "key" && it.id != null && revealedExams.contains(it.id) } +
            tests.count { effectiveExamTier(it.tier, it.isKey) == "key" && it.id != null && revealedTests.contains(it.id) }
    val examCoveragePoints = 15.0 * coverageRatio(examKeyRevealed, examKeyTotal)
    val examDistractorsRequested =
        exams.count { effectiveExamTier(it.tier, it.isKey) == "distractor" && it.id != null && revealedExams.contains(it.id) } +
            tests.count { effectiveExamTier(it.tier, it.isKey) == "distractor" && it.id != null && revealedTests.contains(it.id) }
    val examEfficiencyPenalty = minOf(5, maxOf(0, examDistractorsRequested - 1)).toDouble()
    val examEfficiencyPoints = 5.0 - examEfficiencyPenalty
    val examTotal = examCoveragePoints + examEfficiencyPoints

    // ---- Diagnosis (40) ----
    val diagnosisPoints = if (diagnosisCorrect) 40.0 else 0.0

    val total = historyTotal + examTotal + diagnosisPoints

    // ---- Critical facts ----
    val revealedIds = asked + revealedExams + revealedTests
    val missedFacts = c.criticalFacts.orEmpty().filter { fact ->
        val revealers = fact.revealedBy.orEmpty()
        val revealed = if (fact.revealLogic == "all") {
            revealers.isNotEmpty() && revealers.all { revealedIds.contains(it) }
        } else {
            revealers.any { revealedIds.contains(it) }
        }
        !revealed
    }.map { fact ->
        MissedCriticalFact(
            factId = fact.factId,
            label = fact.label,
            whyCritical = fact.whyCritical,
            revealerIds = fact.revealedBy.orEmpty()
        )
    }

    // ---- XP from score ----
    val xpBase = c.xpReward ?: 25
    val xpFraction = when {
        total >= 85.0 -> 1.0
        total >= 70.0 -> 0.75
        total >= 50.0 -> 0.5
        else -> 0.25
    }
    val xpEarned = round(xpBase * xpFraction).toInt()

    return CaseScore(
        historyEssentialPoints = essentialPoints,
        historyRedFlagPoints = redFlagPoints,
        historyMerged = merged,
        essentialAsked = essentialAsked,
        essentialTotal = essentialNonRed.size,
        redFlagAsked = redFlagAsked,
        redFlagTotal = redFlags.size,
        distractorsAsked = distractorsAsked,
        freeDistractors = freeDistractors,
        distractorPenalty = distractorPenalty,
        historyTotal = historyTotal,
        examKeyRevealed = examKeyRevealed,
        examKeyTotal = examKeyTotal,
        examCoveragePoints = examCoveragePoints,
        examDistractorsRequested = examDistractorsRequested,
        examEfficiencyPenalty = examEfficiencyPenalty,
        examEfficiencyPoints = examEfficiencyPoints,
        examTotal = examTotal,
        diagnosisPoints = diagnosisPoints,
        diagnosisCorrect = diagnosisCorrect,
        total = total,
        xpBase = xpBase,
        xpFraction = xpFraction,
        xpEarned = xpEarned,
        missedFacts = missedFacts
    )
}
