package sy.medpro.watani.ui.skills

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Local progress for the interactive skills trainers (build 1.2.6):
 * best ordering score per OSCE procedure and per-drill miss counts for
 * the dosing trainer. Local-only by design (same rule as AnkiSrs): it
 * never leaves the device and never touches the published content
 * files or their loading flow. Persisted in a per-account
 * SharedPreferences file (`skills_progress_<uid>`, guest file without
 * a suffix) — one value per key:
 * - "proc_best_<procedureId>"  -> best ordering percent (0..100)
 * - "drill_miss_<drillId>"     -> times this drill was answered wrong
 * - "drill_seen_<drillId>"     -> times this drill was answered at all
 */
object SkillsProgress {

    /** Bumped on every write so Compose readers recompute summaries. */
    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes.asStateFlow()

    private fun prefs(context: Context): SharedPreferences {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val name = if (uid.isNullOrBlank()) "skills_progress" else "skills_progress_$uid"
        return context.getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    // ---- OSCE procedures -------------------------------------------------

    fun bestProcedurePercent(context: Context, procedureId: String): Int =
        prefs(context).getInt("proc_best_$procedureId", -1)

    /** Records an ordering attempt; keeps the best percent. Returns the best. */
    fun recordProcedureAttempt(context: Context, procedureId: String, percent: Int): Int {
        val p = prefs(context)
        val best = maxOf(p.getInt("proc_best_$procedureId", -1), percent)
        p.edit().putInt("proc_best_$procedureId", best).apply()
        _changes.value = _changes.value + 1
        return best
    }

    // ---- Dosing drills ---------------------------------------------------

    fun drillMissCount(context: Context, drillId: String): Int =
        prefs(context).getInt("drill_miss_$drillId", 0)

    fun drillSeenCount(context: Context, drillId: String): Int =
        prefs(context).getInt("drill_seen_$drillId", 0)

    /** Records one answered drill (miss counts only grow on a wrong answer). */
    fun recordDrillAnswer(context: Context, drillId: String, correct: Boolean) {
        val p = prefs(context)
        val edit = p.edit()
        edit.putInt("drill_seen_$drillId", p.getInt("drill_seen_$drillId", 0) + 1)
        if (!correct) {
            edit.putInt("drill_miss_$drillId", p.getInt("drill_miss_$drillId", 0) + 1)
        }
        edit.apply()
        _changes.value = _changes.value + 1
    }

    /** Drills the student keeps missing (miss count >= 1), worst first. */
    fun weakDrillIds(context: Context, drillIds: List<String>): List<String> {
        val p = prefs(context)
        return drillIds
            .map { it to p.getInt("drill_miss_$it", 0) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }
}
