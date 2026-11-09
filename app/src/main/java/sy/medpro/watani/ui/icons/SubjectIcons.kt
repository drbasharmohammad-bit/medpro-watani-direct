package sy.medpro.watani.ui.icons

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import sy.medpro.watani.R

/**
 * المُحلِّل الوحيد لأيقونات الاختصاصات المرسومة (اتجاه ٢ — عنابي/وردي
 * مغبرّ، 29 أيقونة). كل سطح يعرض اسم مادة/اختصاص يمرّ من هنا؛ ممنوع
 * بناء جداول مطابقة موازية في الشاشات.
 *
 * التغطية (مستخرجة من البيانات الفعلية لا من التخمين):
 *  - المواد الـ27 canonical بأسماء شاردات questions/ و questions_en/
 *    وملفات Anki (مطابقة حرفيًا لجدول apk-icons-assets/mapping.md).
 *  - أسماء دورات الجامعات (dawrat_jami3at — 104 شاردات، 5 جامعات):
 *    صيغ «الباطنة/الأمراض/الجراحة» وصيغ دمشق المختصرة وصيغ حماة
 *    بشرطات سفلية (تُطبَّع الشرطة إلى مسافة قبل البحث).
 *  - أسماء UWorld (uworld/) وصيغ الحالات السريرية وDDx الحرة
 *    («طب القلب»، «أمراض هضمية»...) بما فيها المركّبة بشرطة مائلة
 *    (يُحلَّل أول مقطع قابل للمطابقة).
 *
 * أسماء بلا أيقونة في الحزمة أصلًا (تُعاد null فلا يُرسم شيء — لا
 * أيقونة عامة مخترعة): طب المسنين فقط. (الأشعة والطب الإسعافي
 * سُدّت فجوتهما 2026-11-09 بأيقونتين مولّدتين بنفس الأسلوب.)
 *
 * قرارات مطابقة موثّقة (تُبلَّغ ولا تُخفى):
 *  - «أطفال» (الجامعات — مادة أطفال موحّدة بلا 1/2) ← أيقونة
 *    الأطفال 1 بوصفها أيقونة طب الأطفال التمثيلية.
 *  - «علم الأورام» (الجامعات) ← أيقونة جراحة الأورام (أيقونة
 *    الأورام الوحيدة في الحزمة).
 *  - «جراحة القلب والصدر» (حمص — مادة مدمجة) ← أيقونة جراحة القلب.
 *  - «مهارات جراحية» (دمشق) ← أيقونة الجراحة العامة (مشرط وخياطة —
 *    الأقرب موضوعيًا لمهارات الجراحة).
 */

