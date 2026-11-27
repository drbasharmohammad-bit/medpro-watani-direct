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
 * Light session-count index entry for one downloaded subject shard
 * (build 1.2.48, performance list item 3 — list screens read the
 * fields they need, not whole bodies). Persisted by
 * [WataniRepository] the first time a shard's envelope is parsed,
 * stamped with the registry hash of the parsed bytes: the «دورة
 * كاملة» list then builds its per-session counts from this record
 * instead of parsing every shard body just to count sessions.
 * [counts] preserves the shard's session order.
 */
@JsonClass(generateAdapter = true)
data class SessionCountsIndexEntry(
    @Json(name = "subject_name") val subjectName: String?,
    @Json(name = "counts") val counts: Map<String, Int>?
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
 *
 * 1.2.52 addition (diagnosis 27 Nov 2026): two steps that fire ONLY
 * for keys the legacy steps could not resolve — the static map now
 * also knows «هـ» (ه + tatweel, the canonical fifth label) and the
 * letters past the legacy ceiling («و»/«ز»/«ح»/«ط» → 5/6/7/8), and
 * the record's own optionLabels are consulted before the text
 * match. Content families that name keys by their own label sets
 * (UWorld) previously fell through to null despite a valid key:
 * 1,790 published questions rendered with no answer reveal. Keys
 * that resolved before resolve identically — the new steps sit
 * strictly after the legacy map and before the text match.
 */
fun parseCorrectIndexOrNull(
    correct: Any?,
    options: List<String>?,
    optionLabels: List<String>? = null
): Int? {
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
        "ه" to 4, "هـ" to 4, "E" to 4, "e" to 4, "4" to 4,
        "و" to 5, "ز" to 6, "ح" to 7, "ط" to 8
    )
    arabicMap[str]?.let { if (it in options.indices) return it }

    // The record's own labels resolve a label key the static map
    // does not know — a content family's label set is self-describing
    // and no static map can enumerate every family's labels. Runs
    // before the option-text match so a label is never mistaken for
    // an option's text.
    if (!optionLabels.isNullOrEmpty()) {
        val idx = optionLabels.indexOf(str)
        if (idx in options.indices) return idx
    }

    options.forEachIndexed { index, option ->
        if (option.trim() == str) return index
    }
    return null
}

fun normalizeCorrectIndex(
    correct: Any?,
    options: List<String>?,
    optionLabels: List<String>? = null
): Int =
    parseCorrectIndexOrNull(correct, options, optionLabels) ?: 0

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
    return parseCorrectIndexOrNull(dto.correct, options, dto.optionLabels)?.let { listOf(it) }
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
    @Json(name = "subtitleUrl") val subtitleUrl: String? = null,
    // Watani topic of this video (build 1.2.17, doctor's order): the
    // Library groups a subject's videos under their Watani topics so
    // students can find a lecture by topic. Purely catalog-driven —
    // the server adds/updates "topic" per video in library.json and
    // the grouping follows with no app change. Default null keeps old
    // catalogs parsing exactly as before; a null/blank topic renders
    // the video ungrouped at the end of its subject, as before.
    @Json(name = "topic") val topic: String? = null,
    // Enriched chapter booklet linked to this video (build 1.2.37):
    // a path relative to the content origin ("booklets/<slug>/<file>.pdf")
    // for 439 of the 581 videos; the 131 foundation videos and the
    // bookless subject carry no field. Display metadata (chapter
    // title/pages) lives in the bundled booklet_links.json asset —
    // this field is the single source of truth for the link itself.
    // Default null keeps old library.json files parsing as before.
    @Json(name = "booklet") val booklet: String? = null
)

// ======================================================================
// First Aid - Med Pro (build 1.2.36) — content family "firstaid/".
//
// Schema contract measured field-by-field from the published packs in
// watani-content/public/v1/firstaid/ (154 unit packages, 2 indexes,
// integration.json, banks_step2.json), NOT from the source course
// folders: the packager already unified both steps into one shape.
// Step 1 packages carry precheck/exitcheck/interactive = null; Step 2
// integration sessions (FA2-INT-*) likewise have no pre/exit checks.
// All fields nullable end-to-end, like every other pack DTO here.
// ======================================================================

