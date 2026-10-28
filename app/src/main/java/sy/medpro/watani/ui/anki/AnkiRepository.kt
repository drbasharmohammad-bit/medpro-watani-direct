package sy.medpro.watani.ui.anki

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import sy.medpro.watani.data.WataniApi
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.text.Charsets

/**
 * Loads the published Anki set (`v1/anki/anki.json`) and caches it under filesDir/anki.
 * Remote version wins: unchanged version → cache is used without re-download, newer
 * version → download and replace. Offline → cache. Any failure without a cache, or any
 * parse failure, yields an empty set — never fabricated cards, never a crash.
 */
class AnkiRepository(private val context: Context) {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val setAdapter = moshi.adapter(AnkiSetDto::class.java)

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.NONE })
        .build()

    suspend fun load(): AnkiSet = withContext(Dispatchers.IO) {
        val cachedText = readCache()
        val remoteText = fetchRemote()
        val text = when {
            remoteText == null -> cachedText
            cachedText == null -> {
                persistCache(remoteText)
                remoteText
            }
            else -> {
                val remoteVersion = versionOf(remoteText)
                val cachedVersion = versionOf(cachedText)
                if (remoteVersion != null && remoteVersion != cachedVersion) {
                    persistCache(remoteText)
                    remoteText
                } else {
                    cachedText
                }
            }
        }
        text?.let { parse(it) } ?: AnkiSet(version = null, subjects = emptyList())
    }

    private fun fetchRemote(): String? = try {
        val url = "${WataniApi.BASE_URL}anki/anki.json"
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) return null
            resp.body?.string()?.takeIf { it.isNotBlank() }
        }
    } catch (_: Exception) {
        null
    }

    private fun versionOf(text: String): String? =
        try { setAdapter.fromJson(text)?.version?.takeIf { it.isNotBlank() } } catch (_: Exception) { null }

    private fun parse(text: String): AnkiSet? =
        try {
            val dto = setAdapter.fromJson(text) ?: return null
            val subjects = dto.subjects.orEmpty().mapNotNull { s ->
                val subjectName = s.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val topics = s.topics.orEmpty().mapNotNull { t ->
                    val topicName = t.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    val cards = t.cards.orEmpty().mapNotNull { c ->
                        val front = c.front?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        val back = c.back?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        AnkiCard(
                            id = ankiCardId(subjectName, topicName, front, back),
                            subject = subjectName,
                            topic = topicName,
                            front = front,
                            back = back,
                            src = c.src?.trim()?.takeIf { it.isNotEmpty() }
                        )
                    }
                    AnkiTopic(topicName, cards)
                }
                AnkiSubject(subjectName, topics)
            }
            AnkiSet(version = dto.version, subjects = subjects)
        } catch (_: Exception) {
            null
        }

    private fun cacheFile(): File {
        val dir = File(context.filesDir, "anki")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "anki.json")
    }

    private fun readCache(): String? = try {
        val file = cacheFile()
        if (file.exists()) file.readText(Charsets.UTF_8).takeIf { it.isNotBlank() } else null
    } catch (_: Exception) {
        null
    }

    private fun persistCache(text: String) {
        try {
            val file = cacheFile()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(text, Charsets.UTF_8)
            if (file.exists()) file.delete()
            temp.renameTo(file)
        } catch (_: Exception) {}
    }
}
