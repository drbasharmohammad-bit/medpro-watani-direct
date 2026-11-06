package sy.medpro.watani.ui.ddx

import sy.medpro.watani.data.DdxCaseDto
import sy.medpro.watani.data.DdxReferenceDto
import java.util.Locale
import kotlin.math.abs

/**
 * Where one reference diagnosis landed in the student's ranking.
 * [studentPosition] is 1-based; null means the diagnosis was absent
 * from the student's probability list.
 */
data class DdxReferencePlacement(
    val rank: Int?,
    val diagnosisAr: String?,
    val diagnosisEn: String?,
    val studentPosition: Int?
)

/**
 * Whether the student flagged one reference must-not-miss diagnosis
 * in their danger set.
 */
data class DdxMnmStatus(
    val rank: Int?,
    val diagnosisAr: String?,
    val diagnosisEn: String?,
    val markedByStudent: Boolean
)

/**
 * Full scoring breakdown for one committed attempt, per the completed
 * scoring specification approved by the doctor on 6 Nov 2026
 * (SCORING_COMPLETION_DESIGN.md, design v1 + the v2 amendment that
 * closes the "mark everything as danger" loophole).
 *
 * [total] is the clamped 0-to-100 aggregate. The fractional component
 * values ([a2Points], [riskPoints], [breadthPoints]) are display
 * doubles derived from exact rational arithmetic; [total] itself is
 * rounded from the exact rational raw score, never from the doubles.
 */
data class DdxScoreBreakdown(
    val a1Points: Int,
    val a2Points: Double,
    val riskPoints: Double,
    val breadthPoints: Double,
    val penaltyPoints: Int,
    val missedMnmCount: Int,
    val disciplineFactor: Double,
    val total: Int,
    val bandLabel: String,
    val bandText: String,
    val hasMissWarning: Boolean,
    val topDiagnosisEn: String?,
    val topDiagnosisAr: String?,
    val topStudentPosition: Int?,
    val referencePlacements: List<DdxReferencePlacement>,
    val mustNotMissStatus: List<DdxMnmStatus>,
    val extraStudentCount: Int
)

/** Warning line shown whenever a completely-missed danger penalty applied. */
const val DDX_MISS_WARNING: String =
    "⚠️ فاتك تشخيص لا يُفوَّت بالكامل — راجعه في الكشف قبل أي شيء آخر"

/** Interpretation band (label + student-facing text) for a total score. */
fun ddxBandFor(total: Int): Pair<String, String> = when {
    total >= 90 -> "ممتاز" to "ممتاز — استدلال سريري متكامل: ترتيب دقيق وخطر مضبوط"
    total >= 75 -> "جيد جدًا" to "جيد جدًا — ترتيب قوي، راجع الفجوات المحدودة في الكشف"
    total >= 60 -> "مقبول" to "مقبول — أساس موجود؛ راجع التشخيصات الفائتة ومواضعها"
    total >= 40 -> "يحتاج تطويرًا" to "يحتاج تطويرًا — فجوات في التغطية أو الترتيب؛ ادرس التعليل المرجعي"
    else -> "ضعيف" to "ضعيف — أعد دراسة الحالة من الكشف المرجعي خطوة بخطوة"
}

/** One-decimal display format for fractional component points. */
fun ddxFormatPoints(value: Double): String = String.format(Locale.US, "%.1f", value)

/**
 * Exact rational arithmetic for scoring (design section 7): every
 * component is computed as a fraction so that exact .5 boundaries
 * round deterministically (half-up) instead of flipping on binary
 * floating-point noise. Denominators in this scoring system are tiny
 * (case sizes 5-8, danger-set sizes, tenths), so Long never overflows.
 */
private class Frac private constructor(val num: Long, val den: Long) {
    operator fun plus(other: Frac): Frac =
        of(num * other.den + other.num * den, den * other.den)

    operator fun minus(other: Frac): Frac =
        of(num * other.den - other.num * den, den * other.den)

    operator fun times(other: Frac): Frac =
        of(num * other.num, den * other.den)

    fun atLeast(other: Frac): Boolean = num * other.den >= other.num * den

    fun toDouble(): Double = num.toDouble() / den.toDouble()

    /** Round half-up (toward +infinity at exact .5) to the nearest integer. */
    fun roundHalfUp(): Long = Math.floorDiv(2 * num + den, 2 * den)

    companion object {
        val ZERO = Frac(0, 1)

        fun of(num: Long, den: Long = 1L): Frac {
            require(den != 0L) { "fraction denominator must not be zero" }
            var n = num
            var d = den
            if (d < 0) {
                n = -n
                d = -d
            }
            val g = gcd(abs(n), d)
            return Frac(n / g, d / g)
        }

        private tailrec fun gcd(a: Long, b: Long): Long =
            if (b == 0L) maxOf(a, 1L) else gcd(b, a % b)
    }
}

/** Normalised matching key for a diagnosis_en value. */
fun ddxMatchKey(diagnosisEn: String?): String = diagnosisEn?.trim()?.lowercase().orEmpty()

