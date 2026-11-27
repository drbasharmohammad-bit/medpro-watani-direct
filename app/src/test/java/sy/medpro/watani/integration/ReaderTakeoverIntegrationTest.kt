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

    /**
     * 1.2.50 resolution contract, from the doctor's device
     * report: a page state must ALWAYS resolve — content or an
     * explicit error — never an eternal «جارٍ تجهيز الصفحة...».
     * A corrupt book file (bytes that are not a PDF at all)
     * resolves at open time to the reader's error state, and
     * the per-page preparing label never appears for it.
     */
    @Test
    fun corruptBookResolvesToOpenErrorNeverPagePreparing() {
        val corrupt = File(context.cacheDir, "reader-resolution-corrupt-book.pdf")
        corrupt.writeBytes("this is not a pdf".toByteArray())

        composeRule.setContent {
            WataniTheme(darkTheme = false) {
                PdfViewerScreen(
                    pdfFile = corrupt,
                    title = "كتيّب تالف للاختبار",
                    onBack = {}
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = WAIT_MS) {
            try {
                composeRule.onNodeWithText(OPEN_ERROR, substring = true).assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        // The book never reaches the pager, so the per-page
        // preparing state cannot be on screen.
        composeRule.onNodeWithText(PAGE_PREPARING, substring = true).assertDoesNotExist()
    }

    /**
     * 1.2.52 engine-boundary contract: the reader's engine is
     * now PDFium (a native library bundled in the APK). Under
     * Robolectric that native library cannot load, so opening
     * even a perfectly valid book must resolve to the reader's
     * error state — the engine boundary is armored against ANY
     * throwable (an UnsatisfiedLinkError is an Error, not an
     * Exception) — never a crash, never a dead spinner, never
     * the per-page preparing label. On a real device the same
     * file opens and reads normally; what this test pins is
     * that a dead engine can never take the reader down with
     * it.
     */
    @Test
    fun validBookWithUnavailableEngineResolvesToOpenErrorNeverCrash() {
        val book = File(context.cacheDir, "reader-engine-boundary-book.pdf")
        book.writeBytes(MINIMAL_PDF.toByteArray(Charsets.US_ASCII))

        composeRule.setContent {
            WataniTheme(darkTheme = false) {
                PdfViewerScreen(
                    pdfFile = book,
                    title = "كتيّب حدود المحرك",
                    onBack = {}
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = WAIT_MS) {
            try {
                composeRule.onNodeWithText(OPEN_ERROR, substring = true).assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        composeRule.onNodeWithText(PAGE_PREPARING, substring = true).assertDoesNotExist()
    }

    private companion object {
        const val WAIT_MS = 10_000L
        const val OPEN_ERROR = "تعذر فتح الملف"
        const val PAGE_PREPARING = "جارٍ تجهيز الصفحة"

        /** A minimal structurally valid one-page PDF. */
        const val MINIMAL_PDF: String =
            "%PDF-1.4\n" +
                "1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n" +
                "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n" +
                "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 595 842]>>endobj\n" +
                "trailer<</Root 1 0 R>>\n%%EOF\n"
    }
}
