package sy.medpro.watani.ui.firstaid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sy.medpro.watani.ui.firstaid.FirstAidAccessRules.PathState
import sy.medpro.watani.ui.firstaid.FirstAidAccessRules.Resume
import sy.medpro.watani.ui.firstaid.FirstAidAccessRules.SystemInfo

/**
 * JVM tests for the First Aid access gate + resume derivation
 * (build 1.2.38).
 *
 * THE CONTRACT under test (doctor's decisions, 21 Nov 2026):
 * - The first 3 path units of each step (course order) are free;
 *   every other path unit, every integration session and the
 *   printed banks need a code-activated account. A booklet follows
 *   the gate of the earliest path unit of its system.
 * - The home resume target derives from the student's OWN state
 *   (last opened unit + done sets). A student with zero progress
 *   gets NO resume target — the 1.2.37 defect was a fixed card
 *   (Step 2's first undone unit) shown to everyone, beginners
 *   included.
 */
class FirstAidAccessRulesTest {

    private val path = listOf("U1", "U2", "U3", "U4", "U5")

    // ---- activation gate ---------------------------------------------------

    @Test
    fun freeSetIsExactlyTheFirstThreePathUnits() {
        assertEquals(setOf("U1", "U2", "U3"), FirstAidAccessRules.freePathUnitIds(path))
        assertEquals(setOf("U1", "U2"), FirstAidAccessRules.freePathUnitIds(listOf("U1", "U2")))
        assertEquals(emptySet<String>(), FirstAidAccessRules.freePathUnitIds(emptyList()))
    }

    @Test
    fun unactivatedStudentOpensOnlyFreePathUnits() {
        assertTrue(FirstAidAccessRules.canOpenUnit(path, "U1", isSession = false, activated = false))
        assertTrue(FirstAidAccessRules.canOpenUnit(path, "U3", isSession = false, activated = false))
        assertFalse(FirstAidAccessRules.canOpenUnit(path, "U4", isSession = false, activated = false))
        assertFalse(FirstAidAccessRules.canOpenUnit(path, "U5", isSession = false, activated = false))
    }

    @Test
    fun sessionsAreNeverFree() {
        assertFalse(FirstAidAccessRules.canOpenUnit(path, "U1", isSession = true, activated = false))
        assertFalse(FirstAidAccessRules.canOpenUnit(path, "INT-1", isSession = true, activated = false))
    }

    @Test
    fun activatedStudentOpensEverything() {
        assertTrue(FirstAidAccessRules.canOpenUnit(path, "U5", isSession = false, activated = true))
        assertTrue(FirstAidAccessRules.canOpenUnit(path, "INT-1", isSession = true, activated = true))
        assertTrue(FirstAidAccessRules.canOpenBanks(activated = true))
    }

    @Test
    fun banksAreNeverFree() {
        assertFalse(FirstAidAccessRules.canOpenBanks(activated = false))
    }

    // ---- booklet gate --------------------------------------------------------

    @Test
    fun bookletFollowsItsGateUnit() {
        assertTrue(FirstAidAccessRules.canOpenBooklet("U2", path, activated = false))
        assertFalse(FirstAidAccessRules.canOpenBooklet("U4", path, activated = false))
        assertTrue(FirstAidAccessRules.canOpenBooklet("U4", path, activated = true))
    }

    @Test
    fun bookletWithoutAGateUnitIsActivatedOnly() {
        assertFalse(FirstAidAccessRules.canOpenBooklet(null, path, activated = false))
        assertTrue(FirstAidAccessRules.canOpenBooklet(null, path, activated = true))
    }

    @Test
    fun bookletGateUnitMatchesBySystemCode() {
        val systems = listOf(
            SystemInfo(code = "RESP", nameEn = "Respiratory", unitIds = listOf("U4", "U5")),
            SystemInfo(code = "CARD", nameEn = "Cardiovascular", unitIds = listOf("U1", "U2", "U3"))
        )
        // Step-2 style file name: the code sits between dashes.
        assertEquals(
            "U1",
            FirstAidAccessRules.bookletGateUnitId(
                "booklets2/FirstAid-Step2-CARD-Booklet.pdf", systems, path
            )
        )
        assertEquals(
            "U4",
            FirstAidAccessRules.bookletGateUnitId(
                "booklets2/FirstAid-Step2-RESP-Booklet.pdf", systems, path
            )
        )
    }

    @Test
    fun bookletGateUnitMatchesByEnglishName() {
        val systems = listOf(
            SystemInfo(code = null, nameEn = "Biochemistry", unitIds = listOf("U2", "U1"))
        )
        // Step-1 Mobile style file name; the gate is the EARLIEST
        // unit of the system in PATH order (U1), not list order (U2).
        assertEquals(
            "U1",
            FirstAidAccessRules.bookletGateUnitId(
                "booklets1/FirstAid-Biochemistry-Booklet-Mobile.pdf", systems, path
            )
        )
    }

