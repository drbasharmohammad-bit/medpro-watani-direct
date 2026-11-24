package sy.medpro.watani.data

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import sy.medpro.watani.ui.ImageDecode

/**
 * Build 1.2.48 (performance list): the pure logic of the round —
 * the bounded-parallel fetch helper (item 9: independent requests
 * in parallel, capped), the daily-challenge pool signature (item 1:
 * the pool rebuilds only when a candidate file actually changes),
 * and the decode sample-size arithmetic (item 6: bitmaps decoded at
 * display size, never upsampled).
 */
class Performance1248LogicTest {

    // ---------- ParallelFetch (item 9) ----------

    @Test
    fun parallelFetchPreservesInputOrder() = runBlocking {
        val results = ParallelFetch.mapBounded((1..20).toList(), 4) { n ->
            delay((21 - n).toLong())
            n * 10
        }
        assertEquals((1..20).map { it * 10 }, results)
    }

    @Test
    fun parallelFetchNeverExceedsTheConcurrencyCap() = runBlocking {
        val inFlight = AtomicInteger(0)
        val maxSeen = AtomicInteger(0)
        ParallelFetch.mapBounded((1..24).toList(), 3) { n ->
            val now = inFlight.incrementAndGet()
            maxSeen.accumulateAndGet(now) { a, b -> maxOf(a, b) }
            delay(15)
            inFlight.decrementAndGet()
            n
        }
        assertTrue("cap 3 must hold, saw ${maxSeen.get()}", maxSeen.get() <= 3)
        assertTrue("parallelism must actually happen, saw ${maxSeen.get()}", maxSeen.get() >= 2)
    }

    @Test
    fun parallelFetchHandlesEmptyAndFailure() = runBlocking {
        assertEquals(emptyList<Int>(), ParallelFetch.mapBounded(emptyList<Int>(), 3) { it })
        try {
            ParallelFetch.mapBounded(listOf(1, 2, 3), 2) { n ->
                if (n == 2) error("boom")
                n
            }
            fail("a failing block must fail the whole map")
        } catch (expected: IllegalStateException) {
            assertEquals("boom", expected.message)
        }
    }

    // ---------- Daily challenge pool signature (item 1) ----------

    @Test
    fun poolSignatureIsStableForTheSameFiles() {
        val files = listOf(
            Triple("questions_قلبية.json", 100L, 5L),
            Triple("questions_توليد.json", 200L, 7L)
        )
        assertEquals(
            DailyChallengeSource.poolSignature(files),
            DailyChallengeSource.poolSignature(files)
        )
    }

    @Test
    fun poolSignatureChangesWithAnyFileChange() {
        val base = listOf(Triple("a.json", 100L, 5L))
        assertNotEquals(
            DailyChallengeSource.poolSignature(base),
            DailyChallengeSource.poolSignature(listOf(Triple("a.json", 101L, 5L)))
        )
        assertNotEquals(
            DailyChallengeSource.poolSignature(base),
            DailyChallengeSource.poolSignature(listOf(Triple("a.json", 100L, 6L)))
        )
        assertNotEquals(
            DailyChallengeSource.poolSignature(base),
            DailyChallengeSource.poolSignature(listOf(Triple("b.json", 100L, 5L)))
        )
        assertNotEquals(
            DailyChallengeSource.poolSignature(base),
            DailyChallengeSource.poolSignature(base + Triple("b.json", 1L, 1L))
        )
        assertEquals("", DailyChallengeSource.poolSignature(emptyList()))
    }

    // ---------- ImageDecode.sampleSize (item 6) ----------

    @Test
    fun sampleSizeNeverUpsamplesAndCoversTheTarget() {
        // Source at/under target decodes at full size.
        assertEquals(1, ImageDecode.sampleSize(800, 600, 1200))
        assertEquals(1, ImageDecode.sampleSize(1200, 900, 1200))
        // 4000 wide toward 1200: /2 = 2000 covers, /4 = 1000 does not.
        assertEquals(2, ImageDecode.sampleSize(4000, 3000, 1200))
        // Height-bound: waveform 2000x1000 toward 1080x256 — width
        // allows /1 only (2000/2 = 1000 < 1080), so 1.
        assertEquals(1, ImageDecode.sampleSize(2000, 1000, 1080, 256))
        // Both bounds loose: 4000x2000 toward 1000x250: /4 = 1000x500
        // covers both; /8 = 500 wide fails.
        assertEquals(4, ImageDecode.sampleSize(4000, 2000, 1000, 250))
        // Degenerate inputs stay at 1.
        assertEquals(1, ImageDecode.sampleSize(0, 0, 1200))
        assertEquals(1, ImageDecode.sampleSize(4000, 3000, 0, 0))
    }
}
