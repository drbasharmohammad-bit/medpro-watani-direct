package sy.medpro.watani.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * «ادرس مع ميد برو» concepts pack (build 1.2.13, doctor's order
 * 14 Nov 2026) — a fixed curriculum of concept units that lives
 * INSIDE the Library tab. Content file: concepts.json, a server pack
 * loaded exactly like dosing_drills.json (manifest -> downloadSection
 * -> encrypted sections_cache; see WataniRepository.getConcepts and
 * the concepts branch of SectionVerification). Unlike the drills
 * there is NO bundled sample: the section stays completely hidden
 * until a downloaded pack with at least one unit exists on the
 * device (the content is published later, by its own pipeline).
 *
 * Contract (one object, units array):
 * {"version":1,"units":[{"id","subject","topic","weight","watani",
 *   "damascus","explanation":[{"h","body"}],"traps":[...],
 *   "self_test":[{"qid","bank","text","options":[],"correct_index",
 *   "note"}],"diff_table":{"columns":[],"rows":[[]]},
 *   "cross_links":[{"subject","text"}],
 *   "mnemonic":{"body","hook"}}]}
 *
 * All fields are nullable end-to-end (same forgiving DTO style as the
 * skills/dosing layers); the UI hides whatever a partial unit omits.
 */

@JsonClass(generateAdapter = true)
data class ConceptsEnvelope(
    @Json(name = "version") val version: Int?,
    @Json(name = "units") val units: List<ConceptUnitDto>?
)

@JsonClass(generateAdapter = true)
data class ConceptUnitDto(
    @Json(name = "id") val id: String?,
    @Json(name = "subject") val subject: String?,
    @Json(name = "topic") val topic: String?,
    @Json(name = "weight") val weight: Int?,
    @Json(name = "watani") val watani: Int?,
    @Json(name = "damascus") val damascus: Int?,
    @Json(name = "explanation") val explanation: List<ConceptSectionDto>?,
    @Json(name = "traps") val traps: List<String>?,
    @Json(name = "self_test") val selfTest: List<ConceptSelfTestDto>?,
    @Json(name = "diff_table") val diffTable: ConceptDiffTableDto?,
    @Json(name = "cross_links") val crossLinks: List<ConceptCrossLinkDto>?,
    @Json(name = "mnemonic") val mnemonic: ConceptMnemonicDto?
)

@JsonClass(generateAdapter = true)
data class ConceptSectionDto(
    @Json(name = "h") val h: String?,
    @Json(name = "body") val body: String?
)

@JsonClass(generateAdapter = true)
data class ConceptSelfTestDto(
    @Json(name = "qid") val qid: String?,
    @Json(name = "bank") val bank: String?,
    @Json(name = "text") val text: String?,
    @Json(name = "options") val options: List<String>?,
    @Json(name = "correct_index") val correctIndex: Int?,
    @Json(name = "note") val note: String?
)

@JsonClass(generateAdapter = true)
data class ConceptDiffTableDto(
    @Json(name = "columns") val columns: List<String>?,
    @Json(name = "rows") val rows: List<List<String>>?
)

@JsonClass(generateAdapter = true)
data class ConceptCrossLinkDto(
    @Json(name = "subject") val subject: String?,
    @Json(name = "text") val text: String?
)

@JsonClass(generateAdapter = true)
data class ConceptMnemonicDto(
    @Json(name = "body") val body: String?,
    @Json(name = "hook") val hook: String?
)

// ------------------------------------------------------------------
// Pure logic (unit-tested in ConceptsLogicTest): text normalization,
// subject grouping/sorting, quiz-question -> unit matching and the
// self-test key resolution. No Android imports.
// ------------------------------------------------------------------

/**
 * Normalized comparison form for Arabic subject/topic names:
 * whitespace collapsed, tatweel and harakat stripped, and the usual
 * letter variants folded (أ/إ/آ -> ا, ؤ -> و, ئ -> ي, ة -> ه,
 * ى -> ي). Applied to BOTH sides of every comparison, so a bank
 * topic spelled «قصور القلب» matches a unit topic spelled with any
 * of those variants.
 */
