package sy.medpro.watani.integration

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sy.medpro.watani.ui.focus.FocusScreen
import sy.medpro.watani.ui.theme.WataniTheme

/**
 * Layer 3 — the focus timer's orientation contract (build
 * 1.2.46, device report 23 Nov 21:31): in landscape the old
 * single scrolling column with its fixed 248dp ring could never
 * show the whole ring inside the short landscape pane — the ring
 * and its digits were sliced and the controls sank toward the
 * floating bottom bar. The rebuilt screen branches on the
 * available shape: landscape gets a two-pane row whose ring is
 * sized from the pane height (always fully visible, digits
 * included) and whose start action leads the scrollable control
 * pane. These tests pin exactly that: at a landscape size the
 * phase title, the FULL timer digits and the start button are
 * all displayed without any scrolling, and the stats card exists
 * in the pane (scroll-reachable, never clipped away). The
 * portrait shape keeps the classic single column.
 */
@RunWith(RobolectricTestRunner::class)
class FocusLandscapeIntegrationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        IntegrationTestSupport.ensureFirebaseApp(context)
        IntegrationTestSupport.clearLocalState(context)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w960dp-h400dp")
    fun landscapeShowsWholeRingDigitsAndStartWithoutScrolling() {
        composeRule.setContent {
            WataniTheme(darkTheme = false) { FocusScreen() }
        }
        composeRule.onNodeWithText("جلسة تركيز").assertIsDisplayed()
        // The complete timer digits — the element the device
        // report showed sliced under the bottom bar.
        composeRule.onNodeWithText("25:00").assertIsDisplayed()
        // The primary action leads the landscape control pane:
        // visible at rest, no scrolling, no bar coverage.
        composeRule.onNodeWithText("بدء جلسة التركيز").assertIsDisplayed()
        // The stats card is part of the pane (scroll-reachable).
        composeRule.onNodeWithText("دقيقة اليوم").assertExists()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w400dp-h800dp")
    fun portraitKeepsTheClassicColumn() {
        composeRule.setContent {
            WataniTheme(darkTheme = false) { FocusScreen() }
        }
        composeRule.onNodeWithText("جلسة تركيز").assertIsDisplayed()
        composeRule.onNodeWithText("25:00").assertIsDisplayed()
        composeRule.onNodeWithText("بدء جلسة التركيز").assertExists()
        composeRule.onNodeWithText("دقيقة اليوم").assertExists()
    }
}
