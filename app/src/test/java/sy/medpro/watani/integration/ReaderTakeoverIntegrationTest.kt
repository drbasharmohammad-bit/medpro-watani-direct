package sy.medpro.watani.integration

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sy.medpro.watani.ui.library.PdfViewerScreen
import sy.medpro.watani.ui.theme.WataniTheme
import java.io.File

/**
 * Layer 3 — the unified PDF reader's window-takeover contract
 * (build 1.2.46): the reader owns the whole window for its whole
 * lifetime. On open it must report the takeover to its host (the
 * host hides the app Scaffold chrome; the reader itself now also
 * hides the system bars, so the page inherits the physical
 * screen), and on exit it must hand the window back. A book file
 * that cannot be opened must land on the reader's own error state
 * — never a crash and never a dead spinner — because the reader
 * is the single PDF surface for the library and First Aid
 * booklets alike, most of them read fully offline.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h2400dp")
class ReaderTakeoverIntegrationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        IntegrationTestSupport.ensureFirebaseApp(context)
        IntegrationTestSupport.clearLocalState(context)
    }

    @Test
    fun readerReportsTakeoverOnOpenErrorStateOnMissingFileAndRestoresOnExit() {
        val fullscreenEvents = mutableListOf<Boolean>()
        var showReader by mutableStateOf(true)
        val missing = File(context.cacheDir, "reader-takeover-missing-book.pdf")
        if (missing.exists()) missing.delete()

        composeRule.setContent {
            WataniTheme(darkTheme = false) {
                if (showReader) {
                    PdfViewerScreen(
                        pdfFile = missing,
                        title = "كتيّب اختبار التكامل",
                        onBack = {},
                        onFullscreenChange = { fullscreenEvents.add(it) }
                    )
                }
            }
        }

        // The takeover is reported as soon as the reader opens.
        composeRule.waitUntil(timeoutMillis = WAIT_MS) { fullscreenEvents.contains(true) }

        // A missing book file resolves to the reader's error
        // state (the open runs off the main thread, so poll).
        composeRule.waitUntil(timeoutMillis = WAIT_MS) {
            try {
                composeRule.onNodeWithText(OPEN_ERROR, substring = true).assertExists()
                true
            } catch (_: AssertionError) {
                // Not on screen yet — waitUntil keeps polling.
                false
            }
        }

        // Leaving the reader hands the window back to the host.
        composeRule.runOnUiThread { showReader = false }
        composeRule.waitUntil(timeoutMillis = WAIT_MS) {
            fullscreenEvents.lastOrNull() == false
        }
        assertTrue(fullscreenEvents.first() == true)
    }

    private companion object {
        const val WAIT_MS = 10_000L
        const val OPEN_ERROR = "تعذر فتح الملف"
    }
}
