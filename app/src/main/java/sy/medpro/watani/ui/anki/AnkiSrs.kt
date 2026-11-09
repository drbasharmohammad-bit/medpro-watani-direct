package sy.medpro.watani.ui.anki

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The four review grades, Anki-style. */
enum class AnkiGrade { AGAIN, HARD, GOOD, EASY }

/**
 * Per-card spaced-repetition state. Local-only by design (the task's
 * rule): it never leaves the device and never touches the published
 * Anki file or its loading flow — it only schedules reviews of the
 * cards the existing flow already loaded.
 */
data class AnkiSrsState(
    val dueAtMillis: Long,
    val intervalDays: Double,
    val easeFactor: Double,
    val repetitions: Int,
    val lapses: Int
)

/**
 * SM-2-lite scheduler persisted in a per-account SharedPreferences
 * file (same account-binding pattern as the case-player favorites:
 * `anki_srs_<uid>`, guest file without a suffix). One string record
 * per card id: "dueAt|intervalDays|ease_x100|repetitions|lapses".
 */
object AnkiSrs {
    private const val DAY_MS = 86_400_000L
    private const val AGAIN_DELAY_MS = 10 * 60_000L
    private const val DEFAULT_EASE = 2.5
    private const val MIN_EASE = 1.3

    /** Bumped on every grade so Compose readers recompute due counts. */
    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes.asStateFlow()

    private fun prefs(context: Context): SharedPreferences {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val name = if (uid.isNullOrBlank()) "anki_srs" else "anki_srs_$uid"
        return context.getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    private fun parse(raw: String?): AnkiSrsState? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split("|")
        if (parts.size < 5) return null
        return AnkiSrsState(
            dueAtMillis = parts[0].toLongOrNull() ?: return null,
            intervalDays = parts[1].toDoubleOrNull() ?: 0.0,
            easeFactor = (parts[2].toIntOrNull() ?: 250) / 100.0,
            repetitions = parts[3].toIntOrNull() ?: 0,
            lapses = parts[4].toIntOrNull() ?: 0
        )
    }

    fun state(context: Context, cardId: Int): AnkiSrsState? =
        parse(prefs(context).getString(cardId.toString(), null))

    fun states(context: Context, cardIds: Collection<Int>): Map<Int, AnkiSrsState> {
        val p = prefs(context)
        val out = HashMap<Int, AnkiSrsState>()
        for (id in cardIds) {
            parse(p.getString(id.toString(), null))?.let { out[id] = it }
        }
        return out
    }

    /** Ids (from [cardIds]) whose review is due at [now], earliest first. */
    fun dueIds(context: Context, cardIds: Collection<Int>, now: Long = System.currentTimeMillis()): List<Int> =
        states(context, cardIds)
            .filterValues { it.dueAtMillis <= now }
            .entries
            .sortedBy { it.value.dueAtMillis }
            .map { it.key }

    fun dueCount(context: Context, cardIds: Collection<Int>, now: Long = System.currentTimeMillis()): Int =
        dueIds(context, cardIds, now).size

    /** The interval (days) a grade would produce, for button hints. */
    fun previewIntervalDays(context: Context, cardId: Int, grade: AnkiGrade): Double {
        val prev = state(context, cardId)
        return schedule(prev, grade).first
    }

    private fun schedule(prev: AnkiSrsState?, grade: AnkiGrade): Pair<Double, AnkiSrsState> {
        val now = System.currentTimeMillis()
        if (prev == null) {
            return when (grade) {
                AnkiGrade.AGAIN -> AGAIN_DELAY_MS / DAY_MS.toDouble() to
                    AnkiSrsState(now + AGAIN_DELAY_MS, 0.0, DEFAULT_EASE, 0, 1)
                AnkiGrade.HARD -> 1.0 to
                    AnkiSrsState(now + DAY_MS, 1.0, DEFAULT_EASE, 1, 0)
                AnkiGrade.GOOD -> 1.0 to
                    AnkiSrsState(now + DAY_MS, 1.0, DEFAULT_EASE, 1, 0)
                AnkiGrade.EASY -> 4.0 to
                    AnkiSrsState(now + 4 * DAY_MS, 4.0, DEFAULT_EASE, 1, 0)
            }
        }
        return when (grade) {
            AnkiGrade.AGAIN -> AGAIN_DELAY_MS / DAY_MS.toDouble() to prev.copy(
                dueAtMillis = now + AGAIN_DELAY_MS,
                intervalDays = 0.0,
                easeFactor = maxOf(MIN_EASE, prev.easeFactor - 0.2),
                repetitions = 0,
                lapses = prev.lapses + 1
            )
            AnkiGrade.HARD -> {
                val interval = if (prev.intervalDays <= 0.0) 1.0 else maxOf(1.0, prev.intervalDays * 1.2)
                interval to prev.copy(
                    dueAtMillis = now + (interval * DAY_MS).toLong(),
                    intervalDays = interval,
                    easeFactor = maxOf(MIN_EASE, prev.easeFactor - 0.15),
                    repetitions = prev.repetitions + 1
                )
            }
            AnkiGrade.GOOD -> {
                val interval = if (prev.intervalDays <= 0.0) 1.0 else prev.intervalDays * prev.easeFactor
                interval to prev.copy(
                    dueAtMillis = now + (interval * DAY_MS).toLong(),
                    intervalDays = interval,
                    repetitions = prev.repetitions + 1
                )
            }
            AnkiGrade.EASY -> {
                val interval = if (prev.intervalDays <= 0.0) 4.0 else prev.intervalDays * prev.easeFactor * 1.3
                interval to prev.copy(
                    dueAtMillis = now + (interval * DAY_MS).toLong(),
                    intervalDays = interval,
                    easeFactor = prev.easeFactor + 0.15,
                    repetitions = prev.repetitions + 1
                )
            }
        }
    }

    fun grade(context: Context, cardId: Int, grade: AnkiGrade) {
        val prev = state(context, cardId)
        val next = schedule(prev, grade).second
        val raw = "${next.dueAtMillis}|${next.intervalDays}|${(next.easeFactor * 100).toInt()}|${next.repetitions}|${next.lapses}"
        prefs(context).edit().putString(cardId.toString(), raw).apply()
        _changes.value = _changes.value + 1
    }
}
