package sy.medpro.watani.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sy.medpro.watani.data.ToolGate
import sy.medpro.watani.data.ToolUsageStore
import sy.medpro.watani.data.ToolsLimitStore
import sy.medpro.watani.ui.anki.AnkiGrade
import sy.medpro.watani.ui.anki.FsrsStore
import sy.medpro.watani.ui.anki.UserCards

/**
 * Layer-3 integration (build 1.2.54) for the two flashcards/tools
 * orders, exercised through the real stores on a real (Robolectric)
 * Android context:
 *
 * 1. Student-authored cards: create -> persisted -> FSRS-graded ->
 *    due state exists -> delete removes card AND its schedule.
 * 2. SM-2 -> FSRS migration through the store: a legacy record is
 *    converted on first read (stability from the SM-2 interval, due
 *    time preserved), persisted in the new format, legacy key gone.
 * 3. Tools gate end-to-end: 10 recorded uses pass, the 11th locks
 *    for a non-activated student, an activated student always passes,
 *    and the gateway-served limit (config) replaces the default.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h2400dp")
class CardsAndToolsIntegrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Guest stores (no Firebase user under Robolectric): clear all
        // three prefs files so tests are independent of run order.
        for (name in listOf("anki_srs", "user_cards", "tool_usage", "tools_gate")) {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test
    fun userCardCreateStudyDeleteFlow() {
        val card = UserCards.add(context, "ما أشيع سبب للانسداد المعوي؟", "الالتصاقات بعد الجراحات البطنية.")
        assertEquals(1, UserCards.list(context).size)
        assertEquals(card.id, UserCards.list(context).first().id)
        // New card: no schedule yet, so it is study-ready but not "due".
        assertNull(FsrsStore.state(context, card.id))
        // Grade it Good: FSRS initial stability 3.173 days applies.
        FsrsStore.grade(context, card.id, AnkiGrade.GOOD)
        val state = FsrsStore.state(context, card.id)
        assertNotNull(state)
        assertEquals(3.173, state!!.stability, 0.01)
        assertEquals(5.2824, state.difficulty, 0.01)
        assertTrue(state.dueAtMillis > System.currentTimeMillis())
        assertEquals(0, FsrsStore.dueCount(context, listOf(card.id)))
        // Edit keeps the schedule; delete removes card and schedule.
        UserCards.update(context, card.id, "وجه محدّث", "خلف محدّث")
        assertEquals("وجه محدّث", UserCards.list(context).first().front)
        assertNotNull(FsrsStore.state(context, card.id))
        UserCards.delete(context, card.id)
        assertTrue(UserCards.list(context).isEmpty())
        assertNull(FsrsStore.state(context, card.id))
    }

    @Test
    fun legacySm2RecordMigratesOnFirstRead() {
        val prefs = context.getSharedPreferences("anki_srs", Context.MODE_PRIVATE)
        val due = System.currentTimeMillis() + 86_400_000L
        // Legacy SM-2 record for deck card 4242: due|interval 12.5|ease 250|reps 4|lapses 1.
        prefs.edit().putString("4242", "$due|12.5|250|4|1").commit()
        val state = FsrsStore.state(context, "4242")
        assertNotNull(state)
        assertEquals(12.5, state!!.stability, 0.01)
        assertEquals(5.0, state.difficulty, 0.01)
        assertEquals(due, state.dueAtMillis)
        assertEquals(4, state.repetitions)
        // Converted record persisted in the new format; legacy key gone.
        assertNull(prefs.getString("4242", null))
        assertNotNull(prefs.getString("f:4242", null))
        // A user-card key (non-numeric) never touches legacy lookup.
        assertNull(FsrsStore.state(context, "u0123456789abcdef"))
    }

    @Test
    fun toolsGateFlowThroughStores() {
        val limit = ToolsLimitStore.current(context)
        assertEquals(ToolGate.DEFAULT_FREE_LIMIT, limit)
        // Ten recorded uses pass one by one; the eleventh locks.
        repeat(10) { i ->
            val d = ToolGate.decide(ToolUsageStore.count(context, "calculators"), limit, activated = false)
            assertEquals("use ${i + 1} must pass", ToolGate.Access.ALLOW, d.access)
            ToolUsageStore.recordUse(context, "calculators")
        }
        assertEquals(10, ToolUsageStore.count(context, "calculators"))
        assertEquals(
            ToolGate.Access.LOCKED,
            ToolGate.decide(ToolUsageStore.count(context, "calculators"), limit, activated = false).access
        )
        // Activated passes regardless of the counter.
        assertEquals(
            ToolGate.Access.ALLOW,
            ToolGate.decide(ToolUsageStore.count(context, "calculators"), limit, activated = true).access
        )
        // Counters are per tool: another tool is untouched.
        assertEquals(0, ToolUsageStore.count(context, "osce"))
        // Sync snapshot carries only non-zero counters, and a remote
        // max folds back monotonically.
        val snapshot = ToolUsageStore.snapshotJson(context)
        assertEquals(10, snapshot.getInt("calculators"))
        ToolUsageStore.applyRemoteMax(context, JSONObject().put("calculators", 4).put("osce", 3))
        assertEquals(10, ToolUsageStore.count(context, "calculators"))
        assertEquals(3, ToolUsageStore.count(context, "osce"))
    }

    @Test
    fun toolsLimitComesFromGatewayConfig() {
        ToolsLimitStore.updateFromConfig(context, JSONObject().put("toolsFreeLimit", 3))
        assertEquals(3, ToolsLimitStore.current(context))
        // A config without the field (older gateway) changes nothing.
        ToolsLimitStore.updateFromConfig(context, JSONObject())
        assertEquals(3, ToolsLimitStore.current(context))
        // With limit 3, three uses pass and the fourth locks.
        repeat(3) { ToolUsageStore.recordUse(context, "logbook") }
        assertEquals(
            ToolGate.Access.LOCKED,
            ToolGate.decide(ToolUsageStore.count(context, "logbook"), ToolsLimitStore.current(context), activated = false).access
        )
    }
}