/** تطبيع الاسم قبل البحث: قصّ، شرطات سفلية ← مسافات، دمج المسافات. */
fun normalizeSubjectName(raw: String): String =
    raw.trim()
        .replace('_', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()

private val SUBJECT_ICON_RES: Map<String, Int> = mapOf(
    // ===== المواد الـ27 canonical (questions/ + questions_en/ + Anki) =====
    "الأذنية" to R.drawable.ic_subject_ent,
    "الأطفال 1" to R.drawable.ic_subject_pediatrics_1,
    "الأطفال 2" to R.drawable.ic_subject_pediatrics_2,
    "التوليد" to R.drawable.ic_subject_obstetrics,
    "الجراحة البولية" to R.drawable.ic_subject_urology_surgery,
    "الجراحة التجميلية" to R.drawable.ic_subject_plastic_surgery,
    "الجراحة العامة" to R.drawable.ic_subject_general_surgery,
    "الجراحة العصبية" to R.drawable.ic_subject_neurosurgery,
    "الجراحة العظمية" to R.drawable.ic_subject_orthopedic_surgery,
    "الجلدية" to R.drawable.ic_subject_dermatology,
    "الخمجية" to R.drawable.ic_subject_infectious,
    "الدموية" to R.drawable.ic_subject_hematology,
    "الرثوية" to R.drawable.ic_subject_rheumatology,
    "الصدرية" to R.drawable.ic_subject_pulmonology,
    "الطب النفسي" to R.drawable.ic_subject_psychiatry,
    "العصبية" to R.drawable.ic_subject_neurology,
    "العينية" to R.drawable.ic_subject_ophthalmology,
    "الغدية والسكري" to R.drawable.ic_subject_endocrinology,
    "القلبية" to R.drawable.ic_subject_cardiology,
    "الكلية والشوارد" to R.drawable.ic_subject_nephrology,
    "النسائية" to R.drawable.ic_subject_gynecology,
    "الهضمية والكبد" to R.drawable.ic_subject_gastroenterology,
    "جراحة الأطفال" to R.drawable.ic_subject_pediatric_surgery,
    "جراحة الأورام" to R.drawable.ic_subject_surgical_oncology,
    "جراحة الأوعية" to R.drawable.ic_subject_vascular_surgery,
    "جراحة الصدر" to R.drawable.ic_subject_thoracic_surgery,
    "جراحة القلب" to R.drawable.ic_subject_cardiac_surgery,

    // ===== دورات الجامعات: صيغ «الأمراض/الباطنة» (حلب، حمص، اللاذقية) =====
    "أذن أنف حنجرة" to R.drawable.ic_subject_ent,
    "أمراض العين وجراحتها" to R.drawable.ic_subject_ophthalmology,
    "الأمراض الجلدية" to R.drawable.ic_subject_dermatology,
    "الأمراض العصبية الباطنة" to R.drawable.ic_subject_neurology,
    "الأمراض النسائية" to R.drawable.ic_subject_gynecology,
    "الباطنة الخمجية" to R.drawable.ic_subject_infectious,
    "الباطنة الدموية" to R.drawable.ic_subject_hematology,
    "الباطنة الرثوية" to R.drawable.ic_subject_rheumatology,
    "الباطنة الغدية" to R.drawable.ic_subject_endocrinology,
    "الباطنة الهضمية" to R.drawable.ic_subject_gastroenterology,
    "باطنة صدرية" to R.drawable.ic_subject_pulmonology,
    "باطنة قلبية" to R.drawable.ic_subject_cardiology,
    "أمراض الكلية الباطنة" to R.drawable.ic_subject_nephrology,

    // ===== دورات الجامعات: الصيغ المختصرة وصيغ حماة (بعد تطبيع الشرطات) =====
    "أطفال" to R.drawable.ic_subject_pediatrics_1,
    "توليد" to R.drawable.ic_subject_obstetrics,
    "طب التوليد" to R.drawable.ic_subject_obstetrics,
    "جراحة أطفال" to R.drawable.ic_subject_pediatric_surgery,
    "جراحة أوعية" to R.drawable.ic_subject_vascular_surgery,
    "جراحة بولية" to R.drawable.ic_subject_urology_surgery,
    "جراحة تجميلية" to R.drawable.ic_subject_plastic_surgery,
    "جراحة صدرية" to R.drawable.ic_subject_thoracic_surgery,
    "جراحة عامة" to R.drawable.ic_subject_general_surgery,
    "جراحة عصبية" to R.drawable.ic_subject_neurosurgery,
    "جراحة عظمية" to R.drawable.ic_subject_orthopedic_surgery,
    "جراحة قلبية" to R.drawable.ic_subject_cardiac_surgery,
    "علم الأورام" to R.drawable.ic_subject_surgical_oncology,
    "جراحة القلب والصدر" to R.drawable.ic_subject_cardiac_surgery,
    "مهارات جراحية" to R.drawable.ic_subject_general_surgery,
    "دم" to R.drawable.ic_subject_hematology,
    "غدية" to R.drawable.ic_subject_endocrinology,
    "كلية" to R.drawable.ic_subject_nephrology,
    "الكلية" to R.drawable.ic_subject_nephrology,
    "طب نفسي" to R.drawable.ic_subject_psychiatry,
    "الهضمية" to R.drawable.ic_subject_gastroenterology,

    // ===== صيغ دمشق العارية (بلا «ال») في دورات الجامعات + نظائرها =====
    "أذنية" to R.drawable.ic_subject_ent,
    "جلدية" to R.drawable.ic_subject_dermatology,
    "خمجية" to R.drawable.ic_subject_infectious,
    "رثوية" to R.drawable.ic_subject_rheumatology,
    "صدرية" to R.drawable.ic_subject_pulmonology,
    "عصبية" to R.drawable.ic_subject_neurology,
    "عينية" to R.drawable.ic_subject_ophthalmology,
    "قلبية" to R.drawable.ic_subject_cardiology,
    "نسائية" to R.drawable.ic_subject_gynecology,
    "هضمية" to R.drawable.ic_subject_gastroenterology,
    "الأطفال" to R.drawable.ic_subject_pediatrics_1,

    // ===== أُضيفت 2026-11-09 بأمر الدكتور: أيقونتان مولّدتان بنفس أسلوب
    // الحزمة (عنابي/وردي مغبرّ) لسدّ فجوتي الأشعة والطب الإسعافي =====
    "الأشعة" to R.drawable.ic_subject_radiology,
    "الأشعة 1" to R.drawable.ic_subject_radiology,
    "الأشعة 2" to R.drawable.ic_subject_radiology,
    "أشعة" to R.drawable.ic_subject_radiology,
    "الأشعة التشخيصية" to R.drawable.ic_subject_radiology,
    "أمراض الأشعة" to R.drawable.ic_subject_radiology,
    "الطب الإسعافي" to R.drawable.ic_subject_emergency_medicine,
    "طب إسعافي" to R.drawable.ic_subject_emergency_medicine,
    "الإسعافية" to R.drawable.ic_subject_emergency_medicine,
    "إسعافي" to R.drawable.ic_subject_emergency_medicine,

    // ===== UWorld + الحالات السريرية (cases/cases_de) =====
    "الإنتانية" to R.drawable.ic_subject_infectious,
    "الغدد الصم" to R.drawable.ic_subject_endocrinology,
    "النفسية" to R.drawable.ic_subject_psychiatry,
    "النسائية والتوليد" to R.drawable.ic_subject_gynecology,

    // ===== عبارات DDx الحرة (ddx.json — specialty_ar) =====
    "طب القلب" to R.drawable.ic_subject_cardiology,
    "أمراض قلبية" to R.drawable.ic_subject_cardiology,
    "طب القلب وجراحة القلب والأوعية" to R.drawable.ic_subject_cardiology,
    "جراحة القلب والأوعية" to R.drawable.ic_subject_cardiac_surgery,
    "طب الصدرية" to R.drawable.ic_subject_pulmonology,
    "طب الأطفال" to R.drawable.ic_subject_pediatrics_1,
    "الأعصاب" to R.drawable.ic_subject_neurology,
    "الروماتيزم" to R.drawable.ic_subject_rheumatology,
    "أمراض الدم" to R.drawable.ic_subject_hematology,
    "أمراض الكلية" to R.drawable.ic_subject_nephrology,
    "الأمراض الإنتانية" to R.drawable.ic_subject_infectious,
    "أمراض هضمية" to R.drawable.ic_subject_gastroenterology,
    "أمراض الكبد" to R.drawable.ic_subject_gastroenterology,
    "أمراض إنتانية" to R.drawable.ic_subject_infectious,
    "روماتيزم" to R.drawable.ic_subject_rheumatology,
    "عظمية" to R.drawable.ic_subject_orthopedic_surgery,
    "طب الصدرية والأمراض الخمجية" to R.drawable.ic_subject_pulmonology,
    "طب الأورام" to R.drawable.ic_subject_surgical_oncology,
    "المسالك البولية" to R.drawable.ic_subject_urology_surgery,
    "العيون" to R.drawable.ic_subject_ophthalmology,
    "الأذنية والحنجرة" to R.drawable.ic_subject_ent,
    "العظمية" to R.drawable.ic_subject_orthopedic_surgery,
    "الغدد الصم والسكري" to R.drawable.ic_subject_endocrinology
)

/** خريطة أقسام المكتبة (library.json) بمعرّفاتها الإنجليزية — الأقسام
 *  فئات أوسع من الاختصاصات؛ ما لا نظير مرسومًا له يبقى بلا أيقونة
 *  (medpro-books فقط: مجموعة علامة لا اختصاص). */
private val LIBRARY_ICON_RES: Map<String, Int> = mapOf(
    "surgery-and-anesthesia" to R.drawable.ic_subject_general_surgery,
    "obstetrics-and-gynecology" to R.drawable.ic_subject_gynecology,
    "gastroenterology" to R.drawable.ic_subject_gastroenterology,
    "psychiatry" to R.drawable.ic_subject_psychiatry,
    "cardiology" to R.drawable.ic_subject_cardiology,
    "neurology" to R.drawable.ic_subject_neurology,
    "renal-and-genitourinary" to R.drawable.ic_subject_nephrology,
    "hematology-and-oncology" to R.drawable.ic_subject_hematology,
    "infectious-disease" to R.drawable.ic_subject_infectious,
    "endocrinology" to R.drawable.ic_subject_endocrinology,
    "pediatric" to R.drawable.ic_subject_pediatrics_1,
    "musculoskeletal" to R.drawable.ic_subject_orthopedic_surgery,
    "pulmonary-critical-care" to R.drawable.ic_subject_pulmonology,
    "emergency-medicine" to R.drawable.ic_subject_emergency_medicine
)

/**
 * يحلّ اسم مادة/اختصاص إلى مورد الأيقونة، أو null إن لم توجد أيقونة
 * مرسومة له. يدعم الأسماء المركّبة بشرطة مائلة (عبارات DDx) بحلّ
 * أول مقطع قابل للمطابقة.
 */
fun subjectIconRes(rawName: String?): Int? {
    if (rawName.isNullOrBlank()) return null
    val name = normalizeSubjectName(rawName)
    SUBJECT_ICON_RES[name]?.let { return it }
    if (name.contains('/')) {
        name.split('/').forEach { segment ->
            SUBJECT_ICON_RES[normalizeSubjectName(segment)]?.let { return it }
        }
    }
    return null
}

/** حلّ أيقونة قسم مكتبة: بالمعرّف أولًا ثم بالاسم (عربي/إنجليزي). */
fun librarySubjectIconRes(subjectId: String?, subjectName: String?): Int? {
    if (!subjectId.isNullOrBlank()) {
        LIBRARY_ICON_RES[subjectId.trim()]?.let { return it }
    }
    return subjectIconRes(subjectName)
}

/**
 * يرسم أيقونة الاختصاص بجانب الاسم. بلا tint إطلاقًا (التلوين يمحو
 * ثنائية العنابي/الوردي المغبرّ — قاعدة عقد الدمج). إن لم توجد
 * أيقونة للاسم لا يُرسم شيء (ولا تُخترع أيقونة عامة).
 */
@Composable
fun SubjectIcon(
    subjectName: String?,
    modifier: Modifier = Modifier
) {
    val res = subjectIconRes(subjectName) ?: return
    Image(
        painter = painterResource(res),
        contentDescription = null,
        modifier = modifier
    )
}

/** نسخة المكتبة: تحلّ بالمعرّف/الاسم من خريطة المكتبة. */
@Composable
fun LibrarySubjectIcon(
    subjectId: String?,
    subjectName: String?,
    modifier: Modifier = Modifier
) {
    val res = librarySubjectIconRes(subjectId, subjectName) ?: return
    Image(
        painter = painterResource(res),
        contentDescription = null,
        modifier = modifier
    )
}
