package sy.medpro.watani.ui.cases

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the case-list activation gate (doctor's order: the
 * first 10 cases of EACH section/family are free, from the 11th on
 * activation is required — one unified activation state for the
 * whole app, checked by the screens via WorkerActivationApi).
 *
 * Families under test:
 * - Legacy clinical cases and upgraded virtual-patient cases
 *   (schema v2) share ONE mixed list in CasesScreen, so each case is
 *   ranked within its OWN family in display order.
 * - The German list is a single family: rank = display index.
 */
class CaseAccessRulesTest {

    // ---- isFreeRank: the boundary, unactivated -----------------------------

    @Test
    fun firstCaseIsFreeForUnactivated() {
        assertTrue(CaseAccessRules.isFreeRank(rankInFamily = 0, activated = false))
    }

    @Test
    fun tenthCaseIsFreeForUnactivated() {
        // Rank 9 is the 10th case: still inside the free ten.
        assertTrue(CaseAccessRules.isFreeRank(rankInFamily = 9, activated = false))
    }

    @Test
    fun eleventhCaseIsLockedForUnactivated() {
        // Rank 10 is the 11th case: the first locked one.
        assertFalse(CaseAccessRules.isFreeRank(rankInFamily = 10, activated = false))
        assertTrue(CaseAccessRules.isLockedRank(rankInFamily = 10, activated = false))
    }

    @Test
    fun farBeyondTheFreeTenIsLockedForUnactivated() {
        assertFalse(CaseAccessRules.isFreeRank(rankInFamily = 11, activated = false))
        assertFalse(CaseAccessRules.isFreeRank(rankInFamily = 100, activated = false))
        assertFalse(CaseAccessRules.isFreeRank(rankInFamily = Int.MAX_VALUE, activated = false))
    }

    @Test
    fun freeTenAreNotLockedForUnactivated() {
        assertFalse(CaseAccessRules.isLockedRank(rankInFamily = 0, activated = false))
        assertFalse(CaseAccessRules.isLockedRank(rankInFamily = 9, activated = false))
    }

    // ---- isFreeRank: activated opens everything ----------------------------

    @Test
    fun activatedOpensEveryRank() {
        assertTrue(CaseAccessRules.isFreeRank(rankInFamily = 0, activated = true))
        assertTrue(CaseAccessRules.isFreeRank(rankInFamily = 9, activated = true))
        assertTrue(CaseAccessRules.isFreeRank(rankInFamily = 10, activated = true))
        assertTrue(CaseAccessRules.isFreeRank(rankInFamily = 500, activated = true))
        assertFalse(CaseAccessRules.isLockedRank(rankInFamily = 500, activated = true))
    }

    @Test
    fun freeLimitIsTen() {
        assertEquals(10, CaseAccessRules.FREE_CASES_PER_FAMILY)
    }

    // ---- familyRanks: the mixed clinical / virtual-patient list ------------

    @Test
    fun ranksAreCountedWithinEachFamilyInDisplayOrder() {
        // Interleaved display order: legacy, upgraded, legacy,
        // upgraded, upgraded, legacy — each family counts its own.
        val flags = listOf(false, true, false, true, true, false)
        assertEquals(listOf(0, 0, 1, 1, 2, 2), CaseAccessRules.familyRanks(flags))
    }

    @Test
    fun eachFamilyGetsItsOwnFreeTenInTheMixedList() {
        // 12 upgraded cases first, then 12 legacy cases: BOTH
        // families must show ranks 0..11, i.e. each has its own
        // free ranks 0..9 and its own locked ranks 10..11.
        val flags = List(12) { true } + List(12) { false }
        val ranks = CaseAccessRules.familyRanks(flags)
        assertEquals((0..11).toList() + (0..11).toList(), ranks)
        val freeCount = flags.indices.count {
            CaseAccessRules.isFreeRank(ranks[it], activated = false)
        }
        assertEquals(20, freeCount)
    }

    @Test
    fun upgradedCasesDoNotConsumeLegacyFreeSlots() {
        // 15 upgraded cases ahead of the legacy ones: the first
        // legacy case is still rank 0 of ITS family (free), and the
        // 11th upgraded case (rank 10) is locked.
        val flags = List(15) { true } + listOf(false)
        val ranks = CaseAccessRules.familyRanks(flags)
        assertEquals(0, ranks.last())
        assertTrue(CaseAccessRules.isFreeRank(ranks.last(), activated = false))
        assertFalse(CaseAccessRules.isFreeRank(ranks[10], activated = false))
        assertTrue(CaseAccessRules.isFreeRank(ranks[9], activated = false))
    }

    // ---- familyRanks: the German single-family list -------------------------

    @Test
    fun germanSingleFamilyRanksAreTheDisplayIndices() {
        val ranks = CaseAccessRules.familyRanks(List(15) { false })
        assertEquals((0..14).toList(), ranks)
        // First ten German cases free, 11th on locked (unactivated).
        assertTrue(CaseAccessRules.isFreeRank(ranks[0], activated = false))
        assertTrue(CaseAccessRules.isFreeRank(ranks[9], activated = false))
        assertFalse(CaseAccessRules.isFreeRank(ranks[10], activated = false))
        assertFalse(CaseAccessRules.isFreeRank(ranks[14], activated = false))
        // Activated: the whole German list opens.
        assertTrue(CaseAccessRules.isFreeRank(ranks[14], activated = true))
    }

    // ---- familyRanks: degenerate inputs --------------------------------------

    @Test
    fun emptyListHasNoRanks() {
        assertEquals(emptyList<Int>(), CaseAccessRules.familyRanks(emptyList()))
    }

    @Test
    fun shortFamilyIsEntirelyFree() {
        val ranks = CaseAccessRules.familyRanks(listOf(true, true, false))
        assertEquals(listOf(0, 1, 0), ranks)
        assertTrue(ranks.all { CaseAccessRules.isFreeRank(it, activated = false) })
    }
}
