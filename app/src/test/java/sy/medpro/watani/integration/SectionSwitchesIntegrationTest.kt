package sy.medpro.watani.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sy.medpro.watani.data.CoursesRepository
import sy.medpro.watani.data.SectionKey
import sy.medpro.watani.data.SectionSwitchStore
import sy.medpro.watani.data.SectionSwitches
import sy.medpro.watani.data.SwitchState

/**
 * Layer 3 — build 1.2.53 section switches + library courses, end to
 * end at the data layer (the layer the screens consume):
 *
 *  - A fresh install (no stored switches, gateway unreachable)
 *    decides EXACTLY like today's build — the defaults are the
 *    current behaviour, so publishing the gateway field changes
 *    nothing until the doctor flips a switch in the panel.
 *  - A fetched config persists: after the process «dies» (the store
 *    is stateless over SharedPreferences) the flipped values are
 *    still the decision source offline.
 *  - A failed/absent refresh never clears a stored flip.
 *  - The courses catalog round-trips through the repository's own
 *    on-disk cache: seed the cache file in the repository's format,
 *    read it back through the production path, fully offline.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class SectionSwitchesIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        IntegrationTestSupport.ensureFirebaseApp(context)
        IntegrationTestSupport.clearLocalState(context)
        SectionSwitchStore.clear(context)
    }

    @Test
    fun freshInstallDecidesWithTodaysDefaults() {
        val map = SectionSwitchStore.current(context)
        assertEquals(SectionSwitches.DEFAULTS, map)
        assertEquals(SwitchState.ACTIVATED, SectionSwitchStore.state(context, SectionKey.UWORLD))
        assertEquals(SwitchState.OPEN, SectionSwitchStore.state(context, SectionKey.BANKS))
    }

    @Test
    fun fetchedConfigPersistsAndSurvivesOffline() {
        val config = JSONObject(
            """{"sectionSwitches":{"banks":"locked","library":"open","tips":"activated"}}"""
        )
        SectionSwitchStore.updateFromConfig(context, config)
        // «Restart»: the store holds no in-memory cache of its own —
        // every read goes to the persisted copy, like a cold start.
        assertEquals(SwitchState.LOCKED, SectionSwitchStore.state(context, SectionKey.BANKS))
        assertEquals(SwitchState.OPEN, SectionSwitchStore.state(context, SectionKey.LIBRARY))
        assertEquals(SwitchState.ACTIVATED, SectionSwitchStore.state(context, SectionKey.TIPS))
        // Untouched sections keep defaults.
        assertEquals(SwitchState.ACTIVATED, SectionSwitchStore.state(context, SectionKey.UWORLD))
    }

    @Test
    fun absentRefreshKeepsStoredFlips() {
        SectionSwitchStore.updateFromConfig(
            context, JSONObject("""{"sectionSwitches":{"cases":"locked"}}""")
        )
        SectionSwitchStore.updateFromConfig(context, null)
        SectionSwitchStore.updateFromConfig(context, JSONObject("""{"minVersion":74327}"""))
        assertEquals(SwitchState.LOCKED, SectionSwitchStore.state(context, SectionKey.CASES))
    }

    @Test
    fun coursesCatalogRoundTripsThroughRepositoryCache() = runBlocking {
        val repo = CoursesRepository(context)
        val fixture = """
            {"courses":[{"id":"c1","title":"كورس","description":null,"coverUrl":null,
             "access":"free",
             "lessons":[{"id":"l1","title":"درس","type":"pdf","path":"books/x.pdf","size":10}]}]}
        """.trimIndent()
        // Seed the repository's own cache file (its documented
        // on-disk location), then read through the production path.
        val cacheFile = File(context.filesDir, "courses_cache/courses.json")
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText(fixture)
        val catalog = repo.cachedCatalog()
        assertNotNull(catalog)
        assertEquals("c1", catalog!!.courses!![0].id)
        assertEquals("books/x.pdf", catalog.courses!![0].lessons!![0].path)
        assertTrue(cacheFile.exists())
    }
}
