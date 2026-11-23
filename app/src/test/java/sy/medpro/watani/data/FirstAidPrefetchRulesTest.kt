package sy.medpro.watani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the First Aid unit warm-up planner (build 1.2.45).
 *
 * THE CONTRACT under test (the doctor's professional-cache standard,
 * 23 Nov 2026 — device report: a unit's first open sat behind a long
 * bare spinner because nothing fetched the package ahead of time):
 * the warm-up must plan exactly the packages that are NOT yet cached
 * — every cached id skipped (its open is instant and re-fetching
 * would burn the student's bandwidth), order preserved (course
 * order, so the units a student reaches first land first), blanks
 * and duplicates removed.
 */
class FirstAidPrefetchRulesTest {

    @Test
    fun `uncached ids are planned in course order`() {
        val planned = FirstAidPrefetchRules.missing(listOf("U3", "U1", "U2")) { false }
        assertEquals(listOf("U3", "U1", "U2"), planned)
    }

    @Test
    fun `cached ids are skipped`() {
        val cached = setOf("U2", "U4")
        val planned = FirstAidPrefetchRules.missing(listOf("U1", "U2", "U3", "U4")) { it in cached }
        assertEquals(listOf("U1", "U3"), planned)
    }

    @Test
    fun `fully cached step plans nothing`() {
        val planned = FirstAidPrefetchRules.missing(listOf("U1", "U2")) { true }
        assertTrue(planned.isEmpty())
    }

    @Test
    fun `blank and duplicate ids collapse`() {
        val planned = FirstAidPrefetchRules.missing(listOf("U1", "", "U1", "U2", "U2")) { false }
        assertEquals(listOf("U1", "U2"), planned)
    }

    @Test
    fun `empty index plans nothing`() {
        assertTrue(FirstAidPrefetchRules.missing(emptyList()) { false }.isEmpty())
    }
}
