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
    val examDaysRemaining: Int = 60,
    val dailyStudyHours: Int = 8,
    val completedPlanPhaseIds: Set<String> = emptySet(),
    val masteredEponymIds: Set<String> = emptySet(),
    val masteredFlashcardIds: Set<Int> = emptySet(),
    val savedMistakes: List<SavedMistakeItem> = emptyList(),
    val specialtyAnswered: Map<String, Int> = emptyMap(),
    val specialtyCorrect: Map<String, Int> = emptyMap()
)

object OfflineStudyVault {
    private const val PREF_NAME = "watani_offline_study_vault"
    private const val KEY_TOTAL_ANSWERED = "total_answered"
    private const val KEY_TOTAL_CORRECT = "total_correct"
    private const val KEY_EXAM_DAYS = "exam_days_remaining"
    private const val KEY_DAILY_HOURS = "daily_study_hours"
    private const val KEY_COMPLETED_PHASES = "completed_plan_phases"
    private const val KEY_MASTERED_EPONYMS = "mastered_eponyms"
    private const val KEY_MASTERED_FLASHCARDS = "mastered_flashcards"
    private const val KEY_SAVED_MISTAKES_JSON = "saved_mistakes_json"
    private const val KEY_SPECIALTY_STATS_JSON = "specialty_stats_json"

    private val _statsFlow = MutableStateFlow(defaultInitialStats())
    val statsFlow: StateFlow<OfflineStudyStats> = _statsFlow.asStateFlow()
    private var isInitialized = false

    private fun defaultInitialStats(): OfflineStudyStats {
        return OfflineStudyStats(
            totalAnswered = 0,
            totalCorrect = 0,
            examDaysRemaining = 60,
            dailyStudyHours = 8,
            savedMistakes = emptyList(),
            specialtyAnswered = emptyMap(),
            specialtyCorrect = emptyMap()
        )
    }

    fun ensureInitialized(context: Context) {
        if (isInitialized) return
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_TOTAL_ANSWERED)) {
            isInitialized = true
            persist(context, _statsFlow.value)
            return
        }

        val totalAns = prefs.getInt(KEY_TOTAL_ANSWERED, 0)
        val totalCor = prefs.getInt(KEY_TOTAL_CORRECT, 0)
        val examDays = prefs.getInt(KEY_EXAM_DAYS, 60)
        val dailyHrs = prefs.getInt(KEY_DAILY_HOURS, 8)
        val completedPhases = prefs.getStringSet(KEY_COMPLETED_PHASES, emptySet()) ?: emptySet()
        val masteredEponyms = prefs.getStringSet(KEY_MASTERED_EPONYMS, emptySet()) ?: emptySet()
        val masteredFlashcards = (prefs.getStringSet(KEY_MASTERED_FLASHCARDS, emptySet()) ?: emptySet())
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

        _statsFlow.value = OfflineStudyStats(
            totalAnswered = totalAns,
            totalCorrect = totalCor,
            examDaysRemaining = examDays,
            dailyStudyHours = dailyHrs,
            completedPlanPhaseIds = completedPhases,
            masteredEponymIds = masteredEponyms,
            masteredFlashcardIds = masteredFlashcards,
            savedMistakes = mistakesList,
            specialtyAnswered = specAns,
            specialtyCorrect = specCor
        )
        isInitialized = true
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

        val updated = current.copy(
            totalAnswered = current.totalAnswered + 1,
            totalCorrect = current.totalCorrect + (if (isCorrect) 1 else 0),
            savedMistakes = nextMistakes,
            specialtyAnswered = nextAnsMap,
            specialtyCorrect = nextCorMap
        )
        _statsFlow.value = updated
        persist(context, updated)
    }

    fun recordCaseCompletion(context: Context, xpReward: Int) {
        ensureInitialized(context)
        val current = _statsFlow.value
        val updated = current.copy(
            totalAnswered = current.totalAnswered + 1,
            totalCorrect = current.totalCorrect + 1
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

    fun updateStudyPlannerConfig(context: Context, daysRemaining: Int, dailyHours: Int) {
        ensureInitialized(context)
        val updated = _statsFlow.value.copy(
            examDaysRemaining = daysRemaining.coerceIn(7, 180),
            dailyStudyHours = dailyHours.coerceIn(2, 16)
        )
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
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
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

        prefs.edit()
            .putInt(KEY_TOTAL_ANSWERED, stats.totalAnswered)
            .putInt(KEY_TOTAL_CORRECT, stats.totalCorrect)
            .putInt(KEY_EXAM_DAYS, stats.examDaysRemaining)
            .putInt(KEY_DAILY_HOURS, stats.dailyStudyHours)
            .putStringSet(KEY_COMPLETED_PHASES, stats.completedPlanPhaseIds)
            .putStringSet(KEY_MASTERED_EPONYMS, stats.masteredEponymIds)
            .putStringSet(KEY_MASTERED_FLASHCARDS, stats.masteredFlashcardIds.map { it.toString() }.toSet())
            .putString(KEY_SAVED_MISTAKES_JSON, mistakesArr.toString())
            .putString(KEY_SPECIALTY_STATS_JSON, specObj.toString())
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
