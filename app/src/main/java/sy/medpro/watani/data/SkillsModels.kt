package sy.medpro.watani.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Interactive skills trainers content (build 1.2.6) — two root content
 * files loaded exactly like auscultation.json / ecg.json: sections_cache
 * files fetched through the manifest-driven downloadSection path (see
 * WataniRepository.getOsceProcedures / getDosingDrills). Unlike the
 * older packs both files are BARE JSON ARRAYS (no envelope, no declared
 * count), so their SectionVerification branches parse the array and
 * return its real size.
 *
 * osce_procedures.json — 15 OSCE procedures with sourced checklists:
 * [{id, ar, en, category, indicationsAr[], contraindicationsAr[],
 *   equipmentAr[], steps: [{order, ar, whyAr, critical}],
 *   aftercareAr[], complicationsAr[], sources: [{name, url}],
 *   sourceNote}]
 *
 * dosing_drills.json — 20 drug/dose/route drills:
 * [{id, drug, scenarioAr, patient: {age, sex, weightKg}, focusAr,
 *   options[4], correctIndex, explanationAr,
 *   source: {name, detail, url}}]
 *
 * All fields are nullable end-to-end (same forgiving DTO style as the
 * auscultation layer); the UIs hide whatever a partial pack omits.
 * Tiny bundled samples (assets/osce_procedures_sample.json,
 * assets/dosing_drills_sample.json) are the fallback until the real
 * packs are published to the manifest — the server pack always wins.
 */

@JsonClass(generateAdapter = true)
data class ProcedureStepDto(
    @Json(name = "order") val order: Int?,
    @Json(name = "ar") val ar: String?,
    @Json(name = "whyAr") val whyAr: String?,
    @Json(name = "critical") val critical: Boolean?
)

@JsonClass(generateAdapter = true)
data class ProcedureSourceDto(
    @Json(name = "name") val name: String?,
    @Json(name = "url") val url: String?
)

@JsonClass(generateAdapter = true)
data class ProcedureDto(
    @Json(name = "id") val id: String?,
    @Json(name = "ar") val ar: String?,
    @Json(name = "en") val en: String?,
    @Json(name = "category") val category: String?,
    @Json(name = "indicationsAr") val indicationsAr: List<String>?,
    @Json(name = "contraindicationsAr") val contraindicationsAr: List<String>?,
    @Json(name = "equipmentAr") val equipmentAr: List<String>?,
    @Json(name = "steps") val steps: List<ProcedureStepDto>?,
    @Json(name = "aftercareAr") val aftercareAr: List<String>?,
    @Json(name = "complicationsAr") val complicationsAr: List<String>?,
    @Json(name = "sources") val sources: List<ProcedureSourceDto>?,
    @Json(name = "sourceNote") val sourceNote: String?
) {
    /** Steps in their true checklist order (by the declared order field). */
    fun orderedSteps(): List<ProcedureStepDto> =
        (steps ?: emptyList()).sortedBy { it.order ?: Int.MAX_VALUE }
}

@JsonClass(generateAdapter = true)
data class DosingPatientDto(
    @Json(name = "age") val age: Int?,
    @Json(name = "sex") val sex: String?,
    @Json(name = "weightKg") val weightKg: Double?
)

@JsonClass(generateAdapter = true)
data class DosingSourceDto(
    @Json(name = "name") val name: String?,
    @Json(name = "detail") val detail: String?,
    @Json(name = "url") val url: String?
)

@JsonClass(generateAdapter = true)
data class DosingDrillDto(
    @Json(name = "id") val id: String?,
    @Json(name = "drug") val drug: String?,
    @Json(name = "scenarioAr") val scenarioAr: String?,
    @Json(name = "patient") val patient: DosingPatientDto?,
    @Json(name = "focusAr") val focusAr: String?,
    @Json(name = "options") val options: List<String>?,
    @Json(name = "correctIndex") val correctIndex: Int?,
    @Json(name = "explanationAr") val explanationAr: String?,
    @Json(name = "source") val source: DosingSourceDto?
)
