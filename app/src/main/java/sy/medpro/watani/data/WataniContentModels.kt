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

/**
 * Manifest-hash fix (Nov 2026): decides whether a downloaded section is
 * up to date against the CURRENT manifest [entry].
 *
 * The old rule compared counts only, so a server-side correction that
 * keeps the same question count (e.g. fixing a wrong answer key) never
 * reached existing users — the section always looked "downloaded" and
 * no re-download was ever offered.
 *
 * Rules:
 * - No downloaded entity -> not downloaded.
 * - Stored count below the manifest count -> not up to date (old rule).
 * - Manifest entry has no hash (null/blank) -> count-only verdict, so
 *   hash-less sections keep working exactly as before.
 * - Manifest has a hash but the stored hash is blank (a download made
 *   before hashes were recorded) -> needs ONE update so the hash gets
 *   recorded; after that re-download the stored hash equals the
 *   manifest hash (the repository stores entry.hash verbatim on every
 *   successful download), so no repeated-update loop can occur.
 * - Both hashes present -> up to date only when equal. The comparison
 *   is a trimmed, case-insensitive TEXT comparison of the stored and
 *   manifest values exactly as they are: the hash algorithm differs
 *   per section family (blake2b-8 for the questions family, sha256[:16] for
 *   Damascus files). Since the F-01 fix the stored hash is COMPUTED by
 *   the repository from the installed file itself (never copied from
 *   the manifest), so this comparison is a real integrity check.
 */
fun isSectionUpToDate(downloaded: DownloadedSectionEntity?, entry: ManifestFileEntry): Boolean {
    if (downloaded == null) return false
    if (downloaded.count < (entry.count ?: 0)) return false
    val manifestHash = entry.hash?.trim().orEmpty()
    if (manifestHash.isEmpty()) return true
    val storedHash = downloaded.hash.trim()
    if (storedHash.isEmpty()) return false
    return storedHash.equals(manifestHash, ignoreCase = true)
}

/**
 * Refresh decision for the self-loading pack screens (build 1.2.6,
 * doctor's device report): a pack screen that already holds a local
 * copy must silently re-download it when the manifest moved on —
 * before this fix the screens only downloaded when NO local file
 * existed, so a republished pack (e.g. ecg.json 44 -> 92 strips)
 * never reached installed devices.
 *
 * Pure decision, kept separate from the repository so it is
 * unit-testable off-device:
 * - No manifest [entry] (offline, or the freshest manifest available
 *   is a stale disk cache that predates the pack) -> no refresh; the
 *   local copy stays authoritative.
 * - No downloaded [downloaded] entity -> no refresh; the pack was
 *   never registered as downloaded, and the screen's existing
 *   missing-file flow owns the first download.
 * - Otherwise refresh exactly when the section is not up to date
 *   (see [isSectionUpToDate] for the hash/count rules; a successful
 *   refresh records the computed hash, so the next open is a no-op —
 *   no repeated-download loop).
 */
fun sectionNeedsRefresh(downloaded: DownloadedSectionEntity?, entry: ManifestFileEntry?): Boolean {
    if (entry == null) return false
    if (downloaded == null) return false
    return !isSectionUpToDate(downloaded, entry)
}

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
    // Structured explanation fields (build 1.2.3, doctor's order):
    // explanation waves for every family sharing this DTO (questions/,
    // dawrat_jami3at/, uworld/) arrive as CONTENT updates only — the
    // same section files republished with these fields added per
    // question. All nullable with defaults: files published before the
    // waves parse exactly as before (fields absent -> null -> no
    // explanation button), and no APK change is needed when they land.
    @Json(name = "why_correct") val whyCorrect: String? = null,
    @Json(name = "why_wrong") val whyWrong: Map<String, String>? = null,
    @Json(name = "summary") val summary: String? = null,
    // «الزبدة» (build 1.2.10, doctor's final order 13 Nov 2026): the
    // question's existing تجميعة block, carried as its own field by the
    // content builders (the merged explanation string no longer embeds
    // it) so QuizScreen can offer «الشرح» | «الزبدة» tabs. Nullable with
    // a default like the structured fields above: files published before
    // the split parse exactly as before (absent -> null -> no tab row).
    @Json(name = "zubda") val zubda: String? = null,
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
    @Json(name = "xpReward") val xpReward: Int?,
    // Virtual-patient upgrade (schema v2) — additive and optional only.
    // Absence of every field below means the legacy player behaviour.
    // Converted cases carry legacyPresentation instead of presentation;
    // published files keep presentation and gain the new fields, so the
    // player reads presentation first and falls back to legacyPresentation.
    @Json(name = "schemaV2") val schemaV2: Boolean?,
    @Json(name = "openingVignette") val openingVignette: String?,
    @Json(name = "historyBank") val historyBank: List<HistoryQuestionDto>?,
    @Json(name = "criticalFacts") val criticalFacts: List<CriticalFactDto>?,
    @Json(name = "legacyPresentation") val legacyPresentation: String?
)

@JsonClass(generateAdapter = true)
data class CaseItemDto(
    @Json(name = "id") val id: String?,
    @Json(name = "isKey") val isKey: Boolean?,
    @Json(name = "text") val text: String?,
    @Json(name = "answer") val answer: String?,
    @Json(name = "finding") val finding: String?,
    // Optional v2 tier: key / supportive / distractor. When absent it is
    // derived from isKey (true -> key, otherwise -> supportive).
    @Json(name = "tier") val tier: String?
)

