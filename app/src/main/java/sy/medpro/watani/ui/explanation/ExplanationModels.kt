package sy.medpro.watani.ui.explanation

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Canvas for every layer field. Layers differ only in their key field:
 * Dawrat/Watani layers carry `id` (NAT string), Damascus layers carry `qid` (number).
 * Everything else (explanation, sources, confidence, flags, correct_label, n_options)
 * is identical, so every field is nullable and unknown fields are ignored.
 */
@JsonClass(generateAdapter = true)
data class ExplanationRecordDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "qid") val qid: Any? = null,
    @Json(name = "subject") val subject: String? = null,
    @Json(name = "session") val session: Any? = null,
    @Json(name = "year") val year: Any? = null,
    @Json(name = "term") val term: Any? = null,
    @Json(name = "classification") val classification: Any? = null,
    @Json(name = "explanation") val explanation: ExplanationBlockDto? = null,
    @Json(name = "sources") val sources: List<Any?>? = null,
    @Json(name = "confidence") val confidence: String? = null,
    @Json(name = "flags") val flags: List<Any?>? = null,
    @Json(name = "correct_label") val correctLabel: String? = null,
    @Json(name = "n_options") val nOptions: Int? = null
)

@JsonClass(generateAdapter = true)
data class ExplanationBlockDto(
    @Json(name = "summary") val summary: String? = null,
    @Json(name = "why_correct") val whyCorrect: String? = null,
    @Json(name = "why_wrong") val whyWrong: Map<String, String>? = null
)

/** Normalized view of an explanation: no question text, no option texts. */
data class ExplanationContent(
    val summary: String?,
    val whyCorrect: String?,
    val whyWrong: List<Pair<String, String>>,
    val sources: List<String>,
    val confidence: String?,
    val flags: List<String>,
    val correctLabel: String?,
    val nOptions: Int?
) {
    val hasAnyText: Boolean
        get() = !summary.isNullOrBlank() || !whyCorrect.isNullOrBlank() || whyWrong.isNotEmpty() ||
            sources.isNotEmpty() || flags.isNotEmpty()

    val isMediumConfidence: Boolean
        get() = confidence?.trim()?.equals("medium", ignoreCase = true) == true
}

/**
 * Layer index: `{ "files": { "<shard>.json": { "count": N } }, "qid_index": { "<qid>": "<shard>.json" } }`.
 * `qid_index` is the fast path; when it is absent the loader falls back to scanning `files`.
 */
@JsonClass(generateAdapter = true)
data class ExplanationIndex(
    @Json(name = "files") val files: Map<String, ExplanationIndexEntry>? = null,
    @Json(name = "qid_index") val qidIndex: Map<String, String>? = null,
    @Json(name = "version") val version: Any? = null
)

@JsonClass(generateAdapter = true)
data class ExplanationIndexEntry(
    @Json(name = "count") val count: Int? = null,
    @Json(name = "hash") val hash: String? = null,
    @Json(name = "size") val size: Long? = null
)

/**
 * Layer key rule (all layers, present and future): `id` if present, else `qid`,
 * normalized to string. App question ids are normalized the same way.
 */
fun explanationKeyOf(id: String?, qid: Any?): String? {
    id?.trim()?.takeIf { it.isNotEmpty() }?.let { return normalizeExplanationKey(it) }
    return qid?.let { normalizeExplanationKey(it) }
}

fun normalizeExplanationKey(raw: Any?): String? = when (raw) {
    null -> null
    is String -> raw.trim().takeIf { it.isNotEmpty() }
    is Double -> if (raw == Math.floor(raw) && !raw.isInfinite()) raw.toLong().toString() else raw.toString()
    is Float -> raw.toDouble().let { if (it == Math.floor(it) && !it.isInfinite()) it.toLong().toString() else it.toString() }
    is Number -> raw.toString()
    else -> raw.toString().trim().takeIf { it.isNotEmpty() }
}

fun ExplanationRecordDto.lookupKey(): String? = explanationKeyOf(id, qid)

fun sourceLineOf(raw: Any?): String? = when (raw) {
    null -> null
    is String -> raw.trim().takeIf { it.isNotEmpty() }
    is Map<*, *> -> raw.entries
        .filter { it.value != null }
        .joinToString(" — ") { (k, v) -> "${k.toString().trim()}: ${v.toString().trim()}" }
        .takeIf { it.isNotBlank() }
    else -> raw.toString().trim().takeIf { it.isNotEmpty() }
}

fun flagLineOf(raw: Any?): String? = when (raw) {
    null -> null
    is String -> raw.trim().takeIf { it.isNotEmpty() }
    is Map<*, *> -> raw.values.filterNotNull().joinToString(" · ") { it.toString().trim() }.takeIf { it.isNotBlank() }
        ?: raw.keys.filterNotNull().joinToString(" · ") { it.toString().trim() }.takeIf { it.isNotBlank() }
    else -> raw.toString().trim().takeIf { it.isNotEmpty() }
}

/** Order only; the set of letters and their texts come entirely from the layer data. */
fun optionLetterRank(letter: String): Int {
    val l = letter.trim().uppercase()
    return when (l) {
        "A", "أ", "إ", "ا" -> 0
        "B", "ب" -> 1
        "C", "ج" -> 2
        "D", "د" -> 3
        "E", "ه", "ة" -> 4
        "F", "و" -> 5
        "G", "ز" -> 6
        "H", "ح" -> 7
        else -> 1000
    }
}

fun ExplanationRecordDto.toContent(): ExplanationContent? {
    val block = explanation ?: return null
    val wrong = block.whyWrong.orEmpty().entries
        .filter { it.value.isNotBlank() }
        .sortedBy { optionLetterRank(it.key) }
        .map { it.key.trim() to it.value }
    val content = ExplanationContent(
        summary = block.summary?.trim()?.takeIf { it.isNotEmpty() },
        whyCorrect = block.whyCorrect?.trim()?.takeIf { it.isNotEmpty() },
        whyWrong = wrong,
        sources = sources.orEmpty().mapNotNull { sourceLineOf(it) },
        confidence = confidence?.trim()?.takeIf { it.isNotEmpty() },
        flags = flags.orEmpty().mapNotNull { flagLineOf(it) },
        correctLabel = correctLabel?.trim()?.takeIf { it.isNotEmpty() },
        nOptions = nOptions
    )
    return content.takeIf { it.hasAnyText }
}
