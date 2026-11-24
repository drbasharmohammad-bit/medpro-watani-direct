package sy.medpro.watani.integration

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sy.medpro.watani.ui.SlimBarTitle
import sy.medpro.watani.ui.SlimSubBar
import sy.medpro.watani.ui.drawer.DrawerScreenScaffold
import sy.medpro.watani.ui.theme.WataniTheme

/**
 * Layer 3 — bar titles are NEVER clipped (1.2.49, doctor's device
 * report 24 Nov 2026: on the cases screen the count line «(436
 * حالة)» was sliced at the bottom edge of the maroon panel, and
 * the doctor reported the same cut across most of the app).
 *
 * Root cause: the slim bars have a FIXED height (48dp sub-bar)
 * while titles were bare wrapping Texts — a long title wrapped to
 * a second line that overflowed the fixed-height row and was cut
 * by the panel edge. The fix is text-side only (panel height
 * unchanged): SlimBarTitle is always one line, auto-shrinks to the
 * real available width, and pins an Arabic-safe line height.
 *
 * These tests compose the REAL bars at a narrow phone width with
 * the LONGEST real titles in the app and prove, from layout
 * bounds, that (a) a long title occupies exactly one line — the
 * same height as a one-word title in the identical bar — and
 * (b) nothing extends past its bar's bottom edge, including the
 * two-line title+subtitle bar columns. This is the standing check
 * docs/RELEASE_CHECKLIST.md now requires of every new screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class BarTitleClippingIntegrationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        IntegrationTestSupport.ensureFirebaseApp(context)
        IntegrationTestSupport.clearLocalState(context)
    }

    @Test
    fun longestRealTitlesStayOneLineInsideTheFixedBar() {
        composeRule.setContent {
            WataniTheme(darkTheme = false) {
                Column {
                    SlimSubBar(onBack = {}) { SlimBarTitle(SHORT_TITLE) }
                    SlimSubBar(onBack = {}) { SlimBarTitle(LONG_CASES_TITLE) }
                    SlimSubBar(onBack = {}) {
                        Column {
                            SlimBarTitle(
                                LONG_COLUMN_TITLE,
                                maxFontSize = 15.sp,
                                minFontSize = 11.sp
                            )
                            Text(
                                LONG_SUBTITLE,
                                fontSize = 10.sp,
                                lineHeight = 13.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
        val shortBounds = composeRule.onNodeWithText(SHORT_TITLE)
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val longBounds = composeRule.onNodeWithText(LONG_CASES_TITLE)
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val columnTitleBounds = composeRule.onNodeWithText(LONG_COLUMN_TITLE)
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val subtitleBounds = composeRule.onNodeWithText(LONG_SUBTITLE)
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot

        // (a) One line: the longest title in the app takes the same
        // height as a one-word title in the same bar. A wrapped
        // second line would roughly double it.
        assertTrue(
            "long title must stay one line (h=${longBounds.height}) vs short (h=${shortBounds.height})",
            longBounds.height <= shortBounds.height + HEIGHT_TOLERANCE_PX
        )
        // (b) Nothing crosses a bar edge: the second bar's title
        // ends above the third bar's title, and the third bar's
        // subtitle ends at or above the composed root's bottom
        // (the third bar is the last element).
        assertTrue(
            "long title bottom ${longBounds.bottom} must be above next bar title top ${columnTitleBounds.top}",
            longBounds.bottom <= columnTitleBounds.top
        )
        assertTrue(
            "subtitle bottom ${subtitleBounds.bottom} must fit the bar (root bottom ${rootBounds.bottom})",
            subtitleBounds.bottom <= rootBounds.bottom + HEIGHT_TOLERANCE_PX
        )
        // (c) No horizontal overflow either.
        assertTrue(
            "long title right ${longBounds.right} must fit width ${rootBounds.right}",
            longBounds.right <= rootBounds.right + HEIGHT_TOLERANCE_PX
        )
    }

    @Test
    fun drawerScaffoldWithTheCasesTitleDoesNotClip() {
        composeRule.setContent {
            WataniTheme(darkTheme = false) {
                DrawerScreenScaffold(title = LONG_CASES_TITLE, onBack = {}) {}
            }
        }
        val titleBounds = composeRule.onNodeWithText(LONG_CASES_TITLE)
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val density = context.resources.displayMetrics.density
        // The sub-bar is exactly 48dp tall and starts at the top of
        // the scaffold: the whole title must live inside it.
        assertTrue(
            "cases title bottom ${titleBounds.bottom} must be inside the 48dp bar (${SUB_BAR_DP * density}px)",
            titleBounds.bottom <= SUB_BAR_DP * density + HEIGHT_TOLERANCE_PX
        )
        assertTrue(
            "cases title height ${titleBounds.height} must be a single line inside the 48dp bar",
            titleBounds.height <= SUB_BAR_DP * density + HEIGHT_TOLERANCE_PX
        )
    }

    private companion object {
        const val SHORT_TITLE = "حالة"
        /** The doctor's exact reported title (cases screen, with
         * its live count) — the longest bar title in the app. */
        const val LONG_CASES_TITLE = "المريض الافتراضي والحالات السريرية (436 حالة)"
        /** The longest two-line-column bar title (offline planner). */
        const val LONG_COLUMN_TITLE = "مخطط جدول الدراسة الذكي للامتحان الوطني"
        const val LONG_SUBTITLE = "يعمل أوفلاين 100% • توزيع معياري حسب ثقل المواد"
        const val SUB_BAR_DP = 48f
        const val HEIGHT_TOLERANCE_PX = 2f
    }
}
