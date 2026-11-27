package sy.medpro.watani.ui.anki

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * FSRS persistence for the flashcards section (replaces the SM-2-lite
 * AnkiSrs store, build 1.2.54). Same per-account SharedPreferences
 * files as before (`anki_srs_<uid>`, guest file without a suffix) so
 * the account binding is unchanged.
 *
 * Records are keyed by a string card key: the built-in deck keeps its
 * content-hash Int id (stringified), student-authored cards use their
 * own `u…` ids — one scheduler and one store for both decks.
 *
 * New record format under key `f:<cardKey>`:
 * "stability_x10000|difficulty_x100|dueAt|lastReview|repetitions|lapses".
 *
 * Migration: a legacy SM-2 record ("dueAt|interval|ease_x100|reps|
 * lapses" under the bare Int key) is converted on first read through
 * [Fsrs.migrateFromSm2], persisted in the new format, and the legacy
 * key is removed — progress is converted, never wiped.
 */
object FsrsStore {
    private const val PREFIX = "f:"

    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes.asStateFlow()

    private fun prefs(context: Context): SharedPreferences {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val name = if (uid.isNullOrBlank()) "anki_srs" else "anki_srs_$uid"
        return context.getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    fun state(context: Context, cardKey: String): Fsrs.State? {
        val p = prefs(context)
        val current = FsrsRecordCodec.parse(p.getString(PREFIX + cardKey, null))
        // One-time legacy conversion for built-in deck cards (their
        // keys are the bare Int ids the SM-2 store used).
        val legacy = if (current == null && cardKey.all { it.isDigit() }) {
            FsrsRecordCodec.parseLegacy(p.getString(cardKey, null))?.also { converted ->
                p.edit()
                    .putString(PREFIX + cardKey, FsrsRecordCodec.serialize(converted))
                    .remove(cardKey)
                    .apply()
            }
        } else {
            null
        }
        return current ?: legacy
    }

    fun states(context: Context, cardKeys: Collection<String>): Map<String, Fsrs.State> {
        val out = HashMap<String, Fsrs.State>()
        for (key in cardKeys) state(context, key)?.let { out[key] = it }
        return out
    }

    fun dueKeys(context: Context, cardKeys: Collection<String>, now: Long = System.currentTimeMillis()): List<String> =
        states(context, cardKeys)
            .filterValues { it.dueAtMillis <= now }
            .entries
            .sortedBy { it.value.dueAtMillis }
            .map { it.key }

    fun dueCount(context: Context, cardKeys: Collection<String>, now: Long = System.currentTimeMillis()): Int =
        dueKeys(context, cardKeys, now).size

    fun previewIntervalDays(context: Context, cardKey: String, grade: AnkiGrade): Double =
        Fsrs.previewIntervalDays(state(context, cardKey), grade, System.currentTimeMillis())

    fun grade(context: Context, cardKey: String, grade: AnkiGrade) {
        val next = Fsrs.schedule(state(context, cardKey), grade, System.currentTimeMillis())
        prefs(context).edit().putString(PREFIX + cardKey, FsrsRecordCodec.serialize(next)).apply()
        _changes.value = _changes.value + 1
    }

    /** Removes a card's scheduling state (student card deleted). */
    fun forget(context: Context, cardKey: String) {
        prefs(context).edit().remove(PREFIX + cardKey).remove(cardKey).apply()
        _changes.value = _changes.value + 1
    }
}

/**
 * Wire codec for FSRS records (kept apart from [FsrsStore] so the
 * store stays a thin persistence facade). Scales are part of the
 * on-disk format: stability x10000, difficulty/ease x100.
 */
private object FsrsRecordCodec {
    private const val STABILITY_SCALE = 10000.0
    private const val DIFFICULTY_SCALE = 100.0
    private const val DEFAULT_EASE_X100 = 250

    fun serialize(s: Fsrs.State): String =
        "${(s.stability * STABILITY_SCALE).toLong()}|${(s.difficulty * DIFFICULTY_SCALE).toInt()}|" +
            "${s.dueAtMillis}|${s.lastReviewMillis}|${s.repetitions}|${s.lapses}"

    fun parse(raw: String?): Fsrs.State? {
        val p = raw?.takeIf { it.isNotBlank() }?.split("|")?.takeIf { it.size >= 6 }
        return p?.let {
            val stability = it[0].toLongOrNull()
            val difficulty = it[1].toIntOrNull()
            val dueAt = it[2].toLongOrNull()
            if (stability == null || difficulty == null || dueAt == null) {
                null
            } else {
                Fsrs.State(
                    stability = stability / STABILITY_SCALE,
                    difficulty = difficulty / DIFFICULTY_SCALE,
                    dueAtMillis = dueAt,
                    lastReviewMillis = it[3].toLongOrNull() ?: 0L,
                    repetitions = it[4].toIntOrNull() ?: 0,
                    lapses = it[5].toIntOrNull() ?: 0
                )
            }
        }
    }

    /** Legacy SM-2 record -> FSRS state (see Fsrs.migrateFromSm2). */
    fun parseLegacy(raw: String?): Fsrs.State? {
        val p = raw?.takeIf { it.isNotBlank() }?.split("|")?.takeIf { it.size >= 5 }
        val dueAt = p?.get(0)?.toLongOrNull()
        return if (p == null || dueAt == null) {
            null
        } else {
            Fsrs.migrateFromSm2(
                Fsrs.Sm2Record(
                    intervalDays = p[1].toDoubleOrNull() ?: 0.0,
                    easeFactor = (p[2].toIntOrNull() ?: DEFAULT_EASE_X100) / DIFFICULTY_SCALE,
                    repetitions = p[3].toIntOrNull() ?: 0,
                    lapses = p[4].toIntOrNull() ?: 0,
                    dueAtMillis = dueAt
                ),
                System.currentTimeMillis()
            )
        }
    }
}
