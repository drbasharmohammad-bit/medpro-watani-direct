package sy.medpro.watani.integration

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import sy.medpro.watani.MainActivity

/**
 * Layer 3 — cold launch: the REAL MainActivity, launched the way
 * the system launches it, with no persisted session and no learned
 * minVersion. The documented cold-start sequence must play out end
 * to end: root verdict passes (Robolectric device signals stay
 * under the two-category block threshold), the brand intro plays
 * and advances on its own, the min-version floor does not block
 * (nothing learned yet), and the entry gate — the sign-in screen —
 * is what the student lands on. Firebase initialises from the
 * activity's own production wiring; there is no user, so no
 * network-shaped state is faked anywhere in this flow.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class MainActivityLaunchIntegrationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        IntegrationTestSupport.clearLocalState(context)
    }

    @Test
    fun coldLaunchLandsOnSignInGate() {
        // The brand intro advances on its own (virtual clock), then
        // the sign-in gate composes. Wait for the gate's greeting.
        composeRule.waitUntil(timeoutMillis = WAIT_MS) {
            composeRule.onAllNodesWithText("أهلاً بك", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("أهلاً بك", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("تسجيل الدخول عبر Google").assertExists()
    }

    private companion object {
        const val WAIT_MS = 15_000L
    }
}
