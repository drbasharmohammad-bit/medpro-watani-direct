package sy.medpro.watani.integration

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import sy.medpro.watani.data.SectionVerification
import sy.medpro.watani.data.WataniRepository
import sy.medpro.watani.ui.library.LibraryViewModel
import sy.medpro.watani.ui.viewmodel.MainViewModel
import java.io.File

/**
 * Layer 3 — the professional-cache standard (doctor's order
 * 23 Nov 2026: «بكل الأقسام لازم يكون الكاش احترافي مثل الشركات
 * البرمجية العالمية»), cache-audit fixes F1/F2/F3/F5 of build
 * 1.2.45, driven against the REAL repository and view models with
 * the on-disk caches seeded exactly as a working install holds
 * them (the IntegrationTestSupport pattern — no network is ever
 * contacted: every fetch in these flows fails closed offline, which
 * is precisely the condition the fixes must survive gracefully).
 *
 *  - F1: a cold start with a manifest cache on disk must seed the
 *    section lists SYNCHRONOUSLY from that cache — before the fix,
 *    MainViewModel started empty and the home sat behind the
 *    «جاري جلب الفهرس» spinner until the network answered or the
 *    60-second timeout died, although a complete signed manifest
 *    was already on the device.
 *  - F2 (client half): the shared HTTP client must carry an
 *    overall call timeout — before the fix callTimeoutMillis was
 *    0 (no cap), so a trickling connection could spin a First Aid
 *    unit's first open for minutes with no resolution.
 *  - F3: the library index must be served from the on-disk copy
 *    FIRST — before the fix, fetchLibrary burned three network
 *    attempts with backoff before ever reading the cached
 *    library.json, so the library tab span behind a full-screen
 *    spinner on a dead network despite a complete local index.
 *  - F5: the years envelope must be parse-cached in memory stamped
 *    by the registry hash — before the fix, getYearsEnvelope
 *    re-read, re-decrypted and re-parsed the whole file on EVERY
 *    call (every subject open, every year-quiz open).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h1280dp")
class CacheProfessionalIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        IntegrationTestSupport.ensureFirebaseApp(context)
        IntegrationTestSupport.clearLocalState(context)
        context.getSharedPreferences("watani_downloaded_sections", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    // ---------- F1: cold start seeds from the on-disk manifest cache ----------

    @Test
    fun coldStartSeedsSectionListsFromTheDiskManifestCache() {
        val manifestJson = """
            {"version":"seed-v1","files":{
              "questions/قلبية.json":{"hash":"h1","size":100,"count":7,"version":"1"},
              "dawrat_jami3at/damascus/أذنية.json":{"hash":"h2","size":200,"count":2,"version":"1"}
            }}
        """.trimIndent()
        // Seeded in the LEGACY PLAINTEXT form with the fixture
        // mtime the support seeds use (1 s after epoch): the
        // encrypted write path needs the Android Keystore, which
        // Robolectric does not provide, while the repository's
        // read path accepts a pre-hardening plaintext cache and
        // migrates it — exactly what an upgraded install's first
        // cold start sees.
        val cacheFile = File(context.filesDir, WataniRepository.MANIFEST_CACHE_FILENAME)
        cacheFile.writeText(manifestJson, Charsets.UTF_8)
        cacheFile.setLastModified(1_000L)
        assertTrue("the manifest cache fixture must exist", cacheFile.exists())

        val viewModel = MainViewModel(application)

        // Synchronous, before any looper idle: the seed is applied
        // in the constructor, so the very first frame already has
        // the section lists — no «جاري جلب الفهرس» spinner.
        val state = viewModel.uiState.value
        assertEquals("seed-v1", state.manifestVersion)
        assertTrue(state.files.containsKey("questions/قلبية.json"))
        assertTrue(state.files.containsKey("dawrat_jami3at/damascus/أذنية.json"))
        assertEquals(7, state.files["questions/قلبية.json"]?.count)
    }

    // ---------- F2: the shared client has an overall call timeout ----------

    @Test
    fun sharedHttpClientCarriesAnOverallCallTimeout() {
        val repository = WataniRepository(context)
        val field = WataniRepository::class.java.getDeclaredField("client")
        field.isAccessible = true
        val client = field.get(repository) as OkHttpClient
        assertEquals(120_000, client.callTimeoutMillis)
    }

    // ---------- F3: the library serves its on-disk index first ----------

    @Test
    fun libraryServesTheOnDiskIndexWithoutWaitingForTheNetwork() {
        IntegrationTestSupport.seedSection(
            context,
            "library.json",
            """
            {"subjects":[{"id":"s1","name":"القلبية","books":[],
              "videos":[{"id":"v1","title":"محاضرة اختبار","fileUrl":"https://example.com/v1.mp4"}]}]}
            """.trimIndent()
        )

        val viewModel = LibraryViewModel(application)

        // The cached envelope is served from disk (an IO hop), while
        // the network refresh behind it can only fail offline — poll
        // the main looper until the state lands or the deadline hits.
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (viewModel.state.value.library != null) break
            Thread.sleep(50)
        }
        val state = viewModel.state.value
        assertNotNull("the on-disk library index must be served", state.library)
        assertEquals("s1", state.library!!.subjects!!.first().id)
        assertEquals(false, state.isLoading)
        // The background refresh's offline failure stays silent —
        // a student with a cached index never sees an error wall.
        assertNull(state.error)
    }

    // ---------- F5: the years envelope is parse-cached, registry-stamped ----------

    @Test
    fun yearsEnvelopeIsServedFromTheParseCacheOnSecondRead() {
        val path = IntegrationTestSupport.UNI_YEARS_PATH
        val json = IntegrationTestSupport.universityYearsJson()
        IntegrationTestSupport.seedSection(context, path, json)
        // The registry entry a real download would have written:
        // version|hash|count|downloadedAt, the hash computed from
        // the verified plaintext exactly as downloadSection stores it.
        val hash = SectionVerification.manifestHashHex(path, json.toByteArray(Charsets.UTF_8))
        context.getSharedPreferences("watani_downloaded_sections", Context.MODE_PRIVATE)
            .edit().putString(path, "test|$hash|5|1000").commit()

        val repository = WataniRepository(context)
        val first = runBlocking { repository.getYearsEnvelope(path) }
        val second = runBlocking { repository.getYearsEnvelope(path) }

        assertNotNull(first)
        assertEquals(5, first!!.count)
        // Same INSTANCE: the second read came from the in-memory
        // parse cache, not from a re-read + re-decrypt + re-parse.
        assertSame(first, second)
    }
}
