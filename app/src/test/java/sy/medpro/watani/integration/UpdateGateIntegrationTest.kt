package sy.medpro.watani.integration

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import sy.medpro.watani.data.AppVersionGate
import sy.medpro.watani.data.WorkerActivationApi
import sy.medpro.watani.ui.security.UpdateRequiredScreen
import sy.medpro.watani.ui.theme.WataniTheme

/**
 * Layer 3 — the mandatory-update flow, end to end through the
 * production pieces a cold start actually runs: the gateway's
 * minVersion is learned into the (encrypted) activation store via
 * the public API, AppVersionGate reads it back and compares it
 * with the versionCode PackageManager reports for this very build,
 * and the block screen renders when the floor is above us. The
 * "no update" path is the fresh state: nothing learned, never
 * blocked — an offline student who never reached the gateway is
 * never locked out.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class UpdateGateIntegrationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        IntegrationTestSupport.ensureFirebaseApp(context)
        IntegrationTestSupport.clearLocalState(context)
    }

    @Test
    fun learnedMinVersionAboveCurrentBlocks() {
        val current = AppVersionGate.currentVersionCode(context).toInt()
        // The gateway config path persists the learned floor
        // through exactly this public entry point.
        WorkerActivationApi.saveKnownMinVersion(context, current + 1)
        assertEquals(current + 1, WorkerActivationApi.knownMinVersion(context))
        assertTrue(AppVersionGate.isUpdateRequired(context))
    }

    @Test
    fun learnedMinVersionAtOrBelowCurrentDoesNotBlock() {
        val current = AppVersionGate.currentVersionCode(context)
        WorkerActivationApi.saveKnownMinVersion(context, current.toInt())
        assertFalse(AppVersionGate.isUpdateRequired(context))
        assertFalse(AppVersionGate.isBlocked(current, current.toInt()))
        assertFalse(AppVersionGate.isBlocked(current, current.toInt() - 1))
        assertTrue(AppVersionGate.isBlocked(current, current.toInt() + 1))
    }

    @Test
    fun blockScreenRendersWithDownloadAction() {
        composeRule.setContent {
            WataniTheme(darkTheme = false) {
                UpdateRequiredScreen()
            }
        }
        composeRule.onNodeWithText("يلزم تحديث التطبيق").assertIsDisplayed()
        composeRule.onNodeWithText("تحميل التحديث وتثبيته").assertIsDisplayed()
    }
}
