package sy.medpro.watani.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AuscultationEnvelope(
    @Json(name = "count") val count: Int?,
    @Json(name = "clips") val clips: List<AuscultationClipDto>?
)

@JsonClass(generateAdapter = true)
data class AuscultationArEnvelope(
    @Json(name = "count") val count: Int?,
    @Json(name = "clips") val clips: List<AuscultationArClipDto>?
)

@JsonClass(generateAdapter = true)
data class AuscultationArClipDto(
    @Json(name = "id") val id: String?,
    @Json(name = "category") val category: String?,
    @Json(name = "arTitle") val arTitle: String?,
    @Json(name = "arDescription") val arDescription: String?
)

@JsonClass(generateAdapter = true)
data class AuscultationClipDto(
    @Json(name = "id") val id: String?,
    @Json(name = "file") val file: String?,
    @Json(name = "category") val category: String?,
    @Json(name = "label") val label: String?,
    @Json(name = "dataset") val dataset: String?
)
