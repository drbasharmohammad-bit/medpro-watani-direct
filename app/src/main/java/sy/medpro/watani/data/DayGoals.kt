package sy.medpro.watani.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import java.time.LocalDate

/**
 * أهداف اليوم بضبط الطالب نفسه (بناء 1.2.24 — إعادة تصميم الويدجت
 * والأهداف بأمر الدكتور بعد تجربته ويدجت 1.2.23 على جهازه).
 *
 * نموذج البيانات:
 * - هدف الأسئلة اليومي رقم يختاره الطالب بنفسه ويُحفظ في
 *   [DailyGoalStore] (نفس مخزن تثبيت 1.2.23 — من ثبّت رقمًا هناك
 *   يبقى رقمه). الرقم التلقائي (المتبقي ÷ الأيام) لم يعد هدفًا
 *   معروضًا أبدًا؛ يُعرض فقط كاقتراح اختياري داخل حوار الضبط.
 * - أهداف نصية حرة لليوم (اسم مادة يدرسها أو أي نص يكتبه): قائمة
 *   [DayGoalItem] تُضاف وتُحذف وتُعلَّم منجزة، وتخصّ يومًا واحدًا —
 *   مع بداية يوم جديد تبدأ فارغة (rollover بالتاريخ المحفوظ).
 *
 * كل تعديل من واجهة التطبيق يتبعه بثّ تحديث لعائلة الويدجتات
 * (ui.widget.Widgets.updateAll) فتنعكس الأهداف على الشاشة
 * الرئيسية للهاتف مباشرة.
 */

// ---------------------------------------------------------------------
// Pure item logic (no Android imports — unit tested).
// ---------------------------------------------------------------------

data class DayGoalItem(
    val id: Int,
    val text: String,
    val done: Boolean = false
)

object DayGoalsMath {

    const val MAX_ITEMS: Int = 12
    const val MAX_TEXT_LENGTH: Int = 80

    private const val FIELD_SEP = "\u001F"
    private const val RECORD_SEP = "\u001E"

    /**
     * Normalizes a free-text goal the student typed: trims, collapses
     * every whitespace run (including newlines) to one space, drops
     * control characters (so the storage separators can never appear
     * inside a text), and caps the length. Returns null when nothing
     * usable remains — the caller then rejects the add.
     */
    fun sanitizeText(raw: String?): String? {
        if (raw == null) return null
        val cleaned = raw
            .filter { it >= ' ' || it == '\t' || it == '\n' }
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_TEXT_LENGTH)
            .trim()
        return cleaned.takeIf { it.isNotEmpty() }
    }

    /** Adds a goal; returns the list unchanged when the text is
     * unusable or the day already holds [MAX_ITEMS] goals. */
    fun addItem(items: List<DayGoalItem>, rawText: String?, newId: Int): List<DayGoalItem> {
        if (items.size >= MAX_ITEMS) return items
        val text = sanitizeText(rawText) ?: return items
        return items + DayGoalItem(id = newId, text = text, done = false)
    }

    fun toggleItem(items: List<DayGoalItem>, id: Int): List<DayGoalItem> =
        items.map { if (it.id == id) it.copy(done = !it.done) else it }

    fun removeItem(items: List<DayGoalItem>, id: Int): List<DayGoalItem> =
        items.filterNot { it.id == id }

    fun doneCount(items: List<DayGoalItem>): Int = items.count { it.done }

    /**
     * A stored list belongs to its stored date only: same date keeps
     * the items, any other date (a new day, or a clock moved back)
     * starts the day empty.
     */
    fun rollover(storedDate: String?, today: String, items: List<DayGoalItem>): List<DayGoalItem> =
        if (storedDate == today) items else emptyList()

    /** Compact single-string encoding for SharedPreferences. Fields
     * are separator-joined; sanitized texts can never contain the
     * separators, so no escaping layer is needed. */
    fun encode(items: List<DayGoalItem>): String =
        items.joinToString(RECORD_SEP) { item ->
            listOf(if (item.done) "1" else "0", item.id.toString(), item.text)
                .joinToString(FIELD_SEP)
        }

    /** Inverse of [encode]; malformed records are skipped, never
     * fatal — a corrupt store degrades to fewer goals, not a crash. */
    fun decode(raw: String?): List<DayGoalItem> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split(RECORD_SEP).mapNotNull { record ->
            val parts = record.split(FIELD_SEP)
            if (parts.size != 3) return@mapNotNull null
            val id = parts[1].toIntOrNull() ?: return@mapNotNull null
            val text = sanitizeText(parts[2]) ?: return@mapNotNull null
            DayGoalItem(id = id, text = text, done = parts[0] == "1")
        }.take(MAX_ITEMS)
    }
}

