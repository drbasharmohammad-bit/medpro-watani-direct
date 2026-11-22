package sy.medpro.watani.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.auth.FirebaseUser
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
 * production code guarantees OFFLINE. WorkerActivationApi.activate
 * takes the signed-in FirebaseUser explicitly and fails closed
 * (SEC-A1) before any network call or storage write when the code
 * is blank or when the user's ID token cannot be minted; both
 * legs are exercised here against the real API and the real
 * (encrypted) store, and the store's offline read/write round
 * trip — the same store the activation state and the update gate
 * are read back from — is proven through its public surface.
 *
 * The user double is the Firebase SDK's own concrete FirebaseUser
 * implementation (com.google.firebase.auth.internal.zzad in
 * firebase-auth 24.2.0), allocated WITHOUT a constructor via
 * Unsafe: it carries no account state, so its token can never be
 * minted — exactly the SEC-A1 precondition. (A hand-written
 * subclass is impossible from test source: FirebaseUser's public
 * abstract surface includes methods whose types live in the
 * hyphenated internal GMS package
 * com.google.android.gms.internal.firebase-auth-api, which
 * Kotlin/Java source cannot name — verified against the AAR.)
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
@org.robolectric.annotation.Config(sdk = [34])
class ActivationJourneyIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        IntegrationTestSupport.ensureFirebaseApp(context)
        IntegrationTestSupport.clearLocalState(context)
    }

    /** A FirebaseUser with no account state behind it (see the
     * class doc): every token request fails, which is precisely
     * the state the SEC-A1 fail-closed branch exists for. */
    private fun statelessUser(): FirebaseUser {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafe = unsafeClass.getDeclaredField("theUnsafe")
            .apply { isAccessible = true }.get(null)
        val allocate = unsafeClass.getMethod("allocateInstance", Class::class.java)
        val impl = Class.forName("com.google.firebase.auth.internal.zzad")
        return allocate.invoke(theUnsafe, impl) as FirebaseUser
    }

    @Test
    fun blankCodeFailsClosedAndStoresNothing() = runBlocking {
        val (ok, message) = WorkerActivationApi.activate(context, statelessUser(), "   ")
        assertFalse(ok)
        assertEquals("يرجى إدخال كود تفعيل صالح.", message)
        assertFalse(WorkerActivationApi.isActivated(context))
        assertFalse(WorkerActivationApi.hasExpiredToken(context))
    }

    @Test
    fun unmintableTokenFailsClosedBeforeAnyGatewayCall() = runBlocking {
        // A well-formed-looking code and a user object whose ID
        // token cannot be minted: SEC-A1 must refuse BEFORE the
        // gateway is contacted and before anything is stored.
        val (ok, message) = WorkerActivationApi.activate(
            context, statelessUser(), PLAUSIBLE_CODE
        )
        assertFalse(ok)
        assertEquals("تعذر التحقق من الحساب — تحقق من الاتصال ثم أعد المحاولة.", message)
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

    private companion object {
        const val PLAUSIBLE_CODE = "WATANI-INTEGRATION-TEST"
        const val FUTURE_MIN_VERSION = 999_999
        const val APK_URL = "https://watani-content.pages.dev/watani-latest.apk"
    }
}
