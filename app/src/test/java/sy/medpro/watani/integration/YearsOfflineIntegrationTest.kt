package sy.medpro.watani.integration

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sy.medpro.watani.data.DownloadedSectionEntity
import sy.medpro.watani.data.ManifestFileEntry
import sy.medpro.watani.data.SubjectEnvelope
import sy.medpro.watani.data.WataniRepository
import sy.medpro.watani.data.isSectionPairUpToDate
import sy.medpro.watani.data.questionsForYear
import sy.medpro.watani.ui.home.TopicSelectionScreen
import sy.medpro.watani.ui.theme.WataniTheme

/**
 * Layer 3 — «حسب السنوات» for university subjects, fully offline:
 * the subject shard AND its independent years file are seeded
 * into the app's own cache (the network double) and read back
 * through the REAL repository (restricted-path lease gate
 * included — signed out, the lease check passes by design, the
 * same boundary FullDawraOfflineIntegrationTest relies on). The
 * REAL subject screen then switches to the years mode fed by the
 * years envelope, and a year row's tap carries the year whose
 * FLAT question list (no topics level inside years — doctor's
 * correction 23 Nov 2026) the year quiz receives from the years
 * file alone. The unified all-or-nothing download verdict is
 * pinned against the same fixture paths: one file of the pair is
 * never "downloaded".
 *
 * Scope note (documented gap, same class as the two gaps in
 * docs/RELEASE_CHECKLIST.md): the QUIZ opened from a year row
 * (MainViewModel.openYearQuiz) is not driven end-to-end here —
 * its entry gates are the activation/device gates, and a live
 * activation cannot be fabricated offline (FirebaseAuth has no
 * test seam and activations are server-issued). What is proven
 * instead is everything up to that gate: the offline read, the
 * year-row navigation payload, and the exact flat question
 * selection the quiz receives (questionsForYear over the
 * repository-parsed envelope).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h1280dp")
class YearsOfflineIntegrationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @Before
    fun setUp() {
        IntegrationTestSupport.ensureFirebaseApp(context)
        IntegrationTestSupport.clearLocalState(context)
        IntegrationTestSupport.seedSection(
            context,
            IntegrationTestSupport.UNI_SUBJECT_PATH,
            IntegrationTestSupport.universitySubjectJson()
        )
        IntegrationTestSupport.seedSection(
            context,
            IntegrationTestSupport.UNI_YEARS_PATH,
            IntegrationTestSupport.universityYearsJson()
        )
    }

    private fun subjectEnvelope(): SubjectEnvelope =
        moshi.adapter(SubjectEnvelope::class.java)
            .fromJson(IntegrationTestSupport.universitySubjectJson())!!

    @Test
    fun repositoryReadsSeededYearsFileOffline() {
        runBlocking {
            val envelope = WataniRepository(context)
                .getYearsEnvelope(IntegrationTestSupport.UNI_YEARS_PATH)
            assertNotNull(envelope)
            assertEquals("أذنية", envelope!!.subjectName)
            assertEquals(2, envelope.years?.size)
            // The year's questions are one flat list under it.
            assertEquals(
                listOf("Y26-1", "Y26-2", "Y26-3"),
                questionsForYear(envelope, 2026).map { it.id }
            )
            assertEquals(
                listOf("Y25-1", "Y25-2"),
                questionsForYear(envelope, 2025).map { it.id }
            )
        }
    }

    @Test
    fun subjectCountsAsDownloadedOnlyWithBothFilesOfThePair() {
        val files = mapOf(
            IntegrationTestSupport.UNI_SUBJECT_PATH to
                ManifestFileEntry(hash = "h1", size = 1L, count = 2, version = "1"),
            IntegrationTestSupport.UNI_YEARS_PATH to
                ManifestFileEntry(hash = "h2", size = 1L, count = 5, version = "1")
        )
        fun entity(path: String, hash: String, count: Int) = DownloadedSectionEntity(
            sectionPath = path, version = "1", hash = hash,
            count = count, downloadedAt = 0L
        )
        val subjectOnly = listOf(
            entity(IntegrationTestSupport.UNI_SUBJECT_PATH, "h1", 2)
        )
        val yearsOnly = listOf(
            entity(IntegrationTestSupport.UNI_YEARS_PATH, "h2", 5)
        )
        val both = subjectOnly + yearsOnly
        assertFalse(
            isSectionPairUpToDate(IntegrationTestSupport.UNI_SUBJECT_PATH, files, subjectOnly)
        )
        assertFalse(
            isSectionPairUpToDate(IntegrationTestSupport.UNI_SUBJECT_PATH, files, yearsOnly)
        )
        assertTrue(
            isSectionPairUpToDate(IntegrationTestSupport.UNI_SUBJECT_PATH, files, both)
        )
    }

    @Test
    fun yearsModeListsYearsAndYearQuizSelectionResolvesOffline() {
        var clickedYear: Int? = null
        var clickedYearName: String? = null
        composeRule.setContent {
            WataniTheme(darkTheme = false) {
                TopicSelectionScreen(
                    subjectName = "أذنية",
                    subjectEnvelope = subjectEnvelope(),
                    downloaded = true,
                    onDownloadClick = {},
                    onTopicClick = {},
                    onBack = {},
                    yearsEnvelope = runBlocking {
                        WataniRepository(context)
                            .getYearsEnvelope(IntegrationTestSupport.UNI_YEARS_PATH)
                    },
                    onYearClick = { year, name ->
                        clickedYear = year
                        clickedYearName = name
                    }
                )
            }
        }
        // The toggle exists because the years envelope has years;
        // switch to «حسب السنوات» and tap the 2026 row.
        composeRule.onNodeWithText("حسب السنوات").performClick()
        composeRule.onNodeWithText(IntegrationTestSupport.UNI_YEAR_ROW)
            .assertIsDisplayed()
        composeRule.onNodeWithText(IntegrationTestSupport.UNI_YEAR_ROW)
            .performClick()
        assertEquals(2026, clickedYear)
        assertEquals(IntegrationTestSupport.UNI_YEAR_ROW, clickedYearName)

        // The row's destination is the year's flat quiz directly
        // (no topics screen in between): the exact selection
        // openYearQuiz feeds ScreenState.Quiz for the tapped year
        // is the year's whole question list from the years file.
        val envelope = runBlocking {
            WataniRepository(context)
                .getYearsEnvelope(IntegrationTestSupport.UNI_YEARS_PATH)
        }
        assertEquals(
            listOf("Y26-1", "Y26-2", "Y26-3"),
            questionsForYear(envelope, clickedYear!!).map { it.id }
        )
    }
}
