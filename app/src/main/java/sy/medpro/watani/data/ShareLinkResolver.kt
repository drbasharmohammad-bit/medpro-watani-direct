package sy.medpro.watani.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Question sharing (Nov 2026): deep-link banks, share text and the
 * public share index / sanitized guest shards on Pages.
 *
 * Link shape: https://watani-content.pages.dev/q/<bank>/<qid>
 * (plus the watani://q/<bank>/<qid> custom scheme twin).
 *
 * The bank is derived from the section path a quiz was opened from:
 *   dawrat/...                    -> "dawrat"
 *   questions/...                 -> "ses"
 *   uworld/...                    -> "uworld"
 *   dawrat_jami3at/<univ>/...     -> "<univ>" (damascus/homs/aleppo/
 *                                    hama/lama/lattakia)
 * Anything else has no deep-link bank (generic Telegram fallback).
 */
object ShareLinks {
    const val CONTENT_BASE = "https://watani-content.pages.dev"
    const val FALLBACK_URL = "https://t.me/medprowatain"

    private val UNIVERSITY_KEYS = setOf("damascus", "homs", "aleppo", "hama", "lama", "lattakia")

    fun bankForPath(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val rel = path.trim().trimStart('/')
        val segments = rel.split("/").filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null
        return when (segments[0]) {
            "dawrat" -> "dawrat"
            "questions" -> "ses"
            "uworld" -> "uworld"
            "dawrat_jami3at" -> segments.getOrNull(1)?.takeIf { it in UNIVERSITY_KEYS }
            else -> null
        }
    }

    /** Deep link for one question, or null when bank/id is missing. */
    fun deepLink(bank: String?, qid: String?): String? {
        if (bank.isNullOrBlank() || qid.isNullOrBlank()) return null
        return "$CONTENT_BASE/q/$bank/$qid"
    }

    fun linkFor(bank: String?, qid: String?): String = deepLink(bank, qid) ?: FALLBACK_URL

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
     */
    fun buildShareText(question: QuestionDto, bank: String?): String {
        val sb = StringBuilder()
        sb.append(question.text?.trim().orEmpty())
        val options = question.options.orEmpty()
        options.forEachIndexed { i, opt ->
            sb.append('\n').append(labelledOption(question, i, opt))
        }
        sb.append("\n\n")
        sb.append("الجواب والشرح الكامل بتطبيق Med Pro — وطني 👇")
        sb.append('\n').append(linkFor(bank, question.id))
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

/** Parsed share-index/<bank>.json: shard paths (relative to the v1 root)
 * plus a qid -> shard-index map. */
data class ShareIndex(val shards: List<String>, val map: Map<String, Int>)

/**
 * Fetches the public share index and the sanitized guest shards.
 * Both live on Pages OUTSIDE the gateway on purpose: the guest shard is
 * structurally stripped of correct/explanation by the publisher, so a
 * guest can read the question (never the answer) without activation.
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

    suspend fun fetchIndex(bank: String): ShareIndex? = withContext(Dispatchers.IO) {
        val text = getText("${ShareLinks.CONTENT_BASE}/share-index/$bank.json") ?: return@withContext null
        try {
            val json = JSONObject(text)
            val shardsArr = json.optJSONArray("shards") ?: return@withContext null
            val shards = (0 until shardsArr.length()).map { shardsArr.optString(it) }
            val mapObj = json.optJSONObject("map") ?: return@withContext null
            val map = mutableMapOf<String, Int>()
            val keys = mapObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = mapObj.optInt(k, -1)
            }
            ShareIndex(shards, map)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Guest path: fetch the sanitized shard and find the question.
     * Returns (question, envelope subject_name). The returned question is
     * defensively re-stripped (correct/correctIndices/explanation nulled)
     * even though the shard is published without them.
     */
    suspend fun fetchGuestQuestion(shardPath: String, qid: String): Pair<QuestionDto, String?>? =
        withContext(Dispatchers.IO) {
            val cleanPath = shardPath.trim().trimStart('/').removePrefix("v1/")
            val text = getText("${ShareLinks.CONTENT_BASE}/share-data/$cleanPath")
                ?: return@withContext null
            try {
                val json = JSONObject(text)
                val subjectName = json.optString("subject_name").takeIf { it.isNotBlank() }
                val found = findQuestion(json, qid) ?: return@withContext null
                Pair(found.copy(correct = null, correctIndices = null, explanation = null), subjectName)
            } catch (_: Exception) {
                null
            }
        }

    private fun findQuestion(envelope: JSONObject, qid: String): QuestionDto? {
        for (groupKey in listOf("topics", "sessions")) {
            val groups = envelope.optJSONArray(groupKey) ?: continue
            for (g in 0 until groups.length()) {
                val questions = groups.optJSONObject(g)?.optJSONArray("questions") ?: continue
                for (i in 0 until questions.length()) {
                    val q = questions.optJSONObject(i) ?: continue
                    if (jsonId(q) == qid) return parseQuestion(q)
                }
            }
        }
        return null
    }

    /** Ids may be JSON strings or numbers depending on the bank. */
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
