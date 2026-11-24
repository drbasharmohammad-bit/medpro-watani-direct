package sy.medpro.watani.data

import org.json.JSONObject

/**
 * «زوادة الطالب» (build 1.2.51, doctor's order 24 Nov 2026): the
 * shared contract for the student-tips section — the 27 national-bank
 * subjects, the six fixed sections, the published pack format
 * (`tips/<subject>.json` in the content manifest) and the submission
 * validation rules.
 *
 * Everything in this file is pure (org.json only — no Android, no
 * I/O) so the pack parsing, ordering and validation rules are
 * unit-testable off-device, exactly like FullDawra / SectionVerification.
 */
object TipsContract {

    /** The 27 subjects — the national bank's own canonical list and
     * order (the same [OFFICIAL_SUBJECT_ORDER] the bank publishes). */
    val SUBJECTS: List<String> = OFFICIAL_SUBJECT_ORDER

    /** The six sections, in display order: id to Arabic title. The
     * ids are the wire/manifest contract; the titles are what the
     * student sees. Pinned by unit tests — never rename an id. */
    val SECTIONS: List<Pair<String, String>> = listOf(
        "most_common" to "الأشيع في الامتحان الوطني",
        "key_points" to "نقاط مهمة",
        "until_proven_otherwise" to "حتى يثبت العكس",
        "mnemonics" to "ربطات الذاكرة",
        "exam_traps" to "فخاخ الامتحان",
        "numbers_limits" to "أرقام وحدود ما بتنتسى"
    )

    val SECTION_IDS: List<String> = SECTIONS.map { it.first }

    fun sectionTitle(sectionId: String): String? =
        SECTIONS.firstOrNull { it.first == sectionId }?.second

    fun isKnownSection(sectionId: String): Boolean = sectionTitle(sectionId) != null

    /** Manifest / content path of a subject's published tips pack. */
    fun packPath(subject: String): String = "tips/$subject.json"

    fun isTipsSubject(subject: String): Boolean = SUBJECTS.contains(subject.trim())
}

/** One published tip inside a pack. [author] is null when the pack
 * carries no name (the UI renders «بدون اسم» softly — never a blank
 * line, never an invented name). */
data class TipItem(
    val id: String,
    val text: String,
    val author: String?,
    val createdAt: String
)

/** A tip as the gateway returns it in GET /v1/tips/mine — the
 * student's own submission with its moderation state. */
data class ServerTip(
    val id: String,
    val clientId: String?,
    val subject: String,
    val sectionId: String,
    val text: String,
    val author: String?,
    val status: String,
    val rejectReason: String?,
    val createdAt: Long
)

/**
 * A subject's published tips pack:
 * `{"subject","version","updated_at","sections":{"<id>":[{id,text,author,created_at}]}}`.
 *
 * Parse rules (pinned by tests):
 * - Sections are exposed in the CONTRACT order (the six ids), never
 *   in whatever order the JSON happened to carry; unknown section
 *   ids in the file are ignored (forward compatibility).
 * - A missing section parses as an empty list — a pack with no
 *   «فخاخ» yet is a normal pack, not an error.
 * - Items keep their file order inside a section.
 * - `author: null` (or a missing/blank author) stays null.
 * - A malformed pack returns null from [parse] — callers treat a
 *   missing/corrupt pack as an EMPTY section, never as an error.
 */
data class TipsPack(
    val subject: String,
    val version: Int,
    val updatedAt: String,
    val sections: Map<String, List<TipItem>>
) {
    fun tipsFor(sectionId: String): List<TipItem> = sections[sectionId] ?: emptyList()

    fun countFor(sectionId: String): Int = tipsFor(sectionId).size

    val totalCount: Int get() = sections.values.sumOf { it.size }

    companion object {
        fun parse(json: JSONObject): TipsPack? {
            val subject = json.optString("subject", "").trim()
            if (subject.isEmpty()) return null
            val sectionsJson = json.optJSONObject("sections")
            val parsed = LinkedHashMap<String, List<TipItem>>()
            for (sectionId in TipsContract.SECTION_IDS) {
                val arr = sectionsJson?.optJSONArray(sectionId)
                val items = mutableListOf<TipItem>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val text = obj.optString("text", "")
                        if (text.isBlank()) continue
                        val author = if (obj.isNull("author")) null
                            else obj.optString("author", "").trim().takeIf { it.isNotEmpty() }
                        items.add(
                            TipItem(
                                id = obj.optString("id", ""),
                                text = text,
                                author = author,
                                createdAt = obj.optString("created_at", "")
                            )
                        )
                    }
                }
                parsed[sectionId] = items
            }
            return TipsPack(
                subject = subject,
                version = json.optInt("version", 0),
                updatedAt = json.optString("updated_at", ""),
                sections = parsed
            )
        }

        fun parse(text: String): TipsPack? = try {
            parse(JSONObject(text))
        } catch (_: Exception) {
            null
        }
    }
}

/** How a tip's author line renders: the name when present, the
 * explicit «بدون اسم» otherwise (the doctor's chosen wording). */
fun tipAuthorDisplay(author: String?): String =
    author?.trim()?.takeIf { it.isNotEmpty() } ?: "بدون اسم"

/**
 * Submission validation — the client mirror of the gateway rules
 * (the server re-checks everything; this mirror exists so the
 * student gets an instant, clear Arabic message and the send button
 * can disable itself before any network is involved):
 * - 10..500 characters after trimming.
 * - No links (http://, https://, www.) — tips are study text, and
 *   the moderation queue is not a link board.
 */
object TipsValidation {
    const val MIN_LENGTH = 10
    const val MAX_LENGTH = 500

    private val LINK_PATTERN = Regex("""(?i)(https?://|www\.)""")

    /** Arabic error message, or null when the text is submittable. */
    fun errorFor(rawText: String): String? {
        val text = rawText.trim()
        return when {
            text.length < MIN_LENGTH -> "النصيحة قصيرة جدًا — اكتب $MIN_LENGTH أحرف على الأقل."
            text.length > MAX_LENGTH -> "النصيحة طويلة جدًا — الحد الأقصى $MAX_LENGTH حرف."
            LINK_PATTERN.containsMatchIn(text) -> "لا يمكن إضافة روابط داخل النصيحة."
            else -> null
        }
    }

    fun isValid(rawText: String): Boolean = errorFor(rawText) == null

    fun containsLink(rawText: String): Boolean = LINK_PATTERN.containsMatchIn(rawText)
}

/** The tips-writing gate from GET /v1/config (`tipsWriting`):
 * 'all' (or anything unknown/absent) lets every signed-in student
 * write; 'activated' restricts writing to activated students.
 * Reading is never gated. Pure — pinned by tests. */
fun tipsWritingAllowed(tipsWriting: String?, isActivated: Boolean): Boolean =
    when (tipsWriting?.trim()?.lowercase()) {
        "activated" -> isActivated
        else -> true
    }
