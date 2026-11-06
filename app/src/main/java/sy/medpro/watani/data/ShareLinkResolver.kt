package sy.medpro.watani.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Question sharing (Nov 2026, generalized): deep links are built from
 * the section's own path — there is NO bank/university list anywhere.
 *
 * sectionKey = the section's relative path exactly as it appears as a
 * manifest key, without the ".json" suffix
 * (e.g. "dawrat_jami3at/damascus/أذنية", "questions/الأذنية",
 * "dawrat/الأذنية", "uworld/الأطفال").
 *
 * Link shape: https://watani-content.pages.dev/q/<sectionKey>/<qidOrIndex>
 * (plus the watani://q/<sectionKey>/<qidOrIndex> custom scheme twin),
 * with every path segment percent-encoded (Uri.encode).
 *
 * qidOrIndex = the question's textual id when it has one, otherwise
 * its decimal index inside the section's canonical flattened list
 * (see [flattenEnvelope]).
 */
object ShareLinks {
    const val CONTENT_BASE = "https://watani-content.pages.dev"
    const val FALLBACK_URL = "https://t.me/medprowatain"

    /** Section key for a section source path: the path itself, minus
     * only a trailing ".json" suffix. Null when there is no path. */
    fun sectionKeyOf(sourcePath: String?): String? {
        if (sourcePath.isNullOrBlank()) return null
        val rel = sourcePath.trim().trimStart('/')
        if (rel.isEmpty()) return null
        return rel.removeSuffix(".json").takeIf { it.isNotEmpty() }
    }

    private fun encodeSegments(path: String): String =
        path.trim().trim('/').split("/").filter { it.isNotEmpty() }
            .joinToString("/") { Uri.encode(it) }

    /** Deep link for one question, or null when key/id is missing. */
    fun shareUrl(sectionKey: String?, qidOrIndex: String?): String? {
        if (sectionKey.isNullOrBlank() || qidOrIndex.isNullOrBlank()) return null
        return "$CONTENT_BASE/q/${encodeSegments(sectionKey)}/${Uri.encode(qidOrIndex.trim())}"
    }

    /** Custom-scheme twin of [shareUrl]: watani://q/<sectionKey>/<qidOrIndex>. */
    fun appSchemeUrl(sectionKey: String?, qidOrIndex: String?): String? {
        if (sectionKey.isNullOrBlank() || qidOrIndex.isNullOrBlank()) return null
        return "watani://q/${encodeSegments(sectionKey)}/${Uri.encode(qidOrIndex.trim())}"
    }

    fun linkFor(sectionKey: String?, qidOrIndex: String?): String =
        shareUrl(sectionKey, qidOrIndex) ?: FALLBACK_URL

    /** Public guest shard URL: share-data/<sectionKey>.json, with each
     * section-key segment percent-encoded. */
    fun guestShardUrl(sectionKey: String): String =
        "$CONTENT_BASE/share-data/${encodeSegments(sectionKey)}.json"

    // ---- Canonical flattening (must match the shard generator) ----

    /**
     * Canonical flattened question list of a section envelope: scan in
     * document order — `sessions` questions first, then `topics`
     * (which is the actual key order of the section files) — removing
     * duplicates by id (first occurrence wins). Questions without an
     * id always keep their positions.
     */
    fun flattenEnvelope(envelope: SubjectEnvelope): List<QuestionDto> {
        val ordered = mutableListOf<QuestionDto>()
        envelope.sessions.orEmpty().forEach { ordered.addAll(it.questions.orEmpty()) }
        envelope.topics.orEmpty().forEach { ordered.addAll(it.questions.orEmpty()) }
        return deduplicateById(ordered)
    }

    /** Dedup by textual id (first wins); id-less questions always stay. */
    fun deduplicateById(questions: List<QuestionDto>): List<QuestionDto> {
        val seen = mutableSetOf<String>()
        return questions.filter { q ->
            val id = q.id?.trim().orEmpty()
            id.isEmpty() || seen.add(id)
        }
    }

    /** Share key of the flattened element at [flatIndex]: its textual
     * id when present, otherwise its decimal flattened index. */
    fun shareKeyAt(flat: List<QuestionDto>, flatIndex: Int): String {
        val id = flat.getOrNull(flatIndex)?.id?.trim().orEmpty()
        return id.ifEmpty { flatIndex.toString() }
    }

    /**
     * Per-question share keys for a quiz opened from [envelope],
     * aligned with [quizQuestions]. Each quiz question is matched
     * against the canonical flattened list by object reference first,
     * then by id, then by text+options equality; no match -> null
     * (that card falls back to the generic link).
     */
    fun shareKeysFor(quizQuestions: List<QuestionDto>, envelope: SubjectEnvelope): List<String?> {
        val flat = flattenEnvelope(envelope)
        return quizQuestions.map { q ->
            var idx = flat.indexOfFirst { it === q }
            if (idx < 0) {
                val qid = q.id?.trim().orEmpty()
                if (qid.isNotEmpty()) {
                    idx = flat.indexOfFirst { it.id?.trim() == qid }
                }
            }
            if (idx < 0) {
                idx = flat.indexOfFirst { it.text == q.text && it.options == q.options }
            }
            if (idx < 0) null else shareKeyAt(flat, idx)
        }
    }

    /**
     * Numeric-tolerant id comparison: the two textual forms compared
     * as-is (trimmed), and as Long when both parse as Long (so a JSON
     * numeric id and its string form — or a zero-padded form — match).
     */
    fun idMatches(id: String?, target: String): Boolean {
        val a = id?.trim().orEmpty()
        val b = target.trim()
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        val aLong = a.toLongOrNull()
        val bLong = b.toLongOrNull()
        return aLong != null && bLong != null && aLong == bLong
    }

    /**
     * Resolves a shared question inside a canonical flattened list:
     * an id match (numeric-tolerant) wins; otherwise, when
     * [qidOrIndex] is numeric, the flattened element at that index
     * (in range) is the question.
     */
    fun resolveInFlat(flat: List<QuestionDto>, qidOrIndex: String): QuestionDto? {
        val target = qidOrIndex.trim()
        if (target.isEmpty()) return null
        flat.firstOrNull { idMatches(it.id, target) }?.let { return it }
        val index = target.toLongOrNull() ?: return null
        if (index < 0 || index >= flat.size) return null
        return flat[index.toInt()]
    }

    private val DEFAULT_LABELS = listOf("أ", "ب", "ج", "د", "هـ", "و", "ز", "ح", "ط", "ي")

    fun optionLabel(question: QuestionDto, index: Int): String {
        val provided = question.optionLabels?.getOrNull(index)?.trim().orEmpty()
        if (provided.isNotEmpty()) return provided
        return DEFAULT_LABELS.getOrNull(index) ?: "${index + 1}"
    }

    /** "وسم) نص" — a provided label that already carries punctuation is used as-is. */
    fun labelledOption(question: QuestionDto, index: Int, text: String): String {
        val label = optionLabel(question, index)
        return if (label.any { it == ')' || it == '.' || it == ':' || it == 'ـ' }) "$label $text"
        else "$label) $text"
    }

    /**
     * The exact share text (spec): question, each option on its own
     * labelled line, blank line, the fixed CTA line, then the link.
     * The link is the section deep link for (sectionKey, qidOrIndex),
     * or the generic fallback when either part is missing.
     */
    fun buildShareText(question: QuestionDto, sectionKey: String?, qidOrIndex: String?): String {
        val sb = StringBuilder()
        sb.append(question.text?.trim().orEmpty())
        val options = question.options.orEmpty()
        options.forEachIndexed { i, opt ->
            sb.append('\n').append(labelledOption(question, i, opt))
        }
        sb.append("\n\n")
        sb.append("الجواب والشرح الكامل بتطبيق Med Pro — وطني 👇")
        sb.append('\n').append(linkFor(sectionKey, qidOrIndex))
        return sb.toString()
    }

    /** Subject label for the share card: envelope subject_name first,
     * then the section file name without extension, then the quiz title. */
    fun subjectLabel(envelopeSubjectName: String?, sourcePath: String?, quizTitle: String): String {
        if (!envelopeSubjectName.isNullOrBlank()) return envelopeSubjectName.trim()
        val fileBase = sourcePath?.substringAfterLast('/')?.substringBeforeLast('.')?.trim()
        if (!fileBase.isNullOrEmpty()) return fileBase
        return quizTitle
    }
}

/**
 * Fetches the public sanitized guest shards. They live on Pages
 * OUTSIDE the gateway on purpose: a guest shard is structurally
 * stripped of correct/explanation by the publisher, so a guest can
 * read the question (never the answer) without activation.
 */
class ShareLinkResolver {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun getText(url: String): String? {
        return try {
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Guest path: fetch share-data/<sectionKey>.json and resolve the
     * question inside it by the same rule as the activated path
     * (numeric-tolerant id match, else numeric flattened index).
     * The shard's question list is either a flat `questions` array
     * (already the canonical flattening) or a sanitized section
     * envelope, which is flattened canonically here (sessions first,
     * then topics, dedup by id — first wins).
     * Returns (question, envelope subject_name). The returned question
     * is defensively re-stripped (correct/correctIndices/explanation
     * nulled) even though the shard is published without them.
     */
    suspend fun fetchGuestQuestion(sectionKey: String, qidOrIndex: String): Pair<QuestionDto, String?>? =
        withContext(Dispatchers.IO) {
            val text = getText(ShareLinks.guestShardUrl(sectionKey))
                ?: return@withContext null
            try {
                val json = JSONObject(text)
                val subjectName = json.optString("subject_name").takeIf { it.isNotBlank() }
                val flat = flattenGuestJson(json)
                val found = ShareLinks.resolveInFlat(flat, qidOrIndex)
                    ?: return@withContext null
                Pair(found.copy(correct = null, correctIndices = null, explanation = null), subjectName)
            } catch (_: Exception) {
                null
            }
        }

    private fun flattenGuestJson(envelope: JSONObject): List<QuestionDto> {
        val flatQuestions = envelope.optJSONArray("questions")
        if (flatQuestions != null) {
            return ShareLinks.deduplicateById(parseQuestions(flatQuestions))
        }
        val ordered = mutableListOf<QuestionDto>()
        // Canonical order: sessions first, then topics.
        for (groupKey in listOf("sessions", "topics")) {
            val groups = envelope.optJSONArray(groupKey) ?: continue
            for (g in 0 until groups.length()) {
                val questions = groups.optJSONObject(g)?.optJSONArray("questions") ?: continue
                ordered.addAll(parseQuestions(questions))
            }
        }
        return ShareLinks.deduplicateById(ordered)
    }

    private fun parseQuestions(arr: JSONArray): List<QuestionDto> {
        val out = mutableListOf<QuestionDto>()
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { out.add(parseQuestion(it)) }
        }
        return out
    }

    /** Ids may be JSON strings or numbers depending on the section. */
    private fun jsonId(q: JSONObject): String? {
        if (!q.has("id") || q.isNull("id")) return null
        return q.opt("id")?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun parseQuestion(q: JSONObject): QuestionDto {
        val options = mutableListOf<String>()
        q.optJSONArray("options")?.let { arr ->
            for (i in 0 until arr.length()) options.add(arr.optString(i))
        }
        val labels = mutableListOf<String>()
        q.optJSONArray("optionLabels")?.let { arr ->
            for (i in 0 until arr.length()) labels.add(arr.optString(i))
        }
        return QuestionDto(
            id = jsonId(q),
            text = q.optString("text").takeIf { it.isNotBlank() },
            options = options,
            optionLabels = labels.takeIf { it.isNotEmpty() },
            // Sanitized shard: answer fields are never read here at all.
            correct = null,
            correctIndices = null,
            explanation = null,
            years = null,
            source = q.optString("source").takeIf { it.isNotBlank() },
            session = q.optString("session").takeIf { it.isNotBlank() },
            topics = null,
            free = null
        )
    }
}
