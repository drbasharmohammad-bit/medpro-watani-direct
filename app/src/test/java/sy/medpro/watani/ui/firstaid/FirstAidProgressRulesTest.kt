package sy.medpro.watani.ui.firstaid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sy.medpro.watani.ui.firstaid.FirstAidProgressRules.UnitStatus

/**
 * JVM tests for the First Aid unlock/completion rules (build 1.2.36).
 *
 * THE CONTRACT under test (merge plan §5.2/§5.4): a Step 2 unit is
 * done when its exit check reaches the pack's own 0.6 threshold and
 * that opens the next path unit; a Step 1 unit (no exit check exists
 * in its source pack) is done by finishing its training; integration
 * sessions are never locked.
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

    // ---- path locking ------------------------------------------------------

    @Test
    fun firstUnitIsAvailableWithNothingDone() {
        assertEquals(
            UnitStatus.AVAILABLE,
            FirstAidProgressRules.status(path, "U1", emptySet(), isIntegrationSession = false)
        )
    }

    @Test
    fun laterUnitsAreLockedUntilPreviousIsDone() {
        assertEquals(
            UnitStatus.LOCKED,
            FirstAidProgressRules.status(path, "U2", emptySet(), isIntegrationSession = false)
        )
        assertEquals(
            UnitStatus.LOCKED,
            FirstAidProgressRules.status(path, "U3", setOf("U1"), isIntegrationSession = false)
        )
    }

    @Test
    fun finishingAUnitOpensExactlyTheNext() {
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
            UnitStatus.LOCKED,
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
    fun integrationSessionsAreNeverLocked() {
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
