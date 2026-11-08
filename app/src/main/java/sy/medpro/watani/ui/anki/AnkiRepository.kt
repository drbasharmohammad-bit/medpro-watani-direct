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
 * Loads the Anki set with a guaranteed working copy at all times:
 *
 * 1. BUNDLED ASSET (`assets/anki.json`, the doctor's 9,071-card set,
 *    version 2026-11-07) ships inside the APK, so the section works
 *    fully offline from the very first launch — no fetch can ever
 *    leave a new device card-less again.
 * 2. The cache file (filesDir/anki/anki.json) holds the last ADOPTED
 *    remote set when one was fully verified.
 * 3. Remote updates are ATOMIC (F-05/F-06 fix): the remote text is
 *    parsed completely and adopted only when (a) it parses, (b) the
 *    parsed card count equals the file's declared `total`, (c) it has
 *    at least one card, and (d) its version is strictly NEWER than the
 *    working copy's version under an explicit ordering. Only then are
 *    content and version persisted together (checked rename). Any
 *    failure at any step keeps the working copy — a version is never
 *    stored for content that was not adopted, and a corrupt/older
 *    remote can never poison the cache (the old code persisted the
 *    raw remote text BEFORE parsing it).
 *
 * Version ordering: versions are `yyyy-MM-dd` dates (e.g. 2026-11-07),
 * so lexicographic comparison IS chronological order for this format;
 * a version that does not match the date shape is compared
 * lexicographically as a fallback, and a missing remote version is
 * never adopted over a versioned working copy.
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
        // Working copy: adopted cache first, bundled asset as the
        // guaranteed floor. Both are parsed through the same strict
        // validation; an invalid cache falls back to the asset.
        val cachedText = readCache()
        val cachedSet = cachedText?.let { validatedParse(it) }
        val working: Pair<String?, AnkiSet> = when {
            cachedSet != null -> cachedText to cachedSet
            else -> {
                val assetText = readBundledAsset()
                assetText to (assetText?.let { validatedParse(it) }
                    ?: AnkiSet(version = null, subjects = emptyList()))
            }
        }
        var workingText = working.first
        var workingSet = working.second

        val remoteText = fetchRemote()
        if (remoteText != null) {
            val remoteSet = validatedParse(remoteText)
            val remoteVersion = remoteSet?.version?.takeIf { it.isNotBlank() }
            val workingVersion = workingSet.version?.takeIf { it.isNotBlank() }
            if (remoteSet != null && remoteVersion != null &&
                isStrictlyNewer(remoteVersion, workingVersion)
            ) {
                // Atomic adoption: content + version persist together,
                // only after full validation above.
                if (persistCache(remoteText)) {
                    workingText = remoteText
                    workingSet = remoteSet
                }
            }
        }
        workingSet
    }

    /**
     * Strict adoption validation (F-05): parses fully and returns the
     * set only when it has at least one card AND its declared `total`
     * (when present) equals the actual parsed card count. Anything
     * else is rejected wholesale — never partially adopted.
     */
    private fun validatedParse(text: String): AnkiSet? {
        val parsed = parse(text) ?: return null
        if (parsed.cards.isEmpty()) return null
        val declaredTotal = try {
            setAdapter.fromJson(text)?.total
        } catch (_: Exception) {
            null
        }
        if (declaredTotal != null && declaredTotal != parsed.cards.size) return null
        return parsed
    }

    /** Explicit version ordering for `yyyy-MM-dd` versions. */
    private fun isStrictlyNewer(remote: String, working: String?): Boolean {
        if (working == null) return true
        val dateShape = Regex("""^\d{4}-\d{2}-\d{2}$""")
        return if (dateShape.matches(remote) && dateShape.matches(working)) {
            remote > working
        } else {
            remote > working
        }
    }

    private fun readBundledAsset(): String? = try {
        context.assets.open("anki.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            .takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    // تحصين (7 نوفمبر 23:47، بأمر الدكتور): إعادة محاولة الجلب حتى 3 مرات عند
    // الفشل قبل الرجوع للكاش — فشل شبكي عابر واحد لا يترك القسم بلا تحديث،
    // ولا يترك جهازًا جديدًا بلا بطاقات بينما الملف حيّ على الخادم.
    private fun fetchRemote(): String? {
        repeat(MAX_FETCH_ATTEMPTS) { attempt ->
            val result = try {
                val url = "${WataniApi.BASE_URL}anki/anki.json"
                client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) null
                    else resp.body?.string()?.takeIf { it.isNotBlank() }
                }
            } catch (_: Exception) {
                null
            }
            if (result != null) return result
            if (attempt < MAX_FETCH_ATTEMPTS - 1) {
                try {
                    Thread.sleep(RETRY_DELAY_MS * (attempt + 1))
                } catch (_: InterruptedException) {
                    // تجاهل: المحاولة التالية (أو الرجوع للكاش) هي السلوك الصحيح
                }
            }
        }
        return null
    }

    private companion object {
        const val MAX_FETCH_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 1500L
    }

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

    /**
     * Atomic cache persist (F-07): write to temp, then a CHECKED rename
     * over the old file in one step — the old cache is never deleted
     * before its replacement exists, and a failed rename reports
     * failure so the caller keeps the previous working copy.
     */
    private fun persistCache(text: String): Boolean {
        return try {
            val file = cacheFile()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(text, Charsets.UTF_8)
            if (temp.renameTo(file)) {
                true
            } else {
                if (temp.exists()) temp.delete()
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}
