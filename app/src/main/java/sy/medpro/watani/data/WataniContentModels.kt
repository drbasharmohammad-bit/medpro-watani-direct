package sy.medpro.watani.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ManifestResponse(
    @Json(name = "version") val version: String?,
    @Json(name = "files") val files: Map<String, ManifestFileEntry>?
)

@JsonClass(generateAdapter = true)
data class ManifestFileEntry(
    @Json(name = "hash") val hash: String?,
    @Json(name = "size") val size: Long?,
    @Json(name = "count") val count: Int?,
    @Json(name = "version") val version: String?
)

@JsonClass(generateAdapter = true)
data class SubjectEnvelope(
    @Json(name = "version") val version: Any?,
    @Json(name = "subject_id") val subjectId: String?,
    @Json(name = "subject_name") val subjectName: String?,
    @Json(name = "university") val university: String?,
    @Json(name = "university_name") val universityName: String?,
    @Json(name = "count") val count: Int?,
    @Json(name = "topics") val topics: List<TopicDto>?,
    @Json(name = "sessions") val sessions: List<SessionDto>?
)

@JsonClass(generateAdapter = true)
data class TopicDto(
    @Json(name = "id") val id: String?,
    @Json(name = "name") val name: String?,
    @Json(name = "questions") val questions: List<QuestionDto>?
)

@JsonClass(generateAdapter = true)
data class SessionDto(
    @Json(name = "id") val id: String?,
    @Json(name = "name") val name: String?,
    @Json(name = "year") val year: Any?,
    @Json(name = "term") val term: String?,
    @Json(name = "questionCount") val questionCount: Int?,
    @Json(name = "questions") val questions: List<QuestionDto>?
)

@JsonClass(generateAdapter = true)
data class QuestionDto(
    @Json(name = "id") val id: String?,
    @Json(name = "text") val text: String?,
    @Json(name = "options") val options: List<String>?,
    @Json(name = "optionLabels") val optionLabels: List<String>?,
    @Json(name = "correct") val correct: Any?,
    @Json(name = "correctIndices") val correctIndices: List<Any>?,
    @Json(name = "explanation") val explanation: String?,
    @Json(name = "years") val years: List<Any>?,
    @Json(name = "source") val source: String?,
    @Json(name = "session") val session: String?,
    @Json(name = "topics") val topics: List<Any>?,
    @Json(name = "free") val free: Boolean?
)

@JsonClass(generateAdapter = true)
data class CasesEnvelope(
    @Json(name = "version") val version: Any?,
    @Json(name = "count") val count: Int?,
    @Json(name = "cases") val cases: List<ClinicalCaseDto>?
)

@JsonClass(generateAdapter = true)
data class ClinicalCaseDto(
    @Json(name = "id") val id: String?,
    @Json(name = "title") val title: String?,
    @Json(name = "specialty") val specialty: String?,
    @Json(name = "source") val source: String?,
    @Json(name = "sourceNote") val sourceNote: String?,
    @Json(name = "difficulty") val difficulty: String?,
    @Json(name = "presentation") val presentation: String?,
    @Json(name = "history") val history: List<CaseItemDto>?,
    @Json(name = "examinations") val examinations: List<CaseItemDto>?,
    @Json(name = "tests") val tests: List<CaseTestDto>?,
    @Json(name = "diagnosisOptions") val diagnosisOptions: List<String>?,
    @Json(name = "correctDiagnosis") val correctDiagnosis: Any?,
    @Json(name = "explanation") val explanation: String?,
    @Json(name = "learningPoints") val learningPoints: List<String>?,
    @Json(name = "xpReward") val xpReward: Int?
)

@JsonClass(generateAdapter = true)
data class CaseItemDto(
    @Json(name = "id") val id: String?,
    @Json(name = "isKey") val isKey: Boolean?,
    @Json(name = "text") val text: String?,
    @Json(name = "answer") val answer: String?,
    @Json(name = "finding") val finding: String?
)

