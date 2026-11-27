package sy.medpro.watani.ui.anki

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToLong

/** The four review grades (kept from the old SM-2 UI vocabulary). */
enum class AnkiGrade(val rating: Int) {
    AGAIN(1), HARD(2), GOOD(3), EASY(4)
}

/**
 * FSRS-5 scheduler (doctor's order 27 Nov 2026: replace the old
 * SM-2-lite Anki scheduler with FSRS — Free Spaced Repetition
 * Scheduler — for the whole flashcards section, built-in deck and
 * student-authored cards alike).
 *
 * Pure Kotlin, Android-free, implementing the published FSRS-5
 * reference formulas with the official default weights
 * (open-spaced-repetition reference):
 *
 * - initial stability S0(G) = w[G-1]
 * - initial difficulty D0(G) = w4 - e^(w5*(G-1)) + 1, clamped [1,10]
 * - retrievability R(t,S) = (1 + FACTOR*t/S)^DECAY,
 *   FACTOR = 19/81, DECAY = -0.5 (so R(S,S) = 0.9)
 * - next interval I(r,S) = (S/FACTOR) * (r^(1/DECAY) - 1) at the
 *   desired retention r (0.9 by default, so I = S)
 * - difficulty update: dD = -w6*(G-3); D' = D + dD*(10-D)/9, then
 *   mean reversion D'' = w7*D0(4) + (1-w7)*D' (FSRS-5 reverts to
 *   D0(4)), clamped [1,10]
 * - recall stability (G >= 2):
 *   S' = S * (1 + e^w8 * (11-D) * S^(-w9) * (e^(w10*(1-R)) - 1)
 *        * hardPenalty * easyBonus),
 *   hardPenalty = w15 when G = Hard, easyBonus = w16 when G = Easy
 * - post-lapse stability (G = Again):
 *   S'f = min( w11 * D^(-w12) * ((S+1)^w13 - 1) * e^(w14*(1-R)),
 *              S / e^(w17*w18) )
 * - same-day review (elapsed < 1 day): S' = S * e^(w17*(G-3+w18))
 *
 * Presentation floor (documented deviation, same spirit as Anki's
 * relearn step): a lapse or a first Again whose computed interval is
 * under one day is due again in [AGAIN_DELAY_MS] (10 minutes) rather
 * than a fractional day; the stored stability/difficulty are always
 * the exact FSRS values.
 */
@Suppress("MagicNumber", "TooManyFunctions")
// MagicNumber: every literal here IS a published FSRS-5 algorithm
// constant (the 19 default weights, the difficulty clamp [1,10],
// the ease mapping) — naming them would rename the reference
// formulas, not clarify them. TooManyFunctions: one cohesive
// scheduler object; the reference API is kept whole on purpose.
object Fsrs {
    /** FSRS-5 official default weights (19 parameters). */
    val W: DoubleArray = doubleArrayOf(
        0.40255, 1.18385, 3.173, 15.69105, 7.1949, 0.5345, 1.4604, 0.0046,
        1.54575, 0.1192, 1.01925, 1.9395, 0.11, 0.29605, 2.2698, 0.2315,
        2.9898, 0.51655, 0.6621
    )

    const val DECAY = -0.5
    const val FACTOR = 19.0 / 81.0
    const val DESIRED_RETENTION = 0.9
    const val DAY_MS = 86_400_000L
    const val AGAIN_DELAY_MS = 10 * 60_000L
    private const val MIN_STABILITY = 0.01

    /** Per-card FSRS memory state. */
    data class State(
        val stability: Double,
        val difficulty: Double,
        val dueAtMillis: Long,
        val lastReviewMillis: Long,
        val repetitions: Int,
        val lapses: Int
    )

    fun initialStability(grade: AnkiGrade): Double = W[grade.rating - 1]

    fun initialDifficulty(grade: AnkiGrade): Double =
        clampDifficulty(W[4] - exp(W[5] * (grade.rating - 1)) + 1)

    fun retrievability(elapsedDays: Double, stability: Double): Double {
        if (stability <= 0.0) return 0.0
        return (1 + FACTOR * elapsedDays / stability).pow(DECAY)
    }

    /** Interval in days at [retention] for stability [stability]. */
    fun intervalDays(stability: Double, retention: Double = DESIRED_RETENTION): Double =
        stability / FACTOR * (retention.pow(1 / DECAY) - 1)

    fun nextDifficulty(difficulty: Double, grade: AnkiGrade): Double {
        val delta = -W[6] * (grade.rating - 3)
        val damped = difficulty + delta * (10 - difficulty) / 9
        val reverted = W[7] * initialDifficulty(AnkiGrade.EASY) + (1 - W[7]) * damped
        return clampDifficulty(reverted)
    }

