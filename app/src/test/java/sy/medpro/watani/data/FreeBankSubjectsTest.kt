package sy.medpro.watani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * Layer 2 — the free-bank subjects table (build 1.2.47, the
 * doctor's activation policy): القلبية / التوليد / الكلية والشوارد
 * are open in full in the national bank and in every university
 * bank; everything else in the banks stays activation-locked.
 * These are the exact production functions the quiz gates, the
 * fetch switch and the revocation wipe consult — and the path
 * table mirrors the worker gateway's own test table
 * (watani-api/test_activation_gateway.mjs, section 9), so the app
 * and the gateway cannot drift apart silently.
 */
class FreeBankSubjectsTest {

    private companion object {
        /** Mirrors FreeBankSubjects.SHARD_HASH_HEX_LENGTH. */
        const val SHARD_HASH_HEX_LENGTH = 16
    }

    private val freePaths = listOf(
        // National bank (Arabic + English copies), canonical names.
        "questions/القلبية.json",
        "questions/التوليد.json",
        "questions/الكلية والشوارد.json",
        "questions_en/القلبية.json",
        "questions_en/الكلية والشوارد.json",
        // Damascus: short forms, both views (topics + years twin).
        "dawrat_jami3at/damascus/قلبية.json",
        "dawrat_jami3at/damascus/توليد.json",
        "dawrat_jami3at/damascus/كلية.json",
        "dawrat_jami3at/damascus_years/القلبية.json",
        "dawrat_jami3at/damascus_years/كلية.json",
        // Hama / Idlib: canonical names.
        "dawrat_jami3at/hama/القلبية.json",
        "dawrat_jami3at/idlib/التوليد.json",
        // Aleppo / Homs / Lattakia: their own subject names.
        "dawrat_jami3at/aleppo/باطنة قلبية.json",
        "dawrat_jami3at/aleppo/طب التوليد.json",
        "dawrat_jami3at/homs/باطنة قلبية.json",
        "dawrat_jami3at/lattakia/أمراض الكلية الباطنة.json",
        "dawrat_jami3at/tartus/التوليد.json",
        // Explanation shards (both naming schemes) + the layer index.
        "explanations/القلبية.json",
        "explanations/قلبية.json",
        "explanations/التوليد.json",
        "explanations/الكلية والشوارد.json",
        "explanations/كلية.json",
        "explanations/index.json"
    )

    private val lockedPaths = listOf(
        // الجراحة العامة is NOT free (the doctor replaced it with
        // nephrology in the final list) — in every naming scheme.
        "questions/الجراحة العامة.json",
        "dawrat_jami3at/damascus/جراحة عامة.json",
        // Lookalikes must never match by substring.
        "questions/جراحة القلب.json",
        "dawrat_jami3at/damascus/جراحة القلب والصدر.json",
        "dawrat_jami3at/lattakia/الأمراض النسائية.json",
        "questions/الغدية والسكري.json",
        "questions/العصبية.json",
        // Whole families that stay locked in full.
        "uworld/2025.json",
        "uworld/2026.json",
        "library.json",
        "dawrat_sessions.json",
        "dawrat_sessions/شباط 2019.json",
        "books/كتاب القلب.pdf",
        "library/subject/index.json",
        "dawrat/القلبية.json",
        // Shape violations: too deep, too shallow, wrong extension.
        "questions/القلبية/extra.json",
        "dawrat_jami3at/القلبية.json",
        "questions/القلبية.pdf",
        "explanations/الغدية والسكري.json",
        "cases.json"
    )

    @Test
    fun freeSubjectFilesAreFreeInEveryBank() {
        for (path in freePaths) {
            assertTrue("expected free: $path", FreeBankSubjects.isFreeBankPath(path))
            assertFalse("expected no activation: $path", WorkerActivationApi.requiresActivation(path))
        }
    }

    @Test
    fun everythingElseStaysActivationLocked() {
        for (path in lockedPaths) {
            assertFalse("expected locked: $path", FreeBankSubjects.isFreeBankPath(path))
        }
        // The restricted subset of the locked list still requires
        // activation through the one gate function the UI consults
        // (cases.json is the only entry that is not restricted at all).
        val restrictedLocked = lockedPaths - listOf("cases.json")
        for (path in restrictedLocked) {
            assertTrue("expected activation required: $path", WorkerActivationApi.requiresActivation(path))
        }
    }

    @Test
    fun freeListIsExactlyTheDoctorsThree() {
        assertEquals(
            listOf("القلبية", "التوليد", "الكلية والشوارد"),
            FreeBankSubjects.FREE_SUBJECTS.map { it.canonical }
        )
    }

    @Test
    fun sectionsCacheNamesOfFreeSubjectsAreRecognized() {
        val freeFiles = listOf(
            "questions_القلبية.json.json",
            "questions_en_الكلية والشوارد.json.json",
            "dawrat_jami3at_damascus_قلبية.json.json",
            "dawrat_jami3at_damascus_years_التوليد.json.json",
            "dawrat_jami3at_aleppo_باطنة قلبية.json.json",
            "dawrat_jami3at_lattakia_أمراض الكلية الباطنة.json.json",
            "questions_التوليد.json.json.tmp"
        )
        for (name in freeFiles) {
            assertTrue("expected free cache file: $name", FreeBankSubjects.isFreeBankCacheFile(name))
            assertFalse("wipe must keep: $name", RestrictedInventory.isRestrictedSectionFile(name))
        }
        val lockedFiles = listOf(
            "questions_الجراحة العامة.json.json",
            "questions_الغدية والسكري.json.json",
            "dawrat_jami3at_damascus_جراحة القلب.json.json",
            "uworld_2025.json.json",
            "library.json.json",
            "dawrat_sessions_شباط 2019.json.json"
        )
        for (name in lockedFiles) {
            assertFalse("expected locked cache file: $name", FreeBankSubjects.isFreeBankCacheFile(name))
            assertTrue("wipe must drop: $name", RestrictedInventory.isRestrictedSectionFile(name))
        }
    }

    private fun shardHash(shardName: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(shardName.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(SHARD_HASH_HEX_LENGTH)

    @Test
    fun explanationCacheKeepsOnlyFreeShardsAndTheIndex() {
        assertTrue(FreeBankSubjects.isFreeExplanationCacheFile("index.json"))
        for (alias in listOf("القلبية", "قلبية", "التوليد", "توليد", "الكلية والشوارد", "كلية", "باطنة قلبية", "طب التوليد", "أمراض الكلية الباطنة")) {
            val h = shardHash("$alias.json")
            assertTrue("free shard file: $alias", FreeBankSubjects.isFreeExplanationCacheFile("$h.json"))
            assertTrue("free shard fingerprint: $alias", FreeBankSubjects.isFreeExplanationCacheFile("$h.fp"))
        }
        val lockedHash = shardHash("الغدية والسكري.json")
        assertFalse(FreeBankSubjects.isFreeExplanationCacheFile("$lockedHash.json"))
        assertFalse(FreeBankSubjects.isFreeExplanationCacheFile("$lockedHash.fp"))
        assertFalse(FreeBankSubjects.isFreeExplanationCacheFile("manifest_cache.json"))
    }
}
