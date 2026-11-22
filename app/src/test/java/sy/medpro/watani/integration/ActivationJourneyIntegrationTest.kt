package sy.medpro.watani.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import sy.medpro.watani.data.WorkerActivationApi

/**
 * Layer 3 — the activation journey up to the boundary the
 * production code guarantees OFFLINE. WorkerActivationApi fails
 * closed (SEC-A1) before any network or storage write when the
 * code is blank or when there is no signed-in user to bind the
 * activation to: both journeys are exercised here against the
 * real API and the real (encrypted) store, and the store's
 * offline read/write round trip — the same store the update gate
 * and the activation state are read back from — is proven through
 * its public surface.
 *
 * The remaining leg (a gateway round trip: right code -> stored
 * activation, wrong code -> server rejection) cannot run in this
 * suite by construction: WORKER_BASE is a compile-time constant
 * and the OkHttp client is private with certificate pinning, so
 * no offline double can stand in for the gateway without a
 * production seam, and the task forbids both live gateway calls
 * in tests and production-code changes. It is recorded as an
 * explicit gap in docs/RELEASE_CHECKLIST.md; the fail-closed half
 * — the half that protects students — is fully covered here.
 */
@RunWith(RobolectricTestRunner::class)
class ActivationJourneyIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        IntegrationTestSupport.ensureFirebaseApp(context)
        IntegrationTestSupport.clearLocalState(context)
    }

    @Test
    fun blankCodeFailsClosedAndStoresNothing() = runBlocking {
        val result = WorkerActivationApi.activate(context, "   ")
        assertTrue(result.isFailure)
        assertFalse(WorkerActivationApi.isActivated(context))
        assertFalse(WorkerActivationApi.hasExpiredToken(context))
    }

    @Test
    fun signedOutActivationFailsClosedAndStoresNothing() = runBlocking {
        // A well-formed-looking code, but no signed-in user exists
        // for the activation to bind to (dummy Firebase app, no
        // session): production must refuse before any gateway call
        // or storage write.
        val result = WorkerActivationApi.activate(context, PLAUSIBLE_CODE)
        assertTrue(result.isFailure)
        assertFalse(WorkerActivationApi.isActivated(context))
        val stored = context.getSharedPreferences(
            WorkerActivationApi.PREFS_NAME, Context.MODE_PRIVATE
        )
        assertFalse(stored.getBoolean("is_activated", false))
    }

    @Test
    fun activationStoreRoundTripsOfflineThroughPublicApi() {
        // The min-version floor and the learned APK address live in
        // the same per-account store family as the activation
        // state; both directions must survive with no network.
        WorkerActivationApi.saveKnownMinVersion(context, FUTURE_MIN_VERSION)
        assertEquals(FUTURE_MIN_VERSION, WorkerActivationApi.knownMinVersion(context))
        WorkerActivationApi.saveKnownApkUrl(context, APK_URL)
        assertEquals(APK_URL, WorkerActivationApi.knownApkUrl(context))
        assertTrue(WorkerActivationApi.knownApkUrls(context).contains(APK_URL))
    }

    @Test
    fun emptyStoreReadsAsNotActivated() {
        assertFalse(WorkerActivationApi.isActivated(context))
        assertFalse(WorkerActivationApi.hasExpiredToken(context))
        assertEquals(0, WorkerActivationApi.knownMinVersion(context))
    }

    private companion object {
        const val PLAUSIBLE_CODE = "WATANI-INTEGRATION-TEST"
        const val FUTURE_MIN_VERSION = 999_999
        const val APK_URL = "https://watani-content.pages.dev/watani-latest.apk"
    }
}
