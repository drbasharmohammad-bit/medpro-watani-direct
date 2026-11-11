package sy.medpro.watani.data

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * بنك أخطائي — the personal mistakes bank (build 1.2.5).
 *
 * Every wrong answer on any quiz surface (national bank, English bank,
 * dawrat_jami3at, uworld — all through the shared QuizScreen — plus the
 * daily challenge and the exam simulator) is recorded here WITH its
 * full re-askable payload (stem, options, correct index, explanation),
 * and reviewed in a spaced-repetition deck until mastered.
 *
 * LOCAL-ONLY by design: the bank lives in a per-account
 * SharedPreferences file on the device (same account-binding pattern
 * as AnkiSrs: `watani_mistakes_bank_<uid>`, guest file without the
 * suffix). It is NEVER synced, never sent to the gateway, and never
 * merged into OfflineStudyVault's server-backed totals — the legacy
 * `savedMistakes` list there stays exactly as it was.
 */

// ---------------------------------------------------------------------
// Pure identity + scheduling logic (no Android imports — unit tested).
// ---------------------------------------------------------------------

/** One recorded mistake with everything needed to re-ask it offline. */
data class MistakeEntry(
    val key: String,
    val bank: String,
    val file: String,
    val questionId: String,
    val subject: String,
    val stem: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String,
    val whyCorrect: String?,
    val whyWrong: Map<String, String>?,
    val summary: String?,
    val ltr: Boolean,
    val firstWrongAt: Long,
    val lastWrongAt: Long,
    val wrongCount: Int,
    /** Current SRS interval in days (ladder 1 → 3 → 7 → 21). */
    val intervalDays: Int,
    /** Consecutive correct reviews answered while intervalDays >= 7. */
    val longIntervalStreak: Int,
    val mastered: Boolean,
    /** Epoch millis when the item becomes due for review. */
    val dueAt: Long
) {
    fun srsState(): MistakeSrsState = MistakeSrsState(intervalDays, longIntervalStreak, mastered)

    /** Rebuilds the question exactly as the shared QuestionCard expects
     * it, so a review renders (and reveals its explanation) identically
     * to the original quiz. */
    fun toDto(): QuestionDto = QuestionDto(
        id = questionId.ifBlank { null },
        text = stem,
        options = options,
        optionLabels = null,
        correct = correctIndex,
        correctIndices = null,
        explanation = explanation.ifBlank { null },
        whyCorrect = whyCorrect,
        whyWrong = whyWrong,
        summary = summary,
        years = null,
        source = null,
        session = null,
        topics = null,
        free = null
    )
}

/**
 * Mistake identity. A question is keyed by bank + source file + its own
 * stable id whenever the bank provides one; questions without a stable
 * id fall back to bank + file + presented index, and surfaces that
 * retain neither (the daily challenge pools questions out of their
 * files) fall back to a content hash of the normalized stem.
 */
object MistakeKeys {
    fun composeKey(
        bank: String,
        file: String,
        questionId: String?,
        indexInFile: Int,
        stem: String
    ): String {
        val b = bank.trim().ifBlank { "quiz" }
        val f = file.trim()
        val id = questionId?.trim().orEmpty()
        return when {
            id.isNotEmpty() -> "$b|$f|id:$id"
            indexInFile >= 0 -> "$b|$f|idx:$indexInFile"
            else -> "$b|$f|stem:${stemHash(stem)}"
        }
    }

    /** Splits a quiz source path ("dawrat_jami3at/damascus/قلبية.json")
     * into (bank, file). A missing path (ad-hoc quizzes) degrades to
     * the quiz title as the file label under the generic "quiz" bank. */
    fun bankOf(sourcePath: String?, fallbackTitle: String): Pair<String, String> {
        val path = sourcePath?.trim().orEmpty()
        if (path.isEmpty()) return "quiz" to fallbackTitle
        val bank = path.substringBefore('/', path)
        return bank to path
    }

    /** FNV-1a 64-bit over the normalized stem — deterministic across
     * runs and devices (unlike String.hashCode seeds, this is spelled
     * out so the fallback key can never drift). */
    fun stemHash(stem: String): String {
        val normalized = stem.trim().replace(Regex("\\s+"), " ").lowercase()
        var hash = -3750763034362895579L // FNV offset basis 14695981039346656037 (signed)
        for (ch in normalized) {
            hash = hash xor ch.code.toLong()
            hash *= 1099511628211L // FNV prime
        }
        return java.lang.Long.toHexString(hash)
    }
}

