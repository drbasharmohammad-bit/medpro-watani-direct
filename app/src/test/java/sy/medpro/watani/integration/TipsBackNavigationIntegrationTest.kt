package sy.medpro.watani.integration

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sy.medpro.watani.data.TipsContract
import sy.medpro.watani.ui.theme.WataniTheme
import sy.medpro.watani.ui.tips.TipsRootScreen
import java.util.concurrent.atomic.AtomicInteger

/**
 * Layer 3 — sequential back navigation inside «زوادة الطالب»
 * (build 1.2.54, doctor's report): entering the section, opening
 * a subject, then a section inside it, and pressing the SYSTEM
 * back button must walk the visit sequence one step at a time —
 * section → subject → subjects list — and only the press at the
 * section's root may hand back to the caller. Before the fix the
 * section registered no BackHandler at all, so the system back
 * fell through to the host's handler, which discarded the whole
 * section and landed the student back at the main tabs from any
 * depth; the on-screen arrow (a static parent map) was the only
 * sequential path. The deep-entry case is pinned too: entering
 * straight into a subject (home card / subject strip) makes that
 * subject the visit root, so back exits the section instead of
 * inventing a visit to the subjects list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h2400dp")
class TipsBackNavigationIntegrationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        IntegrationTestSupport.ensureFirebaseApp(context)
        IntegrationTestSupport.clearLocalState(context)
    }

    private fun pressSystemBack() {
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun systemBackWalksTheVisitSequenceOneStepAtATime() {
        val exits = AtomicInteger(0)
        val subject = TipsContract.SUBJECTS.first()
        val sectionTitle = TipsContract.SECTIONS.first().second

        composeRule.setContent {
            WataniTheme(darkTheme = false) {
                TipsRootScreen(onBack = { exits.incrementAndGet() })
            }
        }

        // Section root: the subjects list.
        composeRule.onNodeWithText(SECTION_TITLE).assertIsDisplayed()

        // Forward: subjects → subject → section.
        composeRule.onNodeWithText(subject).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(sectionTitle).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(sectionTitle).assertIsDisplayed()

        // Back once: the SECTION's parent visit — the subject
        // page — never the section exit, never Home.
        pressSystemBack()
        assertEquals(0, exits.get())
        assertFalse(composeRule.activity.isFinishing)
        composeRule.onNodeWithText(subject).assertIsDisplayed()

        // Back again: the subjects list (the section root).
        pressSystemBack()
        assertEquals(0, exits.get())
        composeRule.onNodeWithText(SECTION_TITLE).assertIsDisplayed()

        // Back at the root: exactly one hand-back to the caller.
        pressSystemBack()
        assertEquals(1, exits.get())
    }

    @Test
    fun deepEntryIntoSubjectBacksOutOfTheSectionDirectly() {
        val exits = AtomicInteger(0)
        val subject = TipsContract.SUBJECTS.first()

        composeRule.setContent {
            WataniTheme(darkTheme = false) {
                TipsRootScreen(
                    onBack = { exits.incrementAndGet() },
                    initialSubject = subject
                )
            }
        }

        composeRule.onNodeWithText(subject).assertIsDisplayed()

        // The deep-entry subject IS the visit root: one back
        // press hands back to the caller; the subjects list the
        // student never visited must not appear.
        pressSystemBack()
        assertEquals(1, exits.get())
        composeRule.onNodeWithText(SECTION_TITLE).assertDoesNotExist()
    }

    private companion object {
        const val SECTION_TITLE = "زوادة الطالب"
    }
}