/**
 * The reference top (most likely) diagnosis: the differential entry
 * whose rank equals scoring_hints.top_rank when present, otherwise
 * the entry with rank 1, otherwise the lowest-ranked entry available.
 */
fun ddxTopReference(case: DdxCaseDto): DdxReferenceDto? {
    val refs = case.differentialReference.orEmpty().filter { !it.diagnosisEn.isNullOrBlank() }
    if (refs.isEmpty()) return null
    val wantedRank = case.scoringHints?.topRank ?: 1
    return refs.firstOrNull { it.rank == wantedRank }
        ?: refs.firstOrNull { it.rank == 1 }
        ?: refs.minByOrNull { it.rank ?: Int.MAX_VALUE }
}

/**
 * The adjudicated reference danger set used for display and scoring.
 *
 * Prefers scoring_hints.must_not_miss_ranks when present and non-empty
 * (the overseer-adjudicated set, which may differ from the raw flags),
 * and falls back to the entries flagged must_not_miss otherwise.
 * Returned in reference-rank order.
 */
fun ddxReferenceMustNotMiss(case: DdxCaseDto): List<DdxReferenceDto> {
    val refs = case.differentialReference.orEmpty()
    val hintRanks = case.scoringHints?.mustNotMissRanks.orEmpty()
    val chosen = if (hintRanks.isNotEmpty()) {
        refs.filter { it.rank != null && it.rank in hintRanks }
    } else {
        refs.filter { it.mustNotMiss == true }
    }
    return chosen.sortedBy { it.rank ?: Int.MAX_VALUE }
}

/**
 * Scores one committed DDx attempt with the full approved scoring
 * specification (SCORING_COMPLETION_DESIGN.md v1 + v2, approved by
 * the doctor on 6 Nov 2026). Only the final commitment is scored.
 *
 * Component A — likelihood accuracy, 50 points:
 * - A1 (up to 30, documented table): position of the reference top
 *   diagnosis in the student's probability list P: 1 gives 30, 2
 *   gives 20, 3 gives 12, 4 to 5 give 5, absent or beyond 5 gives 0.
 * - A2 (up to 20): proximity of the remaining reference order. Each
 *   other reference diagnosis earns closeness points by the distance
 *   between its student position and its reference rank (distance
 *   0/1/2/3/4+ gives 4/3/2/1/0; absent gives 0), normalised so the
 *   cap is exactly 20 for any case size: A2 = 20 * R / (4 * (n-1)).
 *
 * Component B — the independent danger dimension, 30 points (v2):
 * recall x precision over the student's danger set D:
 * B = 30 * (covered / k) * (covered / |D|), where covered counts the
 * reference must-not-miss diagnoses the student flagged. A blanket
 * "everything is danger" set therefore earns only in proportion to
 * its actual discrimination, while a set with no false flags scores
 * exactly as plain coverage would. k = 0 (no reference danger; not
 * present in the current bank) earns the full 30.
 *
 * Component C — disciplined breadth, 20 points:
 * C = 20 * (matched / n) * f, where matched counts the reference
 * diagnoses present in P at all, and f is the discipline factor over
 * the over-inclusion limit: f = 1 while extras e <= 3, then f falls
 * by 0.1 per extra down to a 0.5 floor. Extras are the entries of P
 * beyond the matched reference diagnoses (unmatched free text and
 * repeats of an already-matched diagnosis included).
 *
 * The single material penalty — completely missing a lethal danger:
 * a reference must-not-miss diagnosis counts as completely missed
 * when it is absent from P and not shielded by D. Presence in P
 * always shields; presence in D shields only while D is selective
 * (|D| <= k) — the v2 amendment. 5 points per completely missed
 * diagnosis, capped at 15, deducted from the total.
 *
 * The raw sum is rounded half-up from exact fractions and clamped
 * to [0, 100]. [studentRankingEn] mirrors the student's probability
 * list slot for slot: a null slot is a free-text entry, which never
 * matches a reference diagnosis but still occupies its position, so
 * positions are never compressed before counting. Matching is by the
 * diagnosis_en identity key (trimmed, case-insensitive), each
 * reference diagnosis matching at its first occurrence only.
 *
 * [studentMnmEn] mirrors the student's danger commitment entry for
 * entry: a null slot is a free-text flag. The set size |D| follows
 * the design (section 14.2): flagging the same reference diagnosis
 * twice collapses to one entry, while every entry that matches no
 * reference diagnosis — free text included — counts in full.
 */