fun normalizeConceptText(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val sb = StringBuilder()
    var lastWasSpace = true // also trims leading whitespace
    for (ch in raw.trim()) {
        val c = when (ch) {
            'أ', 'إ', 'آ', 'ٱ' -> 'ا'
            'ؤ' -> 'و'
            'ئ' -> 'ي'
            'ة' -> 'ه'
            'ى' -> 'ي'
            'ـ' -> null // tatweel
            in 'ً'..'ٟ', 'ٰ' -> null // harakat / superscript alef
            else -> ch
        }
        if (c == null) continue
        if (c.isWhitespace()) {
            if (!lastWasSpace) {
                sb.append(' ')
                lastWasSpace = true
            }
        } else {
            sb.append(c)
            lastWasSpace = false
        }
    }
    return sb.toString().trim().lowercase()
}

/** One subject group on the «ادرس مع ميد برو» subjects list. */
data class ConceptSubjectGroup(
    val subject: String,
    val units: List<ConceptUnitDto>,
    val totalWeight: Int
)

/**
 * Subjects list contract: units grouped by their subject string,
 * each group's topics sorted by weight descending, and the groups
 * themselves sorted by TOTAL weight descending. Units with a blank
 * subject or topic cannot be listed or matched, so they are dropped
 * here (they also cannot inflate the visible counts).
 */
fun groupConceptsBySubject(units: List<ConceptUnitDto>): List<ConceptSubjectGroup> {
    val usable = units.filter {
        !it.subject.isNullOrBlank() && !it.topic.isNullOrBlank()
    }
    return usable
        .groupBy { it.subject!!.trim() }
        .map { (subject, groupUnits) ->
            val sorted = groupUnits.sortedByDescending { it.weight ?: 0 }
            ConceptSubjectGroup(
                subject = subject,
                units = sorted,
                totalWeight = sorted.sumOf { it.weight ?: 0 }
            )
        }
        .sortedByDescending { it.totalWeight }
}

/**
 * Quiz -> concept link matching (build 1.2.13 spec): a question
 * belongs to a unit when one of its topic candidates (the quiz title
 * — the topic the quiz was opened from — plus any topic strings the
 * question itself carries) equals the unit's topic under
 * [normalizeConceptText]. When several units share that topic, the
 * one whose subject also matches [subjectName] wins; a single
 * candidate wins outright. Returns null when nothing matches — the
 * quiz then shows no link at all.
 */
fun findConceptUnit(
    units: List<ConceptUnitDto>,
    subjectName: String?,
    topicCandidates: List<String?>
): ConceptUnitDto? {
    val wantedTopics = topicCandidates
        .map { normalizeConceptText(it) }
        .filter { it.isNotEmpty() }
        .toSet()
    if (wantedTopics.isEmpty()) return null
    val byTopic = units.filter { unit ->
        !unit.topic.isNullOrBlank() &&
            normalizeConceptText(unit.topic) in wantedTopics
    }
    if (byTopic.isEmpty()) return null
    if (byTopic.size == 1) return byTopic.first()
    val wantedSubject = normalizeConceptText(subjectName)
    if (wantedSubject.isNotEmpty()) {
        byTopic.firstOrNull {
            normalizeConceptText(it.subject) == wantedSubject
        }?.let { return it }
    }
    // Several units share the topic and the subject does not
    // disambiguate: take the heaviest (most exam-relevant) one.
    return byTopic.maxByOrNull { it.weight ?: 0 }
}

/**
 * Self-test key resolution. The pack contract carries correct_index
 * as an index into the options; the published sample value (4 in a
 * five-option question) is 0-based, but a 1-based value equal to the
 * option count would fall outside the 0-based range — that single
 * ambiguous case is read as 1-based instead of dropping the key.
 * Anything else out of range resolves to null (no reveal button
 * grading on an invented key).
 */
fun resolveSelfTestCorrectIndex(correctIndex: Int?, optionsCount: Int): Int? {
    if (correctIndex == null || optionsCount <= 0) return null
    if (correctIndex in 0 until optionsCount) return correctIndex
    if (correctIndex in 1..optionsCount) return correctIndex - 1
    return null
}