// ---------------------------------------------------------------------
// Countdown split math (pure — unit tested). The widget shows whole
// days as text and lets a RemoteViews Chronometer tick the within-day
// remainder (hours/minutes/seconds) by itself in the launcher
// process — the app never wakes per second, so no battery drain.
// ---------------------------------------------------------------------

object CountdownMath {

    const val DAY_MILLIS: Long = 86_400_000L

    data class Parts(val days: Long, val withinDayMillis: Long)

    /** Splits a remaining duration into whole days + the remainder
     * of the current day. Non-positive input collapses to zero. */
    fun split(remainingMillis: Long): Parts {
        if (remainingMillis <= 0L) return Parts(0L, 0L)
        return Parts(
            days = remainingMillis / DAY_MILLIS,
            withinDayMillis = remainingMillis % DAY_MILLIS
        )
    }

    /** Chronometer base for a count-down to the end of the current
     * day remainder: elapsedRealtime now + the within-day part. */
    fun chronometerBase(elapsedRealtimeNow: Long, withinDayMillis: Long): Long =
        elapsedRealtimeNow + withinDayMillis

    /** Integer progress percent for RemoteViews progress bars. */
    fun progressPercent(done: Int, target: Int): Int =
        if (target <= 0) 0 else (done * 100 / target).coerceIn(0, 100)
}

// ---------------------------------------------------------------------
// Store — per account, same binding pattern as DailyGoalStore.
// ---------------------------------------------------------------------

object DayGoalsStore {
    private const val PREF_BASE = "watani_day_goals"
    private const val KEY_DATE = "goals_date"
    private const val KEY_ITEMS = "goals_items"
    private const val KEY_NEXT_ID = "goals_next_id"

    private fun ownerUid(): String? = try {
        FirebaseAuth.getInstance().currentUser?.uid
    } catch (_: Exception) {
        null
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(
            ownerUid()?.let { "${PREF_BASE}_$it" } ?: PREF_BASE,
            Context.MODE_PRIVATE
        )

    private fun today(): String = LocalDate.now().toString()

    /**
     * Today's text goals. Applies the date rollover on read and
     * writes the fresh day back, so the app and the widget (which
     * reads through this same function) always agree on the list.
     */
    fun todayItems(context: Context): List<DayGoalItem> {
        val p = prefs(context)
        val storedDate = p.getString(KEY_DATE, null)
        val decoded = DayGoalsMath.decode(p.getString(KEY_ITEMS, null))
        val rolled = DayGoalsMath.rollover(storedDate, today(), decoded)
        if (storedDate != today()) {
            p.edit()
                .putString(KEY_DATE, today())
                .putString(KEY_ITEMS, DayGoalsMath.encode(rolled))
                .apply()
        }
        return rolled
    }

    fun saveItems(context: Context, items: List<DayGoalItem>) {
        prefs(context).edit()
            .putString(KEY_DATE, today())
            .putString(KEY_ITEMS, DayGoalsMath.encode(items.take(DayGoalsMath.MAX_ITEMS)))
            .apply()
    }

    /** Monotonic id source for new items (persisted, never reused
     * within the store's lifetime, so toggles/deletes stay exact). */
    fun nextId(context: Context): Int {
        val p = prefs(context)
        val next = p.getInt(KEY_NEXT_ID, 1)
        p.edit().putInt(KEY_NEXT_ID, next + 1).apply()
        return next
    }
}
