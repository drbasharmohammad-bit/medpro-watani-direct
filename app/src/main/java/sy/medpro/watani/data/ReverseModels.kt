package sy.medpro.watani.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// «اقلبها» (Reverse Case Building) content models.
// The published section file reverse.json is an envelope:
// { version, count, rounds } where each round is self-contained.
// The internal-only references field present in the staging bank is
// deliberately NOT modelled here and is never shown to the student.

@JsonClass(generateAdapter = true)
data class ReverseEnvelope(
    @Json(name = "version") val version: Any?,
    @Json(name = "count") val count: Int?,
    @Json(name = "rounds") val rounds: List<ReverseRoundDto>?
)

@JsonClass(generateAdapter = true)
data class ReverseRoundDto(
    @Json(name = "id") val id: String?,
    // build | intruder | complete
    @Json(name = "pattern") val pattern: String?,
    @Json(name = "system") val system: String?,
    @Json(name = "system_ar") val systemAr: String?,
    @Json(name = "diagnosis") val diagnosis: String?,
    @Json(name = "diagnosis_en") val diagnosisEn: String?,
    @Json(name = "pair_rival") val pairRival: String?,
    @Json(name = "difficulty") val difficulty: Int?,
    @Json(name = "context") val context: String?,
    // build + intruder rounds
    @Json(name = "features") val features: List<ReverseFeatureDto>?,
    // complete rounds only: the already-presented belonging features
    @Json(name = "presented_features") val presentedFeatures: List<ReversePresentedFeatureDto>?,
    // complete rounds only: the 4 candidate options for the missing slot
    @Json(name = "options") val options: List<ReverseOptionDto>?,
    // complete rounds only: what the missing slot represents
    @Json(name = "missing_slot") val missingSlot: String?,
    @Json(name = "complete_picture") val completePicture: String?,
    @Json(name = "key_rule") val keyRule: String?
)

@JsonClass(generateAdapter = true)
data class ReverseFeatureDto(
    @Json(name = "text") val text: String?,
    // discriminating | supportive | intruder | dangerous_intruder
    @Json(name = "kind") val kind: String?,
    // For intruder features only: the diagnosis the feature truly belongs to.
    @Json(name = "belongs_to") val belongsTo: String?,
    // Stored weight for belonging features (3 discriminating, 1 supportive).
    // Intruder penalties are NOT read from here; they derive from kind.
    @Json(name = "points") val points: Int?,
    @Json(name = "why") val why: String?
)

@JsonClass(generateAdapter = true)
data class ReversePresentedFeatureDto(
    @Json(name = "text") val text: String?,
    @Json(name = "why") val why: String?
)

@JsonClass(generateAdapter = true)
data class ReverseOptionDto(
    @Json(name = "text") val text: String?,
    // key | true_weak | rival
    @Json(name = "verdict") val verdict: String?,
    @Json(name = "why") val why: String?
)
