package sy.medpro.watani.ui.reverse

import sy.medpro.watani.data.ReverseFeatureDto
import sy.medpro.watani.data.ReverseRoundDto
import kotlin.random.Random

// Pure scoring and helpers for «اقلبها», implementing DESIGN.md sections 2-3
// literally. No Android dependencies here so the rules stay unit-testable.

object ReversePatterns {
    const val BUILD = "build"
    const val INTRUDER = "intruder"
    const val COMPLETE = "complete"
}

object ReverseKinds {
    const val DISCRIMINATING = "discriminating"
    const val SUPPORTIVE = "supportive"
    const val INTRUDER = "intruder"
    const val DANGEROUS_INTRUDER = "dangerous_intruder"
}

object ReverseVerdicts {
    const val KEY = "key"
    const val TRUE_WEAK = "true_weak"
    const val RIVAL = "rival"
}

const val INTRUDER_HIT_POINTS = 5
const val COMPLETE_KEY_POINTS = 5
const val COMPLETE_TRUE_WEAK_POINTS = 2
const val INTRUDER_PENALTY = -1
const val DANGEROUS_INTRUDER_PENALTY = -3

fun patternLabel(pattern: String?): String = when (pattern) {
    ReversePatterns.BUILD -> "ركّب الصورة"
    ReversePatterns.INTRUDER -> "اطرِد الدخيل"
    ReversePatterns.COMPLETE -> "أكمل الناقص"
    else -> "جولة"
}

fun difficultyLabel(difficulty: Int?): String = when (difficulty) {
    1 -> "تمهيدي"
    2 -> "وطني"
    3 -> "مميِّز عالٍ"
    else -> ""
}

fun ReverseFeatureDto.isBelonging(): Boolean =
    kind == ReverseKinds.DISCRIMINATING || kind == ReverseKinds.SUPPORTIVE

fun ReverseFeatureDto.isIntruder(): Boolean =
    kind == ReverseKinds.INTRUDER || kind == ReverseKinds.DANGEROUS_INTRUDER

/**
 * Points for SELECTING this feature in a build round.
 * Belonging features use the stored points weight when present
 * (3 discriminating / 1 supportive in the bank), falling back to the
 * kind default. Intruder penalties come from kind only: -1 / -3.
 */
fun featureSelectionPoints(feature: ReverseFeatureDto): Int = when (feature.kind) {
    ReverseKinds.DISCRIMINATING -> feature.points ?: 3
    ReverseKinds.SUPPORTIVE -> feature.points ?: 1
    ReverseKinds.INTRUDER -> INTRUDER_PENALTY
    ReverseKinds.DANGEROUS_INTRUDER -> DANGEROUS_INTRUDER_PENALTY
    else -> 0
}

/** Max build score = sum of the stored weights of all belonging features. */
fun buildMaxScore(round: ReverseRoundDto): Int =
    round.features.orEmpty().filter { it.isBelonging() }.sumOf { featureSelectionPoints(it) }

data class BuildResult(
    val score: Int,
    val maxScore: Int,
    val percent: Int,
    val rating: String,
    /** Discriminating features the student did NOT select («فجوة حاسمة»). */
    val missedDiscriminating: List<ReverseFeatureDto>,
    val selectedIntruders: List<ReverseFeatureDto>
)

/** Verbal rating for a build round, by percentage of the max score. */
fun buildRating(percent: Int): String = when {
    percent >= 90 -> "صورة مكتملة"
    percent >= 70 -> "صورة جيدة — راجع الفجوات"
    percent >= 50 -> "صورة ناقصة"
    else -> "أعد بناء الصورة"
}

/**
 * Scores a finished build round. selectedIndices are indices into
 * round.features. Missing a belonging feature scores 0 for it; the
 * total never drops below zero.
 */
fun scoreBuild(round: ReverseRoundDto, selectedIndices: Set<Int>): BuildResult {
    val features = round.features.orEmpty()
    var raw = 0
    features.forEachIndexed { index, feature ->
        if (index in selectedIndices) raw += featureSelectionPoints(feature)
    }
    val max = buildMaxScore(round)
    val score = maxOf(0, raw)
    val percent = if (max > 0) (score * 100) / max else 0
    return BuildResult(
        score = score,
        maxScore = max,
        percent = percent,
        rating = buildRating(percent),
        missedDiscriminating = features.filterIndexed { index, feature ->
            feature.kind == ReverseKinds.DISCRIMINATING && index !in selectedIndices
        },
        selectedIntruders = features.filterIndexed { index, feature ->
            feature.isIntruder() && index in selectedIndices
        }
    )
}

/** Index of the single intruder feature in an intruder round, or -1. */
fun intruderIndex(round: ReverseRoundDto): Int =
    round.features.orEmpty().indexOfFirst { it.isIntruder() }

/** Intruder round: hitting the intruder = +5, anything else = 0. */
fun scoreIntruder(round: ReverseRoundDto, pickedIndex: Int): Int =
    if (pickedIndex >= 0 && pickedIndex == intruderIndex(round)) INTRUDER_HIT_POINTS else 0

fun intruderMaxScore(): Int = INTRUDER_HIT_POINTS

/** Complete round: key = +5, true_weak = +2, rival (or unknown) = 0. */
fun scoreComplete(round: ReverseRoundDto, pickedIndex: Int): Int {
    val option = round.options.orEmpty().getOrNull(pickedIndex) ?: return 0
    return when (option.verdict) {
        ReverseVerdicts.KEY -> COMPLETE_KEY_POINTS
        ReverseVerdicts.TRUE_WEAK -> COMPLETE_TRUE_WEAK_POINTS
        else -> 0
    }
}

fun completeMaxScore(): Int = COMPLETE_KEY_POINTS

fun keyOptionIndex(round: ReverseRoundDto): Int =
    round.options.orEmpty().indexOfFirst { it.verdict == ReverseVerdicts.KEY }

/**
 * Deterministic shuffled display order for a round, seeded by the round
 * id so the order is fixed for the whole lifetime of the round and does
 * not change on recomposition or re-entry.
 */
fun seededShuffledIndices(seedKey: String, size: Int): List<Int> {
    if (size <= 1) return (0 until size).toList()
    return (0 until size).toList().shuffled(Random(seedKey.hashCode()))
}
