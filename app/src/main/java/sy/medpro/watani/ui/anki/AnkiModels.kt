package sy.medpro.watani.ui.anki

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Published file shape: `{"version", "source", "total", "subjects": [{"name", "topics": [{"name", "cards": [{"front", "back", "src"?}]}]}]}`. */
@JsonClass(generateAdapter = true)
data class AnkiSetDto(
    @Json(name = "version") val version: String? = null,
    @Json(name = "source") val source: String? = null,
    @Json(name = "total") val total: Int? = null,
    @Json(name = "subjects") val subjects: List<AnkiSubjectDto>? = null
)

@JsonClass(generateAdapter = true)
data class AnkiSubjectDto(
    @Json(name = "name") val name: String? = null,
    @Json(name = "topics") val topics: List<AnkiTopicDto>? = null
)

@JsonClass(generateAdapter = true)
data class AnkiTopicDto(
    @Json(name = "name") val name: String? = null,
    @Json(name = "cards") val cards: List<AnkiCardDto>? = null
)

@JsonClass(generateAdapter = true)
data class AnkiCardDto(
    @Json(name = "front") val front: String? = null,
    @Json(name = "back") val back: String? = null,
    @Json(name = "src") val src: String? = null
)

data class AnkiCard(
    val id: Int,
    val subject: String,
    val topic: String,
    val front: String,
    val back: String,
    val src: String?
)

data class AnkiTopic(val name: String, val cards: List<AnkiCard>)

data class AnkiSubject(val name: String, val topics: List<AnkiTopic>) {
    val cardCount: Int get() = topics.sumOf { it.cards.size }
}

data class AnkiSet(val version: String?, val subjects: List<AnkiSubject>) {
    val cards: List<AnkiCard> get() = subjects.flatMap { s -> s.topics.flatMap { it.cards } }
}

const val ANKI_ALL = "الكل"

/**
 * Ids are a stable hash of subject|topic|front|back computed identically every launch.
 * They intentionally do not match the former derivation ids, so old mastery does not carry over.
 */
fun ankiCardId(subject: String, topic: String, front: String, back: String): Int =
    "$subject|$topic|$front|$back".hashCode()

/**
 * Atomic-adoption validation for remote Anki sets (F-05/F-06),
 * extracted as pure logic so the EXACT rules the repository runs are
 * unit-testable off-device. No Android imports.
 */
object AnkiValidation {

    /**
     * Parses [text] fully and returns the set ONLY when adoption is
     * safe: it parses, holds at least one card, and its declared
     * `total` (when present) equals the actual parsed card count.
     * Anything else returns null — the caller keeps its working copy.
     */
    fun validatedParse(text: String, moshi: com.squareup.moshi.Moshi): AnkiSet? {
        val adapter = moshi.adapter(AnkiSetDto::class.java)
        val parsed = parse(text, adapter) ?: return null
        if (parsed.cards.isEmpty()) return null
        val declaredTotal = try {
            adapter.fromJson(text)?.total
        } catch (_: Exception) {
            null
        }
        if (declaredTotal != null && declaredTotal != parsed.cards.size) return null
        return parsed
    }

    /**
     * Explicit version ordering. Versions are `yyyy-MM-dd` dates, for
     * which lexicographic order IS chronological order; the same
     * lexicographic rule is the documented fallback for any other
     * shape. A remote set is adopted only when strictly newer.
     */
    fun isStrictlyNewer(remote: String, working: String?): Boolean {
        if (working == null) return true
        return remote > working
    }

    private fun parse(text: String, adapter: com.squareup.moshi.JsonAdapter<AnkiSetDto>): AnkiSet? {
        return try {
            val dto = adapter.fromJson(text) ?: return null
            val subjects = dto.subjects.orEmpty().mapNotNull { s ->
                val subjectName = s.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val topics = s.topics.orEmpty().mapNotNull { t ->
                    val topicName = t.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    val cards = t.cards.orEmpty().mapNotNull { c ->
                        val front = c.front?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        val back = c.back?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        AnkiCard(
                            id = ankiCardId(subjectName, topicName, front, back),
                            subject = subjectName,
                            topic = topicName,
                            front = front,
                            back = back,
                            src = c.src?.trim()?.takeIf { it.isNotEmpty() }
                        )
                    }
                    AnkiTopic(topicName, cards)
                }
                AnkiSubject(subjectName, topics)
            }
            AnkiSet(version = dto.version, subjects = subjects)
        } catch (_: Exception) {
            null
        }
    }
}