data class MistakeSrsState(
    val intervalDays: Int,
    val longIntervalStreak: Int,
    val mastered: Boolean
)

/**
 * SM-2-lite scheduling for the mistakes deck:
 * - A fresh (or repeated) mistake is due again in 1 day.
 * - Each correct review grows the interval: 1 → 3 → 7 → 21 days.
 * - The item is mastered after 2 consecutive correct reviews answered
 *   at an interval of 7 days or more; it then leaves the active deck.
 * - A wrong review at any point drops it back to 1 day (and un-masters
 *   it if the same question is missed again in a regular quiz).
 */
object MistakesSrs {
    const val DAY_MS: Long = 86_400_000L
    const val FIRST_INTERVAL_DAYS: Int = 1
    const val MASTERY_MIN_INTERVAL_DAYS: Int = 7
    const val MASTERY_STREAK: Int = 2

    fun initial(): MistakeSrsState = MistakeSrsState(FIRST_INTERVAL_DAYS, 0, false)

    fun nextIntervalDays(current: Int): Int = when {
        current < 3 -> 3
        current < 7 -> 7
        else -> 21
    }

    fun onWrong(state: MistakeSrsState): MistakeSrsState =
        MistakeSrsState(FIRST_INTERVAL_DAYS, 0, false)

    fun onCorrect(state: MistakeSrsState): MistakeSrsState {
        if (state.mastered) return state
        val streak = if (state.intervalDays >= MASTERY_MIN_INTERVAL_DAYS) {
            state.longIntervalStreak + 1
        } else {
            0
        }
        if (streak >= MASTERY_STREAK) {
            return MistakeSrsState(state.intervalDays, streak, true)
        }
        return MistakeSrsState(nextIntervalDays(state.intervalDays), streak, false)
    }

    fun dueAtMillis(fromMillis: Long, intervalDays: Int): Long =
        fromMillis + intervalDays * DAY_MS

    fun isDue(entry: MistakeEntry, nowMillis: Long): Boolean =
        !entry.mastered && entry.dueAt <= nowMillis

    /**
     * «درّبني الآن» queue: due items first (most overdue first), then
     * the most recent other mistakes; mastered items never queue.
     */
    fun buildSessionQueue(
        entries: List<MistakeEntry>,
        nowMillis: Long,
        limit: Int = 20
    ): List<MistakeEntry> {
        val active = entries.filter { !it.mastered }
        val due = active.filter { it.dueAt <= nowMillis }.sortedBy { it.dueAt }
        val rest = active.filter { it.dueAt > nowMillis }.sortedByDescending { it.lastWrongAt }
        return (due + rest).take(limit.coerceAtLeast(0))
    }
}

data class MistakesStats(
    val total: Int,
    val dueToday: Int,
    val mastered: Int
)

// ---------------------------------------------------------------------
// Local store (Android) — SharedPreferences + JSON, per account.
// ---------------------------------------------------------------------

object MistakesBank {
    private const val PREF_BASE = "watani_mistakes_bank"
    private const val KEY_ENTRIES_JSON = "entries_json"
    private const val MAX_ENTRIES = 1000

    private val lock = Any()
    private var cache: MutableList<MistakeEntry>? = null
    private var cacheOwner: String? = null

    /** Bumped on every mutation so Compose readers refresh. */
    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes.asStateFlow()

    private fun ownerUid(): String? = try {
        FirebaseAuth.getInstance().currentUser?.uid
    } catch (_: Exception) {
        null
    }

