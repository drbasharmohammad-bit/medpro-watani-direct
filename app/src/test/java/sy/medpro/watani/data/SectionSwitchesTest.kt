package sy.medpro.watani.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import sy.medpro.watani.data.SectionSwitches.EntryDecision

/**
 * Build 1.2.53 — section switches (doctor's «مرونة» order, 27 Nov
 * 2026): the admin panel flips each section between open /
 * activated-only / locked-for-everyone, and the app merges the
 * gateway values over defaults that reproduce TODAY'S behaviour
 * exactly, so nothing changes until the doctor flips a switch
 * himself.
 */
class SectionSwitchesTest {

    @Test
    fun defaultsReproduceTodaysBehaviour() {
        val d = SectionSwitches.DEFAULTS
        // Banks: entry open to every signed-in student today (the
        // per-subject gates inside do the locking).
        assertEquals(SwitchState.OPEN, d[SectionKey.BANKS])
        // Cases: entry open today (first cases free, rest activated —
        // the section's own internal gate).
        assertEquals(SwitchState.OPEN, d[SectionKey.CASES])
        // First Aid: entry open today (first units free).
        assertEquals(SwitchState.OPEN, d[SectionKey.FIRSTAID])
        // Videos: inside the activation-gated library today.
        assertEquals(SwitchState.ACTIVATED, d[SectionKey.VIDEOS])
        // UWorld: activation-required tab today.
        assertEquals(SwitchState.ACTIVATED, d[SectionKey.UWORLD])
        // Library: activation-required tab today.
        assertEquals(SwitchState.ACTIVATED, d[SectionKey.LIBRARY])
        // Student tips: open to every signed-in student today.
        assertEquals(SwitchState.OPEN, d[SectionKey.TIPS])
        assertEquals(SectionKey.values().size, d.size)
    }

    @Test
    fun parseNullOrMissingGivesDefaults() {
        assertEquals(SectionSwitches.DEFAULTS, SectionSwitches.parse(null))
        assertEquals(SectionSwitches.DEFAULTS, SectionSwitches.parse(JSONObject()))
    }

    @Test
    fun parseAppliesValidOverridesOnly() {
        val cfg = JSONObject(
            """{"banks":"locked","uworld":"open","videos":"bogus","unknown":"locked","cases":42}"""
        )
        val parsed = SectionSwitches.parse(cfg)
        assertEquals(SwitchState.LOCKED, parsed[SectionKey.BANKS])
        assertEquals(SwitchState.OPEN, parsed[SectionKey.UWORLD])
        // Invalid value → default, never a crash and never a guess.
        assertEquals(SwitchState.ACTIVATED, parsed[SectionKey.VIDEOS])
        // Non-string value → default.
        assertEquals(SwitchState.OPEN, parsed[SectionKey.CASES])
        // Untouched keys keep defaults.
        assertEquals(SwitchState.ACTIVATED, parsed[SectionKey.LIBRARY])
    }

    @Test
    fun parseIsCaseAndSpaceTolerant() {
        val cfg = JSONObject("""{"tips":" LOCKED "}""")
        assertEquals(SwitchState.LOCKED, SectionSwitches.parse(cfg)[SectionKey.TIPS])
    }

    @Test
    fun entryDecisionMatrix() {
        assertEquals(EntryDecision.ALLOW, SectionSwitches.entryDecision(SwitchState.OPEN, false))
        assertEquals(EntryDecision.ALLOW, SectionSwitches.entryDecision(SwitchState.OPEN, true))
        assertEquals(EntryDecision.NEEDS_ACTIVATION, SectionSwitches.entryDecision(SwitchState.ACTIVATED, false))
        assertEquals(EntryDecision.ALLOW, SectionSwitches.entryDecision(SwitchState.ACTIVATED, true))
        assertEquals(EntryDecision.LOCKED, SectionSwitches.entryDecision(SwitchState.LOCKED, false))
        assertEquals(EntryDecision.LOCKED, SectionSwitches.entryDecision(SwitchState.LOCKED, true))
    }

    @Test
    fun configNamesRoundTrip() {
        for (key in SectionKey.values()) {
            assertEquals(key, SectionKey.fromConfigName(key.configName))
        }
        assertEquals(null, SectionKey.fromConfigName("dawrat_sessions"))
    }
}
