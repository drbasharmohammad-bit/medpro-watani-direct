package sy.medpro.watani.integration

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sy.medpro.watani.data.FullDawraLock
import sy.medpro.watani.data.ManifestFileEntry
import sy.medpro.watani.ui.home.FullDawraEntryCard
import sy.medpro.watani.ui.theme.WataniTheme
import sy.medpro.watani.ui.viewmodel.MainViewModel
import sy.medpro.watani.ui.viewmodel.ScreenState

/**
 * Layer 3 — the 1.2.49 «دورة كاملة» HARD LOCK (doctor's device
 * report 24 Nov 2026: on 1.2.48 the feature still opened for him).
 *
 * Root cause proven by the code audit recorded in
 * RELEASE_1_2_49.md: the list/detail navigation entry points had
 * no gate at all, and the only gate anywhere sat on the start
 * button, keyed on activation — so an activated account (the
 * doctor's own) walked straight in and the session was built from
 * the already-downloaded cache.
 *
 * This suite reproduces the doctor's exact phone state — the
 * session file IN the cache AND a downloaded-sections record for
 * it — and proves every route in is refused: list, detail, start,
 * and download, each surfacing only the lock message, with no
 * navigation and no file written. The lock predicate itself is
 * pinned as unconditional (it takes no state, so no account type
 * can satisfy it), and the entry card is composed to prove the
 * «مقفلة حاليًا» badge renders on its face.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class FullDawraLockIntegrationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        IntegrationTestSupport.ensureFirebaseApp(context)
        IntegrationTestSupport.clearLocalState(context)
        // The doctor's phone state: the session file is already in
        // the section cache, and the downloaded-sections registry
        // holds a record for it («version|hash|count|downloadedAt»,
        // the production WataniRepository format).
        IntegrationTestSupport.seedSection(
            context,
            IntegrationTestSupport.DAWRA_PATH,
            IntegrationTestSupport.dawraSessionJson()
        )
        context.getSharedPreferences(DOWNLOADED_PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putString(IntegrationTestSupport.DAWRA_PATH, "test|hash|4|1000")
            .commit()
    }

    private fun viewModel(): MainViewModel =
        MainViewModel(context.applicationContext as Application)

    @Test
    fun lockPredicateIsUnconditionalAndBadgeTextIsExact() {
        assertTrue(FullDawraLock.isLocked)
        assertEquals("مقفلة حاليًا", FullDawraLock.LOCK_BADGE)
        assertEquals("«دورة كاملة» مقفلة حاليًا.", FullDawraLock.LOCK_MESSAGE)
    }

    @Test
    fun everyEntryPointRefusesDespiteSeededCacheAndDownloadRecord() {
        val vm = viewModel()
        // Sanity: the seeded state really is on disk, so a refusal
        // below cannot be explained by missing content.
        val cacheFile = File(
            File(context.filesDir, "sections_cache"),
            IntegrationTestSupport.DAWRA_PATH.replace('/', '_') + ".json"
        )
        assertTrue(cacheFile.exists())

        vm.openFullDawraList()
        var state = vm.uiState.value
        assertEquals(ScreenState.Home, state.screenState)
        assertEquals(FullDawraLock.LOCK_MESSAGE, state.gateMessage)

        vm.openFullDawraDetail(DAWRA_NAME)
        state = vm.uiState.value
        assertEquals(ScreenState.Home, state.screenState)
        assertEquals(FullDawraLock.LOCK_MESSAGE, state.gateMessage)

        vm.openFullDawra(DAWRA_NAME)
        state = vm.uiState.value
        assertEquals(ScreenState.Home, state.screenState)
        assertEquals(FullDawraLock.LOCK_MESSAGE, state.gateMessage)
    }

    @Test
    fun downloadOfADawraSessionPathIsRefusedAndWritesNothing() {
        val vm = viewModel()
        vm.downloadSection(
            UNSEEDED_DAWRA_PATH,
            ManifestFileEntry(hash = "x", size = 1L, count = 1, version = "test")
        )
        assertEquals(FullDawraLock.LOCK_MESSAGE, vm.uiState.value.downloadError)
        val cacheDir = File(context.filesDir, "sections_cache")
        val written = cacheDir.listFiles()?.any { it.name.contains("غير مزروعة") } ?: false
        assertFalse("no dawrat_sessions file may be written while locked", written)
    }

    @Test
    fun entryCardShowsTheLockBadgeOnItsFace() {
        composeRule.setContent {
            WataniTheme(darkTheme = false) {
                FullDawraEntryCard(onClick = {})
            }
        }
        composeRule.onNodeWithText("دورة كاملة").assertIsDisplayed()
        composeRule.onNodeWithText(FullDawraLock.LOCK_BADGE).assertIsDisplayed()
    }

    private companion object {
        const val DOWNLOADED_PREFS = "watani_downloaded_sections"
        const val DAWRA_NAME = "دورة اختبار التكامل"
        const val UNSEEDED_DAWRA_PATH = "dawrat_sessions/دورة غير مزروعة.json"
    }
}
