package sy.medpro.watani.integration

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sy.medpro.watani.ui.firstaid.FirstAidSectionScreen
import sy.medpro.watani.ui.theme.WataniTheme

/**
 * Layer 3 — the First Aid gate («First Aid - Med Pro», build
 * 1.2.38 rules): the REAL section screen, fed by seeded section
 * fixtures (the network double) and read as a signed-out student,
 * so the production activation read is false. First three path
 * units of each step are free; everything beyond them, the printed
 * banks, and the integration sessions are for activated students:
 * locked units carry the «بالتفعيل» chip, and tapping any locked
 * entry raises the activation prompt instead of opening content,
 * while a free unit opens with no prompt. The rule table itself
 * is pinned by the layer-2 FirstAidAccessRulesTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w800dp-h1600dp")
class FirstAidGateIntegrationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        IntegrationTestSupport.ensureFirebaseApp(context)
        IntegrationTestSupport.clearLocalState(context)
        IntegrationTestSupport.seedFirstAidFixtures(context)
    }

    private fun showSectionAtHome() {
        composeRule.setContent {
            WataniTheme(darkTheme = false) {
                FirstAidSectionScreen()
            }
        }
        // The four section files load async off the cache; the home
        // hero text appears once they are in.
        composeRule.waitUntil(timeoutMillis = WAIT_MS) {
            composeRule.onAllNodesWithText("First Aid")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openStep1Path() {
        showSectionAtHome()
        composeRule.onNodeWithText("S1").performClick()
        composeRule.waitUntil(timeoutMillis = WAIT_MS) {
            composeRule.onAllNodesWithText(PATH_TITLE_STEP1)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun pathMarksUnitsBeyondTheFreeThreeWithActivationChip() {
        openStep1Path()
        // Five Step 1 units, three free: exactly two «بالتفعيل» chips.
        composeRule.onAllNodesWithText(ACTIVATION_CHIP).assertCountEquals(2)
        composeRule.onNodeWithText(IntegrationTestSupport.FA_STEP1_FREE_UNIT_TITLE)
            .assertExists()
        composeRule.onNodeWithText(IntegrationTestSupport.FA_STEP1_LOCKED_UNIT_TITLE)
            .assertExists()
    }

    @Test
    fun lockedUnitTapShowsActivationPrompt() {
        openStep1Path()
        composeRule.onNodeWithText(IntegrationTestSupport.FA_STEP1_LOCKED_UNIT_TITLE)
            .performClick()
        composeRule.onNodeWithText(IntegrationTestSupport.ACTIVATION_DIALOG_TITLE)
            .assertExists()
    }

    @Test
    fun freeUnitTapOpensUnitWithoutPrompt() {
        openStep1Path()
        composeRule.onNodeWithText(IntegrationTestSupport.FA_STEP1_FREE_UNIT_TITLE)
            .performClick()
        // The unit player replaced the path screen, and no
        // activation prompt was raised on the way in.
        composeRule.waitUntil(timeoutMillis = WAIT_MS) {
            composeRule.onAllNodesWithText(PATH_TITLE_STEP1)
                .fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText(IntegrationTestSupport.ACTIVATION_DIALOG_TITLE)
            .assertDoesNotExist()
    }

    @Test
    fun printedBanksTapShowsActivationPrompt() {
        showSectionAtHome()
        // Home list order with these fixtures: hero, start card,
        // paths header, Step 1 card, Step 2 card, integration
        // strip, printed-banks card.
        composeRule.onNode(hasScrollAction()).performScrollToIndex(BANKS_ITEM_INDEX)
        composeRule.onNodeWithText(BANKS_CARD_TITLE).performClick()
        composeRule.onNodeWithText(IntegrationTestSupport.ACTIVATION_DIALOG_TITLE)
            .assertExists()
    }

    @Test
    fun integrationSessionTapShowsActivationPrompt() {
        showSectionAtHome()
        composeRule.onNode(hasScrollAction()).performScrollToIndex(INTEGRATION_ITEM_INDEX)
        composeRule.onNodeWithText(INTEGRATION_STRIP_TITLE).performClick()
        composeRule.waitUntil(timeoutMillis = WAIT_MS) {
            composeRule.onAllNodesWithText(IntegrationTestSupport.FA_SESSION_TITLE)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(IntegrationTestSupport.FA_SESSION_TITLE).performClick()
        composeRule.onNodeWithText(IntegrationTestSupport.ACTIVATION_DIALOG_TITLE)
            .assertExists()
    }

    private companion object {
        const val WAIT_MS = 15_000L
        const val PATH_TITLE_STEP1 = "Step 1 — الأساسيات"
        const val ACTIVATION_CHIP = "بالتفعيل"
        const val BANKS_CARD_TITLE = "بنوك الكتاب المطبوعة — Step 2"
        const val INTEGRATION_STRIP_TITLE = "طبقة التكامل بين الكتابين"
        const val BANKS_ITEM_INDEX = 7
        const val INTEGRATION_ITEM_INDEX = 6
    }
}
