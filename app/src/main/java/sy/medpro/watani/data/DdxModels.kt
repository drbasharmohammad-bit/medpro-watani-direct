package sy.medpro.watani.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * DDx Trainer data models.
 *
 * Published file shape: an envelope object with version, count and cases.
 * The staging field internal_metadata is deliberately NOT modelled here:
 * it is stripped from the published file and must never reach the app.
 *
 * All fields are nullable, following the pattern of the other content
 * models in WataniContentModels.kt, so a partially populated case still
 * parses and the UI degrades gracefully instead of failing the envelope.
 */
@JsonClass(generateAdapter = true)
data class DdxEnvelope(
    @Json(name = "version") val version: Any?,
    @Json(name = "count") val count: Int?,
    @Json(name = "cases") val cases: List<DdxCaseDto>?
)

@JsonClass(generateAdapter = true)
data class DdxCaseDto(
    @Json(name = "id") val id: String?,
    @Json(name = "title_ar") val titleAr: String?,
    @Json(name = "chief_complaint_ar") val chiefComplaintAr: String?,
    @Json(name = "system_ar") val systemAr: String?,
    @Json(name = "specialty_ar") val specialtyAr: String?,
    @Json(name = "difficulty") val difficulty: String?,
    @Json(name = "estimated_minutes") val estimatedMinutes: Int?,
    @Json(name = "presentation") val presentation: DdxPresentationDto?,
    @Json(name = "staged_data") val stagedData: List<DdxStageDto>?,
    @Json(name = "differential_reference") val differentialReference: List<DdxReferenceDto>?,
    @Json(name = "final_diagnosis") val finalDiagnosis: DdxFinalDiagnosisDto?,
    @Json(name = "red_flags_ar") val redFlagsAr: List<String>?,
    @Json(name = "teaching_summary_ar") val teachingSummaryAr: String?,
    @Json(name = "scoring_hints") val scoringHints: DdxScoringHintsDto?
)

@JsonClass(generateAdapter = true)
data class DdxPresentationDto(
    @Json(name = "age") val age: Int?,
    @Json(name = "sex") val sex: String?,
    @Json(name = "setting_ar") val settingAr: String?,
    @Json(name = "chief_complaint_text_ar") val chiefComplaintTextAr: String?
)

@JsonClass(generateAdapter = true)
data class DdxStageDto(
    @Json(name = "stage") val stage: Int?,
    @Json(name = "title_ar") val titleAr: String?,
    @Json(name = "items_ar") val itemsAr: List<String>?
)

@JsonClass(generateAdapter = true)
data class DdxReferenceDto(
    @Json(name = "rank") val rank: Int?,
    @Json(name = "diagnosis_ar") val diagnosisAr: String?,
    @Json(name = "diagnosis_en") val diagnosisEn: String?,
    @Json(name = "must_not_miss") val mustNotMiss: Boolean?,
    @Json(name = "placement_reason_ar") val placementReasonAr: String?,
    @Json(name = "supporting_ar") val supportingAr: List<String>?,
    @Json(name = "refuting_ar") val refutingAr: List<String>?,
    @Json(name = "decisive_test_ar") val decisiveTestAr: String?
)

@JsonClass(generateAdapter = true)
data class DdxFinalDiagnosisDto(
    @Json(name = "diagnosis_ar") val diagnosisAr: String?,
    @Json(name = "diagnosis_en") val diagnosisEn: String?,
    @Json(name = "decisive_evidence_ar") val decisiveEvidenceAr: String?
)

@JsonClass(generateAdapter = true)
data class DdxScoringHintsDto(
    @Json(name = "must_not_miss_ranks") val mustNotMissRanks: List<Int>?,
    @Json(name = "top_rank") val topRank: Int?
)

/**
 * One entry of the diagnosis dictionary built from the whole bank
 * (every case's differential_reference). Plain UI model, not JSON.
 * [diagnosisEn] is the internal matching key, per the design doc.
 */
data class DdxDictionaryEntry(
    val diagnosisAr: String,
    val diagnosisEn: String
)

/**
 * One element of the student's working list during play.
 *
 * A dictionary-picked element is identity-fixed: [keyEn] holds the
 * reference diagnosis_en used for scoring matches. A free-text element
 * (typed without picking a suggestion) has a null [keyEn], is displayed
 * exactly as typed, and — per the documented scoring principles — earns
 * 0 points and 0 penalty; it never matches a reference diagnosis.
 */
data class DdxListEntry(
    val keyEn: String?,
    val labelAr: String,
    val labelEn: String?,
    val isFreeText: Boolean
)
