package sy.medpro.watani.ui.firstaid

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Per-card spaced-repetition state for First Aid flashcards. */
data class FirstAidCardState(
    /** Index into the repetition ladder (may grow past its end into
     * the long-term range). */
    val stage: Int,
    val dueAtMillis: Long,
    val lapses: Int
)

/**
 * Pure scheduler applying the courses' OWN repetition.json ladder
 * (build 1.2.36) — the ladder ships inside every unit package and is
 * identical in both steps, so it is a content rule, not an app choice:
 *
 * - intervals_days = [0, 1, 3, 7, 14, 30] for stages 0..5;
 * - past the ladder, long_term_range_days = [45, 90]: the interval
 *   grows 45 -> 60 -> 75 -> 90 and then stays at 90;
 * - on_lapse_reset_to_days = 1: a lapse drops the card back to the
 *   1-day stage (ladder stage 1), due tomorrow;
 * - quiz_error_cards = "same-day": a card missed inside a unit quiz
 *   enters review due immediately (stage 0).
 *
 * Android-free so the JVM tests pin every rung and edge.
 */
object FirstAidSrsScheduler {

    const val DAY_MS = 86_400_000L

    val LADDER_DAYS: List<Long> = listOf(0, 1, 3, 7, 14, 30)
    const val LONG_TERM_MIN_DAYS = 45L
    const val LONG_TERM_MAX_DAYS = 90L
    const val LONG_TERM_STEP_DAYS = 15L
    const val LAPSE_RESET_DAYS = 1L

    /** The ladder stage whose interval is the lapse reset (1 day). */
    val LAPSE_STAGE: Int = LADDER_DAYS.indexOf(LAPSE_RESET_DAYS)

    fun intervalDaysForStage(stage: Int): Long {
        if (stage < 0) return LADDER_DAYS.first()
        if (stage < LADDER_DAYS.size) return LADDER_DAYS[stage]
        val beyond = stage - (LADDER_DAYS.size - 1)
        return minOf(
            LONG_TERM_MAX_DAYS,
            LONG_TERM_MIN_DAYS + (beyond - 1) * LONG_TERM_STEP_DAYS
        )
    }

    /** A brand-new card is due the same day (ladder stage 0 = 0 days). */
    fun newCard(now: Long): FirstAidCardState =
        FirstAidCardState(stage = 0, dueAtMillis = now, lapses = 0)

    fun onCorrect(state: FirstAidCardState, now: Long): FirstAidCardState {
        val nextStage = state.stage + 1
        return state.copy(
            stage = nextStage,
            dueAtMillis = now + intervalDaysForStage(nextStage) * DAY_MS
        )
    }

    fun onLapse(state: FirstAidCardState, now: Long): FirstAidCardState =
        FirstAidCardState(
            stage = LAPSE_STAGE,
            dueAtMillis = now + LAPSE_RESET_DAYS * DAY_MS,
            lapses = state.lapses + 1
        )

    /** quiz_error_cards = "same-day": due NOW at stage 0; the lapse
     * count of any existing state is preserved. */
    fun quizErrorCard(now: Long, existing: FirstAidCardState? = null): FirstAidCardState =
        FirstAidCardState(stage = 0, dueAtMillis = now, lapses = existing?.lapses ?: 0)

    fun isDue(state: FirstAidCardState, now: Long): Boolean = state.dueAtMillis <= now
}

/**
 * Per-account First Aid flashcard SRS store (build 1.2.36), AnkiSrs
 * pattern: prefs file `firstaid_srs_<uid>` (guest file without a
 * suffix), one string record per card key "<unitId>#<cardIndex>":
 * "stage|dueAtMillis|lapses". Scheduling itself lives in the pure
 * [FirstAidSrsScheduler]; this object only persists its verdicts.
 */
object FirstAidSrs {

    /** Bumped on every write so Compose readers recompute due counts. */
    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes.asStateFlow()

    private fun prefs(context: Context): SharedPreferences {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val name = if (uid.isNullOrBlank()) "firstaid_srs" else "firstaid_srs_$uid"
        return context.getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    fun cardKey(unitId: String, cardIndex: Int): String = "$unitId#$cardIndex"

    private fun parse(raw: String?): FirstAidCardState? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split("|")
        if (parts.size < 3) return null
        return FirstAidCardState(
            stage = parts[0].toIntOrNull() ?: return null,
            dueAtMillis = parts[1].toLongOrNull() ?: return null,
            lapses = parts[2].toIntOrNull() ?: 0
        )
    }

    private fun save(context: Context, key: String, state: FirstAidCardState) {
        prefs(context).edit()
            .putString(key, "${state.stage}|${state.dueAtMillis}|${state.lapses}")
            .apply()
        _changes.value = _changes.value + 1
    }

    fun state(context: Context, unitId: String, cardIndex: Int): FirstAidCardState? =
        parse(prefs(context).getString(cardKey(unitId, cardIndex), null))

    /** First sight of a card enrolls it (due same day), then the grade
     * applies on top — so a first-ever "correct" lands at stage 1. */
    fun recordReview(
        context: Context,
        unitId: String,
        cardIndex: Int,
        correct: Boolean,
        now: Long = System.currentTimeMillis()
    ): FirstAidCardState {
        val current = state(context, unitId, cardIndex)
            ?: FirstAidSrsScheduler.newCard(now)
        val next = if (correct) {
            FirstAidSrsScheduler.onCorrect(current, now)
        } else {
            FirstAidSrsScheduler.onLapse(current, now)
        }
        save(context, cardKey(unitId, cardIndex), next)
        return next
    }

    /** A flashcard the student missed inside a unit quiz joins today's
     * review (repetition.json: quiz_error_cards = "same-day"). */
    fun enqueueQuizError(
        context: Context,
        unitId: String,
        cardIndex: Int,
        now: Long = System.currentTimeMillis()
    ) {
        val existing = state(context, unitId, cardIndex)
        save(
            context, cardKey(unitId, cardIndex),
            FirstAidSrsScheduler.quizErrorCard(now, existing)
        )
    }

    fun dueKeys(
        context: Context,
        keys: Collection<String>,
        now: Long = System.currentTimeMillis()
    ): List<String> {
        val p = prefs(context)
        return keys.filter { key ->
            parse(p.getString(key, null))?.let { FirstAidSrsScheduler.isDue(it, now) } == true
        }
    }

    fun dueCount(
        context: Context,
        keys: Collection<String>,
        now: Long = System.currentTimeMillis()
    ): Int = dueKeys(context, keys, now).size

    /** Every enrolled card key that is due now, across all units —
     * the section home's «مراجعة المستحق اليوم» player groups these
     * by unit and loads each unit's flashcards. */
    fun dueKeysAll(
        context: Context,
        now: Long = System.currentTimeMillis()
    ): List<String> {
        val p = prefs(context)
        return p.all.entries.mapNotNull { (key, value) ->
            val state = (value as? String)?.let { parse(it) } ?: return@mapNotNull null
            if (FirstAidSrsScheduler.isDue(state, now)) key else null
        }
    }

    /** Due counter over EVERY enrolled card (the section card's
     * «مستحق اليوم» badge): scans the account's whole SRS file. */
    fun dueCountAll(
        context: Context,
        now: Long = System.currentTimeMillis()
    ): Int {
        val p = prefs(context)
        return p.all.values.count { value ->
            (value as? String)?.let { parse(it) }
                ?.let { FirstAidSrsScheduler.isDue(it, now) } == true
        }
    }
}
