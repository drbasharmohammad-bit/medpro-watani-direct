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
