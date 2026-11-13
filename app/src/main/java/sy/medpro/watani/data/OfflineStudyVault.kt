package sy.medpro.watani.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class SavedMistakeItem(
    val id: String,
    val subjectTitle: String,
    val questionText: String,
    val selectedAnswer: String,
    val correctAnswer: String,
    val explanation: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class OfflineStudyStats(
    val totalAnswered: Int = 0,
    val totalCorrect: Int = 0,
    /** Displayed countdown. When an exam date is set (student's own date,
     * else the admin panel's date) this is recomputed from the date on
     * every app start — the old frozen "60" never counted down, so it
     * was wrong the moment time passed. Manual days (chips) are kept in
     * [manualExamDays] and rule only in manual mode. */
    val examDaysRemaining: Int = 60,
    val manualExamDays: Int = 60,
    /** Student-chosen exam date (epoch millis, local midnight), or null. */
    val examDateMillis: Long? = null,
    /** Last exam date fetched from the admin panel, or null. */
    val adminExamDateMillis: Long? = null,
    /** True when the student picked manual days (chips) — dates ignored. */
    val examModeManual: Boolean = false,
    val dailyStudyHours: Int = 8,
    val completedPlanPhaseIds: Set<String> = emptySet(),
    val masteredEponymIds: Set<String> = emptySet(),
    val masteredFlashcardIds: Set<Int> = emptySet(),
    val wrongFlashcardIds: Set<Int> = emptySet(),
    val savedMistakes: List<SavedMistakeItem> = emptyList(),
    val specialtyAnswered: Map<String, Int> = emptyMap(),
    val specialtyCorrect: Map<String, Int> = emptyMap(),
    /** أداء كل مادة بعنوانها الخام كما يمرّ في recordQuestionAttempt
     * (بلا تصنيف الفئات الخمس — تلك لـ[specialtyAnswered]/[specialtyCorrect]
     * وتستعملها واجهات أخرى). تُدفع للبوابة كـsubjectPerformance لتقارير
     * الدراسة الشهرية عبر تليجرام. */
    val subjectAnswered: Map<String, Int> = emptyMap(),
    val subjectCorrect: Map<String, Int> = emptyMap(),
    /** نشاط يومي: yyyy-MM-dd ← عدد الأسئلة المجابة ذلك اليوم (نفس عدّاد
     * [answeredToday] لكنه متراكم عبر الأيام، ويُقتطع لآخر 35 يومًا).
     * يُدفع للبوابة كـdailyActivity. */
    val dailyActivity: Map<String, Int> = emptyMap(),
    /** أسئلة حُلّت اليوم (بطاقة «يومك» بالرئيسية): عدّاد يومي يُصفَّر
     * عند تغيّر [answeredTodayDate] (yyyy-MM-dd). */
    val answeredToday: Int = 0,
    val answeredTodayDate: String = ""
)

object OfflineStudyVault {
    // Account binding (Oct 2026 — the doctor's bug report): progress used
    // to live in ONE store shared by every account on the device, so a
    // second Google account inherited the first account's points, and the
    // sync then pushed them to the new account's leaderboard row. Every
    // store is now per-account: the signed-out (guest) store plus one
    // store per Google uid. A new account starts at zero; the previous
    // account's progress stays under its own uid and returns when that
    // account signs in again. Switching accounts never deletes anything.
    private const val PREF_BASE = "watani_offline_study_vault"
    private var currentOwnerUid: String? = null
    private var ownerLoaded = false

    private fun prefsNameFor(uid: String?): String =
        if (uid.isNullOrBlank()) PREF_BASE else "${PREF_BASE}_$uid"

    private fun prefsFor(context: Context): android.content.SharedPreferences =
        context.applicationContext.getSharedPreferences(prefsNameFor(currentOwnerUid), Context.MODE_PRIVATE)
    private const val KEY_TOTAL_ANSWERED = "total_answered"
    private const val KEY_TOTAL_CORRECT = "total_correct"
    private const val KEY_EXAM_DAYS = "exam_days_remaining"
    private const val KEY_EXAM_DATE = "exam_date_millis"
    private const val KEY_ADMIN_EXAM_DATE = "admin_exam_date_millis"
    private const val KEY_EXAM_MODE_MANUAL = "exam_mode_manual"
    private const val KEY_DAILY_HOURS = "daily_study_hours"
    private const val KEY_COMPLETED_PHASES = "completed_plan_phases"
    private const val KEY_MASTERED_EPONYMS = "mastered_eponyms"
    private const val KEY_MASTERED_FLASHCARDS = "mastered_flashcards"
    private const val KEY_WRONG_FLASHCARDS = "wrong_flashcards"
    private const val KEY_SAVED_MISTAKES_JSON = "saved_mistakes_json"
    private const val KEY_SPECIALTY_STATS_JSON = "specialty_stats_json"
    private const val KEY_SUBJECT_STATS_JSON = "subject_stats_json"
    private const val KEY_DAILY_ACTIVITY_JSON = "daily_activity_json"
    private const val KEY_ANSWERED_TODAY = "answered_today"
    private const val KEY_ANSWERED_TODAY_DATE = "answered_today_date"

    private val _statsFlow = MutableStateFlow(defaultInitialStats())
    val statsFlow: StateFlow<OfflineStudyStats> = _statsFlow.asStateFlow()
    private var isInitialized = false

    private fun defaultInitialStats(): OfflineStudyStats {
        return OfflineStudyStats(
            totalAnswered = 0,
            totalCorrect = 0,
            examDaysRemaining = 60,
            manualExamDays = 60,
            dailyStudyHours = 8,
            savedMistakes = emptyList(),
            specialtyAnswered = emptyMap(),
            specialtyCorrect = emptyMap(),
            subjectAnswered = emptyMap(),
            subjectCorrect = emptyMap(),
            dailyActivity = emptyMap()
        )
    }

    /** Days from today (local) until the given date millis; past dates read 0. */
    fun daysUntilExam(dateMillis: Long): Int {
        return try {
            val zone = java.time.ZoneId.systemDefault()
            val today = java.time.LocalDate.now(zone)
            val target = java.time.Instant.ofEpochMilli(dateMillis).atZone(zone).toLocalDate()
            java.time.temporal.ChronoUnit.DAYS.between(today, target).toInt().coerceAtLeast(0)
        } catch (_: Exception) {
            0
        }
    }

    /** The STUDENT's own date rules whenever it exists — the student
     * picks his exam date (Nov 2026, doctor's order: the timer is the
     * student's, not the panel's). The admin panel date is only a
     * fallback while the student has set no date; manual days are the
     * last fallback. */
    private fun effectiveDays(stats: OfflineStudyStats): Int {
        stats.examDateMillis?.let { return daysUntilExam(it) }
        stats.adminExamDateMillis?.let { return daysUntilExam(it) }
        return stats.manualExamDays
    }

    private fun todayString(): String = java.time.LocalDate.now().toString()

    /** اقتطاع خريطة النشاط اليومي لآخر 35 يومًا (المفاتيح yyyy-MM-dd
     * فالترتيب المعجمي هو الترتيب الزمني نفسه). */
    private fun trimDailyActivity(map: Map<String, Int>): Map<String, Int> =
        if (map.size <= 35) map
        else map.entries.sortedBy { it.key }.takeLast(35).associate { it.key to it.value }

    /** خريطة النشاط اليومي بعد إجابة جديدة: اليوم +1 ثم الاقتطاع. */
    private fun bumpedDailyActivity(current: OfflineStudyStats): Map<String, Int> {
        val today = todayString()
        val next = current.dailyActivity.toMutableMap()
        next[today] = (next[today] ?: 0) + 1
        return trimDailyActivity(next)
    }

    /** خطوة العدّاد اليومي: يوم جديد يبدأ من 1، ونفس اليوم يزيد 1. */
    private fun bumpedAnsweredToday(current: OfflineStudyStats): Pair<Int, String> {
        val today = todayString()
        return if (current.answeredTodayDate == today) {
            (current.answeredToday + 1) to today
        } else {
            1 to today
        }
    }

    fun ensureInitialized(context: Context) {
        if (isInitialized) return
        val prefs = prefsFor(context)
        if (!prefs.contains(KEY_TOTAL_ANSWERED)) {
            isInitialized = true
            _statsFlow.value = _statsFlow.value.copy(answeredToday = 0, answeredTodayDate = todayString())
            persist(context, _statsFlow.value)
            fetchAdminExamDate(context)
            return
        }

        val totalAns = prefs.getInt(KEY_TOTAL_ANSWERED, 0)
        val totalCor = prefs.getInt(KEY_TOTAL_CORRECT, 0)
        val examDays = prefs.getInt(KEY_EXAM_DAYS, 60)
        val examDate = prefs.getLong(KEY_EXAM_DATE, -1L).takeIf { it >= 0L }
        val adminExamDate = prefs.getLong(KEY_ADMIN_EXAM_DATE, -1L).takeIf { it >= 0L }
        val examManual = prefs.getBoolean(KEY_EXAM_MODE_MANUAL, false)
        val dailyHrs = prefs.getInt(KEY_DAILY_HOURS, 8)
        val completedPhases = prefs.getStringSet(KEY_COMPLETED_PHASES, emptySet()) ?: emptySet()
        val masteredEponyms = prefs.getStringSet(KEY_MASTERED_EPONYMS, emptySet()) ?: emptySet()
        val masteredFlashcards = (prefs.getStringSet(KEY_MASTERED_FLASHCARDS, emptySet()) ?: emptySet())
            .mapNotNull { it.toIntOrNull() }
            .toSet()
        val wrongFlashcards = (prefs.getStringSet(KEY_WRONG_FLASHCARDS, emptySet()) ?: emptySet())
            .mapNotNull { it.toIntOrNull() }
            .toSet()

        val mistakesJson = prefs.getString(KEY_SAVED_MISTAKES_JSON, null)
        val mistakesList = if (!mistakesJson.isNullOrBlank()) {
            parseMistakes(mistakesJson)
        } else {
            defaultInitialStats().savedMistakes
        }

        val specJson = prefs.getString(KEY_SPECIALTY_STATS_JSON, null)
        val (specAns, specCor) = if (!specJson.isNullOrBlank()) {
            parseSpecialtyStats(specJson)
        } else {
            val d = defaultInitialStats()
            d.specialtyAnswered to d.specialtyCorrect
        }

        // أداء المواد بالعنوان الخام: نفس صيغة JSON الخاصة بإحصاءات الفئات.
        val subjectJson = prefs.getString(KEY_SUBJECT_STATS_JSON, null)
        val (subjAns, subjCor) = if (!subjectJson.isNullOrBlank()) {
            parseSpecialtyStats(subjectJson)
        } else {
            emptyMap<String, Int>() to emptyMap()
        }

        val dailyJson = prefs.getString(KEY_DAILY_ACTIVITY_JSON, null)
        val dailyAct = if (!dailyJson.isNullOrBlank()) {
            parseDailyActivity(dailyJson)
        } else {
            emptyMap()
        }

        // العدّاد اليومي: تاريخ مخزّن مختلف عن اليوم يعني يومًا جديدًا — يُصفَّر.
        val storedTodayDate = prefs.getString(KEY_ANSWERED_TODAY_DATE, "") ?: ""
        val storedAnsweredToday = prefs.getInt(KEY_ANSWERED_TODAY, 0)
        val today = todayString()
        val (loadedAnsweredToday, loadedAnsweredTodayDate) =
            if (storedTodayDate == today) storedAnsweredToday to storedTodayDate else 0 to today

        val baseStats = OfflineStudyStats(
            totalAnswered = totalAns,
            totalCorrect = totalCor,
            answeredToday = loadedAnsweredToday,
            answeredTodayDate = loadedAnsweredTodayDate,
            examDaysRemaining = examDays,
            manualExamDays = examDays,
            examDateMillis = examDate,
            adminExamDateMillis = adminExamDate,
            examModeManual = examManual,
            dailyStudyHours = dailyHrs,
            completedPlanPhaseIds = completedPhases,
            masteredEponymIds = masteredEponyms,
            masteredFlashcardIds = masteredFlashcards,
            wrongFlashcardIds = wrongFlashcards,
            savedMistakes = mistakesList,
            specialtyAnswered = specAns,
            specialtyCorrect = specCor,
            subjectAnswered = subjAns,
            subjectCorrect = subjCor,
            dailyActivity = dailyAct
        )
        _statsFlow.value = baseStats.copy(examDaysRemaining = effectiveDays(baseStats))
        isInitialized = true
        fetchAdminExamDate(context)
    }

    /**
     * Binds the vault to a Google account. Call on every auth change
     * (MainActivity's listener) and at the start of every sync. Switching
     * reloads the flow from that account's own store; signing out loads
     * the guest store, so nothing of the previous account shows. Fresh
     * per-uid stores start at zero — the sync's pull-remote step restores
     * that account's own server totals on first sign-in.
     */
    fun switchAccount(context: Context, uid: String?) {
        if (ownerLoaded && currentOwnerUid == uid) return
        ownerLoaded = true
        currentOwnerUid = uid
        isInitialized = false
        ensureInitialized(context)
    }

    /** Adopts server totals into the current account's vault (flow + disk).
     * Used by the sync's pull-remote step; never writes another account's file. */
    fun adoptRemoteTotals(context: Context, totalAnswered: Int, totalCorrect: Int) {
        ensureInitialized(context)
        val updated = _statsFlow.value.copy(
            totalAnswered = totalAnswered,
            totalCorrect = totalCorrect
        )
        _statsFlow.value = updated
        persist(context, updated)
    }

    /** Adopts the account's own subject-performance / daily-activity maps
     * from the server (first sync on a fresh per-uid store only — the sync
     * gates this on the local maps being empty, so newer local data is
     * never overwritten). A null argument keeps the local value for that
     * part; the daily map is trimmed to the last 35 days on adoption. */
    fun adoptRemoteSubjectStats(
        context: Context,
        subjectAnswered: Map<String, Int>?,
        subjectCorrect: Map<String, Int>?,
        dailyActivity: Map<String, Int>?
    ) {
        ensureInitialized(context)
        val current = _statsFlow.value
        val updated = current.copy(
            subjectAnswered = subjectAnswered ?: current.subjectAnswered,
            subjectCorrect = subjectCorrect ?: current.subjectCorrect,
            dailyActivity = dailyActivity?.let { trimDailyActivity(it) } ?: current.dailyActivity
        )
        _statsFlow.value = updated
        persist(context, updated)
    }

    /** Adopts the account's own saved-mistakes list from the server
     * (first sync on a fresh per-uid store only — the sync gates this on
     * the local list being empty, so newer local data is never overwritten). */
    fun adoptRemoteMistakes(context: Context, mistakes: List<SavedMistakeItem>) {
        ensureInitialized(context)
        val updated = _statsFlow.value.copy(savedMistakes = mistakes)
        _statsFlow.value = updated
        persist(context, updated)
    }

    fun recordQuestionAttempt(
        context: Context,
        subjectTitle: String,
        questionId: String,
        questionText: String,
        selectedOptionText: String,
        correctOptionText: String,
        explanation: String,
        isCorrect: Boolean
    ) {
        ensureInitialized(context)
        val current = _statsFlow.value
        val category = classifySpecialtyCategory(subjectTitle)
        val nextAnsMap = current.specialtyAnswered.toMutableMap().apply {
            put(category, (get(category) ?: 0) + 1)
        }
        val nextCorMap = current.specialtyCorrect.toMutableMap().apply {
            if (isCorrect) put(category, (get(category) ?: 0) + 1)
        }
        // أداء المادة بعنوانها الخام (بلا تصنيف) — لتقارير البوابة الشهرية.
        val rawTitle = subjectTitle.trim()
        val nextSubjectAnsMap = current.subjectAnswered.toMutableMap().apply {
            if (rawTitle.isNotEmpty()) put(rawTitle, (get(rawTitle) ?: 0) + 1)
        }
        val nextSubjectCorMap = current.subjectCorrect.toMutableMap().apply {
            if (isCorrect && rawTitle.isNotEmpty()) put(rawTitle, (get(rawTitle) ?: 0) + 1)
        }

        val nextMistakes = if (!isCorrect) {
            val item = SavedMistakeItem(
                id = questionId,
                subjectTitle = subjectTitle,
                questionText = questionText,
                selectedAnswer = selectedOptionText,
                correctAnswer = correctOptionText,
                explanation = explanation.ifBlank { "الإجابة الصحيحة المعتمدة هي: $correctOptionText" }
            )
            listOf(item) + current.savedMistakes.filterNot { it.questionText == questionText }.take(49)
        } else {
            current.savedMistakes
        }

        val (nextAnsweredToday, nextAnsweredTodayDate) = bumpedAnsweredToday(current)
        val updated = current.copy(
            totalAnswered = current.totalAnswered + 1,
            totalCorrect = current.totalCorrect + (if (isCorrect) 1 else 0),
            savedMistakes = nextMistakes,
            specialtyAnswered = nextAnsMap,
            specialtyCorrect = nextCorMap,
            subjectAnswered = nextSubjectAnsMap,
            subjectCorrect = nextSubjectCorMap,
            dailyActivity = bumpedDailyActivity(current),
            answeredToday = nextAnsweredToday,
            answeredTodayDate = nextAnsweredTodayDate
        )
        _statsFlow.value = updated
        persist(context, updated)
    }

    fun recordCaseCompletion(context: Context, xpReward: Int) {
        ensureInitialized(context)
        val current = _statsFlow.value
        val (nextAnsweredToday, nextAnsweredTodayDate) = bumpedAnsweredToday(current)
        val updated = current.copy(
            totalAnswered = current.totalAnswered + 1,
            totalCorrect = current.totalCorrect + 1,
            dailyActivity = bumpedDailyActivity(current),
            answeredToday = nextAnsweredToday,
            answeredTodayDate = nextAnsweredTodayDate
        )
        _statsFlow.value = updated
        persist(context, updated)
    }

    fun removeMistake(context: Context, mistakeId: String) {
        ensureInitialized(context)
        val current = _statsFlow.value
        val updated = current.copy(
            savedMistakes = current.savedMistakes.filterNot { it.id == mistakeId }
        )
        _statsFlow.value = updated
        persist(context, updated)
    }

    /** Manual days (chips): the student's explicit choice wins over any
     * date — the student's own date is cleared and dates are ignored
     * until the student picks a date again. */
    fun updateStudyPlannerConfig(context: Context, daysRemaining: Int, dailyHours: Int) {
        ensureInitialized(context)
        val days = daysRemaining.coerceIn(7, 180)
        val updated = _statsFlow.value.copy(
            examDaysRemaining = days,
            manualExamDays = days,
            examDateMillis = null,
            examModeManual = true,
            dailyStudyHours = dailyHours.coerceIn(2, 16)
        )
        _statsFlow.value = updated
        persist(context, updated)
    }

    /** Daily hours only — must never disturb the exam date or the mode. */
    fun updateDailyHours(context: Context, dailyHours: Int) {
        ensureInitialized(context)
        val updated = _statsFlow.value.copy(
            dailyStudyHours = dailyHours.coerceIn(2, 16)
        )
        _statsFlow.value = updated
        persist(context, updated)
    }

    /** Student-chosen exam date (epoch millis, local midnight), or null to
     * clear it (falls back to the admin panel date, else manual days). */
    fun setExamDate(context: Context, dateMillis: Long?) {
        ensureInitialized(context)
        val base = _statsFlow.value.copy(
            examDateMillis = dateMillis,
            examModeManual = false
        )
        val updated = base.copy(examDaysRemaining = effectiveDays(base))
        _statsFlow.value = updated
        persist(context, updated)
    }

    /** Admin panel date ("yyyy-MM-dd"), migration phase 3: now read
     * from the gateway (GET /v1/config/exam-date, public) instead of
     * Firestore app_config/exam. Same semantics as before: a fallback
     * source only — the student's own date always wins (effectiveDays);
     * fire-and-forget and structurally cheap, re-run whenever the
     * vault (re)initializes, including on every account switch
     * (switchAccount resets initialization). Offline or a missing
     * date is silently ignored. */
    private fun fetchAdminExamDate(context: Context) {
        try {
            WorkerActivationApi.fetchExamDateAsync { raw ->
                if (raw != null) applyAdminExamDate(context, raw)
            }
        } catch (_: Exception) {
        }
    }

    fun applyAdminExamDate(context: Context, dateString: String?) {
        if (dateString.isNullOrBlank()) return
        val millis = try {
            val zone = java.time.ZoneId.systemDefault()
            java.time.LocalDate.parse(dateString.trim()).atStartOfDay(zone).toInstant().toEpochMilli()
        } catch (_: Exception) {
            return
        }
        ensureInitialized(context)
        val current = _statsFlow.value
        if (current.adminExamDateMillis == millis) return
        val base = current.copy(adminExamDateMillis = millis)
        val updated = base.copy(examDaysRemaining = effectiveDays(base))
        _statsFlow.value = updated
        persist(context, updated)
    }

    fun togglePlanPhaseCompleted(context: Context, phaseId: String) {
        ensureInitialized(context)
        val current = _statsFlow.value
        val nextSet = if (current.completedPlanPhaseIds.contains(phaseId)) {
            current.completedPlanPhaseIds - phaseId
        } else {
            current.completedPlanPhaseIds + phaseId
        }
        val updated = current.copy(completedPlanPhaseIds = nextSet)
        _statsFlow.value = updated
        persist(context, updated)
    }

    fun toggleEponymMastered(context: Context, eponymId: String) {
        ensureInitialized(context)
        val current = _statsFlow.value
        val nextSet = if (current.masteredEponymIds.contains(eponymId)) {
            current.masteredEponymIds - eponymId
        } else {
            current.masteredEponymIds + eponymId
        }
        val updated = current.copy(masteredEponymIds = nextSet)
        _statsFlow.value = updated
        persist(context, updated)
    }

    fun toggleFlashcardMastered(context: Context, cardId: Int) {
        ensureInitialized(context)
        val current = _statsFlow.value
        val nextSet = if (current.masteredFlashcardIds.contains(cardId)) {
            current.masteredFlashcardIds - cardId
        } else {
            current.masteredFlashcardIds + cardId
        }
        val updated = current.copy(masteredFlashcardIds = nextSet)
        _statsFlow.value = updated
        persist(context, updated)
    }

    fun addFlashcardMastered(context: Context, cardId: Int) {
        ensureInitialized(context)
        val current = _statsFlow.value
        val nextWrong = current.wrongFlashcardIds - cardId
        if (current.masteredFlashcardIds.contains(cardId) && nextWrong == current.wrongFlashcardIds) return
        val updated = current.copy(
            masteredFlashcardIds = current.masteredFlashcardIds + cardId,
            wrongFlashcardIds = nextWrong
        )
        _statsFlow.value = updated
        persist(context, updated)
    }

    fun addFlashcardWrong(context: Context, cardId: Int) {
        ensureInitialized(context)
        val current = _statsFlow.value
        if (current.wrongFlashcardIds.contains(cardId)) return
        val updated = current.copy(wrongFlashcardIds = current.wrongFlashcardIds + cardId)
        _statsFlow.value = updated
        persist(context, updated)
    }

    fun removeFlashcardWrong(context: Context, cardId: Int) {
        ensureInitialized(context)
        val current = _statsFlow.value
        if (!current.wrongFlashcardIds.contains(cardId)) return
        val updated = current.copy(wrongFlashcardIds = current.wrongFlashcardIds - cardId)
        _statsFlow.value = updated
        persist(context, updated)
    }

    private fun classifySpecialtyCategory(rawTitle: String): String {
        val t = rawTitle.lowercase()
        return when {
            t.contains("جراح") || t.contains("surgery") -> "الجراحة"
            t.contains("أطفال") || t.contains("اطفال") || t.contains("ped") -> "الأطفال"
            t.contains("نسائ") || t.contains("توليد") || t.contains("ob") || t.contains("gyn") -> "النسائية والتوليد"
            t.contains("جلد") || t.contains("عين") || t.contains("أذن") || t.contains("اذن") -> "الجلدية والعينية والأذنية"
            else -> "الباطنة"
        }
    }

    private fun persist(context: Context, stats: OfflineStudyStats) {
        val prefs = prefsFor(context)
        val mistakesArr = JSONArray()
        stats.savedMistakes.forEach { m ->
            val obj = JSONObject()
            obj.put("id", m.id)
            obj.put("subjectTitle", m.subjectTitle)
            obj.put("questionText", m.questionText)
            obj.put("selectedAnswer", m.selectedAnswer)
            obj.put("correctAnswer", m.correctAnswer)
            obj.put("explanation", m.explanation)
            obj.put("timestamp", m.timestamp)
            mistakesArr.put(obj)
        }

        val specObj = JSONObject()
        val ansObj = JSONObject()
        val corObj = JSONObject()
        stats.specialtyAnswered.forEach { (k, v) -> ansObj.put(k, v) }
        stats.specialtyCorrect.forEach { (k, v) -> corObj.put(k, v) }
        specObj.put("answered", ansObj)
        specObj.put("correct", corObj)

        val subjectObj = JSONObject()
        val subjAnsObj = JSONObject()
        val subjCorObj = JSONObject()
        stats.subjectAnswered.forEach { (k, v) -> subjAnsObj.put(k, v) }
        stats.subjectCorrect.forEach { (k, v) -> subjCorObj.put(k, v) }
        subjectObj.put("answered", subjAnsObj)
        subjectObj.put("correct", subjCorObj)

        val dailyObj = JSONObject()
        stats.dailyActivity.forEach { (k, v) -> dailyObj.put(k, v) }

        val editor = prefs.edit()
            .putInt(KEY_TOTAL_ANSWERED, stats.totalAnswered)
            .putInt(KEY_TOTAL_CORRECT, stats.totalCorrect)
            .putInt(KEY_EXAM_DAYS, stats.manualExamDays)
            .putBoolean(KEY_EXAM_MODE_MANUAL, stats.examModeManual)
        if (stats.examDateMillis != null) editor.putLong(KEY_EXAM_DATE, stats.examDateMillis)
        else editor.remove(KEY_EXAM_DATE)
        if (stats.adminExamDateMillis != null) editor.putLong(KEY_ADMIN_EXAM_DATE, stats.adminExamDateMillis)
        else editor.remove(KEY_ADMIN_EXAM_DATE)
        editor
            .putInt(KEY_DAILY_HOURS, stats.dailyStudyHours)
            .putStringSet(KEY_COMPLETED_PHASES, stats.completedPlanPhaseIds)
            .putStringSet(KEY_MASTERED_EPONYMS, stats.masteredEponymIds)
            .putStringSet(KEY_MASTERED_FLASHCARDS, stats.masteredFlashcardIds.map { it.toString() }.toSet())
            .putStringSet(KEY_WRONG_FLASHCARDS, stats.wrongFlashcardIds.map { it.toString() }.toSet())
            .putString(KEY_SAVED_MISTAKES_JSON, mistakesArr.toString())
            .putString(KEY_SPECIALTY_STATS_JSON, specObj.toString())
            .putString(KEY_SUBJECT_STATS_JSON, subjectObj.toString())
            .putString(KEY_DAILY_ACTIVITY_JSON, dailyObj.toString())
            .putInt(KEY_ANSWERED_TODAY, stats.answeredToday)
            .putString(KEY_ANSWERED_TODAY_DATE, stats.answeredTodayDate)
            .apply()
    }

    private fun parseMistakes(json: String): List<SavedMistakeItem> {
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        SavedMistakeItem(
                            id = o.optString("id", "m_$i"),
                            subjectTitle = o.optString("subjectTitle", "الباطنة"),
                            questionText = o.optString("questionText", ""),
                            selectedAnswer = o.optString("selectedAnswer", ""),
                            correctAnswer = o.optString("correctAnswer", ""),
                            explanation = o.optString("explanation", ""),
                            timestamp = o.optLong("timestamp", 0L)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseDailyActivity(json: String): Map<String, Int> {
        return try {
            val root = JSONObject(json)
            val map = mutableMapOf<String, Int>()
            root.keys().forEach { k -> map[k] = root.optInt(k, 0) }
            trimDailyActivity(map)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun parseSpecialtyStats(json: String): Pair<Map<String, Int>, Map<String, Int>> {
        return try {
            val root = JSONObject(json)
            val ansObj = root.optJSONObject("answered") ?: JSONObject()
            val corObj = root.optJSONObject("correct") ?: JSONObject()
            val ansMap = mutableMapOf<String, Int>()
            val corMap = mutableMapOf<String, Int>()
            ansObj.keys().forEach { k -> ansMap[k] = ansObj.optInt(k, 0) }
            corObj.keys().forEach { k -> corMap[k] = corObj.optInt(k, 0) }
            ansMap to corMap
        } catch (_: Exception) {
            emptyMap<String, Int>() to emptyMap()
        }
    }
}
