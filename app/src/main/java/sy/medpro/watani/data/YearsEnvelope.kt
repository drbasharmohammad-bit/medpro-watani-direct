package sy.medpro.watani.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * «حسب السنوات» لمواد الجامعات (تجهيز 23 Nov 2026): طبقة تصفّح
 * إضافية فوق بنوك الجامعات — أسئلة المادة مُجمّعة حسب سنة الدورة،
 * وداخل كل سنة أسئلتها كما هي قائمة واحدة مباشرة، بلا مستوى
 * مواضيع إطلاقًا (تصحيح الدكتور 23 Nov 2026 10:33: المواضيع هي
 * عرض «حسب المواضيع» القائم من شاردة المادة نفسها، ولا تُبنى
 * مواضيع جديدة داخل السنوات). تعيش بملف مستقل لكل مادة بجانب
 * شاردتها:
 *
 *   dawrat_jami3at/<university>_years/<subject>.json
 *
 * بنية الملف مجمّدة من جهة النشر:
 * {"version": "damascus-years-2026-11-23", "subject_id": …,
 *  "subject_name": …, "university": …, "university_name": …,
 *  "count": 645,
 *  "years": [{"year": 2026, "name": "دمشق 2026", "count": 100,
 *             "questions": [QuestionDto…]}]}
 *
 * سجلات الأسئلة هي [QuestionDto] نفسها بشكل الشاردات الحالي،
 * فالكويز والشروحات والمشاركة تعمل عليها بلا أي تحويل.
 * كل الحقول اختيارية كسائر أغلفة المحتوى: ملف ناقص الحقول يُحلَّل
 * ولا يكسر الشاشة.
 */
@JsonClass(generateAdapter = true)
data class YearsEnvelope(
    @Json(name = "version") val version: String?,
    @Json(name = "subject_id") val subjectId: String?,
    @Json(name = "subject_name") val subjectName: String?,
    @Json(name = "university") val university: String?,
    @Json(name = "university_name") val universityName: String?,
    @Json(name = "count") val count: Int?,
    @Json(name = "years") val years: List<YearDto>?
)

@JsonClass(generateAdapter = true)
data class YearDto(
    @Json(name = "year") val year: Int?,
    @Json(name = "name") val name: String?,
    @Json(name = "count") val count: Int?,
    @Json(name = "questions") val questions: List<QuestionDto>?
)

/** عائلة بنوك الجامعات وحدها تملك طبقة سنوات. */
private const val UNIVERSITY_FAMILY_PREFIX = "dawrat_jami3at/"

/**
 * يحوّل مسار شاردة مادة جامعية إلى مسار ملف سنواتها المستقل:
 * `dawrat_jami3at/<uni>/<file>.json` ←
 * `dawrat_jami3at/<uni>_years/<file>.json`.
 *
 * يرجع null لكل ما ليس شاردة مادة جامعية مباشرة: عائلات أخرى
 * (questions/، uworld/…), ملفات بلا مجلد جامعة, مسارات أعمق,
 * وملفات السنوات نفسها (حارس ضد التحويل المزدوج `_years_years`).
 * دالة نقية — تُختبر وحدويًا وتُستخدم من الـViewModel والواجهة.
 */
fun yearsPathFor(subjectPath: String): String? {
    val parts = subjectPath.removePrefix(UNIVERSITY_FAMILY_PREFIX).split("/")
    val isSubjectShard = subjectPath.startsWith(UNIVERSITY_FAMILY_PREFIX) &&
        subjectPath.endsWith(".json") &&
        parts.size == 2 &&
        parts[0].isNotBlank() && parts[1].isNotBlank() &&
        !parts[0].endsWith("_years")
    return if (isSubjectShard) {
        "$UNIVERSITY_FAMILY_PREFIX${parts[0]}_years/${parts[1]}"
    } else {
        null
    }
}

/** ملخّص سنة واحدة لقائمة السنوات: الاسم المعروض وعدد الأسئلة. */
data class YearSummary(
    val year: Int,
    val name: String,
    val count: Int
)

/**
 * حكم «المادة محمّلة» الموحّد (مواصفة الدكتور 23 Nov 2026:
 * التحميل كل-أو-لا-شيء): مادة جامعية لها ملف سنوات بالمانيفست
 * لا تُعدّ محمّلة إلا باكتمال الملفين معًا وحداثة كلٍّ منهما
 * (isSectionUpToDate). ملف واحد فقط — بأي اتجاه — يعني «غير
 * محمّلة» على كل سطح، بلا حالة جزئية وبلا لافتات نقص.
 *
 * - مسار بلا توأم سنوات بالمانيفست (كل العائلات الأخرى، ومواد
 *   جامعية لم يُنشر ملف سنواتها بعد) ⇒ الحكم هو حكم الملف وحده
 *   كما كان حرفيًا.
 * - غياب مدخل المانيفست لمسار ⇒ حكم الحضور فقط، مرآةً لسلوك
 *   شاشة المادة القائم عند غياب المدخل.
 *
 * دالة نقية — تُختبر وحدويًا وتُستخدم من شاشة المادة وقائمة
 * مواد الجامعة.
 */
fun isSectionPairUpToDate(
    path: String,
    files: Map<String, ManifestFileEntry>,
    downloaded: Collection<DownloadedSectionEntity>
): Boolean {
    val byPath = downloaded.associateBy { it.sectionPath }
    fun upToDate(p: String): Boolean {
        val entity = byPath[p]
        val entry = files[p]
        return if (entry != null) isSectionUpToDate(entity, entry) else entity != null
    }
    val twin = yearsPathFor(path)
    return upToDate(path) &&
        (twin == null || !files.containsKey(twin) || upToDate(twin))
}

/** عدد أسئلة سنة: حقل count المنشور أولًا، وإلا عدد أسئلتها المباشرة. */
fun YearDto.questionCount(): Int =
    count ?: questions.orEmpty().size

/**
 * ملخّصات سنوات الغلاف بترتيب الملف نفسه (الناشر يرتّبها)،
 * والسنوات بلا رقم سنة تُسقَط لأنها لا تُفتح ولا تُخاطَب.
 */
fun yearSummaries(envelope: YearsEnvelope?): List<YearSummary> =
    envelope?.years.orEmpty().mapNotNull { dto ->
        val year = dto.year ?: return@mapNotNull null
        YearSummary(
            year = year,
            name = dto.name?.takeIf { it.isNotBlank() } ?: year.toString(),
            count = dto.questionCount()
        )
    }

/**
 * أسئلة سنة بعينها كما هي قائمة واحدة (بلا مواضيع — تصحيح
 * الدكتور 23 Nov 2026), أو قائمة فارغة لغياب الغلاف/السنة.
 * هذا هو الاختيار الذي يغذّي كويز السنة المسطّح في
 * MainViewModel.openYearQuiz.
 */
fun questionsForYear(envelope: YearsEnvelope?, year: Int): List<QuestionDto> =
    envelope?.years.orEmpty().firstOrNull { it.year == year }?.questions.orEmpty()
