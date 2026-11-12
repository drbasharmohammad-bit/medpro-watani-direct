package sy.medpro.watani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sy.medpro.watani.security.NetworkPinning

/**
 * Phase-2 hardening (Nov 2026): pure-logic proofs that run on the JVM
 * with no device — the pin set shape, the min-version decision and
 * the gentle-lease decision. (The Android-Keystore paths themselves
 * are device-only; the crypto byte format they use is proven by the
 * standalone JVM harness recorded in the phase-2 report.)
 */
class HardeningLogicTest {

    @Test
    fun pinning_coversBothHosts_withAtLeastTwoPinsEach() {
        val workerPins = NetworkPinning.pinner.pins.count { it.hostname == NetworkPinning.WORKER_HOST }
        val contentPins = NetworkPinning.pinner.pins.count { it.hostname == NetworkPinning.CONTENT_HOST }
        assertTrue("worker host needs >= 2 pins", workerPins >= 2)
        assertTrue("content host needs >= 2 pins", contentPins >= 2)
        assertTrue(NetworkPinning.PINNING_ENABLED)
    }

    @Test
    fun versionGate_neverBlocksWhenNothingLearned() {
        assertFalse(AppVersionGate.isBlocked(74275, 0))
        assertFalse(AppVersionGate.isBlocked(1, 0))
    }

    @Test
    fun versionGate_blocksOnlyBelowKnownMin() {
        assertTrue(AppVersionGate.isBlocked(74274, 74275))
        assertFalse(AppVersionGate.isBlocked(74275, 74275))
        assertFalse(AppVersionGate.isBlocked(74276, 74275))
        // An unreadable own version (0) fails open, never blocks.
        assertFalse(AppVersionGate.isBlocked(0, 74275))
    }

    @Test
    fun lease_neverVerifiedIsNotExpired() {
        val now = 2_000_000_000_000L
        assertFalse(WorkerActivationApi.isLeaseExpired(0L, now))
    }

    @Test
    fun lease_expiresOnlyAfterThirtyDays() {
        val now = 2_000_000_000_000L
        assertFalse(WorkerActivationApi.isLeaseExpired(now - 29L * 24 * 3600 * 1000, now))
        assertTrue(WorkerActivationApi.isLeaseExpired(now - 31L * 24 * 3600 * 1000, now))
        assertEquals(30L * 24 * 3600 * 1000, WorkerActivationApi.LEASE_DURATION_MS)
    }
}
