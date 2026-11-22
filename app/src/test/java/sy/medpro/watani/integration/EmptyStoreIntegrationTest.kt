package sy.medpro.watani.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sy.medpro.watani.data.AppVersionGate
import sy.medpro.watani.data.WorkerActivationApi

/**
 * Layer 3 — the empty-store guarantees, isolated in their own
 * class on purpose: a Robolectric sandbox (and with it the
 * production encrypted activation store) is shared by every test
 * method of a class, and the store's production instance is
 * cached per process, so a "nothing was ever learned/stored"
 * assertion is only deterministic in a class where no sibling
 * test ever writes. Both tests here are read-only.
 *
 * The guarantees themselves are student-protecting: a device that
 * never reached the gateway has learned no minVersion and is
 * NEVER update-blocked, and an empty activation store reads as
 * not activated with no expired token.
 *
 * Isolation mechanism: Robolectric reuses one sandbox (and its
 * static-backed stores) for every class sharing an identical
 * @Config — the default-qualifiers classes of this suite share
 * one, which is how a sibling class's saved minVersion (999999)
 * was observed here in CI run 45017411012. This class therefore
 * carries its own qualifiers, i.e. its own sandbox, where the
 * store is empty by construction.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w600dp-h900dp")
class EmptyStoreIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        IntegrationTestSupport.ensureFirebaseApp(context)
        IntegrationTestSupport.clearLocalState(context)
    }

    @Test
    fun freshStateNeverBlocks() {
        // Nothing learned from any config fetch yet.
        assertEquals(0, WorkerActivationApi.knownMinVersion(context))
        assertFalse(AppVersionGate.isUpdateRequired(context))
        val current = AppVersionGate.currentVersionCode(context)
        assertFalse(
            AppVersionGate.isBlocked(
                current, WorkerActivationApi.knownMinVersion(context)
            )
        )
    }

    @Test
    fun emptyStoreReadsAsNotActivated() {
        assertFalse(WorkerActivationApi.isActivated(context))
        assertFalse(WorkerActivationApi.hasExpiredToken(context))
        assertEquals(0, WorkerActivationApi.knownMinVersion(context))
    }
}
