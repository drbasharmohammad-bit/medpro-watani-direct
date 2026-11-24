package sy.medpro.watani.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sy.medpro.watani.data.SectionIndex
import sy.medpro.watani.data.WataniRepository

/**
 * Build 1.2.48 (performance list) — Layer 3 flows:
 *
 * 1. Item 3 (fields needed only): parsing a subject shard builds
 *    its persisted light session-count index, stamped with the
 *    registry hash — the «دورة كاملة» list then reads counts
 *    without parsing shard bodies. The index serves only while the
 *    registry hash matches the bytes it was built from.
 * 2. Item 1 (indexes instead of full scans): the small content
 *    packs (ddx.json and family) are parsed ONCE per on-disk stamp
 *    and served from the process cache — a second getter call
 *    returns the very same instance; a changed file re-parses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h1280dp")
class Performance1248IntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val subjectPath = "questions/القلبية.json"
    private val subjectHash = "hash-1248-a"

    private val subjectJson = """
        {"version":"test","subject_id":"القلبية","subject_name":"القلبية","count":3,
         "sessions":[
           {"id":"S1","name":"شباط 2019","questions":[
             {"id":"Q1","text":"سؤال أول","options":["أ","ب"],"correct":"أ"},
             {"id":"Q2","text":"سؤال ثانٍ","options":["أ","ب"],"correct":"ب"}
           ]},
           {"id":"S2","name":"تشرين 2019","questions":[
             {"id":"Q3","text":"سؤال ثالث","options":["أ","ب"],"correct":"أ"}
           ]}
         ]}
    """.trimIndent()

    private val ddxJson = """
        {"version":"test","count":1,
         "cases":[{"id":"D1","title_ar":"حالة اختبار","chief_complaint_ar":"ألم صدري"}]}
    """.trimIndent()

    @Before
    fun setUp() {
        IntegrationTestSupport.ensureFirebaseApp(context)
        IntegrationTestSupport.clearLocalState(context)
        context.getSharedPreferences("watani_downloaded_sections", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences(
            WataniRepository.SESSION_COUNTS_PREFS, Context.MODE_PRIVATE
        ).edit().clear().commit()
    }

    private fun registerDownload(path: String, hash: String) {
        context.getSharedPreferences("watani_downloaded_sections", Context.MODE_PRIVATE)
            .edit()
            .putString(path, "1|$hash|3|1700000000000")
            .commit()
    }

    @Test
    fun subjectParseBuildsTheSessionCountIndex() = runBlocking {
        registerDownload(subjectPath, subjectHash)
        IntegrationTestSupport.seedSection(context, subjectPath, subjectJson)
        val repository = WataniRepository(context)

        val envelope = repository.getSubjectEnvelope(subjectPath)
        assertNotNull(envelope)

        val entry = repository.sessionCountsIndexEntry(subjectPath)
        assertNotNull("the parse must persist the light index", entry)
        assertEquals("القلبية", entry!!.subjectName)
        assertEquals(
            SectionIndex.sessionCounts(envelope!!),
            entry.counts
        )
        assertEquals(mapOf("شباط 2019" to 2, "تشرين 2019" to 1), entry.counts)

        // A path the registry does not hold has no index.
        assertNull(repository.sessionCountsIndexEntry("questions/التوليد.json"))
    }

    @Test
    fun packGetterServesTheSecondCallFromTheProcessCache() = runBlocking {
        registerDownload("ddx.json", "hash-1248-b")
        IntegrationTestSupport.seedSection(context, "ddx.json", ddxJson)
        val repository = WataniRepository(context)

        val first = repository.getDdxEnvelope()
        assertNotNull(first)
        assertEquals(1, first!!.cases?.size)

        // A fresh repository instance (screens build their own)
        // still hits the process-wide cache: same instance back,
        // no second read+decrypt+parse of the pack file.
        val second = WataniRepository(context).getDdxEnvelope()
        assertSame(first, second)
    }

    @Test
    fun packCacheReparsesWhenTheFileChanges() = runBlocking {
        registerDownload("ddx.json", "hash-1248-b")
        IntegrationTestSupport.seedSection(context, "ddx.json", ddxJson)
        val repository = WataniRepository(context)
        val first = repository.getDdxEnvelope()
        assertNotNull(first)

        // Same path, different bytes (different length => different
        // stamp): the cache must NOT serve the stale parse.
        val changedJson = ddxJson.replace("حالة اختبار", "حالة اختبار معدّلة أطول")
        IntegrationTestSupport.seedSection(context, "ddx.json", changedJson)
        val reparsed = repository.getDdxEnvelope()
        assertNotNull(reparsed)
        assertNotSame(first, reparsed)
        assertEquals("حالة اختبار معدّلة أطول", reparsed!!.cases?.firstOrNull()?.titleAr)
    }
}
