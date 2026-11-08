package sy.medpro.watani.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import sy.medpro.watani.ui.anki.AnkiValidation

/**
 * Behavioral proofs for the unified fixes build (doctor's quality
 * bar, Nov 2026): each test drives the EXACT pure function the app
 * executes and asserts the original failure can no longer happen.
 */
class BehavioralFixesTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    // ---------- F-01: manifest agreement ----------
    @Test
    fun manifestHashMatchesRealVectorAndMismatchIsRejected() {
        val bytes = "abc".toByteArray(Charsets.UTF_8)
        // blake2b-8("abc") — Python hashlib vector.
        assertEquals("d8bb14d833d59559", SectionVerification.manifestHashHex("questions/x.json", bytes))
        assertNull(SectionVerification.manifestMismatch("questions/x.json", bytes, 3L, "d8bb14d833d59559"))
        // Tampered hash -> rejected with a reason.
        assertNotNull(SectionVerification.manifestMismatch("questions/x.json", bytes, 3L, "0000000000000000"))
        // Tampered size -> rejected with a reason.
        assertNotNull(SectionVerification.manifestMismatch("questions/x.json", bytes, 99L, "d8bb14d833d59559"))
        // sha256 family: sha256("abc")[:16] = ba7816bf8f01cfea.
        assertNull(SectionVerification.manifestMismatch("cases.json", bytes, 3L, "ba7816bf8f01cfea"))
    }

    // ---------- F-02: zero questions = explicit failure ----------
    @Test
    fun zeroQuestionEnvelopeFailsExplicitly() {
        val json = """{"version":"1","count":12,"topics":[{"id":"t","name":"x","questions":[]}],"sessions":[]}"""
        try {
            SectionVerification.verifySectionCount("questions/a.json", json, moshi)
            fail("zero parsed questions must fail")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("0"))
        }
    }

    @Test
    fun validSubjectEnvelopeCountsExactly() {
        val q = """{"id":"1","text":"t","options":["a","b"],"correct":0,"explanation":"e"}"""
        val json = """{"version":"1","count":2,"topics":[{"id":"t","name":"x","questions":[$q,$q]}],"sessions":[]}"""
        assertEquals(2, SectionVerification.verifySectionCount("questions/a.json", json, moshi))
    }

    // ---------- F-03: guide.json really parsed ----------
    @Test
    fun guideIsParsedAndCounted() {
        val json = """{"version":"1","groups":[{"title":"g1","items":["a"]},{"title":"g2","items":["b"]}]}"""
        assertEquals(2, SectionVerification.verifySectionCount("guide.json", json, moshi))
        try {
            SectionVerification.verifySectionCount("guide.json", """{"version":"1","groups":[]}""", moshi)
            fail("empty guide must fail")
        } catch (_: IllegalStateException) {
        }
    }

    // ---------- F-04: cases count honesty for ANY value ----------
    @Test
    fun casesCountMismatchFailsForAnyDeclaredValue() {
        val caseJson = """{"id":"c1","title":"t","presentation":"p","explanation":"e"}"""
        val ok = """{"version":"1","count":2,"cases":[$caseJson,$caseJson]}"""
        assertEquals(2, SectionVerification.verifySectionCount("cases.json", ok, moshi))
        val bad = """{"version":"1","count":500,"cases":[$caseJson,$caseJson]}"""
        try {
            SectionVerification.verifySectionCount("cases.json", bad, moshi)
            fail("436-style hardcoding is gone: 500 declared vs 2 actual must fail")
        } catch (_: IllegalStateException) {
        }
        // Missing count field = failure, not a silent default.
        val noCount = """{"version":"1","cases":[$caseJson]}"""
        try {
            SectionVerification.verifySectionCount("cases.json", noCount, moshi)
            fail("missing count must fail")
        } catch (_: IllegalStateException) {
        }
    }

    // ---------- Anki F-05/F-06: atomic adoption ----------
    private fun ankiJson(version: String, total: Int, cards: Int): String {
        val card = """{"front":"f","back":"b"}"""
        val cardsJson = (1..cards).joinToString(",") { card }
        return """{"version":"$version","total":$total,"subjects":[{"name":"s","topics":[{"name":"t","cards":[$cardsJson]}]}]}"""
    }

    @Test
    fun ankiAdoptionRules() {
        val good = AnkiValidation.validatedParse(ankiJson("2026-11-07", 3, 3), moshi)
        assertNotNull(good)
        assertEquals(3, good!!.cards.size)
        // total mismatch -> rejected wholesale.
        assertNull(AnkiValidation.validatedParse(ankiJson("2026-11-08", 99, 3), moshi))
        // corrupt text -> rejected.
        assertNull(AnkiValidation.validatedParse("{not json", moshi))
        // empty set -> rejected.
        assertNull(AnkiValidation.validatedParse(ankiJson("2026-11-08", 0, 0), moshi))
        // version ordering: only strictly newer is adopted.
        assertTrue(AnkiValidation.isStrictlyNewer("2026-11-08", "2026-11-07"))
        assertTrue(!AnkiValidation.isStrictlyNewer("2026-11-07", "2026-11-07"))
        assertTrue(!AnkiValidation.isStrictlyNewer("2026-11-06", "2026-11-07"))
    }
}

/** Restore-endpoint decision (org.json needs Robolectric's real implementation). */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class RestoreResponseTest {

    @Test
    fun successAnswerYieldsActivationToStore() {
        val body = """{"ok":true,"token":"tok123","code":"CODE-9","expiresAt":1893456000000}"""
        val restored = RestoreResponse.parse(true, body)
        assertNotNull(restored)
        assertEquals("tok123", restored!!.token)
        assertEquals("CODE-9", restored.code)
        assertEquals(1893456000000L, restored.expiresAtMillis)
    }

    @Test
    fun rejectionAnswersYieldNothingToStore() {
        assertNull(RestoreResponse.parse(false, """{"ok":true,"token":"t","code":"c"}"""))
        assertNull(RestoreResponse.parse(true, """{"ok":false,"error":"device_limit"}"""))
        assertNull(RestoreResponse.parse(true, """{"ok":true,"token":"","code":"c"}"""))
        assertNull(RestoreResponse.parse(true, """{"ok":true,"token":"t"}"""))
        assertNull(RestoreResponse.parse(true, "not json"))
    }

    @Test
    fun expiresAtShapes() {
        val secs = RestoreResponse.parse(true, """{"ok":true,"token":"t","code":"c","expiresAt":1893456000}""")
        assertEquals(1893456000000L, secs!!.expiresAtMillis)
        val iso = RestoreResponse.parse(true, """{"ok":true,"token":"t","code":"c","expiresAt":"2030-01-01T00:00:00Z"}""")
        assertEquals(1893456000000L, iso!!.expiresAtMillis)
    }
}
