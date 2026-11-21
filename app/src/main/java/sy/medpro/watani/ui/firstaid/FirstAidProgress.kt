package sy.medpro.watani.ui.firstaid

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth

/**
 * Pure unlock/completion rules for the First Aid paths (build 1.2.36) —
 * kept free of Android so the JVM tests pin them exactly:
 *
 * - Step 2 path unit: DONE when its best exit-check fraction reaches
 *   the pack's own passThreshold (0.6 — «خروج ≥60% يفتح التالية»).
 * - Step 1 path unit: the source packs have NO pre/exit checks at all,
 *   so DONE = the training quiz was finished (merge plan §5.2 ruling).
 * - Integration sessions (INT/RAPI, either step) complete by
 *   finishing their training quiz.
 * - NO HARD LOCKS (doctor's decision, build 1.2.38): the course
 *   order is advisory only — [status] never returns LOCKED anymore;
 *   every unit is openable on request, and the exit ≥60% rule stays
 *   purely the DEFINITION of Step 2 completion (progress math), not
 *   an opening gate. The LOCKED enum value is kept for source
 *   compatibility with older call sites but is never produced.
 */
object FirstAidProgressRules {

    const val EXIT_PASS_THRESHOLD = 0.6

    enum class UnitStatus { LOCKED, AVAILABLE, DONE }

    /** Exit-check verdict: at least [EXIT_PASS_THRESHOLD] of a non-empty check. */
    fun exitPasses(correct: Int, total: Int): Boolean =
        total > 0 && correct.toDouble() / total.toDouble() >= EXIT_PASS_THRESHOLD

    fun step2Done(exitBestFraction: Double?): Boolean =
        exitBestFraction != null && exitBestFraction >= EXIT_PASS_THRESHOLD

    fun step1Done(trainingFinished: Boolean): Boolean = trainingFinished

    fun status(
        orderedPathUnitIds: List<String>,
        unitId: String,
        doneIds: Set<String>,
        isIntegrationSession: Boolean
    ): UnitStatus {
        // 1.2.38: no hard locks — a unit is DONE or AVAILABLE, never
        // LOCKED, whatever the state of its predecessors. The
        // parameters stay so callers keep compiling unchanged.
        @Suppress("UNUSED_PARAMETER") val advisoryOrder = orderedPathUnitIds
        @Suppress("UNUSED_PARAMETER") val session = isIntegrationSession
        return if (unitId in doneIds) UnitStatus.DONE else UnitStatus.AVAILABLE
    }
}

/**
 * Per-account First Aid progress (build 1.2.36): an EcgProgress-shaped
 * SharedPreferences store, but account-bound like AnkiSrs — the prefs
 * file is `firstaid_progress_<uid>` (guest file without a suffix), so
 * progress follows the student's Google account on this device and
 * never mixes between accounts. The section is free, so nothing here
 * is ever wiped by activation changes.
 *
 * Per unit id it stores: precheck done flag, training finished flag,
 * best exit fraction (x1000 int), flashcards-reviewed flag.
 */
object FirstAidProgress {

    private fun prefs(context: Context): SharedPreferences {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val name = if (uid.isNullOrBlank()) "firstaid_progress" else "firstaid_progress_$uid"
        return context.getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    // ---- stage flags ---------------------------------------------------

    fun isPrecheckDone(context: Context, unitId: String): Boolean =
        prefs(context).getBoolean("pre_$unitId", false)

    fun markPrecheckDone(context: Context, unitId: String) {
        prefs(context).edit().putBoolean("pre_$unitId", true).apply()
    }

    fun isTrainingDone(context: Context, unitId: String): Boolean =
        prefs(context).getBoolean("train_$unitId", false)

    fun markTrainingDone(context: Context, unitId: String) {
        prefs(context).edit().putBoolean("train_$unitId", true).apply()
    }

    fun isFlashcardsDone(context: Context, unitId: String): Boolean =
        prefs(context).getBoolean("cards_$unitId", false)

    fun markFlashcardsDone(context: Context, unitId: String) {
        prefs(context).edit().putBoolean("cards_$unitId", true).apply()
    }

    // ---- exit check ------------------------------------------------------

    /** Best exit fraction ever scored, or null when never attempted. */
    fun exitBestFraction(context: Context, unitId: String): Double? {
        val raw = prefs(context).getInt("exit_$unitId", -1)
        return if (raw < 0) null else raw / 1000.0
    }

    /** Records an exit attempt, keeping the best fraction. Returns the
     * pass verdict of THIS attempt (see [FirstAidProgressRules.exitPasses]). */
    fun recordExit(context: Context, unitId: String, correct: Int, total: Int): Boolean {
        if (total > 0) {
            val fraction = correct.toDouble() / total.toDouble()
            val best = exitBestFraction(context, unitId) ?: 0.0
            if (fraction > best) {
                prefs(context).edit()
                    .putInt("exit_$unitId", (fraction * 1000).toInt())
                    .apply()
            }
        }
        return FirstAidProgressRules.exitPasses(correct, total)
    }

    // ---- last-opened record (1.2.38) -------------------------------------
    //
    // The home resume card derives from the student's OWN trail: the
    // last unit they actually opened (step + id), stored in the same
    // per-account prefs. Before 1.2.38 nothing recorded this, so the
    // card fell back to a fixed course position for every student.

    fun recordUnitOpened(context: Context, step: Int, unitId: String) {
        prefs(context).edit()
            .putInt("last_step", step)
            .putString("last_id", unitId)
            .apply()
    }

    /** (step, unitId) of the last opened unit, or null when the
     * student never opened any unit on this account. */
    fun lastOpened(context: Context): Pair<Int, String>? {
        val p = prefs(context)
        val id = p.getString("last_id", null)?.takeIf { it.isNotBlank() } ?: return null
        val step = p.getInt("last_step", 0)
        return if (step == 1 || step == 2) step to id else null
    }

    // ---- derived state ---------------------------------------------------

    fun isUnitDone(
        context: Context,
        step: Int,
        unitId: String,
        isIntegrationSession: Boolean = false
    ): Boolean {
        if (isIntegrationSession) return isTrainingDone(context, unitId)
        return if (step == 2) {
            FirstAidProgressRules.step2Done(exitBestFraction(context, unitId))
        } else {
            FirstAidProgressRules.step1Done(isTrainingDone(context, unitId))
        }
    }

    fun doneIds(
        context: Context,
        step: Int,
        unitIds: Collection<String>,
        integrationSessionIds: Set<String> = emptySet()
    ): Set<String> = unitIds.filterTo(HashSet()) {
        isUnitDone(context, step, it, it in integrationSessionIds)
    }

    fun status(
        context: Context,
        step: Int,
        orderedPathUnitIds: List<String>,
        unitId: String,
        integrationSessionIds: Set<String> = emptySet()
    ): FirstAidProgressRules.UnitStatus = FirstAidProgressRules.status(
        orderedPathUnitIds = orderedPathUnitIds,
        unitId = unitId,
        doneIds = doneIds(
            context, step,
            orderedPathUnitIds + integrationSessionIds,
            integrationSessionIds
        ),
        isIntegrationSession = unitId in integrationSessionIds
    )
}
