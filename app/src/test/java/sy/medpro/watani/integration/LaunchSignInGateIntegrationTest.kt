package sy.medpro.watani.integration

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import sy.medpro.watani.data.PrivacyConsent
import sy.medpro.watani.ui.auth.SignInGate
import sy.medpro.watani.ui.theme.WataniTheme

/**
 * Layer 3 — launch gate: the real SignInGate composable (the
 * screen MainActivity shows a signed-out student) driven end to
 * end against the real consent store. The Google button must stay
 * disabled until the privacy consent box is ticked; ticking
 * records the acceptance immediately (build 1.2.29 pattern); a
 * device that already accepted the current policy version starts
 * with the box ticked and the button enabled.
 */
@RunWith(RobolectricTestRunner::class)
class LaunchSignInGateIntegrationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        IntegrationTestSupport.ensureFirebaseApp(context)
        IntegrationTestSupport.clearLocalState(context)
    }

    private fun showGate() {
        composeRule.setContent {
            WataniTheme(darkTheme = false) {
                SignInGate(onSignedIn = {})
            }
        }
    }

    @Test
    fun freshInstallShowsGateWithGoogleButtonDisabledUntilConsent() {
        showGate()
        // The gate itself: greeting, name field, Google button.
        composeRule.onNodeWithText("أهلاً بك", substring = true).assertExists()
        composeRule.onNodeWithText("الاسم الكامل").assertExists()
        composeRule.onNodeWithText("تسجيل الدخول عبر Google").assertExists()
        // No stored consent -> the button is disabled (the 1.2.29
        // gate decision, exercised through the real composable).
        composeRule.onNodeWithText("تسجيل الدخول عبر Google").assertIsNotEnabled()
        assertFalse(PrivacyConsent.isConsentGiven(context))
    }

    @Test
    fun tickingConsentRecordsAcceptanceAndEnablesGoogleButton() {
        showGate()
        composeRule.onNode(isToggleable()).performScrollTo().performClick()
        // Ticking recorded the acceptance for the current policy
        // version in the real store, immediately.
        assertTrue(PrivacyConsent.isConsentGiven(context))
        assertTrue(
            PrivacyConsent.acceptedVersion(context) ==
                PrivacyConsent.CURRENT_POLICY_VERSION
        )
        composeRule.onNodeWithText("تسجيل الدخول عبر Google").assertIsEnabled()
    }

    @Test
    fun returningDeviceWithAcceptedPolicyStartsTickedAndEnabled() {
        // A previous run of the app already recorded acceptance of
        // the current policy version on this device.
        PrivacyConsent.recordConsent(context)
        showGate()
        composeRule.onNodeWithText("تسجيل الدخول عبر Google").assertIsEnabled()
    }
}
