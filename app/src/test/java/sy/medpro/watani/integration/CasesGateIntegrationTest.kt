package sy.medpro.watani.integration

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sy.medpro.watani.data.ClinicalCaseDto
import sy.medpro.watani.ui.cases.CasesScreen
import sy.medpro.watani.ui.theme.WataniTheme

/**
 * Layer 3 — the cases activation gate («المريض الافتراضي والحالات
 * السريرية»): the REAL CasesScreen, signed out (no Firebase user,
 * so the production activation read is false). The unified gate
 * (build 1.2.41) makes the first ten cases of each family free:
 * the lock must be VISIBLE on the eleventh case, absent on the
 * free ones; tapping a free case opens it, tapping a locked one
 * raises the activation prompt instead. The fixtures are legacy
 * cases, i.e. one family — the per-family rank rule itself is
 * pinned by the layer-2 CaseAccessRulesTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w800dp-h2400dp")
class CasesGateIntegrationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        IntegrationTestSupport.ensureFirebaseApp(context)
        IntegrationTestSupport.clearLocalState(context)
    }

    private fun legacyCase(number: Int): ClinicalCaseDto = ClinicalCaseDto(
        id = "case-$number",
        title = "حالة اختبار $number",
        specialty = "القلبية",
        source = null,
        sourceNote = null,
        difficulty = null,
        presentation = "قصة حالة اختبار التكامل رقم $number",
        history = null,
        examinations = null,
        tests = null,
        diagnosisOptions = null,
        correctDiagnosis = null,
        explanation = null,
        learningPoints = null,
        xpReward = null,
        schemaV2 = null,
        openingVignette = null,
        historyBank = null,
        criticalFacts = null,
        legacyPresentation = null
    )

    private fun cases(count: Int): List<ClinicalCaseDto> =
        (1..count).map { legacyCase(it) }

    @Test
    fun lockShowsOnlyAfterTheTenthFreeCase() {
        composeRule.setContent {
            WataniTheme(darkTheme = false) {
                CasesScreen(cases = cases(ELEVEN), onCaseClick = {})
            }
        }
        // Eleven legacy cases, one family: exactly one lock — the
        // eleventh case's. The tall test viewport composes them all.
        composeRule.onAllNodesWithContentDescription(LOCK_DESCRIPTION)
            .assertCountEquals(1)
        composeRule.onNodeWithText("حالة اختبار 1").assertExists()
        composeRule.onNodeWithText("حالة اختبار 11").assertExists()
    }

    @Test
    fun freeCaseTapOpensTheCase() {
        var clicked: ClinicalCaseDto? = null
        composeRule.setContent {
            WataniTheme(darkTheme = false) {
                CasesScreen(cases = cases(TWELVE), onCaseClick = { clicked = it })
            }
        }
        composeRule.onNodeWithText("حالة اختبار 1").performClick()
        assertEquals("case-1", clicked?.id)
        composeRule.onNodeWithText(IntegrationTestSupport.ACTIVATION_DIALOG_TITLE)
            .assertDoesNotExist()
    }

    @Test
    fun lockedCaseTapShowsActivationPromptInstead() {
        var clicked: ClinicalCaseDto? = null
        composeRule.setContent {
            WataniTheme(darkTheme = false) {
                CasesScreen(cases = cases(TWELVE), onCaseClick = { clicked = it })
            }
        }
        composeRule.onNode(hasScrollAction()).performScrollToIndex(ELEVENTH_INDEX)
        composeRule.onNodeWithText("حالة اختبار 11").performClick()
        // The gate intercepts: activation prompt, no case opened.
        // (The prompt reads FirebaseAuth at composition — the dummy
        // app from setUp is what lets it compose signed-out.)
        composeRule.onNodeWithText(IntegrationTestSupport.ACTIVATION_DIALOG_TITLE)
            .assertExists()
        assertNull(clicked)
    }

    private companion object {
        const val LOCK_DESCRIPTION = "حالة للمفعّلين"
        const val ELEVEN = 11
        const val TWELVE = 12
        const val ELEVENTH_INDEX = 10
    }
}
