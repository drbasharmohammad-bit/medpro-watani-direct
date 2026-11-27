package sy.medpro.watani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tools gate logic (build 1.2.54): 10 free uses per tool by default,
 * the 11th locks for a non-activated student and always passes for an
 * activated one (offline-safe — the decision is purely local).
 */
class ToolGateTest {

    @Test
    fun firstTenUsesPass_eleventhLocks() {
        for (used in 0..9) {
            val d = ToolGate.decide(used, freeLimit = 10, activated = false)
            assertEquals("used=$used must pass", ToolGate.Access.ALLOW, d.access)
            assertEquals(10 - used, d.remainingFree)
        }
        val eleventh = ToolGate.decide(10, freeLimit = 10, activated = false)
        assertEquals(ToolGate.Access.LOCKED, eleventh.access)
        assertEquals(0, eleventh.remainingFree)
        assertEquals(ToolGate.Access.LOCKED, ToolGate.decide(25, 10, activated = false).access)
    }

    @Test
    fun activatedAlwaysPasses() {
        assertEquals(ToolGate.Access.ALLOW, ToolGate.decide(0, 10, activated = true).access)
        assertEquals(ToolGate.Access.ALLOW, ToolGate.decide(10, 10, activated = true).access)
        assertEquals(ToolGate.Access.ALLOW, ToolGate.decide(10_000, 10, activated = true).access)
    }

    @Test
    fun limitIsAdjustable() {
        // The gateway-driven limit replaces the default wholesale.
        assertEquals(ToolGate.Access.LOCKED, ToolGate.decide(3, freeLimit = 3, activated = false).access)
        assertEquals(ToolGate.Access.ALLOW, ToolGate.decide(2, freeLimit = 3, activated = false).access)
        assertEquals(ToolGate.Access.LOCKED, ToolGate.decide(0, freeLimit = 0, activated = false).access)
        assertEquals(10, ToolGate.DEFAULT_FREE_LIMIT)
    }

    @Test
    fun registryCoversTheTwentyToolsUniquely() {
        assertEquals(20, ToolGate.TOOLS.size)
        assertEquals(20, ToolGate.TOOLS.map { it.id }.toSet().size)
        assertEquals(20, ToolGate.TOOLS.map { it.title }.toSet().size)
        // The two tiles that used to be hard-gated are tools now.
        assertNotNull(ToolGate.byTitle("المراجعة الذكية Anki"))
        assertNotNull(ToolGate.byTitle("محاكاة الامتحان"))
        assertNotNull(ToolGate.byId("calculators"))
        assertNotNull(ToolGate.byId("focus_timer"))
        // Every tool documents what one use means.
        assertTrue(ToolGate.TOOLS.all { it.usageDefinition.isNotBlank() })
    }
}