@JsonClass(generateAdapter = true)
data class FirstAidIndexDto(
    @Json(name = "version") val version: String?,
    @Json(name = "step") val step: Int?,
    @Json(name = "count") val count: Int?,
    @Json(name = "systems") val systems: List<FirstAidSystemDto>?,
    @Json(name = "units") val units: List<FirstAidUnitSummaryDto>?,
    @Json(name = "totals") val totals: FirstAidIndexTotalsDto?,
    @Json(name = "booklets") val booklets: List<FirstAidBookletDto>?
)

@JsonClass(generateAdapter = true)
data class FirstAidSystemDto(
    @Json(name = "code") val code: String?,
    @Json(name = "sectionAr") val sectionAr: String?,
    @Json(name = "sectionEn") val sectionEn: String?,
    @Json(name = "units") val units: List<String>?
)

@JsonClass(generateAdapter = true)
data class FirstAidUnitSummaryDto(
    @Json(name = "id") val id: String?,
    @Json(name = "code") val code: String?,
    @Json(name = "section") val section: String?,
    @Json(name = "sectionAr") val sectionAr: String?,
    @Json(name = "sectionEn") val sectionEn: String?,
    @Json(name = "titleAr") val titleAr: String?,
    @Json(name = "titleEn") val titleEn: String?,
    @Json(name = "order") val order: Int?,
    /** "unit" for path units, "integration" for INT/RAPI sessions. */
    @Json(name = "kind") val kind: String?,
    @Json(name = "parentUnit") val parentUnit: String?,
    @Json(name = "parentLessons") val parentLessons: List<Int>?,
    @Json(name = "quizCount") val quizCount: Int?,
    @Json(name = "precheckCount") val precheckCount: Int?,
    @Json(name = "exitcheckCount") val exitcheckCount: Int?,
    @Json(name = "flashcardCount") val flashcardCount: Int?,
    @Json(name = "glossaryCount") val glossaryCount: Int?,
    @Json(name = "mediaCount") val mediaCount: Int?,
    @Json(name = "mediaBytes") val mediaBytes: Long?,
    /** Manifest path of the unit package, e.g. "firstaid/units2/FA2-CARD-01.json". */
    @Json(name = "package") val pkg: String?,
    @Json(name = "packageBytes") val packageBytes: Long?
)

@JsonClass(generateAdapter = true)
data class FirstAidIndexTotalsDto(
    @Json(name = "quiz") val quiz: Int?,
    @Json(name = "precheck") val precheck: Int?,
    @Json(name = "exitcheck") val exitcheck: Int?
)

/** A booklet PDF is a disk asset (not a manifest entry); [file] is
 * relative to "firstaid/", e.g. "booklets2/FirstAid-Step2-CARD-Booklet.pdf". */
@JsonClass(generateAdapter = true)
data class FirstAidBookletDto(
    @Json(name = "file") val file: String?,
    @Json(name = "size") val size: Long?
)

@JsonClass(generateAdapter = true)
data class FirstAidUnitPackageDto(
    @Json(name = "unit") val unit: FirstAidUnitMetaDto?,
    @Json(name = "referenceMd") val referenceMd: String?,
    @Json(name = "precheck") val precheck: List<FirstAidQuestionDto>?,
    @Json(name = "quiz") val quiz: List<FirstAidQuestionDto>?,
    @Json(name = "exitcheck") val exitcheck: List<FirstAidQuestionDto>?,
    @Json(name = "flashcards") val flashcards: List<FirstAidFlashcardDto>?,
    @Json(name = "glossary") val glossary: List<FirstAidGlossaryDto>?,
    @Json(name = "interactive") val interactive: FirstAidInteractiveDto?,
    @Json(name = "media") val media: List<FirstAidMediaDto>?,
    @Json(name = "repetition") val repetition: FirstAidRepetitionDto?,
    @Json(name = "wataniTopics") val wataniTopics: List<FirstAidWataniTopicDto>?
)