    fun nextRecallStability(difficulty: Double, stability: Double, retrievability: Double, grade: AnkiGrade): Double {
        val hardPenalty = if (grade == AnkiGrade.HARD) W[15] else 1.0
        val easyBonus = if (grade == AnkiGrade.EASY) W[16] else 1.0
        val growth = exp(W[8]) * (11 - difficulty) * stability.pow(-W[9]) *
            (exp(W[10] * (1 - retrievability)) - 1) * hardPenalty * easyBonus
        return maxOf(MIN_STABILITY, stability * (1 + growth))
    }

    fun nextForgetStability(difficulty: Double, stability: Double, retrievability: Double): Double {
        val raw = W[11] * difficulty.pow(-W[12]) *
            ((stability + 1).pow(W[13]) - 1) * exp(W[14] * (1 - retrievability))
        val cap = stability / exp(W[17] * W[18])
        return maxOf(MIN_STABILITY, minOf(raw, cap))
    }

    fun sameDayStability(stability: Double, grade: AnkiGrade): Double =
        maxOf(MIN_STABILITY, stability * exp(W[17] * (grade.rating - 3 + W[18])))

    private fun clampDifficulty(d: Double): Double = d.coerceIn(1.0, 10.0)

    /**
     * Full scheduling step. [prev] null = first review of a new card.
     * Returns the new state; due time honors the documented sub-day
     * Again floor.
     */
    fun schedule(prev: State?, grade: AnkiGrade, now: Long): State {
        if (prev == null) {
            val s = initialStability(grade)
            val d = initialDifficulty(grade)
            return State(
                stability = s,
                difficulty = d,
                dueAtMillis = dueFromInterval(now, s, grade),
                lastReviewMillis = now,
                repetitions = if (grade == AnkiGrade.AGAIN) 0 else 1,
                lapses = if (grade == AnkiGrade.AGAIN) 1 else 0
            )
        }
        val elapsedDays = (now - prev.lastReviewMillis).coerceAtLeast(0) / DAY_MS.toDouble()
        val newDifficulty = nextDifficulty(prev.difficulty, grade)
        val newStability = when {
            elapsedDays < 1.0 -> sameDayStability(prev.stability, grade)
            grade == AnkiGrade.AGAIN -> nextForgetStability(
                prev.difficulty, prev.stability, retrievability(elapsedDays, prev.stability)
            )
            else -> nextRecallStability(
                prev.difficulty, prev.stability, retrievability(elapsedDays, prev.stability), grade
            )
        }
        return State(
            stability = newStability,
            difficulty = newDifficulty,
            dueAtMillis = dueFromInterval(now, newStability, grade),
            lastReviewMillis = now,
            repetitions = if (grade == AnkiGrade.AGAIN) 0 else prev.repetitions + 1,
            lapses = if (grade == AnkiGrade.AGAIN) prev.lapses + 1 else prev.lapses
        )
    }

    private fun dueFromInterval(now: Long, stability: Double, grade: AnkiGrade): Long {
        val days = intervalDays(stability)
        return if (grade == AnkiGrade.AGAIN && days < 1.0) {
            now + AGAIN_DELAY_MS
        } else {
            now + (days * DAY_MS).roundToLong()
        }
    }

    /** Preview: the interval (days) [grade] would produce from [prev]. */
    fun previewIntervalDays(prev: State?, grade: AnkiGrade, now: Long): Double =
        intervalDays(schedule(prev, grade, now).stability)

    /**
     * SM-2 → FSRS migration (doctor's rule: convert the student's
     * existing progress, never wipe it). The old record carries
     * intervalDays, easeFactor (1.3..~2.8+, default 2.5), repetitions,
     * lapses and the due time. Conversion:
     * - stability := max(intervalDays, 0.1) for a card with at least
     *   one successful repetition (its SM-2 interval IS its empirical
     *   stability at ~90% retention); a never-passed card starts at
     *   the FSRS initial stability for Again (w0).
     * - difficulty := clamp(5 + (2.5 - ease) * 4, 1, 10): the default
     *   ease 2.5 maps to mid difficulty 5, the SM-2 floor 1.3 maps to
     *   9.8 (very hard), ease 2.8 maps to 3.8 (easy).
     * - due time, repetitions and lapses carry over unchanged.
     */
    /** One legacy SM-2 record as stored by the old AnkiSrs store. */
    data class Sm2Record(
        val intervalDays: Double,
        val easeFactor: Double,
        val repetitions: Int,
        val lapses: Int,
        val dueAtMillis: Long
    )

    fun migrateFromSm2(record: Sm2Record, now: Long): State {
        val stability = if (record.repetitions > 0) maxOf(record.intervalDays, 0.1) else W[0]
        val difficulty = (5.0 + (2.5 - record.easeFactor) * 4.0).coerceIn(1.0, 10.0)
        return State(
            stability = stability,
            difficulty = difficulty,
            dueAtMillis = record.dueAtMillis,
            lastReviewMillis = now,
            repetitions = record.repetitions,
            lapses = record.lapses
        )
    }
}
