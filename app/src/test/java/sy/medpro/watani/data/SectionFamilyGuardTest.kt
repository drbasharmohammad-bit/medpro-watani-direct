package sy.medpro.watani.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import sy.medpro.watani.integration.IntegrationTestSupport

/**
 * Guard against the «عائلة بلا فرع تحقق» defect class (cache audit
 * 23 Nov 2026, build 1.2.45): [SectionVerification.verifySectionCount]
 * used to send every envelope shape WITHOUT its own branch into the
 * subject check, which counted 0 and made the file UN-downloadable —
 * three documented times (1.2.1 auscultation / OSCE / German cases;
 * 1.2.44 the Damascus years files, the doctor's device report).
 *
 * The fix is structural: [SectionVerification.familyOf] classifies
 * every downloadable path into a [SectionFamily], the verifier
 * dispatches on it with an exhaustive `when` (a new family without
 * a branch is a COMPILE error), the subject family is defined
 * POSITIVELY (the bank prefixes whose files really are subject
 * envelopes), and anything else is UNKNOWN — rejected with the
 * distinct «عائلة ملف غير معروفة» message instead of a silent 0.
 *
 * This suite pins the classification of every family the LIVE
 * manifest carries today, proves a minimal honest payload of each
 * family passes its own branch, and proves an unknown shape fails
 * loudly with the distinct message. Adding a new downloadable
 * family without a branch now breaks THIS test (and, for a new
 * enum entry, the build itself).
 */
class SectionFamilyGuardTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    // ---------- classification: every live family has a branch ----------

    @Test
    fun everyLiveManifestFamilyClassifiesToItsOwnBranch() {
        val expected = mapOf(
            // Root self-loading packs (exact file names).
            "cases.json" to SectionFamily.CASES,
            "cases_de.json" to SectionFamily.CASES,
            "guide.json" to SectionFamily.GUIDE,
            "ddx.json" to SectionFamily.DDX,
            "auscultation.json" to SectionFamily.AUSCULTATION,
            "osce.json" to SectionFamily.OSCE,
            "ecg.json" to SectionFamily.ECG,
            "osce_procedures.json" to SectionFamily.OSCE_PROCEDURES,
            "dosing_drills.json" to SectionFamily.DOSING_DRILLS,
            "concepts.json" to SectionFamily.CONCEPTS,
            "reverse.json" to SectionFamily.REVERSE,
            // First Aid family (prefix + per-file shapes).
            "firstaid/index_step1.json" to SectionFamily.FIRST_AID,
            "firstaid/index_step2.json" to SectionFamily.FIRST_AID,
            "firstaid/integration.json" to SectionFamily.FIRST_AID,
            "firstaid/banks_step2.json" to SectionFamily.FIRST_AID,
            "firstaid/units1/FA1-U1.json" to SectionFamily.FIRST_AID,
            "firstaid/units2/FA2-U1.json" to SectionFamily.FIRST_AID,
            // Standalone dawra files.
            "dawrat_sessions/آذار 2023.json" to SectionFamily.DAWRA_SESSIONS,
            // «حسب السنوات» twins (the 1.2.44 device defect).
            "dawrat_jami3at/damascus_years/أذنية.json" to SectionFamily.YEARS,
            "dawrat_jami3at/lattakia_years/قلبية.json" to SectionFamily.YEARS,
            // Subject envelopes — the ONLY deliberate fall-through.
            "questions/قلبية.json" to SectionFamily.SUBJECT,
            "questions_en/Cardiology.json" to SectionFamily.SUBJECT,
            "uworld/قلبية.json" to SectionFamily.SUBJECT,
            "dawrat_jami3at/damascus/أذنية.json" to SectionFamily.SUBJECT,
            "dawrat_jami3at/hama/قلبية.json" to SectionFamily.SUBJECT
        )
        for ((path, family) in expected) {
            assertEquals("path=$path", family, SectionVerification.familyOf(path))
        }
    }

    @Test
    fun unknownShapeIsClassifiedUnknownNotSubject() {
        assertEquals(
            SectionFamily.UNKNOWN,
            SectionVerification.familyOf("brand_new_pack.json")
        )
        assertEquals(
            SectionFamily.UNKNOWN,
            SectionVerification.familyOf("newfamily/thing.json")
        )
    }

    // ---------- the distinct loud failure for unknown families ----------

    @Test
    fun unknownFamilyFailsWithTheDistinctMessage() {
        try {
            SectionVerification.verifySectionCount(
                "brand_new_pack.json",
                """{"version":"1","count":1,"topics":[]}""",
                moshi
            )
            fail("an envelope family without a branch must never verify")
        } catch (e: IllegalStateException) {
            assertTrue(
                "the failure must be the distinct unknown-family message, was: ${e.message}",
                e.message!!.contains("عائلة ملف غير معروفة")
            )
        }
    }

    // ---------- a minimal honest payload passes each family's own branch ----------

    @Test
    fun minimalPayloadOfEachFamilyVerifiesWithItsRealCount() {
        val q = """{"id":"1","text":"t","options":["a","b"],"correct":0}"""
        val cases = listOf(
            Triple("cases.json", """{"version":"1","count":1,"cases":[{"id":"c1"}]}""", 1),
            Triple("cases_de.json", """{"version":"1","count":1,"cases":[{"id":"c1"}]}""", 1),
            Triple("guide.json", """{"version":"1","groups":[{"title":"g","items":["a"]}]}""", 1),
            Triple("ddx.json", """{"version":"1","count":1,"cases":[{"id":"d1"}]}""", 1),
            Triple("auscultation.json", """{"count":1,"clips":[{"id":"a1"}]}""", 1),
            Triple("osce.json", """{"version":"1","count":1,"stations":[{"id":"s1"}]}""", 1),
            Triple("ecg.json", """{"patterns":[{"id":"p1"}],"strips":[]}""", 1),
            Triple("osce_procedures.json", """[{"id":"pr1"}]""", 1),
            Triple("dosing_drills.json", """[{"id":"dr1"}]""", 1),
            Triple("concepts.json", """{"units":[{"id":"u1"}]}""", 1),
            Triple("reverse.json", """{"version":"1","count":1,"rounds":[{"id":"r1"}]}""", 1),
            Triple(
                "firstaid/index_step1.json",
                """{"version":"1","count":1,"units":[{"id":"u1"}]}""",
                1
            ),
            Triple(
                "firstaid/integration.json",
                """{"version":"1","count":1,"links":[{}]}""",
                1
            ),
            Triple(
                "firstaid/banks_step2.json",
                """{"version":"1","count":2,"tests":[{"questions":[{},{}]}]}""",
                2
            ),
            Triple(
                "firstaid/units1/FA1-U1.json",
                """{"unit":{"id":"FA1-U1"},"quiz":[{}]}""",
                1
            ),
            Triple("dawrat_sessions/آذار 2023.json", """[{"id":"q1"}]""", 1),
            Triple(
                IntegrationTestSupport.UNI_YEARS_PATH,
                IntegrationTestSupport.universityYearsJson(),
                5
            ),
            Triple(
                "questions/a.json",
                """{"version":"1","count":1,"topics":[{"id":"t","name":"x","questions":[$q]}],"sessions":[]}""",
                1
            )
        )
        for ((path, json, count) in cases) {
            assertEquals(
                "path=$path",
                count,
                SectionVerification.verifySectionCount(path, json, moshi)
            )
        }
    }

    // ---------- F4 gate core: a pair verifies BOTH files before either installs ----------

    @Test
    fun pairGateVerifiesBothFilesAndRejectsACorruptTwin() {
        // The subject half verifies on its own…
        assertEquals(
            2,
            SectionVerification.verifySectionCount(
                IntegrationTestSupport.UNI_SUBJECT_PATH,
                IntegrationTestSupport.universitySubjectJson(),
                moshi
            )
        )
        // …the years twin verifies on its own…
        assertEquals(
            5,
            SectionVerification.verifySectionCount(
                IntegrationTestSupport.UNI_YEARS_PATH,
                IntegrationTestSupport.universityYearsJson(),
                moshi
            )
        )
        // …and a twin whose declared count lies (7 vs the real 5)
        // fails verification — so downloadSectionPair aborts BEFORE
        // installing either file and the working pair stays intact.
        val lyingTwin = IntegrationTestSupport.universityYearsJson()
            .replace("\"count\":5", "\"count\":7")
        try {
            SectionVerification.verifySectionCount(
                IntegrationTestSupport.UNI_YEARS_PATH,
                lyingTwin,
                moshi
            )
            fail("a twin with a lying declared count must fail the pair gate")
        } catch (_: IllegalStateException) {
        }
    }
}
