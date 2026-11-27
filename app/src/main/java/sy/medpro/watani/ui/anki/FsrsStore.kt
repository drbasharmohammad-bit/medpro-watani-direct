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

    private fun serialize(s: Fsrs.State): String =
        "${(s.stability * 10000).toLong()}|${(s.difficulty * 100).toInt()}|" +
            "${s.dueAtMillis}|${s.lastReviewMillis}|${s.repetitions}|${s.lapses}"

    private fun parse(raw: String?): Fsrs.State? {
        if (raw.isNullOrBlank()) return null
        val p = raw.split("|")
        if (p.size < 6) return null
        return Fsrs.State(
            stability = (p[0].toLongOrNull() ?: return null) / 10000.0,
            difficulty = (p[1].toIntOrNull() ?: return null) / 100.0,
            dueAtMillis = p[2].toLongOrNull() ?: return null,
            lastReviewMillis = p[3].toLongOrNull() ?: 0L,
            repetitions = p[4].toIntOrNull() ?: 0,
            lapses = p[5].toIntOrNull() ?: 0
        )
    }

    /** Legacy SM-2 record -> FSRS state (see Fsrs.migrateFromSm2). */
    private fun parseLegacy(raw: String?): Fsrs.State? {
        if (raw.isNullOrBlank()) return null
        val p = raw.split("|")
        if (p.size < 5) return null
        val dueAt = p[0].toLongOrNull() ?: return null
        val interval = p[1].toDoubleOrNull() ?: 0.0
        val ease = (p[2].toIntOrNull() ?: 250) / 100.0
        val reps = p[3].toIntOrNull() ?: 0
        val lapses = p[4].toIntOrNull() ?: 0
        return Fsrs.migrateFromSm2(interval, ease, reps, lapses, dueAt, System.currentTimeMillis())
    }

    fun state(context: Context, cardKey: String): Fsrs.State? {
        val p = prefs(context)
        parse(p.getString(PREFIX + cardKey, null))?.let { return it }
        // One-time legacy conversion for built-in deck cards (their
        // keys are the bare Int ids the SM-2 store used).
        if (cardKey.all { it.isDigit() }) {
            val legacy = parseLegacy(p.getString(cardKey, null)) ?: return null
            p.edit()
                .putString(PREFIX + cardKey, serialize(legacy))
                .remove(cardKey)
                .apply()
            return legacy
        }
        return null
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
        prefs(context).edit().putString(PREFIX + cardKey, serialize(next)).apply()
        _changes.value = _changes.value + 1
    }

    /** Removes a card's scheduling state (student card deleted). */
    fun forget(context: Context, cardKey: String) {
        prefs(context).edit().remove(PREFIX + cardKey).remove(cardKey).apply()
        _changes.value = _changes.value + 1
    }
}
