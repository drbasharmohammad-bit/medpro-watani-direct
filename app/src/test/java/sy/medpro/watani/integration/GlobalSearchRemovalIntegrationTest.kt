package sy.medpro.watani.integration

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sy.medpro.watani.ui.DrawerIcons
import sy.medpro.watani.ui.theme.WataniTheme
import sy.medpro.watani.ui.tools.ToolsScreen

/**
 * Layer 3 — «البحث الشامل» (global search over downloaded files) is
 * REMOVED (build 1.2.45, doctor's order 23 Nov 2026: «البحث الشامل مو
 * قلنا الغيه ليش لسى موجود»). The feature had four doors in:
 *  1. a fixed search bar on top of the tools hub,
 *  2. a search button in the home header,
 *  3. a «الأدوات» entry in the navigation drawer,
 *  4. the drawer route that opened GlobalSearchScreen.
 * The screen composable, its drawer-list entry, its icon mapping,
 * its route, the home button (and its onOpenSearch callback) and
 * the tools bar are all deleted. This suite pins the two surfaces
 * that can be composed offline — the tools hub and the drawer icon
 * map. The drawer list and the home header live inside
 * MainActivity's content behind a persisted Firebase session,
 * which cannot be fabricated offline (the same documented gap as
 * NavigationStateIntegrationTest); their removal is enforced by
 * the deleted route: no code path can still name the screen.
 *
 * Red-before proof: on the pre-removal code the tools bar text
 * «البحث الشامل — ابحث بكل محتوى التطبيق» is found by the first
 * test, and DrawerIcons.forTitle returned the search drawable in
 * the third.
 *
 * Scope kept on purpose: the drawer's own «ابحث في الأقسام…»
 * filter field (it filters drawer titles, not content), the PDF
 * reader's in-book text search, and «بنك أخطائي» are separate
 * features and are NOT touched by the removal.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h1600dp")
class GlobalSearchRemovalIntegrationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        IntegrationTestSupport.ensureFirebaseApp(context)
        IntegrationTestSupport.clearLocalState(context)
    }

    @Test
    fun toolsScreenRendersWithoutAnyGlobalSearchEntry() {
        composeRule.setContent {
            WataniTheme(darkTheme = false) {
                ToolsScreen(onToolClick = {})
            }
        }
        composeRule.waitForIdle()
        // The hub really rendered — its first group header is on
        // screen — so the absence below is the removal, not an
        // empty composition.
        composeRule.onNodeWithText("تدرّب ومهارات سريرية").assertIsDisplayed()
        composeRule.onAllNodesWithText("البحث الشامل", substring = true)
            .assertCountEquals(0)
    }

    @Test
    fun toolsTilesStillRouteToTheirOwnScreens() {
        val routed = mutableListOf<String>()
        composeRule.setContent {
            WataniTheme(darkTheme = false) {
                ToolsScreen(onToolClick = { routed.add(it) })
            }
        }
        composeRule.waitForIdle()
        composeRule.onNode(
            hasClickAction() and hasAnyDescendant(hasText("مدرّب التشخيص التفريقي"))
        ).performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("مدرّب التشخيص التفريقي"), routed)
    }

    @Test
    fun drawerIconMapNoLongerKnowsGlobalSearch() {
        assertNull(DrawerIcons.forTitle("البحث الشامل"))
    }
}