@JsonClass(generateAdapter = true)
data class CaseTestDto(
    @Json(name = "id") val id: String?,
    @Json(name = "isKey") val isKey: Boolean?,
    @Json(name = "text") val text: String?,
    @Json(name = "result") val result: String?
)

fun normalizeCorrectIndex(correct: Any?, options: List<String>?): Int {
    if (correct == null || options.isNullOrEmpty()) return 0
    if (correct is Number) {
        val idx = correct.toInt()
        if (idx in options.indices) return idx
    }
    val str = correct.toString().trim()
    str.toIntOrNull()?.let { idx ->
        if (idx in options.indices) return idx
    }
    val arabicMap = mapOf(
        "أ" to 0, "إ" to 0, "ا" to 0, "A" to 0, "a" to 0, "0" to 0,
        "ب" to 1, "B" to 1, "b" to 1, "1" to 1,
        "ج" to 2, "C" to 2, "c" to 2, "2" to 2,
        "د" to 3, "D" to 3, "d" to 3, "3" to 3,
        "ه" to 4, "E" to 4, "e" to 4, "4" to 4
    )
    arabicMap[str]?.let { if (it in options.indices) return it }

    options.forEachIndexed { index, option ->
        if (option.trim() == str) return index
    }
    return 0
}

fun getCorrectIndices(dto: QuestionDto, options: List<String>?): List<Int> {
    if (!dto.correctIndices.isNullOrEmpty()) {
        val indices = dto.correctIndices.mapNotNull {
            when (it) {
                is Number -> it.toInt()
                else -> it.toString().toIntOrNull()
            }
        }.filter { options == null || it in options.indices }
        if (indices.isNotEmpty()) return indices
    }
    return listOf(normalizeCorrectIndex(dto.correct, options))
}

@JsonClass(generateAdapter = true)
data class OsceEnvelope(
    @Json(name = "version") val version: String?,
    @Json(name = "count") val count: Int?,
    @Json(name = "stations") val stations: List<OsceStation>?
)

@JsonClass(generateAdapter = true)
data class OsceStation(
    @Json(name = "id") val id: String?,
    @Json(name = "title") val title: String?,
    @Json(name = "specialty") val specialty: String?,
    @Json(name = "durationSec") val durationSec: Int?,
    @Json(name = "scenario") val scenario: String?,
    @Json(name = "instructions") val instructions: String?,
    @Json(name = "tasks") val tasks: List<String>?,
    @Json(name = "checklist") val checklist: List<OsceChecklistItem>?,
    @Json(name = "keyPoints") val keyPoints: List<String>?,
    @Json(name = "learningPoints") val learningPoints: List<String>?
)

@JsonClass(generateAdapter = true)
data class OsceChecklistItem(
    @Json(name = "item") val item: String?,
    @Json(name = "points") val points: Int?
)

@JsonClass(generateAdapter = true)
data class GuideEnvelope(
    @Json(name = "version") val version: String?,
    @Json(name = "groups") val groups: List<GuideGroupDto>?,
    @Json(name = "privacy") val privacy: List<GuidePrivacyDto>?,
    @Json(name = "info") val info: GuideInfoDto?
)

@JsonClass(generateAdapter = true)
data class GuideGroupDto(
    @Json(name = "title") val title: String?,
    @Json(name = "items") val items: List<String>?,
    @Json(name = "content") val content: String?
)

@JsonClass(generateAdapter = true)
data class GuidePrivacyDto(
    @Json(name = "title") val title: String?,
    @Json(name = "text") val text: String?
)

@JsonClass(generateAdapter = true)
data class GuideInfoDto(
    @Json(name = "telegramChannel") val telegramChannel: String?,
    @Json(name = "telegramBot") val telegramBot: String?,
    @Json(name = "adminEmail") val adminEmail: String?,
    @Json(name = "supportMessage") val supportMessage: String?
)

