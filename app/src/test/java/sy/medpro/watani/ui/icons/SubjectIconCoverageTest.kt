package sy.medpro.watani.ui.icons

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Layer 2 — university-section icon coverage (build 1.2.43, doctor's
 * device report: «مواد تظهر بأيقونة ومواد باسم فقط» بقسم الجامعات).
 *
 * Root cause pinned by this test's data: the section cards used to
 * consult a small 29-key exact-match map, so 66 of the 139
 * dawrat_jami3at files rendered name-only. The canonical resolver
 * (subjectIconRes) must resolve EVERY real file stem and EVERY real
 * subject_name in the published dawrat_jami3at family — the lists
 * below are extracted verbatim from the live content files
 * (139 files, 66 distinct stems, 65 distinct subject names across
 * aleppo/damascus/hama/homs/idlib/lattakia/tartus), not invented.
 * A future content file whose stem stops resolving fails here
 * before it can ship as an iconless row (the card additionally
 * renders a unified fallback glyph for any unresolved name).
 */
class SubjectIconCoverageTest {

    private val universityFileStems = listOf(
        "أذن أنف حنجرة", "أذنية", "أطفال", "أمراض العين وجراحتها",
        "أمراض الكلية الباطنة", "الأذنية", "الأطفال 1", "الأطفال 2",
        "الأمراض الجلدية", "الأمراض العصبية الباطنة", "الأمراض النسائية",
        "الباطنة الخمجية", "الباطنة الدموية", "الباطنة الرثوية",
        "الباطنة الغدية", "الباطنة الهضمية", "التوليد", "الجراحة البولية",
        "الجراحة التجميلية", "الجراحة العامة", "الجراحة العصبية",
        "الجراحة العظمية", "الجلدية", "الخمجية", "الدموية", "الرثوية",
        "الصدرية", "الطب النفسي", "العصبية", "العينية", "الغدية والسكري",
        "القلبية", "الكلية والشوارد", "النسائية", "الهضمية والكبد",
        "باطنة صدرية", "باطنة قلبية", "توليد", "جراحة أطفال",
        "جراحة أوعية", "جراحة الأطفال", "جراحة الأورام", "جراحة الأوعية",
        "جراحة الصدر", "جراحة القلب", "جراحة القلب والصدر",
        "جراحة تجميلية", "جراحة صدر", "جراحة عظمية", "جراحة قلبية",
        "جلدية", "خمجية", "دم", "رثوية", "صدرية", "طب التوليد",
        "طب نفسي", "عصبية", "علم الأورام", "عينية", "غدية", "قلبية",
        "كلية", "مهارات جراحية", "نسائية", "هضمية"
    )

    private val universitySubjectNames = listOf(
        "أذن أنف حنجرة", "أذنية", "أطفال", "أمراض العين وجراحتها",
        "أمراض الكلية الباطنة", "الأذنية", "الأطفال 1", "الأطفال 2",
        "الأمراض الجلدية", "الأمراض العصبية الباطنة", "الأمراض النسائية",
        "الباطنة الخمجية", "الباطنة الدموية", "الباطنة الرثوية",
        "الباطنة الغدية", "الباطنة الهضمية", "التوليد", "الجراحة البولية",
        "الجراحة التجميلية", "الجراحة العامة", "الجراحة العصبية",
        "الجراحة العظمية", "الجلدية", "الخمجية", "الدموية", "الرثوية",
        "الصدرية", "الطب النفسي", "العصبية", "العينية", "الغدية والسكري",
        "القلبية", "الكلية والشوارد", "النسائية", "الهضمية والكبد",
        "باطنة صدرية", "باطنة قلبية", "توليد", "جراحة أطفال",
        "جراحة أوعية", "جراحة الأطفال", "جراحة الأورام", "جراحة الأوعية",
        "جراحة الصدر", "جراحة القلب", "جراحة القلب والصدر",
        "جراحة تجميلية", "جراحة عظمية", "جراحة قلبية",
        "جلدية", "خمجية", "دم", "رثوية", "صدرية", "طب التوليد",
        "طب نفسي", "عصبية", "علم الأورام", "عينية", "غدية", "قلبية",
        "كلية", "مهارات جراحية", "نسائية", "هضمية"
    )

    @Test
    fun everyUniversityFileStemResolvesToAnIcon() {
        val missing = universityFileStems.filter { subjectIconRes(it) == null }
        assertTrue("stems without an icon: $missing", missing.isEmpty())
    }

    @Test
    fun everyUniversitySubjectNameResolvesToAnIcon() {
        val missing = universitySubjectNames.filter { subjectIconRes(it) == null }
        assertTrue("subject names without an icon: $missing", missing.isEmpty())
    }

    @Test
    fun damascusThoracicStemResolves() {
        // The one stem the canonical map itself missed before 1.2.43
        // (the file is «جراحة صدر.json» while its subject_name field
        // reads «جراحة الصدر»).
        assertNotNull(subjectIconRes("جراحة صدر"))
    }

    @Test
    fun underscoreStemsResolveAfterNormalization() {
        assertNotNull(subjectIconRes("جراحة_عامة"))
        assertNotNull(subjectIconRes("باطنة_قلبية"))
    }
}
