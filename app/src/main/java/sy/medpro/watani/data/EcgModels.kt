package sy.medpro.watani.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * ECG mini-course content (build 1.2.4) — root content file "ecg.json",
 * loaded exactly like auscultation.json: a sections_cache file fetched
 * through the manifest-driven downloadSection path (see
 * WataniRepository.getEcgEnvelope). Schema contract (content pack is
 * produced by the publishing pipeline, never bundled except for the
 * small assets/ecg_sample.json fallback used until the pack lands):
 *
 * {
 *   "version": 1,
 *   "method":   [{step, title, ar, source}],
 *   "patterns": [{id, ar, en, category, level, hooks[], pitfalls[],
 *                 criteria: [{ar, source}], sourceLine}],
 *   "strips":   [{id, patternId, hr, axis, age, sex, fs,
 *                 leads: ["I",...,"V6"],
 *                 samples: {"I": [1000 ints in µV], ...},
 *                 guided: {rhythmAr, axisAr, findingsAr[], conclusionAr}}],
 *   "leadMap":  [{leads: [...], wallAr, arteryAr}]
 * }
 *
 * Samples are microvolts at [fs] samples/second; the renderer draws
 * them at the standard 25 mm/s, 10 mm/mV scale, so a value of 1000 µV
 * is exactly 10 mm of deflection. All fields are nullable end-to-end:
 * the DTO layer stays as forgiving as the auscultation one, and the UI
 * hides whatever a partial pack does not carry.
 */
@JsonClass(generateAdapter = true)
data class EcgEnvelope(
    @Json(name = "version") val version: Int?,
    @Json(name = "method") val method: List<EcgMethodStepDto>?,
    @Json(name = "patterns") val patterns: List<EcgPatternDto>?,
    @Json(name = "strips") val strips: List<EcgStripDto>?,
    @Json(name = "leadMap") val leadMap: List<EcgLeadMapDto>?
)

@JsonClass(generateAdapter = true)
data class EcgMethodStepDto(
    @Json(name = "step") val step: Int?,
    @Json(name = "title") val title: String?,
    @Json(name = "ar") val ar: String?,
    @Json(name = "source") val source: String?
)

@JsonClass(generateAdapter = true)
data class EcgCriterionDto(
    @Json(name = "ar") val ar: String?,
    @Json(name = "source") val source: String?
)

@JsonClass(generateAdapter = true)
data class EcgPatternDto(
    @Json(name = "id") val id: String?,
    @Json(name = "ar") val ar: String?,
    @Json(name = "en") val en: String?,
    @Json(name = "category") val category: String?,
    @Json(name = "level") val level: Int?,
    @Json(name = "hooks") val hooks: List<String>?,
    @Json(name = "pitfalls") val pitfalls: List<String>?,
    @Json(name = "criteria") val criteria: List<EcgCriterionDto>?,
    @Json(name = "sourceLine") val sourceLine: String?
)

@JsonClass(generateAdapter = true)
data class EcgGuidedDto(
    @Json(name = "rhythmAr") val rhythmAr: String?,
    @Json(name = "axisAr") val axisAr: String?,
    @Json(name = "findingsAr") val findingsAr: List<String>?,
    @Json(name = "conclusionAr") val conclusionAr: String?
)

@JsonClass(generateAdapter = true)
data class EcgStripDto(
    @Json(name = "id") val id: String?,
    @Json(name = "patternId") val patternId: String?,
    @Json(name = "hr") val hr: Int?,
    @Json(name = "axis") val axis: Int?,
    @Json(name = "age") val age: Int?,
    @Json(name = "sex") val sex: String?,
    @Json(name = "fs") val fs: Int?,
    @Json(name = "leads") val leads: List<String>?,
    @Json(name = "samples") val samples: Map<String, List<Int>>?,
    @Json(name = "guided") val guided: EcgGuidedDto?
) {
    /** Sampling rate with the contract default (100 Hz). */
    fun sampleRate(): Int = fs?.takeIf { it > 0 } ?: 100

    fun leadSamples(lead: String): List<Int> = samples?.get(lead) ?: emptyList()

    /** The rhythm lead (II) when present, else the first declared lead. */
    fun rhythmLead(): String {
        val declared = leads ?: samples?.keys?.toList() ?: emptyList()
        return when {
            declared.contains("II") -> "II"
            samples?.containsKey("II") == true -> "II"
            else -> declared.firstOrNull() ?: "II"
        }
    }
}

@JsonClass(generateAdapter = true)
data class EcgLeadMapDto(
    @Json(name = "leads") val leads: List<String>?,
    @Json(name = "wallAr") val wallAr: String?,
    @Json(name = "arteryAr") val arteryAr: String?
)
