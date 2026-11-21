package sy.medpro.watani.ui.firstaid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sy.medpro.watani.ui.firstaid.FirstAidProgressRules.UnitStatus

/**
 * JVM tests for the First Aid completion rules (build 1.2.36;
 * contract updated 1.2.38).
 *
 * THE CONTRACT under test: a Step 2 unit is done when its exit check
 * reaches the pack's own 0.6 threshold; a Step 1 unit (no exit check
 * exists in its source pack) is done by finishing its training.
 * Since 1.2.38 (doctor's no-hard-locks decision) the ≥60% rule is
 * ONLY the definition of completion — status() NEVER returns LOCKED:
 * every unit is openable whatever its predecessors' state, and the
 * course order is advisory.
 */
class FirstAidProgressRulesTest {

    private val path = listOf("U1", "U2", "U3")

    // ---- exit threshold --------------------------------------------------

    @Test
    fun exitPassesAtExactlySixtyPercent() {
        assertTrue(FirstAidProgressRules.exitPasses(3, 5))
    }

    @Test
    fun exitFailsBelowSixtyPercent() {
        assertFalse(FirstAidProgressRules.exitPasses(2, 5))
        assertFalse(FirstAidProgressRules.exitPasses(59, 100))
    }

    @Test
    fun exitPassesAboveSixtyPercent() {
        assertTrue(FirstAidProgressRules.exitPasses(4, 5))
        assertTrue(FirstAidProgressRules.exitPasses(5, 5))
    }

    @Test
    fun emptyExitCheckNeverPasses() {
        assertFalse(FirstAidProgressRules.exitPasses(0, 0))
    }

    @Test
    fun step2DoneNeedsRecordedPassingFraction() {
        assertFalse(FirstAidProgressRules.step2Done(null))
        assertFalse(FirstAidProgressRules.step2Done(0.59))
        assertTrue(FirstAidProgressRules.step2Done(0.6))
        assertTrue(FirstAidProgressRules.step2Done(1.0))
    }

    // ---- no hard locks (1.2.38) --------------------------------------------

    @Test
    fun firstUnitIsAvailableWithNothingDone() {
        assertEquals(
            UnitStatus.AVAILABLE,
            FirstAidProgressRules.status(path, "U1", emptySet(), isIntegrationSession = false)
        )
    }

    @Test
    fun laterUnitsAreAvailableWithoutAnyPredecessorDone() {
        // The 1.2.36 contract locked these; the doctor's 1.2.38
        // decision removed every hard lock — order is advisory only.
        assertEquals(
            UnitStatus.AVAILABLE,
            FirstAidProgressRules.status(path, "U2", emptySet(), isIntegrationSession = false)
        )
        assertEquals(
            UnitStatus.AVAILABLE,
            FirstAidProgressRules.status(path, "U3", setOf("U1"), isIntegrationSession = false)
        )
        assertEquals(
            UnitStatus.AVAILABLE,
            FirstAidProgressRules.status(path, "U3", emptySet(), isIntegrationSession = false)
        )
    }

    @Test
    fun finishingAUnitMarksItDoneAndEverythingStaysAvailable() {
        val done = setOf("U1")
        assertEquals(
            UnitStatus.DONE,
            FirstAidProgressRules.status(path, "U1", done, isIntegrationSession = false)
        )
        assertEquals(
            UnitStatus.AVAILABLE,
            FirstAidProgressRules.status(path, "U2", done, isIntegrationSession = false)
        )
        assertEquals(
            UnitStatus.AVAILABLE,
            FirstAidProgressRules.status(path, "U3", done, isIntegrationSession = false)
        )
    }

    @Test
    fun doneUnitStaysDoneEvenIfLaterStateChanges() {
        assertEquals(
            UnitStatus.DONE,
            FirstAidProgressRules.status(path, "U2", setOf("U2"), isIntegrationSession = false)
        )
    }

    // ---- integration sessions ------------------------------------------------

    @Test
    fun integrationSessionsAreAvailableOrDone() {
        assertEquals(
            UnitStatus.AVAILABLE,
            FirstAidProgressRules.status(path, "INT-1", emptySet(), isIntegrationSession = true)
        )
        assertEquals(
            UnitStatus.DONE,
            FirstAidProgressRules.status(path, "INT-1", setOf("INT-1"), isIntegrationSession = true)
        )
    }

    // ---- step 1 completion -----------------------------------------------------

    @Test
    fun step1DoneIsTrainingFinished() {
        assertTrue(FirstAidProgressRules.step1Done(trainingFinished = true))
        assertFalse(FirstAidProgressRules.step1Done(trainingFinished = false))
    }
}
