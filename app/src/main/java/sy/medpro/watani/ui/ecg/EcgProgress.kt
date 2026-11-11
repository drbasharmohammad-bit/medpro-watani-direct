package sy.medpro.watani.ui.ecg

import android.content.Context

/**
 * Local progress for the ECG mini-course (build 1.2.4). Everything is
 * SharedPreferences on-device: finished method steps, guided reads
 * completed per strip, flash-drill best score, per-pattern miss counts
 * (the adaptive drill weighting), weak-pattern set and the
 * auscultation-quiz best score. No account, no network — fully offline,
 * and deliberately NOT part of the restricted-content stores.
 */
class EcgProgressStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---- method steps -------------------------------------------------
    fun isMethodStepDone(step: Int): Boolean = prefs.getBoolean("method_$step", false)

    fun markMethodStepDone(step: Int) {
        prefs.edit().putBoolean("method_$step", true).apply()
    }

    fun methodDoneCount(total: Int): Int = (1..total).count { isMethodStepDone(it) }

    // ---- guided reader -------------------------------------------------
    fun isGuidedDone(stripId: String): Boolean = prefs.getBoolean("guided_$stripId", false)

    fun markGuidedDone(stripId: String) {
        prefs.edit().putBoolean("guided_$stripId", true).apply()
    }

    fun guidedDoneCount(stripIds: List<String>): Int = stripIds.count { isGuidedDone(it) }

    // ---- patterns library ----------------------------------------------
    fun isPatternSeen(patternId: String): Boolean = prefs.getBoolean("seen_$patternId", false)

    fun markPatternSeen(patternId: String) {
        prefs.edit().putBoolean("seen_$patternId", true).apply()
    }

    // ---- lead map --------------------------------------------------------
    fun isLeadMapDone(): Boolean = prefs.getBoolean("leadmap_done", false)

    fun markLeadMapDone() {
        prefs.edit().putBoolean("leadmap_done", true).apply()
    }

    // ---- flash drill -----------------------------------------------------
    fun drillBestScore(): Int = prefs.getInt("drill_best", 0)

    fun recordDrillScore(score: Int, total: Int) {
        if (score > drillBestScore()) {
            prefs.edit().putInt("drill_best", score).apply()
        }
        prefs.edit().putInt("drill_last_total", total).apply()
    }

    /** Miss counts drive the adaptive weighting (more misses → drawn more). */
    fun missCount(patternId: String): Int = prefs.getInt("miss_$patternId", 0)

    fun recordMiss(patternId: String) {
        prefs.edit().putInt("miss_$patternId", missCount(patternId) + 1).apply()
    }

    fun recordHit(patternId: String) {
        val current = missCount(patternId)
        if (current > 0) {
            prefs.edit().putInt("miss_$patternId", current - 1).apply()
        }
    }

    /** Patterns missed at least twice — the student's weak set. */
    fun weakPatternIds(patternIds: List<String>): List<String> =
        patternIds.filter { missCount(it) >= 2 }

    /** Draw weight for the adaptive drill picker: 1 + misses. */
    fun drawWeight(patternId: String): Int = 1 + missCount(patternId)

    // ---- auscultation quiz ------------------------------------------------
    fun auscQuizBestScore(): Int = prefs.getInt("ausc_quiz_best", 0)

    fun recordAuscQuizScore(score: Int) {
        if (score > auscQuizBestScore()) {
            prefs.edit().putInt("ausc_quiz_best", score).apply()
        }
    }

    companion object {
        const val PREFS_NAME = "ecg_course_progress"
    }
}
