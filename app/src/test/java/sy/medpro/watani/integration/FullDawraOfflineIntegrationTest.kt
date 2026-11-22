package sy.medpro.watani.integration

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sy.medpro.watani.data.WataniRepository
import sy.medpro.watani.data.buildDawraQuizFromRecords
import sy.medpro.watani.ui.quiz.QuizScreen
import sy.medpro.watani.ui.theme.WataniTheme

/**
 * Layer 3 — «دورة كاملة» fully offline: a session file seeded into
 * the app's own cache (the network double) is read back through
 * the REAL repository (restricted-path lease gate included —
 * signed out, the lease check passes by design), assembled by the
 * REAL dawra builder into subject spans, and played in the REAL
 * QuizScreen: one correct and one wrong answer must move the live
 * header counters exactly (المُنجز / الدقة). No network is touched
 * anywhere in the flow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h1280dp")
class FullDawraOfflineIntegrationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        IntegrationTestSupport.ensureFirebaseApp(context)
        IntegrationTestSupport.clearLocalState(context)
        IntegrationTestSupport.seedSection(
            context,
            IntegrationTestSupport.DAWRA_PATH,
            IntegrationTestSupport.dawraSessionJson()
        )
    }

    @Test
    fun repositoryReadsSeededSessionFileOffline() {
        runBlocking {
            val records = WataniRepository(context)
                .getDawraSessionRecords(IntegrationTestSupport.DAWRA_PATH)
            assertNotNull(records)
            assertEquals(EXPECTED_QUESTIONS, records!!.size)
            assertEquals("TST-Q1", records[0].id)
            assertEquals("القلبية", records[0].subject)
            assertEquals("الهضمية والكبد", records[EXPECTED_QUESTIONS - 1].subject)
        }
    }

    @Test
    fun assemblyGroupsIntoSubjectSpansWithKeys() {
        runBlocking {
            val records = WataniRepository(context)
                .getDawraSessionRecords(IntegrationTestSupport.DAWRA_PATH)
            assertNotNull(records)
            val quiz = buildDawraQuizFromRecords(records!!)
            assertEquals(EXPECTED_QUESTIONS, quiz.questions.size)
            assertEquals(EXPECTED_SUBJECTS, quiz.sections.size)
            assertEquals("القلبية", quiz.sections[0].subjectName)
            assertEquals(EXPECTED_PER_SUBJECT, quiz.sections[0].count)
            assertEquals("الهضمية والكبد", quiz.sections[1].subjectName)
            assertEquals(EXPECTED_PER_SUBJECT, quiz.sections[1].startIndex)
            // The key of each question survives the record -> DTO map.
            assertEquals(
                listOf(0),
                quiz.questions[0].correctIndices?.map { (it as Number).toInt() }
            )
            assertEquals(
                listOf(2),
                quiz.questions[2].correctIndices?.map { (it as Number).toInt() }
            )
        }
    }

    @Test
    fun quizScreenScoresOneCorrectAndOneWrong() {
        runBlocking {
            val records = WataniRepository(context)
                .getDawraSessionRecords(IntegrationTestSupport.DAWRA_PATH)
            assertNotNull(records)
            val quiz = buildDawraQuizFromRecords(records!!)
            // The per-question timer loop never idles; freeze the
            // test clock so idle-waiting does not try to
            // fast-forward it.
            composeRule.mainClock.autoAdvance = false
            composeRule.setContent {
                WataniTheme(darkTheme = false) {
                    QuizScreen(
                        title = "دورة اختبار التكامل",
                        questions = quiz.questions,
                        onBack = {},
                        sourcePath = IntegrationTestSupport.DAWRA_PATH,
                        sections = quiz.sections
                    )
                }
            }
            // Subject span headers render over the flat list.
            composeRule.onNodeWithText("القلبية", substring = true).assertExists()
            composeRule.onNodeWithText("المُنجز: 0 / 4").assertIsDisplayed()
            // Q1: tap the correct option.
            composeRule.onNodeWithText(IntegrationTestSupport.DAWRA_Q1_CORRECT_OPTION)
                .performClick()
            composeRule.onNodeWithText("المُنجز: 1 / 4").assertIsDisplayed()
            composeRule.onNodeWithText("الدقة: 100% (1 صحيحة)").assertIsDisplayed()
            // Q3 (list item 3: header card, Q1, Q2, Q3): scroll it
            // into composition and tap a wrong option.
            composeRule.onNode(hasScrollAction()).performScrollToIndex(Q3_ITEM_INDEX)
            composeRule.onNodeWithText(IntegrationTestSupport.DAWRA_Q3_WRONG_OPTION)
                .performClick()
            composeRule.onNodeWithText("المُنجز: 2 / 4").assertExists()
            composeRule.onNodeWithText("الدقة: 50% (1 صحيحة)").assertExists()
        }
    }

    private companion object {
        const val EXPECTED_QUESTIONS = 4
        const val EXPECTED_SUBJECTS = 2
        const val EXPECTED_PER_SUBJECT = 2
        const val Q3_ITEM_INDEX = 3
    }
}