    private fun prefs(context: Context): SharedPreferences {
        val uid = ownerUid()
        val name = if (uid.isNullOrBlank()) PREF_BASE else "${PREF_BASE}_$uid"
        return context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    private fun loadLocked(context: Context): MutableList<MistakeEntry> {
        val owner = ownerUid()
        val cached = cache
        if (cached != null && cacheOwner == owner) return cached
        val raw = prefs(context).getString(KEY_ENTRIES_JSON, null)
        val loaded = if (raw.isNullOrBlank()) mutableListOf() else parseEntries(raw)
        cache = loaded
        cacheOwner = owner
        return loaded
    }

    private fun persistLocked(context: Context, entries: List<MistakeEntry>) {
        prefs(context).edit().putString(KEY_ENTRIES_JSON, serializeEntries(entries)).apply()
        _changes.value = _changes.value + 1
    }

    /** Snapshot of every recorded mistake (active + mastered). */
    fun entries(context: Context): List<MistakeEntry> = synchronized(lock) {
        loadLocked(context).toList()
    }

    fun stats(context: Context, nowMillis: Long = System.currentTimeMillis()): MistakesStats =
        synchronized(lock) {
            val all = loadLocked(context)
            MistakesStats(
                total = all.count { !it.mastered },
                dueToday = all.count { MistakesSrs.isDue(it, nowMillis) },
                mastered = all.count { it.mastered }
            )
        }

    fun sessionQueue(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
        limit: Int = 20,
        subject: String? = null
    ): List<MistakeEntry> = synchronized(lock) {
        val all = loadLocked(context)
        val scoped = if (subject.isNullOrBlank()) all else all.filter { it.subject == subject }
        MistakesSrs.buildSessionQueue(scoped, nowMillis, limit)
    }

    /**
     * Records a wrong answer from any quiz surface. Re-missing a known
     * question refreshes its payload, bumps its counter and resets its
     * schedule to 1 day (un-mastering it if it had left the deck).
     */
    fun recordWrong(
        context: Context,
        bank: String,
        file: String,
        questionId: String?,
        indexInFile: Int,
        subject: String,
        stem: String,
        options: List<String>,
        correctIndex: Int,
        explanation: String,
        whyCorrect: String? = null,
        whyWrong: Map<String, String>? = null,
        summary: String? = null,
        ltr: Boolean = false,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        if (stem.isBlank() || options.isEmpty() || correctIndex !in options.indices) return
        val key = MistakeKeys.composeKey(bank, file, questionId, indexInFile, stem)
        synchronized(lock) {
            val list = loadLocked(context)
            val existingIdx = list.indexOfFirst { it.key == key }
            if (existingIdx >= 0) {
                val old = list[existingIdx]
                val srs = MistakesSrs.onWrong(old.srsState())
                list[existingIdx] = old.copy(
                    bank = bank,
                    file = file,
                    questionId = questionId?.trim().orEmpty(),
                    subject = subject,
                    stem = stem,
                    options = options,
                    correctIndex = correctIndex,
                    explanation = explanation,
                    whyCorrect = whyCorrect,
                    whyWrong = whyWrong,
                    summary = summary,
                    ltr = ltr,
                    lastWrongAt = nowMillis,
                    wrongCount = old.wrongCount + 1,
                    intervalDays = srs.intervalDays,
                    longIntervalStreak = srs.longIntervalStreak,
                    mastered = srs.mastered,
                    dueAt = MistakesSrs.dueAtMillis(nowMillis, srs.intervalDays)
                )
            } else {
                val srs = MistakesSrs.initial()
                list.add(
                    MistakeEntry(
                        key = key,
                        bank = bank,
                        file = file,
                        questionId = questionId?.trim().orEmpty(),
                        subject = subject,
                        stem = stem,
                        options = options,
                        correctIndex = correctIndex,
                        explanation = explanation,
                        whyCorrect = whyCorrect,
                        whyWrong = whyWrong,
                        summary = summary,
                        ltr = ltr,
                        firstWrongAt = nowMillis,
                        lastWrongAt = nowMillis,
                        wrongCount = 1,
                        intervalDays = srs.intervalDays,
                        longIntervalStreak = srs.longIntervalStreak,
                        mastered = false,
                        dueAt = MistakesSrs.dueAtMillis(nowMillis, srs.intervalDays)
                    )
                )
                trimLocked(list)
            }
            persistLocked(context, list)
        }
    }

    /** Applies one deck-review answer to the item's schedule. */
    fun recordReviewResult(
        context: Context,
        key: String,
        correct: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        synchronized(lock) {
            val list = loadLocked(context)
            val idx = list.indexOfFirst { it.key == key }
            if (idx < 0) return
            val old = list[idx]
            val srs = if (correct) {
                MistakesSrs.onCorrect(old.srsState())
            } else {
                MistakesSrs.onWrong(old.srsState())
            }
            list[idx] = old.copy(
                intervalDays = srs.intervalDays,
                longIntervalStreak = srs.longIntervalStreak,
                mastered = srs.mastered,
                dueAt = if (srs.mastered) old.dueAt else MistakesSrs.dueAtMillis(nowMillis, srs.intervalDays),
                wrongCount = if (correct) old.wrongCount else old.wrongCount + 1,
                lastWrongAt = if (correct) old.lastWrongAt else nowMillis
            )
            persistLocked(context, list)
        }
    }

    /** «مسح بنك الأخطاء» — wipes the whole bank for this account. */
    fun clear(context: Context) {
        synchronized(lock) {
            val list = loadLocked(context)
            list.clear()
            persistLocked(context, list)
        }
    }

    /** Cap guard: mastered items are evicted first (oldest miss first),
     * then the stalest active items. Keeps the store bounded no matter
     * how many quizzes the student takes. */
    private fun trimLocked(list: MutableList<MistakeEntry>) {
        while (list.size > MAX_ENTRIES) {
            val victim = list.filter { it.mastered }.minByOrNull { it.lastWrongAt }
                ?: list.minByOrNull { it.lastWrongAt }
                ?: return
            list.remove(victim)
        }
    }

    private fun serializeEntries(entries: List<MistakeEntry>): String {
        val arr = JSONArray()
        entries.forEach { e ->
            val o = JSONObject()
            o.put("key", e.key)
            o.put("bank", e.bank)
            o.put("file", e.file)
            o.put("questionId", e.questionId)
            o.put("subject", e.subject)
            o.put("stem", e.stem)
            val opts = JSONArray()
            e.options.forEach { opts.put(it) }
            o.put("options", opts)
            o.put("correctIndex", e.correctIndex)
            o.put("explanation", e.explanation)
            o.put("whyCorrect", e.whyCorrect ?: JSONObject.NULL)
            o.put("summary", e.summary ?: JSONObject.NULL)
            if (e.whyWrong != null) {
                val ww = JSONObject()
                e.whyWrong.forEach { (k, v) -> ww.put(k, v) }
                o.put("whyWrong", ww)
            } else {
                o.put("whyWrong", JSONObject.NULL)
            }
            o.put("ltr", e.ltr)
            o.put("firstWrongAt", e.firstWrongAt)
            o.put("lastWrongAt", e.lastWrongAt)
            o.put("wrongCount", e.wrongCount)
            o.put("intervalDays", e.intervalDays)
            o.put("longIntervalStreak", e.longIntervalStreak)
            o.put("mastered", e.mastered)
            o.put("dueAt", e.dueAt)
            arr.put(o)
        }
        return arr.toString()
    }

    private fun parseEntries(json: String): MutableList<MistakeEntry> {
        return try {
            val arr = JSONArray(json)
            val out = mutableListOf<MistakeEntry>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val optsArr = o.optJSONArray("options") ?: JSONArray()
                val opts = buildList {
                    for (j in 0 until optsArr.length()) add(optsArr.optString(j, ""))
                }
                val wwObj = o.optJSONObject("whyWrong")
                val ww = wwObj?.let {
                    val m = mutableMapOf<String, String>()
                    it.keys().forEach { k -> m[k] = it.optString(k, "") }
                    m.toMap()
                }
                out.add(
                    MistakeEntry(
                        key = o.optString("key", ""),
                        bank = o.optString("bank", "quiz"),
                        file = o.optString("file", ""),
                        questionId = o.optString("questionId", ""),
                        subject = o.optString("subject", ""),
                        stem = o.optString("stem", ""),
                        options = opts,
                        correctIndex = o.optInt("correctIndex", 0),
                        explanation = o.optString("explanation", ""),
                        whyCorrect = if (o.isNull("whyCorrect")) null else o.optString("whyCorrect", ""),
                        whyWrong = ww,
                        summary = if (o.isNull("summary")) null else o.optString("summary", ""),
                        ltr = o.optBoolean("ltr", false),
                        firstWrongAt = o.optLong("firstWrongAt", 0L),
                        lastWrongAt = o.optLong("lastWrongAt", 0L),
                        wrongCount = o.optInt("wrongCount", 1),
                        intervalDays = o.optInt("intervalDays", 1),
                        longIntervalStreak = o.optInt("longIntervalStreak", 0),
                        mastered = o.optBoolean("mastered", false),
                        dueAt = o.optLong("dueAt", 0L)
                    )
                )
            }
            out
        } catch (_: Exception) {
            mutableListOf()
        }
    }
}
