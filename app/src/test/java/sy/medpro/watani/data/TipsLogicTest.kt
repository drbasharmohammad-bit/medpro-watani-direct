package sy.medpro.watani.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * «زوادة الطالب» (1.2.51) — layer-2 pins for the pure core:
 * pack parsing (empty sections / null author / contract order),
 * the outbox state machine, the validation mirror (10–500, no
 * links), the six section titles, and the tipsWriting gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TipsLogicTest {

    // ---------------- contract ----------------

    @Test
    fun contractHas27SubjectsAndSixSections() {
        assertEquals(27, TipsContract.SUBJECTS.size)
        assertEquals(27, TipsContract.SUBJECTS.distinct().size)
        assertEquals(
            listOf(
                "most_common", "key_points", "until_proven_otherwise",
                "mnemonics", "exam_traps", "numbers_limits"
            ),
            TipsContract.SECTION_IDS
        )
        assertEquals("الأشيع في الامتحان الوطني", TipsContract.sectionTitle("most_common"))
        assertEquals("نقاط مهمة", TipsContract.sectionTitle("key_points"))
        assertEquals("حتى يثبت العكس", TipsContract.sectionTitle("until_proven_otherwise"))
        assertEquals("ربطات الذاكرة", TipsContract.sectionTitle("mnemonics"))
        assertEquals("فخاخ الامتحان", TipsContract.sectionTitle("exam_traps"))
        assertEquals("أرقام وحدود ما بتنتسى", TipsContract.sectionTitle("numbers_limits"))
        assertNull(TipsContract.sectionTitle("unknown_section"))
        assertEquals("tips/القلبية.json", TipsContract.packPath("القلبية"))
    }

    // ---------------- pack parsing ----------------

    @Test
    fun packParsesSectionsInContractOrderWithCounts() {
        val pack = TipsPack.parse(
            JSONObject(
                """
                {"subject":"القلبية","version":3,"updated_at":"2026-11-24",
                 "sections":{
                   "numbers_limits":[{"id":"n1","text":"حدّ مهم","author":"طالب أ","created_at":"2026-11-20"}],
                   "most_common":[
                     {"id":"m1","text":"الأشيع أولًا","author":"طالب ب","created_at":"2026-11-21"},
                     {"id":"m2","text":"الأشيع ثانيًا","author":null,"created_at":"2026-11-22"}
                   ]
                 }}
                """.trimIndent()
            )
        )
        assertNotNull(pack)
        pack!!
        assertEquals("القلبية", pack.subject)
        assertEquals(3, pack.version)
        assertEquals(TipsContract.SECTION_IDS, pack.sections.keys.toList())
        assertEquals(2, pack.countFor("most_common"))
        assertEquals(1, pack.countFor("numbers_limits"))
        assertEquals(3, pack.totalCount)
        // File order preserved inside a section.
        assertEquals(listOf("m1", "m2"), pack.tipsFor("most_common").map { it.id })
    }

    @Test
    fun packMissingSectionsAreEmptyNotErrors() {
        val pack = TipsPack.parse(JSONObject("""{"subject":"الجلدية","sections":{}}"""))
        assertNotNull(pack)
        assertEquals(0, pack!!.totalCount)
        for (id in TipsContract.SECTION_IDS) {
            assertTrue(pack.tipsFor(id).isEmpty())
        }
        val noSectionsKey = TipsPack.parse(JSONObject("""{"subject":"الجلدية"}"""))
        assertNotNull(noSectionsKey)
        assertEquals(0, noSectionsKey!!.totalCount)
    }

    @Test
    fun packNullAuthorStaysNullAndDisplaysAsAnonymous() {
        val pack = TipsPack.parse(
            JSONObject(
                """{"subject":"العينية","sections":{"key_points":[
                    {"id":"k1","text":"نقطة بلا اسم","author":null,"created_at":"2026-11-01"},
                    {"id":"k2","text":"نقطة باسم","author":"  سارة  ","created_at":"2026-11-02"}
                ]}}""".trimIndent()
            )
        )!!
        val tips = pack.tipsFor("key_points")
        assertNull(tips[0].author)
        assertEquals("بدون اسم", tipAuthorDisplay(tips[0].author))
        assertEquals("سارة", tips[1].author)
        assertEquals("سارة", tipAuthorDisplay(tips[1].author))
        assertEquals("بدون اسم", tipAuthorDisplay(null))
        assertEquals("بدون اسم", tipAuthorDisplay("   "))
    }

    @Test
    fun packMalformedOrSubjectlessReturnsNull() {
        assertNull(TipsPack.parse("{not json"))
        assertNull(TipsPack.parse(JSONObject("""{"sections":{}}""")))
        assertNull(TipsPack.parse(JSONObject("""{"subject":"  "}""")))
    }

    @Test
    fun packUnknownSectionIdsAreIgnored() {
        val pack = TipsPack.parse(
            JSONObject(
                """{"subject":"الصدرية","sections":{
                    "future_section":[{"id":"x","text":"من المستقبل","author":null,"created_at":""}],
                    "exam_traps":[{"id":"t1","text":"فخ معروف","author":null,"created_at":""}]
                }}""".trimIndent()
            )
        )!!
        assertEquals(1, pack.totalCount)
        assertEquals(1, pack.countFor("exam_traps"))
    }

    // ---------------- validation mirror ----------------

    @Test
    fun validationBoundsAre10To500Trimmed() {
        assertNotNull(TipsValidation.errorFor("قصير"))
        assertNotNull(TipsValidation.errorFor("123456789")) // 9 chars
        assertNull(TipsValidation.errorFor("1234567890")) // exactly 10
        assertNull(TipsValidation.errorFor("x".repeat(500)))
        assertNotNull(TipsValidation.errorFor("x".repeat(501)))
        // Surrounding whitespace does not count.
        assertNull(TipsValidation.errorFor("   " + "x".repeat(10) + "   "))
        assertTrue(TipsValidation.isValid("نصيحة مفيدة لزملائي الطلاب"))
    }

    @Test
    fun validationRejectsLinks() {
        assertTrue(TipsValidation.containsLink("راجع https://example.com للمزيد من الشرح"))
        assertTrue(TipsValidation.containsLink("راجع http://example.com"))
        assertTrue(TipsValidation.containsLink("راجع www.example.com"))
        assertNotNull(TipsValidation.errorFor("نصيحة طويلة بما يكفي مع رابط https://x.co"))
        assertFalse(TipsValidation.containsLink("نصيحة طبية بلا أي روابط إطلاقًا هنا"))
    }

    // ---------------- outbox state machine ----------------

    private fun tip(
        localId: String = "loc-1",
        serverId: String? = null,
        status: TipLocalStatus = TipLocalStatus.PENDING_SEND,
        text: String = "نصيحة للاختبار",
        createdAt: Long = 1000L
    ) = OutboxTip(
        localId = localId,
        serverId = serverId,
        subject = "القلبية",
        sectionId = "key_points",
        text = text,
        authorName = "طالب",
        status = status,
        createdAt = createdAt,
        updatedAt = createdAt
    )

    @Test
    fun outboxRoundTripsThroughJson() {
        val entries = listOf(
            tip(),
            tip(localId = "loc-2", serverId = "srv-2", status = TipLocalStatus.REJECTED, createdAt = 2000L)
                .copy(rejectReason = "غير دقيق طبيًا")
        )
        val restored = TipsOutboxLogic.deserialize(TipsOutboxLogic.serialize(entries))
        assertEquals(entries, restored)
        assertEquals(emptyList<OutboxTip>(), TipsOutboxLogic.deserialize(null))
        assertEquals(emptyList<OutboxTip>(), TipsOutboxLogic.deserialize("{broken"))
    }

    @Test
    fun outboxMarkSentMovesToPendingReview() {
        val next = TipsOutboxLogic.markSent(listOf(tip()), "loc-1", "srv-1", now = 5000L)
        assertEquals(TipLocalStatus.PENDING_REVIEW, next[0].status)
        assertEquals("srv-1", next[0].serverId)
    }

    @Test
    fun outboxEditReturnsPublishedToPendingSend() {
        val published = tip(serverId = "srv-1", status = TipLocalStatus.PUBLISHED)
        val next = TipsOutboxLogic.edit(listOf(published), "loc-1", "نص معدّل أطول", now = 9000L)
        assertEquals(TipLocalStatus.PENDING_SEND, next[0].status)
        assertEquals("نص معدّل أطول", next[0].text)
        assertEquals("srv-1", next[0].serverId) // edit endpoint target survives
    }

    @Test
    fun outboxDeleteUnsentDropsAndSentTombstones() {
        val dropped = TipsOutboxLogic.delete(listOf(tip()), "loc-1", now = 3000L)
        assertTrue(dropped.isEmpty())

        val sent = tip(serverId = "srv-1", status = TipLocalStatus.PUBLISHED)
        val tombstoned = TipsOutboxLogic.delete(listOf(sent), "loc-1", now = 3000L)
        assertEquals(1, tombstoned.size)
        assertTrue(tombstoned[0].deleted)
        assertEquals(TipLocalStatus.PENDING_SEND, tombstoned[0].status)
        // Tombstones are hidden from «إضافاتي» and still owed a flush.
        assertTrue(TipsOutboxLogic.visibleMine(tombstoned).isEmpty())
        assertEquals(1, TipsOutboxLogic.pendingFlush(tombstoned).size)
        // DELETE confirmation removes the tombstone for good.
        assertTrue(TipsOutboxLogic.markDeleteConfirmed(tombstoned, "loc-1").isEmpty())
    }

    @Test
    fun outboxMergeAppliesServerModerationStates() {
        val local = listOf(
            tip(localId = "loc-1", serverId = "srv-1", status = TipLocalStatus.PENDING_REVIEW),
            tip(localId = "loc-2", serverId = "srv-2", status = TipLocalStatus.PENDING_REVIEW, createdAt = 2000L)
        )
        val server = listOf(
            ServerTip("srv-1", "loc-1", "القلبية", "key_points", "نصيحة للاختبار", "طالب", "published", null, 1000L),
            ServerTip("srv-2", "loc-2", "القلبية", "key_points", "نصيحة للاختبار", "طالب", "rejected", "مكررة", 2000L)
        )
        val merged = TipsOutboxLogic.applyServerMine(local, server, now = 9999L)
        val byId = merged.associateBy { it.localId }
        assertEquals(TipLocalStatus.PUBLISHED, byId["loc-1"]!!.status)
        assertEquals(TipLocalStatus.REJECTED, byId["loc-2"]!!.status)
        assertEquals("مكررة", byId["loc-2"]!!.rejectReason)
    }

    @Test
    fun outboxMergeKeepsUnsentLocalAndAdoptsUnknownServerEntries() {
        val unsent = tip(localId = "loc-new", status = TipLocalStatus.PENDING_SEND)
        val server = listOf(
            ServerTip("srv-other", null, "الجلدية", "mnemonics", "ربطة من جهاز آخر", null, "published", null, 5000L)
        )
        val merged = TipsOutboxLogic.applyServerMine(listOf(unsent), server, now = 9999L)
        assertEquals(2, merged.size)
        val kept = merged.first { it.localId == "loc-new" }
        assertEquals(TipLocalStatus.PENDING_SEND, kept.status)
        val adopted = merged.first { it.serverId == "srv-other" }
        assertEquals(TipLocalStatus.PUBLISHED, adopted.status)
        assertNull(adopted.authorName)
    }

    @Test
    fun outboxMergeDropsServerDeletedSyncedEntries() {
        val synced = tip(serverId = "srv-gone", status = TipLocalStatus.PUBLISHED)
        val merged = TipsOutboxLogic.applyServerMine(listOf(synced), emptyList(), now = 9999L)
        assertTrue(merged.isEmpty())
    }

    @Test
    fun statusLabelsAreTheArabicContract() {
        assertEquals("بانتظار الإرسال", TipsOutboxLogic.statusLabel(TipLocalStatus.PENDING_SEND))
        assertEquals("قيد المراجعة", TipsOutboxLogic.statusLabel(TipLocalStatus.PENDING_REVIEW))
        assertEquals("منشورة", TipsOutboxLogic.statusLabel(TipLocalStatus.PUBLISHED))
        assertEquals("مرفوضة", TipsOutboxLogic.statusLabel(TipLocalStatus.REJECTED))
    }

    // ---------------- writing gate + pack freshness ----------------

    @Test
    fun writingGateOnlyActivatedModeRestricts() {
        assertTrue(tipsWritingAllowed(null, isActivated = false))
        assertTrue(tipsWritingAllowed("all", isActivated = false))
        assertTrue(tipsWritingAllowed("activated", isActivated = true))
        assertFalse(tipsWritingAllowed("activated", isActivated = false))
        assertTrue(tipsWritingAllowed("something-else", isActivated = false))
    }

    @Test
    fun packFreshnessVerdict() {
        val entry = ManifestFileEntry(hash = "abcdef0123456789", size = 10L, count = null, version = null)
        assertTrue(TipsRepository.packNeedsDownload(hasFile = false, storedHash = null, entry = entry))
        assertTrue(TipsRepository.packNeedsDownload(hasFile = true, storedHash = "0000", entry = entry))
        assertFalse(TipsRepository.packNeedsDownload(hasFile = true, storedHash = "ABCDEF0123456789", entry = entry))
        // No entry (offline): an existing cache is never force-refreshed.
        assertFalse(TipsRepository.packNeedsDownload(hasFile = true, storedHash = "0000", entry = null))
        assertTrue(TipsRepository.packNeedsDownload(hasFile = false, storedHash = null, entry = null))
    }

    @Test
    fun sha256Prefix16MatchesKnownDigest() {
        // sha256("") = e3b0c44298fc1c149afbf4c8996fb924...
        assertEquals("e3b0c44298fc1c14", TipsRepository.sha256Prefix16(ByteArray(0)))
        assertEquals(16, TipsRepository.sha256Prefix16("زوادة".toByteArray(Charsets.UTF_8)).length)
    }
}