    @Test
    fun bookletGateUnitIsNullWhenNothingMatches() {
        val systems = listOf(
            SystemInfo(code = "CARD", nameEn = "Cardiovascular", unitIds = listOf("U1"))
        )
        assertNull(
            FirstAidAccessRules.bookletGateUnitId("booklets2/FirstAid-Step2-RENAL-Booklet.pdf", systems, path)
        )
        assertNull(FirstAidAccessRules.bookletGateUnitId(null, systems, path))
        assertNull(FirstAidAccessRules.bookletGateUnitId("", systems, path))
    }

    // ---- resume derivation ---------------------------------------------------

    private fun state(ids: List<String>, done: Set<String>) = PathState(ids, done)
    private val p1 = listOf("A1", "A2", "A3")
    private val p2 = listOf("B1", "B2", "B3")

    @Test
    fun zeroProgressStudentGetsNoResumeTarget() {
        // THE 1.2.37 defect pin: a brand-new student must NOT be
        // offered a «تابع من حيث توقفت» card at all.
        assertNull(
            FirstAidAccessRules.resumeTarget(
                lastOpenedStep = null, lastOpenedId = null,
                step1 = state(p1, emptySet()),
                step2 = state(p2, emptySet())
            )
        )
    }

    @Test
    fun resumeIsTheLastOpenedUnitWhenNotDone() {
        assertEquals(
            Resume(2, "B2"),
            FirstAidAccessRules.resumeTarget(
                lastOpenedStep = 2, lastOpenedId = "B2",
                step1 = state(p1, emptySet()),
                step2 = state(p2, emptySet())
            )
        )
    }

    @Test
    fun resumeAdvancesPastTheLastOpenedUnitOnceDone() {
        assertEquals(
            Resume(2, "B3"),
            FirstAidAccessRules.resumeTarget(
                lastOpenedStep = 2, lastOpenedId = "B2",
                step1 = state(p1, emptySet()),
                step2 = state(p2, setOf("B2"))
            )
        )
    }

    @Test
    fun resumeWrapsToFirstUndoneWhenTailIsDone() {
        assertEquals(
            Resume(1, "A1"),
            FirstAidAccessRules.resumeTarget(
                lastOpenedStep = 1, lastOpenedId = "A3",
                step1 = state(p1, setOf("A2", "A3")),
                step2 = state(p2, emptySet())
            )
        )
    }

    @Test
    fun touchedPathWinsOverTheOtherPath() {
        // The student last worked in Step 1: resume there even
        // though Step 2 also has undone units.
        assertEquals(
            Resume(1, "A2"),
            FirstAidAccessRules.resumeTarget(
                lastOpenedStep = 1, lastOpenedId = "A2",
                step1 = state(p1, emptySet()),
                step2 = state(p2, emptySet())
            )
        )
    }

    @Test
    fun legacyProgressWithoutLastOpenedFallsBackToStep2First() {
        // Progress recorded before 1.2.38 has done units but no
        // last-opened record: the old order applies (Step 2 first).
        assertEquals(
            Resume(2, "B2"),
            FirstAidAccessRules.resumeTarget(
                lastOpenedStep = null, lastOpenedId = null,
                step1 = state(p1, setOf("A1")),
                step2 = state(p2, setOf("B1"))
            )
        )
    }

    @Test
    fun finishedTouchedPathFallsThroughToTheOtherPath() {
        assertEquals(
            Resume(1, "A1"),
            FirstAidAccessRules.resumeTarget(
                lastOpenedStep = 2, lastOpenedId = "B3",
                step1 = state(p1, emptySet()),
                step2 = state(p2, setOf("B1", "B2", "B3"))
            )
        )
    }

    @Test
    fun everythingDoneMeansNoResumeTarget() {
        assertNull(
            FirstAidAccessRules.resumeTarget(
                lastOpenedStep = 2, lastOpenedId = "B3",
                step1 = state(p1, setOf("A1", "A2", "A3")),
                step2 = state(p2, setOf("B1", "B2", "B3"))
            )
        )
    }

    @Test
    fun staleLastOpenedIdIsIgnored() {
        // A last-opened id that is not in the path (content changed)
        // must not crash or produce a phantom target.
        assertEquals(
            Resume(2, "B1"),
            FirstAidAccessRules.resumeTarget(
                lastOpenedStep = 2, lastOpenedId = "GHOST",
                step1 = state(p1, emptySet()),
                step2 = state(p2, setOf("B2"))
            )
        )
    }
}
