package sy.medpro.watani.data

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Build 1.2.53 — the courses catalog store. Cache-first by the
 * professional cache standard: [cachedCatalog] serves the on-disk
 * copy instantly (the Library tab never waits on the network for
 * a catalog it already holds), and [refreshCatalog] updates it in
 * the background — a failed refresh keeps the cached copy and
 * reports null, never an error wall.
 *
 * The catalog is public metadata (titles and file paths only —
 * the lesson FILES keep whatever protection their own content
 * family carries, enforced by the library download path), so it
 * is fetched directly from the content origin and stored plain
 * under filesDir/courses_cache/courses.json.
 */
class CoursesRepository(context: Context) {

    private val appContext = context.applicationContext
    private val client = OkHttpClient()
    private val adapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        .adapter(CourseCatalog::class.java)

    private fun cacheFile(): File = File(File(appContext.filesDir, "courses_cache"), "courses.json")

    /** The cached catalog, or null when none was ever fetched or
     * the stored copy does not parse. */
    fun cachedCatalog(): CourseCatalog? {
        val file = cacheFile()
        if (!file.exists()) return null
        return try {
            adapter.fromJson(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    /** Background refresh: on success the new catalog replaces
     * the cache and is returned; on any failure the cached copy
     * stays and null is returned. */
    suspend fun refreshCatalog(): CourseCatalog? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(CourseLogic.CONTENT_ORIGIN + "courses.json")
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val text = resp.body?.string() ?: return@withContext null
                val parsed = adapter.fromJson(text) ?: return@withContext null
                val file = cacheFile()
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, "courses.json.tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(file)) {
                    tmp.delete()
                    return@withContext null
                }
                parsed
            }
        } catch (_: Exception) {
            null
        }
    }
}