@JsonClass(generateAdapter = true)
data class FirstAidUnitMetaDto(
    @Json(name = "id") val id: String?,
    @Json(name = "code") val code: String?,
    @Json(name = "dir") val dir: String?,
    @Json(name = "section") val section: String?,
    @Json(name = "sectionAr") val sectionAr: String?,
    @Json(name = "sectionEn") val sectionEn: String?,
    @Json(name = "titleAr") val titleAr: String?,
    @Json(name = "titleEn") val titleEn: String?,
    @Json(name = "sourceChunk") val sourceChunk: String?,
    @Json(name = "sourceSpan") val sourceSpan: String?,
    @Json(name = "bookPages") val bookPages: String?,
    @Json(name = "blueprintSystem") val blueprintSystem: String?,
    @Json(name = "blueprintWeight") val blueprintWeight: String?,
    @Json(name = "disciplines") val disciplines: List<String>?,
    @Json(name = "integrationLinks") val integrationLinks: List<String>?,
    @Json(name = "parentUnit") val parentUnit: String?,
    @Json(name = "parentLessons") val parentLessons: List<Int>?,
    /** Step-2 INT sessions name their two parent systems instead. */
    @Json(name = "parentUnits") val parentUnits: List<String>?,
    @Json(name = "unitKind") val unitKind: String?,
    @Json(name = "preCheck") val preCheck: FirstAidCheckMetaDto?,
    @Json(name = "exitCheck") val exitCheck: FirstAidCheckMetaDto?,
    @Json(name = "status") val status: String?
)

@JsonClass(generateAdapter = true)
data class FirstAidCheckMetaDto(
    @Json(name = "file") val file: String?,
    @Json(name = "questions") val questions: Int?,
    @Json(name = "mode") val mode: String?,
    @Json(name = "passThreshold") val passThreshold: Double?
)

/** One question — the schema is IDENTICAL across precheck/quiz/exitcheck
 * and across both steps (measured on all 4,360 items). */
@JsonClass(generateAdapter = true)
data class FirstAidQuestionDto(
    @Json(name = "question") val question: String?,
    /** Boolean flag in EVERY published item (both steps, all three
     * checks): whether the question carries a clinical vignette. */
    @Json(name = "vignette") val vignette: Boolean?,
    @Json(name = "options") val options: List<String>?,
    @Json(name = "correct_option_id") val correctOptionId: Int?,
    @Json(name = "explanation") val explanation: String?,
    @Json(name = "option_explanations") val optionExplanations: List<String>?,
    @Json(name = "discipline") val discipline: String?,
    @Json(name = "competency") val competency: String?,
    @Json(name = "difficulty") val difficulty: Int?
)

@JsonClass(generateAdapter = true)
data class FirstAidFlashcardDto(
    @Json(name = "front") val front: String?,
    @Json(name = "back") val back: String?
)

@JsonClass(generateAdapter = true)
data class FirstAidGlossaryDto(
    @Json(name = "ar") val ar: String?,
    @Json(name = "en") val en: String?,
    @Json(name = "note") val note: String?
)

/** Media entry: Step-2 items are in-text tables (no [file]); Step-1
 * cropped figures carry [file] = "media/<id>.png" relative to the
 * unit's media folder on the origin ("firstaid/media/<UNIT>/<file>"). */
@JsonClass(generateAdapter = true)
data class FirstAidMediaDto(
    @Json(name = "id") val id: String?,
    @Json(name = "type") val type: String?,
    @Json(name = "captionAr") val captionAr: String?,
    @Json(name = "bookPage") val bookPage: Int?,
    @Json(name = "status") val status: String?,
    @Json(name = "file") val file: String?,
    @Json(name = "layer1Check") val layer1Check: String?,
    @Json(name = "layer1Note") val layer1Note: String?
)

