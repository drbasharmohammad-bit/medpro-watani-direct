package sy.medpro.watani.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sy.medpro.watani.data.OutboxTip
import sy.medpro.watani.data.ServerTip
import sy.medpro.watani.data.TipLocalStatus
import sy.medpro.watani.data.TipsGateway
import sy.medpro.watani.data.TipsGatewayReply
import sy.medpro.watani.data.TipsMineReply
import sy.medpro.watani.data.TipsOutboxLogic
import sy.medpro.watani.data.TipsPack
import sy.medpro.watani.data.TipsRepository
import sy.medpro.watani.data.TipsSyncEngine
import sy.medpro.watani.data.TipsOutboxStore
import java.io.File

/**
 * Layer 3 — «زوادة الطالب» (build 1.2.51) end-to-end, on the repo's
 * offline-doubles discipline: a FAKE gateway stands in for the
 * worker (no live contact, no real tokens), the outbox store is the
 * engine's real state machine over an in-memory store, and the
 * published pack is seeded into the app's REAL tips cache directory
 * and read back through the production [TipsRepository.cachedPack].
 *
 * Flows proven:
 *  1. Add OFFLINE → saved in the outbox as بانتظار الإرسال → the
 *     flush fails soft and loses nothing → connectivity "returns"
 *     (fake goes online) → flush sends it → قيد المراجعة.
 *  2. Moderation approves → refreshMine shows منشورة → the approved
 *     tip appears in the subject's published pack → the pack is
 *     read OFFLINE from the cache (gateway irrelevant by then).
 *  3. Rejection flow: a second tip comes back مرفوضة with its
 *     reason visible in «إضافاتي» state.
 *  4. Editing the published tip returns it to the outbox as an
 *     unsent edit; the flush pushes it through the EDIT endpoint
 *     and it is قيد المراجعة again.
 *  5. Pack cache: a subject with no pack file reads as a normal
 *     empty section (null pack), never an error.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h1600dp")
class StudentTipsIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private class InMemoryStore : TipsOutboxStore {
        private var entries: List<OutboxTip> = emptyList()
        override fun load(): List<OutboxTip> = entries
        override fun save(entries: List<OutboxTip>) {
            this.entries = entries
        }
    }

    /** Fake worker: offline until [online]; submissions land in a
     * server-side list whose moderation state the test drives. */
    private class FakeGateway : TipsGateway {
        var online = false
        val server = mutableListOf<ServerTip>()
        private var seq = 0
        val editedIds = mutableListOf<String>()
        val deletedIds = mutableListOf<String>()

        override suspend fun submit(tip: OutboxTip): TipsGatewayReply {
            if (!online) return TipsGatewayReply(-2)
            val id = "srv-${++seq}"
            server.add(
                ServerTip(
                    id = id, clientId = tip.localId, subject = tip.subject,
                    sectionId = tip.sectionId, text = tip.text, author = tip.authorName,
                    status = "pending_review", rejectReason = null,
                    createdAt = tip.createdAt
                )
            )
            return TipsGatewayReply(200, id)
        }

        override suspend fun edit(serverId: String, text: String): TipsGatewayReply {
            if (!online) return TipsGatewayReply(-2)
            editedIds.add(serverId)
            val idx = server.indexOfFirst { it.id == serverId }
            if (idx >= 0) server[idx] = server[idx].copy(text = text, status = "pending_review")
            return TipsGatewayReply(200, serverId)
        }

        override suspend fun delete(serverId: String): TipsGatewayReply {
            if (!online) return TipsGatewayReply(-2)
            deletedIds.add(serverId)
            server.removeAll { it.id == serverId }
            return TipsGatewayReply(200, serverId)
        }

        override suspend fun fetchMine(since: Long): TipsMineReply {
            if (!online) return TipsMineReply(-2)
            return TipsMineReply(200, server.toList())
        }

        override suspend fun report(serverId: String, reason: String): TipsGatewayReply =
            if (online) TipsGatewayReply(200, serverId) else TipsGatewayReply(-2)

        override suspend fun fetchTipsWriting(): String? = if (online) "all" else null

        fun moderate(id: String, status: String, reason: String? = null) {
            val idx = server.indexOfFirst { it.id == id }
            check(idx >= 0) { "no such server tip $id" }
            server[idx] = server[idx].copy(status = status, rejectReason = reason)
        }
    }

    private lateinit var gateway: FakeGateway
    private lateinit var engine: TipsSyncEngine
    private var now = 1_000_000L

    @Before
    fun setUp() {
        IntegrationTestSupport.ensureFirebaseApp(context)
        IntegrationTestSupport.clearLocalState(context)
        File(context.filesDir, "tips_cache").deleteRecursively()
        gateway = FakeGateway()
        now = 1_000_000L
        engine = TipsSyncEngine(InMemoryStore(), gateway) { now }
    }

    @Test
    fun offlineAddFlushApprovePackAppearanceAndOfflineRead() = runBlocking {
        // 1) OFFLINE add: saved locally at once, flush fails soft.
        val tip = engine.add(
            subject = "القلبية",
            sectionId = "most_common",
            text = "تخطيط القلب أول خطوة دائمًا في ألم الصدر",
            authorName = "طالب مجتهد"
        )
        assertEquals(TipLocalStatus.PENDING_SEND, engine.mine.value.single().status)
        assertEquals("بانتظار الإرسال", TipsOutboxLogic.statusLabel(engine.mine.value.single().status))
        assertEquals(false, engine.flush())
        assertEquals(TipLocalStatus.PENDING_SEND, engine.mine.value.single().status)
        assertTrue(gateway.server.isEmpty())

        // 2) Connectivity returns: the flush sends it → قيد المراجعة.
        gateway.online = true
        assertEquals(true, engine.flush())
        val sent = engine.mine.value.single()
        assertEquals(TipLocalStatus.PENDING_REVIEW, sent.status)
        assertNotNull(sent.serverId)
        assertEquals(tip.localId, gateway.server.single().clientId)

        // 3) Moderation approves: «إضافاتي» shows منشورة.
        gateway.moderate(sent.serverId!!, "published")
        assertEquals(true, engine.refreshMine())
        assertEquals(TipLocalStatus.PUBLISHED, engine.mine.value.single().status)

        // 4) The approved tip appears in the subject's published
        // pack (seeded exactly as the content pipeline publishes it
        // into the app's real cache directory)…
        val packJson = """
            {"subject":"القلبية","version":1,"updated_at":"2026-11-24",
             "sections":{"most_common":[
               {"id":"${sent.serverId}","text":"تخطيط القلب أول خطوة دائمًا في ألم الصدر",
                "author":"طالب مجتهد","created_at":"2026-11-24"}
             ]}}
        """.trimIndent()
        val dir = File(context.filesDir, "tips_cache").apply { mkdirs() }
        File(dir, "القلبية.json").writeText(packJson, Charsets.UTF_8)

        // …and is read back OFFLINE through the production cache path.
        gateway.online = false
        val pack: TipsPack? = TipsRepository.cachedPack(context, "القلبية")
        assertNotNull(pack)
        assertEquals(1, pack!!.countFor("most_common"))
        assertEquals("طالب مجتهد", pack.tipsFor("most_common").single().author)
        assertEquals(0, pack.countFor("exam_traps"))
    }

    @Test
    fun rejectionFlowShowsReasonInMine() = runBlocking {
        gateway.online = true
        engine.add("الجلدية", "exam_traps", "فخ شائع: الخلط بين الصدفية والإكزيما", "طالب")
        engine.add("الجلدية", "key_points", "نقطة مكررة حرفيًا من نصيحة سابقة", "طالب")
        assertEquals(true, engine.flush())
        val ids = gateway.server.map { it.id }
        gateway.moderate(ids[0], "published")
        gateway.moderate(ids[1], "rejected", "مكررة — موجودة بصياغة أخرى")
        assertEquals(true, engine.refreshMine())

        val mine = engine.mine.value.associateBy { it.sectionId }
        assertEquals(TipLocalStatus.PUBLISHED, mine["exam_traps"]!!.status)
        assertEquals(TipLocalStatus.REJECTED, mine["key_points"]!!.status)
        assertEquals("مكررة — موجودة بصياغة أخرى", mine["key_points"]!!.rejectReason)
        assertEquals("مرفوضة", TipsOutboxLogic.statusLabel(mine["key_points"]!!.status))
    }

    @Test
    fun editPublishedReturnsToReviewAndDeleteFlows() = runBlocking {
        gateway.online = true
        val tip = engine.add("العينية", "numbers_limits", "ضغط العين الطبيعي حتى 21 ملم زئبق", null)
        assertEquals(true, engine.flush())
        val serverId = engine.mine.value.single().serverId!!
        gateway.moderate(serverId, "published")
        assertEquals(true, engine.refreshMine())
        assertEquals(TipLocalStatus.PUBLISHED, engine.mine.value.single().status)

        // Edit the published tip → unsent edit locally…
        now += 10_000
        engine.edit(tip.localId, "ضغط العين الطبيعي حتى 21 ملم زئبق — يُقاس صباحًا")
        assertEquals(TipLocalStatus.PENDING_SEND, engine.mine.value.single().status)
        // …flush pushes it through the EDIT endpoint → قيد المراجعة.
        assertEquals(true, engine.flush())
        assertEquals(listOf(serverId), gateway.editedIds)
        assertEquals(TipLocalStatus.PENDING_REVIEW, engine.mine.value.single().status)
        assertEquals("pending_review", gateway.server.single().status)

        // Delete → tombstone hidden at once, DELETE on flush, gone.
        engine.delete(tip.localId)
        assertTrue(engine.mine.value.isEmpty())
        assertEquals(true, engine.flush())
        assertEquals(listOf(serverId), gateway.deletedIds)
        assertTrue(gateway.server.isEmpty())
    }

    @Test
    fun missingPackIsNormalEmptySection() {
        assertNull(TipsRepository.cachedPack(context, "النسائية"))
        assertEquals(emptyMap<String, Int>(), TipsRepository.cachedCounts(context))
    }
}