fun scoreDdxCommit(
    case: DdxCaseDto,
    studentRankingEn: List<String?>,
    studentMnmEn: List<String?>
): DdxScoreBreakdown {
    val studentKeys = studentRankingEn.map { ddxMatchKey(it) }
    val dEntries = studentMnmEn.map { ddxMatchKey(it) }
    val dKeys = dEntries.filter { it.isNotEmpty() }.toSet()

    val references = case.differentialReference.orEmpty()
        .filter { !it.diagnosisEn.isNullOrBlank() }
        .sortedBy { it.rank ?: Int.MAX_VALUE }
    val n = references.size

    val referenceKeys = references.map { ddxMatchKey(it.diagnosisEn) }
        .filter { it.isNotEmpty() }.toSet()
    val dSize = referenceKeys.count { it in dKeys } +
        dEntries.count { it.isEmpty() || it !in referenceKeys }

    fun positionOf(diagnosisEn: String?): Int? {
        val key = ddxMatchKey(diagnosisEn)
        if (key.isEmpty()) return null
        val idx = studentKeys.indexOf(key)
        return if (idx >= 0) idx + 1 else null
    }

    val top = ddxTopReference(case)
    val topPosition = positionOf(top?.diagnosisEn)

    // ---- A1: position of the most likely diagnosis (documented table).
    val a1Points = when {
        topPosition == null -> 0
        topPosition == 1 -> 30
        topPosition == 2 -> 20
        topPosition == 3 -> 12
        topPosition in 4..5 -> 5
        else -> 0
    }

    // ---- A2: proximity of the remaining reference order.
    val m = n - 1
    val a2: Frac = when {
        n == 0 -> Frac.ZERO
        m == 0 -> Frac.of(20)
        else -> {
            var r = 0
            references.forEachIndexed { index, ref ->
                val isTop = top != null &&
                    ref.rank == top.rank && ddxMatchKey(ref.diagnosisEn) == ddxMatchKey(top.diagnosisEn)
                if (isTop) return@forEachIndexed
                val q = positionOf(ref.diagnosisEn) ?: return@forEachIndexed
                val rank = ref.rank ?: (index + 1)
                r += when (abs(q - rank)) {
                    0 -> 4
                    1 -> 3
                    2 -> 2
                    3 -> 1
                    else -> 0
                }
            }
            Frac.of(20L * r, 4L * m)
        }
    }

    // ---- B: independent danger dimension, recall x precision (v2).
    val mnmRefs = ddxReferenceMustNotMiss(case)
    val k = mnmRefs.size
    val covered = mnmRefs.count { ddxMatchKey(it.diagnosisEn) in dKeys }
    val b: Frac = when {
        k == 0 -> Frac.of(30)
        covered == 0 || dSize == 0 -> Frac.ZERO
        else -> Frac.of(30L * covered * covered, k.toLong() * dSize)
    }

    // ---- C: breadth with the discipline factor over the extra limit.
    val matched = references.count { positionOf(it.diagnosisEn) != null }
    val extras = studentRankingEn.size - matched
    val discipline: Frac = if (extras <= 3) {
        Frac.of(1)
    } else {
        val decayed = Frac.of(13L - extras, 10L)
        if (decayed.atLeast(Frac.of(1, 2))) decayed else Frac.of(1, 2)
    }
    val c: Frac = if (n == 0) {
        Frac.ZERO
    } else {
        Frac.of(20L * matched, n.toLong()) * discipline
    }

    // ---- Penalty: completely missed lethal dangers (v2 shield rule).
    val missed = mnmRefs.count { ref ->
        if (positionOf(ref.diagnosisEn) != null) {
            false // presence in P always shields
        } else {
            val shieldedByD = ddxMatchKey(ref.diagnosisEn) in dKeys && dSize <= k
            !shieldedByD
        }
    }
    val penaltyPoints = minOf(5 * missed, 15)

    // ---- Total: exact-fraction sum, half-up rounding, clamped [0, 100].
    val raw = Frac.of(a1Points.toLong()) + a2 + b + c - Frac.of(penaltyPoints.toLong())
    val total = raw.roundHalfUp().coerceIn(0L, 100L).toInt()
    val (bandLabel, bandText) = ddxBandFor(total)

    // Display placements keep the pre-existing behaviour: every
    // reference entry in rank order, scored or not.
    val placements = case.differentialReference.orEmpty()
        .sortedBy { it.rank ?: Int.MAX_VALUE }
        .map { ref ->
            DdxReferencePlacement(
                rank = ref.rank,
                diagnosisAr = ref.diagnosisAr,
                diagnosisEn = ref.diagnosisEn,
                studentPosition = positionOf(ref.diagnosisEn)
            )
        }
    val mnmStatus = mnmRefs.map { ref ->
        DdxMnmStatus(
            rank = ref.rank,
            diagnosisAr = ref.diagnosisAr,
            diagnosisEn = ref.diagnosisEn,
            markedByStudent = ddxMatchKey(ref.diagnosisEn) in dKeys
        )
    }

    return DdxScoreBreakdown(
        a1Points = a1Points,
        a2Points = a2.toDouble(),
        riskPoints = b.toDouble(),
        breadthPoints = c.toDouble(),
        penaltyPoints = penaltyPoints,
        missedMnmCount = missed,
        disciplineFactor = discipline.toDouble(),
        total = total,
        bandLabel = bandLabel,
        bandText = bandText,
        hasMissWarning = penaltyPoints > 0,
        topDiagnosisEn = top?.diagnosisEn,
        topDiagnosisAr = top?.diagnosisAr,
        topStudentPosition = topPosition,
        referencePlacements = placements,
        mustNotMissStatus = mnmStatus,
        extraStudentCount = extras
    )
}