@JsonClass(generateAdapter = true)
data class FirstAidInteractiveDto(
    @Json(name = "unit") val unit: String?,
    @Json(name = "source") val source: String?,
    @Json(name = "note") val note: String?,
    @Json(name = "branching_cases") val branchingCases: List<FirstAidBranchingCaseDto>?,
    @Json(name = "comparison_tables") val comparisonTables: List<FirstAidComparisonTableDto>?,
    @Json(name = "ordering_exercises") val orderingExercises: List<FirstAidOrderingDto>?
)

@JsonClass(generateAdapter = true)
data class FirstAidBranchingCaseDto(
    @Json(name = "id") val id: String?,
    @Json(name = "title") val title: String?,
    @Json(name = "type") val type: String?,
    @Json(name = "setting") val setting: String?,
    @Json(name = "source") val source: String?,
    @Json(name = "steps") val steps: List<FirstAidBranchingStepDto>?
)

@JsonClass(generateAdapter = true)
data class FirstAidBranchingStepDto(
    @Json(name = "step") val step: Int?,
    @Json(name = "prompt") val prompt: String?,
    @Json(name = "feedback_correct") val feedbackCorrect: String?,
    @Json(name = "options") val options: List<FirstAidBranchingOptionDto>?
)

@JsonClass(generateAdapter = true)
data class FirstAidBranchingOptionDto(
    /** Nullable ON PURPOSE: 21 published options ship without a text
     * (the documented FA2-CARD-01 gap, completed at packaging); the
     * player must skip or placeholder a null text, never crash. */
    @Json(name = "text") val text: String?,
    @Json(name = "correct") val correct: Boolean?,
    @Json(name = "feedback") val feedback: String?
)

@JsonClass(generateAdapter = true)
data class FirstAidComparisonTableDto(
    @Json(name = "id") val id: String?,
    @Json(name = "title") val title: String?,
    @Json(name = "type") val type: String?,
    @Json(name = "note") val note: String?,
    @Json(name = "source") val source: String?,
    @Json(name = "columns") val columns: List<String>?,
    @Json(name = "rows") val rows: List<FirstAidComparisonRowDto>?
)

@JsonClass(generateAdapter = true)
data class FirstAidComparisonRowDto(
    @Json(name = "type") val type: String?,
    @Json(name = "cells") val cells: List<String>?
)

@JsonClass(generateAdapter = true)
data class FirstAidOrderingDto(
    @Json(name = "id") val id: String?,
    @Json(name = "title") val title: String?,
    @Json(name = "type") val type: String?,
    @Json(name = "instruction") val instruction: String?,
    @Json(name = "source") val source: String?,
    @Json(name = "correct_order") val correctOrder: List<String>?,
    @Json(name = "shuffled_steps") val shuffledSteps: List<String>?
)

/** The repetition.json ladder, identical in both steps:
 * intervals [0,1,3,7,14,30], long-term 45–90, lapse -> 1 day,
 * quiz-error cards due the same day. FirstAidSrs applies it. */
@JsonClass(generateAdapter = true)
data class FirstAidRepetitionDto(
    @Json(name = "algorithm") val algorithm: String?,
    @Json(name = "intervals_days") val intervalsDays: List<Int>?,
    @Json(name = "long_term_range_days") val longTermRangeDays: List<Int>?,
    @Json(name = "on_lapse_reset_to_days") val onLapseResetToDays: Int?,
    @Json(name = "quiz_error_cards") val quizErrorCards: String?,
    @Json(name = "retention_target") val retentionTarget: Double?,
    @Json(name = "unit") val unit: String?
)

@JsonClass(generateAdapter = true)
data class FirstAidWataniTopicDto(
    @Json(name = "subject") val subject: String?,
    @Json(name = "topic") val topic: String?
)

@JsonClass(generateAdapter = true)
data class FirstAidIntegrationDto(
    @Json(name = "version") val version: String?,
    @Json(name = "count") val count: Int?,
    @Json(name = "links") val links: List<FirstAidTopicLinkDto>?,
    @Json(name = "byUnit") val byUnit: Map<String, List<FirstAidUnitLinkDto>>?,
    @Json(name = "integrationSessions") val integrationSessions: FirstAidIntegrationSessionsDto?,
    @Json(name = "unlinkedDocumented") val unlinkedDocumented: List<FirstAidUnlinkedDto>?,
    @Json(name = "totals") val totals: FirstAidIntegrationTotalsDto?
)

