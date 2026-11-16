package sy.medpro.watani.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import kotlin.math.ceil

/**
 * الهدف اليومي الذكي (بناء 1.2.23).
 *
 * الهدف = أسئلة البنوك المنزّلة المتبقية ÷ الأيام حتى الامتحان، يُعاد
 * حسابه كل يوم من مخازن التقدم القائمة نفسها (OfflineStudyVault لسجل
 * الإجابات وتاريخ الامتحان، وسجل الأقسام المنزّلة لإجمالي البنوك) —
 * لا تتبّع موازٍ جديد. الطالب يقدر يثبّت هدفه يدويًا فيلغي الحساب
 * التلقائي حتى يزيل التثبيت.
 */

// ---------------------------------------------------------------------
// Pure goal math (no Android imports — unit tested).
// ---------------------------------------------------------------------

object DailyGoalMath {

    /** Questions still unanswered: bank total minus everything the
     * student has answered so far (never negative). */
    fun remainingQuestions(bankTotal: Int, answeredTotal: Int): Int =
        (bankTotal - answeredTotal).coerceAtLeast(0)

    /**
     * Auto goal for today. Spread evenly over the days left INCLUDING
     * today (daysLeft counts full days until the exam date, so the
     * divisor is daysLeft + 1 study days). On the exam day itself
     * (daysLeft == 0) everything left is today's goal. Nothing left
     * means no goal.
     */
    fun autoGoal(remaining: Int, daysLeft: Int): Int {
        if (remaining <= 0) return 0
        if (daysLeft <= 0) return remaining
        return ceil(remaining.toDouble() / (daysLeft + 1).toDouble()).toInt().coerceAtLeast(1)
    }

    /** A manual pin wins over the auto goal until cleared. */
    fun effectiveGoal(autoGoal: Int, manualGoal: Int?): Int =
        manualGoal?.coerceAtLeast(1) ?: autoGoal

    fun progressFraction(doneToday: Int, goal: Int): Float =
        if (goal <= 0) 1f else (doneToday.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
}

// ---------------------------------------------------------------------
// Manual-goal store — per account, same binding pattern as MistakesBank.
// ---------------------------------------------------------------------

object DailyGoalStore {
    private const val PREF_BASE = "watani_daily_goal"
    private const val KEY_MANUAL_GOAL = "manual_goal"

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

    /** The student's pinned goal, or null while the auto goal rules. */
    fun manualGoal(context: Context): Int? {
        val raw = prefs(context).getInt(KEY_MANUAL_GOAL, -1)
        return if (raw >= 1) raw else null
    }

    fun setManualGoal(context: Context, goal: Int?) {
        val editor = prefs(context).edit()
        if (goal == null) editor.remove(KEY_MANUAL_GOAL)
        else editor.putInt(KEY_MANUAL_GOAL, goal.coerceIn(1, 2000))
        editor.apply()
    }
}

// ---------------------------------------------------------------------
// Snapshot — one computation shared by the home card, the widget and
// the study reminder, so all three always agree.
// ---------------------------------------------------------------------

data class DailyGoalSnapshot(
    /** Total questions in the downloaded question-bank sections. */
    val bankTotal: Int,
    val answeredTotal: Int,
    val remaining: Int,
    /** Days until the effective exam date (student → panel → manual). */
    val daysLeft: Int,
    /** Effective exam target (epoch millis, local midnight). */
    val examTargetMillis: Long?,
    val autoGoal: Int,
    val manualGoal: Int?,
    /** Today's goal (manual pin when set, else the auto goal). */
    val goal: Int,
    /** Questions answered today (the vault's own daily counter). */
    val doneToday: Int
) {
    val hasBank: Boolean get() = bankTotal > 0
    val isManual: Boolean get() = manualGoal != null
}

object DailyGoalEngine {

    /** Fallback exam date for the widget countdown when neither the
     * student nor the admin panel has set one (doctor's spec). */
    const val DEFAULT_EXAM_DATE: String = "2026-12-22"

    fun defaultExamTargetMillis(): Long {
        val zone = java.time.ZoneId.systemDefault()
        return java.time.LocalDate.parse(DEFAULT_EXAM_DATE)
            .atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /**
     * Binds the vault to the signed-in account before reading it —
     * widget/receiver processes start with the vault unbound (guest),
     * and [OfflineStudyVault.switchAccount] is the app's own binding
     * entry point (a no-op when the owner already matches).
     */
    private fun bindVault(context: Context) {
        try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            OfflineStudyVault.switchAccount(context, uid)
        } catch (_: Exception) {
            try {
                OfflineStudyVault.ensureInitialized(context)
            } catch (_: Exception) {
            }
        }
    }

    fun snapshot(context: Context): DailyGoalSnapshot {
        bindVault(context)
        val stats = OfflineStudyVault.statsFlow.value
        val bankTotal = WataniRepository.downloadedBankQuestionTotal(context)
        val remaining = DailyGoalMath.remainingQuestions(bankTotal, stats.totalAnswered)
        val target = stats.examDateMillis ?: stats.adminExamDateMillis
        val daysLeft = target?.let { OfflineStudyVault.daysUntilExam(it) }
            ?: stats.examDaysRemaining
        val auto = DailyGoalMath.autoGoal(remaining, daysLeft)
        val manual = DailyGoalStore.manualGoal(context)
        return DailyGoalSnapshot(
            bankTotal = bankTotal,
            answeredTotal = stats.totalAnswered,
            remaining = remaining,
            daysLeft = daysLeft,
            examTargetMillis = target,
            autoGoal = auto,
            manualGoal = manual,
            goal = DailyGoalMath.effectiveGoal(auto, manual),
            doneToday = stats.answeredToday
        )
    }

    /** Exam target for countdown surfaces: student's date, else the
     * panel's stored date, else the fixed default (2026-12-22). */
    fun examTargetOrDefault(context: Context): Long {
        bindVault(context)
        val stats = OfflineStudyVault.statsFlow.value
        return stats.examDateMillis ?: stats.adminExamDateMillis ?: defaultExamTargetMillis()
    }
}
