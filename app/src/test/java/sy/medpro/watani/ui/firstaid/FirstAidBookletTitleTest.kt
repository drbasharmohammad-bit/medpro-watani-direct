package sy.medpro.watani.ui.firstaid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import sy.medpro.watani.data.FirstAidBookletDto
import sy.medpro.watani.data.FirstAidSystemDto

/**
 * Layer 2 — First Aid booklet display titles (build 1.2.47, the
 * doctor's device report: the reader showed the RAW FILE NAME
 * truncated mid-word — «FirstAi…iochemistry-Bookl…» — over the
 * biochemistry booklet). The title derives from the booklet's own
 * system in Arabic, exactly as the live indexes name them; the
 * system lists below mirror the published index_step1/index_step2
 * systems the matching had to survive (ampersands, multi-word
 * names, Step-2 codes).
 */
class FirstAidBookletTitleTest {

    private fun system(code: String, ar: String, en: String) =
        FirstAidSystemDto(code = code, sectionAr = ar, sectionEn = en, units = emptyList())

    private val step1Systems = listOf(
        system("BIOC", "الكيمياء الحيوية", "Biochemistry"),
        system("HEME", "أمراض الدم والأورام", "Hematology & Oncology"),
        system("NEUR", "العصبية والحواس الخاصة", "Neurology & Special Senses"),
        system("PUBH", "علوم الصحة العامة", "Public Health Sciences"),
        system("ABBR", "المختصرات والرموز", "Abbreviations & Symbols"),
        system("MSK", "الجهاز العضلي الهيكلي والجلد", "Musculoskeletal & Skin")
    )

    private val step2Systems = listOf(
        system("CARD", "طب القلب والأوعية (من الطب الباطني)", "Cardiology (Internal Medicine)"),
        system("RESP", "طب الرئة والأمراض التنفسية (من الطب الباطني)", "Pulmonology (Internal Medicine)")
    )

    private fun booklet(file: String) = FirstAidBookletDto(file = file, size = 1L)

    @Test
    fun step1BookletsTakeTheirSystemsArabicName() {
        assertEquals(
            "كتيّب الكيمياء الحيوية",
            bookletDisplayTitle(booklet("booklets1/FirstAid-Biochemistry-Booklet-Mobile.pdf"), step1Systems)
        )
        assertEquals(
            "كتيّب أمراض الدم والأورام",
            bookletDisplayTitle(booklet("booklets1/FirstAid-HematologyOncology-Booklet-Mobile.pdf"), step1Systems)
        )
        assertEquals(
            "كتيّب العصبية والحواس الخاصة",
            bookletDisplayTitle(booklet("booklets1/FirstAid-Neurology-Booklet-Mobile.pdf"), step1Systems)
        )
        assertEquals(
            "كتيّب علوم الصحة العامة",
            bookletDisplayTitle(booklet("booklets1/FirstAid-PublicHealth-Booklet-Mobile.pdf"), step1Systems)
        )
        assertEquals(
            "كتيّب المختصرات والرموز",
            bookletDisplayTitle(booklet("booklets1/FirstAid-Abbreviations-Booklet-Mobile.pdf"), step1Systems)
        )
        assertEquals(
            "كتيّب الجهاز العضلي الهيكلي والجلد",
            bookletDisplayTitle(booklet("booklets1/FirstAid-MusculoskeletalSkin-Booklet-Mobile.pdf"), step1Systems)
        )
    }

    @Test
    fun step2BookletsMatchBySystemCode() {
        assertEquals(
            "كتيّب طب القلب والأوعية (من الطب الباطني)",
            bookletDisplayTitle(booklet("booklets2/FirstAid-Step2-CARD-Booklet.pdf"), step2Systems)
        )
        assertEquals(
            "كتيّب طب الرئة والأمراض التنفسية (من الطب الباطني)",
            bookletDisplayTitle(booklet("booklets2/FirstAid-Step2-RESP-Booklet.pdf"), step2Systems)
        )
    }

    @Test
    fun unmatchedBookletFallsBackToAHumanizedNameNeverTheRawFileName() {
        val title = bookletDisplayTitle(
            booklet("booklets1/FirstAid-HematologyOncology-Booklet-Mobile.pdf"),
            emptyList()
        )
        assertEquals("Hematology Oncology", title)
        assertFalse(title.contains(".pdf"))
        assertFalse(title.startsWith("FirstAid"))
    }
}