@JsonClass(generateAdapter = true)
data class FirstAidTopicLinkDto(
    @Json(name = "topicAr") val topicAr: String?,
    @Json(name = "topicEn") val topicEn: String?,
    @Json(name = "step1Unit") val step1Unit: String?,
    @Json(name = "step2Unit") val step2Unit: String?,
    @Json(name = "crossSystem") val crossSystem: Boolean?,
    @Json(name = "evidenceStep1") val evidenceStep1: String?,
    @Json(name = "evidenceStep2") val evidenceStep2: String?
)

@JsonClass(generateAdapter = true)
data class FirstAidUnitLinkDto(
    @Json(name = "topicAr") val topicAr: String?,
    @Json(name = "topicEn") val topicEn: String?,
    @Json(name = "targetUnit") val targetUnit: String?,
    @Json(name = "targetStep") val targetStep: Int?
)

@JsonClass(generateAdapter = true)
data class FirstAidIntegrationSessionsDto(
    @Json(name = "step1") val step1: List<String>?,
    @Json(name = "step2") val step2: List<String>?
)

@JsonClass(generateAdapter = true)
data class FirstAidUnlinkedDto(
    @Json(name = "unit") val unit: String?,
    @Json(name = "topic") val topic: String?,
    @Json(name = "reason") val reason: String?
)

@JsonClass(generateAdapter = true)
data class FirstAidIntegrationTotalsDto(
    @Json(name = "step1Units") val step1Units: Int?,
    @Json(name = "step1UnitsLinked") val step1UnitsLinked: Int?,
    @Json(name = "step2Units") val step2Units: Int?,
    @Json(name = "step2UnitsLinked") val step2UnitsLinked: Int?,
    @Json(name = "step2UnitsUnlinked") val step2UnitsUnlinked: Int?,
    @Json(name = "topicLinksInSystem") val topicLinksInSystem: Int?,
    @Json(name = "crossSystemLinks") val crossSystemLinks: Int?,
    @Json(name = "topicLinksTotal") val topicLinksTotal: Int?,
    @Json(name = "unitPairsWithTopicLinks") val unitPairsWithTopicLinks: Int?,
    @Json(name = "unitPairsWithCrossSystemLinks") val unitPairsWithCrossSystemLinks: Int?,
    @Json(name = "unitPairsTotal") val unitPairsTotal: Int?,
    @Json(name = "linksReprovenFromV1") val linksReprovenFromV1: Int?,
    @Json(name = "linksNewOnV2") val linksNewOnV2: Int?
)

/** The five printed Step-2 practice tests, exactly as printed:
 * 499 questions, 486 with a printed [key] letter, 13 with key = null
 * (shown without auto-grading — never guessed). */
@JsonClass(generateAdapter = true)
data class FirstAidBanksDto(
    @Json(name = "version") val version: String?,
    @Json(name = "count") val count: Int?,
    @Json(name = "keyed") val keyed: Int?,
    @Json(name = "unkeyed") val unkeyed: Int?,
    @Json(name = "tests") val tests: List<FirstAidBankTestDto>?
)

@JsonClass(generateAdapter = true)
data class FirstAidBankTestDto(
    @Json(name = "test") val test: String?,
    @Json(name = "titleNote") val titleNote: String?,
    @Json(name = "sourcePages") val sourcePages: String?,
    @Json(name = "questions") val questions: List<FirstAidBankQuestionDto>?
)

@JsonClass(generateAdapter = true)
data class FirstAidBankQuestionDto(
    @Json(name = "n") val n: Int?,
    @Json(name = "stem") val stem: String?,
    @Json(name = "options") val options: List<String>?,
    /** Printed answer letter "A".."E"; null for the 13 unkeyed items. */
    @Json(name = "key") val key: String?,
    @Json(name = "explanation") val explanation: String?,
    @Json(name = "garbled") val garbled: Boolean?
)
