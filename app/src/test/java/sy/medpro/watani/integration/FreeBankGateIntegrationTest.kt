package sy.medpro.watani.integration

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sy.medpro.watani.data.FirstAidBookletDto
import sy.medpro.watani.data.FirstAidSystemDto
import sy.medpro.watani.data.RestrictedInventory
import sy.medpro.watani.data.WataniApi
import sy.medpro.watani.data.WataniRepository
import sy.medpro.watani.data.WorkerActivationApi
import sy.medpro.watani.ui.firstaid.BookletRow
import sy.medpro.watani.ui.firstaid.FirstAidBookletOpener
import sy.medpro.watani.ui.firstaid.bookletDisplayTitle
import sy.medpro.watani.ui.library.PdfViewerScreen
import sy.medpro.watani.ui.theme.WataniTheme
import java.io.File
import java.security.MessageDigest

/**
 * Layer 3 — build 1.2.47 end-to-end contracts, driven against the
 * REAL repository, inventory, opener and reader with the on-disk
 * state seeded exactly as a working install holds it (the
 * IntegrationTestSupport pattern; the gateway is never contacted —
 * offline, every production gate must fail closed or resolve
 * locally, which is precisely what is asserted):
 *
 *  1. The free-subjects policy (the doctor's order): the quiz-gate
 *     rule the view model consults opens the three free subjects
 *     and keeps everything else — UWorld and the library included —
 *     activation-locked.
 *  2. The fetch switch fails closed with no credential at all:
 *     no activation token and no signed-in user means NO request
 *     for any restricted path (free subjects included — they are
 *     for registered students), while genuinely free content keeps
 *     its direct Pages request.
 *  3. The revocation wipe (M4) no longer confiscates free content:
 *     a free subject's downloaded file, registry entry and
 *     explanation shards survive a wipe that still removes every
 *     locked download (the doctor's «no confiscation» clause).
 *  4. A First Aid booklet opens under its Arabic display title —
 *     never its raw file name — in the catalog row and through the
 *     real opener (the doctor's screenshot defect).
 *  5. A book file that cannot be opened lands on the reader's
 *     explicit error state — never a silent black screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h1600dp")
class FreeBankGateIntegrationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        IntegrationTestSupport.ensureFirebaseApp(context)
        IntegrationTestSupport.clearLocalState(context)
        context.getSharedPreferences("watani_downloaded_sections", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun quizGateRuleOpensFreeSubjectsAndKeepsTheRestLocked() {
        // The exact function MainViewModel's quiz gates consult.
        assertFalse(WorkerActivationApi.requiresActivation("questions/القلبية.json"))
        assertFalse(WorkerActivationApi.requiresActivation("questions/التوليد.json"))
        assertFalse(WorkerActivationApi.requiresActivation("dawrat_jami3at/damascus/كلية.json"))
        assertFalse(WorkerActivationApi.requiresActivation("dawrat_jami3at/damascus_years/قلبية.json"))
        assertFalse(WorkerActivationApi.requiresActivation("dawrat_jami3at/aleppo/باطنة قلبية.json"))
        assertTrue(WorkerActivationApi.requiresActivation("questions/الجراحة العامة.json"))
        assertTrue(WorkerActivationApi.requiresActivation("questions/الغدية والسكري.json"))
        assertTrue(WorkerActivationApi.requiresActivation("uworld/2025.json"))
        assertTrue(WorkerActivationApi.requiresActivation("library.json"))
        assertTrue(WorkerActivationApi.requiresActivation("dawrat_sessions/شباط 2019.json"))
    }

    @Test
    fun contentRequestFailsClosedWithNoCredential() {
        // Signed out + never activated (the support harness's state):
        // every restricted path — free subjects included — yields no
        // request at all rather than a direct public fetch.
        assertNull(WorkerActivationApi.contentRequest(context, "questions/القلبية.json"))
        assertNull(WorkerActivationApi.contentRequest(context, "questions/الغدية والسكري.json"))
        assertNull(WorkerActivationApi.contentRequest(context, "uworld/2025.json"))
        assertNull(WorkerActivationApi.contentRequest(context, "library.json"))
        assertNull(WorkerActivationApi.contentRequest(context, "explanations/القلبية.json"))
        // Genuinely free content keeps its direct Pages request.
        val free = WorkerActivationApi.contentRequest(context, "cases.json")
        assertNotNull(free)
        assertTrue(free!!.url.toString().startsWith(WataniApi.BASE_URL))
    }

    private fun shardHash(shardName: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(shardName.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(16)

    @Test
    fun revocationWipeKeepsFreeDownloadsAndDropsLockedOnes() {
        val freeSection = "questions/القلبية.json"
        val freeYears = "dawrat_jami3at/damascus_years/التوليد.json"
        val lockedSection = "questions/الغدية والسكري.json"
        val lockedUworld = "uworld/2025.json"
        for (path in listOf(freeSection, freeYears, lockedSection, lockedUworld, "library.json")) {
            IntegrationTestSupport.seedSection(context, path, "{}")
        }
        val registry = context.getSharedPreferences("watani_downloaded_sections", Context.MODE_PRIVATE)
        for (path in listOf(freeSection, freeYears, lockedSection, lockedUworld)) {
            registry.edit().putString(path, "test|hash|1|1000").commit()
        }
        val layersDir = File(context.filesDir, "explanation_layers").apply { mkdirs() }
        val freeShard = File(layersDir, "${shardHash("القلبية.json")}.json").apply { writeText("{}") }
        val freeShardFp = File(layersDir, "${shardHash("القلبية.json")}.fp").apply { writeText("fp") }
        val layerIndex = File(layersDir, "index.json").apply { writeText("{}") }
        val lockedShard = File(layersDir, "${shardHash("الغدية والسكري.json")}.json").apply { writeText("{}") }

        RestrictedInventory.wipeRestrictedDownloads(context)

        fun sectionFile(path: String) =
            File(File(context.filesDir, "sections_cache"), path.replace('/', '_') + ".json")
        assertTrue("free section survives", sectionFile(freeSection).exists())
        assertTrue("free years twin survives", sectionFile(freeYears).exists())
        assertFalse("locked section wiped", sectionFile(lockedSection).exists())
        assertFalse("uworld wiped", sectionFile(lockedUworld).exists())
        assertFalse("library wiped", sectionFile("library.json").exists())
        assertTrue("free registry entry survives", registry.contains(freeSection))
        assertTrue("free years registry entry survives", registry.contains(freeYears))
        assertFalse("locked registry entry wiped", registry.contains(lockedSection))
        assertFalse("uworld registry entry wiped", registry.contains(lockedUworld))
        assertTrue("free explanation shard survives", freeShard.exists())
        assertTrue("free shard fingerprint survives", freeShardFp.exists())
        assertTrue("layer index survives", layerIndex.exists())
        assertFalse("locked explanation shard wiped", lockedShard.exists())
    }

    private val biochemSystem = FirstAidSystemDto(
        code = "BIOC", sectionAr = "الكيمياء الحيوية", sectionEn = "Biochemistry",
        units = emptyList()
    )
    private val biochemBooklet = FirstAidBookletDto(
        file = "booklets1/FirstAid-Biochemistry-Booklet-Mobile.pdf", size = null
    )

    @Test
    fun bookletOpenerHandsTheReaderTheArabicDisplayTitle() {
        val repo = WataniRepository(context)
        val local = repo.getFirstAidBookletFile("firstaid/${biochemBooklet.file}")
        local.writeText("%PDF- fake but present", Charsets.UTF_8)

        var openedFile: File? = null
        var openedTitle: String? = null
        val opener = FirstAidBookletOpener(repo) { file, title ->
            openedFile = file
            openedTitle = title
        }
        opener.systems = listOf(biochemSystem)
        runBlocking { opener.open(biochemBooklet) }

        assertEquals(local, openedFile)
        assertEquals("كتيّب الكيمياء الحيوية", openedTitle)
    }

    @Test
    fun bookletRowShowsTheDisplayTitleNotTheFileName() {
        composeRule.setContent {
            WataniTheme(darkTheme = false) {
                BookletRow(
                    step = 1,
                    booklet = biochemBooklet,
                    title = bookletDisplayTitle(biochemBooklet, listOf(biochemSystem))
                ) {}
            }
        }
        composeRule.onNodeWithText("كتيّب الكيمياء الحيوية").assertExists()
        composeRule.onAllNodesWithText("FirstAid-Biochemistry", substring = true)
            .assertCountEquals(0)
    }

    @Test
    fun readerLandsOnAnExplicitErrorStateForAnUnreadableBook() {
        val corrupt = File(context.cacheDir, "freebank-corrupt-book.pdf")
        corrupt.writeText("this is not a pdf at all", Charsets.UTF_8)
        composeRule.setContent {
            WataniTheme(darkTheme = false) {
                PdfViewerScreen(
                    pdfFile = corrupt,
                    title = "كتيّب اختبار",
                    onBack = {}
                )
            }
        }
        // The open (or the first page render, depending on the
        // renderer's behaviour under test) must resolve to one of
        // the reader's explicit error states — never a silent
        // black screen and never an endless spinner.
        composeRule.waitUntil(timeoutMillis = WAIT_MS) {
            val openError = try {
                composeRule.onNodeWithText("تعذر فتح الملف", substring = true).assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
            val pageError = try {
                composeRule.onNodeWithText("تعذّرت قراءة هذه الصفحة", substring = true).assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
            openError || pageError
        }
    }

    private companion object {
        const val WAIT_MS = 10_000L
    }
}