@JsonClass(generateAdapter = true)
data class CaseTestDto(
    @Json(name = "id") val id: String?,
    @Json(name = "isKey") val isKey: Boolean?,
    @Json(name = "text") val text: String?,
    @Json(name = "result") val result: String?,
    // Optional v2 tier: key / supportive / distractor (see CaseItemDto).
    @Json(name = "tier") val tier: String?
)

@JsonClass(generateAdapter = true)
data class HistoryQuestionDto(
    @Json(name = "id") val id: String?,
    @Json(name = "domain") val domain: String?,
    @Json(name = "tier") val tier: String?,
    @Json(name = "isRedFlag") val isRedFlag: Boolean?,
    @Json(name = "text") val text: String?,
    @Json(name = "answer") val answer: String?,
    @Json(name = "revealsFact") val revealsFact: String?,
    @Json(name = "factRef") val factRef: String?,
    @Json(name = "answerBasis") val answerBasis: String?,
    @Json(name = "rationale") val rationale: String?
)

@JsonClass(generateAdapter = true)
data class CriticalFactDto(
    @Json(name = "factId") val factId: String?,
    @Json(name = "label") val label: String?,
    @Json(name = "revealedBy") val revealedBy: List<String>?,
    @Json(name = "whyCritical") val whyCritical: String?,
    @Json(name = "revealLogic") val revealLogic: String?,
    @Json(name = "definitive") val definitive: Boolean?,
    @Json(name = "definitiveGroup") val definitiveGroup: String?
)

/**
 * F-08: strict key resolution. Returns the question's correct indices,
 * or null when the key cannot be determined (missing, unparseable, or
 * out of range). The old behaviour silently mapped every broken key to
 * option 0, so a source-corrupt question was graded as "أ" forever.
 *
 * Byte-for-byte compatibility for VALID content (hard requirement):
 * every parse step below mirrors the legacy [normalizeCorrectIndex]
 * exactly — Number, numeric string, the Arabic/letter map, and exact
 * option-text match — and [getCorrectIndices] returns the identical
 * list for every question whose key resolves. Only keys that
 * previously FELL THROUGH to the invented 0 (or were dropped out of a
 * correctIndices list) now resolve to null instead.
 */
fun parseCorrectIndexOrNull(correct: Any?, options: List<String>?): Int? {
    if (correct == null || options.isNullOrEmpty()) return null
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
    return null
}

fun normalizeCorrectIndex(correct: Any?, options: List<String>?): Int =
    parseCorrectIndexOrNull(correct, options) ?: 0

/**
 * Resolves a question's correct indices, or null when its key is
 * invalid (F-08). A non-empty `correctIndices` list is valid only when
 * EVERY entry parses and lies inside the options range — a partially
 * corrupt list rejects the question instead of silently dropping the
 * bad entries and grading on the rest.
 */
fun resolveCorrectIndices(dto: QuestionDto, options: List<String>?): List<Int>? {
    if (!dto.correctIndices.isNullOrEmpty()) {
        val parsed = dto.correctIndices.map { raw ->
            when (raw) {
                is Number -> raw.toInt()
                else -> raw.toString().toIntOrNull()
            }
        }
        if (parsed.any { it == null }) return null
        val indices = parsed.filterNotNull()
        if (options != null && indices.any { it !in options.indices }) return null
        return indices
    }
    return parseCorrectIndexOrNull(dto.correct, options)?.let { listOf(it) }
}

/** Countable trace of questions rejected for an invalid key (F-08). */
val invalidCorrectKeyCount = java.util.concurrent.atomic.AtomicInteger(0)

/**
 * The question's correct indices for grading/display. F-08: an invalid
 * key yields an EMPTY list (the question is excluded from grading and
 * shown without correction) and increments [invalidCorrectKeyCount],
 * instead of the legacy invented index 0.
 */
fun getCorrectIndices(dto: QuestionDto, options: List<String>?): List<Int> {
    val resolved = resolveCorrectIndices(dto, options)
    if (resolved == null) {
        invalidCorrectKeyCount.incrementAndGet()
        return emptyList()
    }
    return resolved
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


@JsonClass(generateAdapter = true)
data class LibraryEnvelope(
    @Json(name = "subjects") val subjects: List<LibrarySubject>?
)

@JsonClass(generateAdapter = true)
data class LibrarySubject(
    @Json(name = "id") val id: String?,
    @Json(name = "name") val name: String?,
    @Json(name = "books") val books: List<LibraryItem>?,
    @Json(name = "videos") val videos: List<LibraryItem>?
)

/**
 * Doctor's order (11 Nov 2026): library books are published publicly
 * OUTSIDE the app, so they must never surface in the in-app Library.
 * Data-layer filter applied by the repository on every path that hands
 * an envelope to the UI (fresh fetch + offline cache): drops all books
 * and any subject left with no videos (e.g. a books-only subject), so
 * no screen — list, tab or search — can leak a book. The underlying
 * library.json file and the book files themselves are untouched.
 */
fun LibraryEnvelope.videosOnly(): LibraryEnvelope = copy(
    subjects = subjects.orEmpty()
        .map { it.copy(books = emptyList()) }
        .filter { !it.videos.isNullOrEmpty() }
)

@JsonClass(generateAdapter = true)
data class LibraryItem(
    @Json(name = "id") val id: String?,
    @Json(name = "title") val title: String?,
    @Json(name = "fileUrl") val fileUrl: String?,
    @Json(name = "size") val size: Long?,
    @Json(name = "hash") val hash: String?,
    @Json(name = "description") val description: String?,
    // Side-car Arabic SRT for videos. Default null keeps old library.json
    // files (without this field) parsing exactly as before.
    @Json(name = "subtitleUrl") val subtitleUrl: String? = null
)
