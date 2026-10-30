package sy.medpro.watani.ui.explanation

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import sy.medpro.watani.data.WorkerActivationApi
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.text.Charsets

/**
 * Loads an explanation layer from the content network using the same pattern as other
 * downloaded content: index → per-shard downloads → cached on device, offline after
 * download. The layer base path is configurable (default: `explanations/` under v1/,
 * the live layer) so a future layer plugs in by changing the path, not code.
 *
 * Explanation fix (Oct 2026 — explanations never appeared on slow connections):
 * the old version re-downloaded the 530KB index on EVERY question, downloaded
 * shards up to 8.5MB from zero on every attempt, deleted partial downloads on
 * failure, and collapsed every failure into silent "absent" — so on a slow
 * connection a shard never finished before the user moved to the next
 * question, and the explanation simply never appeared. Now:
 * - the index is cached in memory per process and on disk (network-first,
 *   disk fallback), so it is fetched once, not per question;
 * - shard downloads resume via Range (the Worker gateway now serves ranges
 *   from its cache) and partial files are KEPT on failure, so a slow
 *   download completes across attempts instead of restarting;
 * - shard cache files are named by SHA-256 of the shard name: the old
 *   sanitization turned every Arabic shard name into underscores and
 *   could collide two different subjects onto one cache file;
 * - network/index/shard failures are returned as Result.failure (the UI
 *   shows a retry button) instead of silent "absent" — success(null) now
 *   strictly means "the layer loaded and this question has no record".
 */
class ExplanationRepository(
    private val context: Context,
    layerBasePath: String = "explanations/"
) {
    val layerBasePath: String = if (layerBasePath.endsWith("/")) layerBasePath else "$layerBasePath/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val recordListAdapter =
        moshi.adapter<List<ExplanationRecordDto>>(
            Types.newParameterizedType(List::class.java, ExplanationRecordDto::class.java)
        )

    private val indexAdapter = moshi.adapter(ExplanationIndex::class.java)

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.NONE })
        .build()

    /** In-memory cache of shard contents for the current process lifetime, keyed by shard path. */
    private val shardCache = mutableMapOf<String, List<ExplanationRecordDto>>()

    /** In-memory index: fetched once per process, never per question. */
    private var cachedIndex: ExplanationIndex? = null

    private var legacyCleaned = false

    /**
     * Looks up the explanation for a question id. `questionId` must already be normalized
     * to string by the caller ([normalizeExplanationKey]). Returns `Result.success(null)`
     * ONLY for "no explanation for this question" (layer loaded, question not covered,
     * or not activated — the activation prompt handles that case). Returns
     * `Result.failure` for network/index/shard errors so the UI can offer a retry
     * instead of silently showing nothing.
     */
    suspend fun fetchExplanation(questionId: String?): Result<ExplanationContent?> =
        withContext(Dispatchers.IO) {
            if (questionId.isNullOrBlank()) return@withContext Result.success(null)
            try {
                val indexResult = fetchLayerIndex()
                if (indexResult.isFailure) {
                    return@withContext Result.failure(indexResult.exceptionOrNull()!!)
                }
                val index = indexResult.getOrNull()
                    ?: return@withContext Result.success(null)
                // Fast path: qid_index maps the question key straight to its shard, so only
                // that one shard is downloaded instead of every shard in the layer.
                val fastShard = index.qidIndex?.get(questionId)
                if (fastShard != null) {
                    val shardResult = loadShard(fastShard)
                    if (shardResult.isFailure) {
                        return@withContext Result.failure(shardResult.exceptionOrNull()!!)
                    }
                    val match = shardResult.getOrNull().orEmpty()
                        .firstOrNull { it.lookupKey() == questionId }
                    return@withContext Result.success(match?.toContent())
                }
                // Fallback: no qid_index entry (or no qid_index at all) — scan the files map.
                // A failed shard does not hide matches in other shards, but if
                // nothing matched AND some shard failed, report failure (retry)
                // rather than a false "this question has no explanation".
                var anyShardFailed = false
                for (shardName in index.files?.keys.orEmpty()) {
                    val shardResult = loadShard(shardName)
                    if (shardResult.isFailure) {
                        anyShardFailed = true
                        continue
                    }
                    val match = shardResult.getOrNull().orEmpty()
                        .firstOrNull { it.lookupKey() == questionId }
                    if (match != null) {
                        return@withContext Result.success(match.toContent())
                    }
                }
                if (anyShardFailed) {
                    Result.failure(Exception("تعذر تحميل بعض ملفات الشروحات"))
                } else {
                    Result.success(null)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private suspend fun fetchLayerIndex(): Result<ExplanationIndex?> = withContext(Dispatchers.IO) {
        cachedIndex?.let { return@withContext Result.success(it) }
        val diskFile = indexCacheFile()
        // Network-first (fresh), disk fallback (offline / slow connection).
        try {
            // Same switch point as the question bank: explanations/ is a
            // restricted prefix, so the index goes via the Worker gateway
            // with the activation token when activated, and yields null
            // (no explanation) when not activated — never a crash.
            val request = WorkerActivationApi.contentRequest(context, "${layerBasePath}index.json")
                ?: return@withContext Result.success(null)
            client.newCall(request).execute().use { resp ->
                if (resp.code == 401 || resp.code == 403) {
                    // M3: revoked activation — clear + wipe + prompt
                    // event; never fall back to cached explanations.
                    WorkerActivationApi.notifyActivationLost(context)
                    return@withContext Result.success(null)
                }
                if (!resp.isSuccessful) {
                    return@withContext readIndexFromDisk(diskFile)
                        ?: Result.failure(Exception("تعذر تحميل فهرس الشروحات"))
                }
                val text = resp.body?.string()
                    ?: return@withContext readIndexFromDisk(diskFile)
                        ?: Result.failure(Exception("استجابة فارغة من الخادم"))
                val parsed = try {
                    indexAdapter.fromJson(text)
                } catch (_: Exception) {
                    null
                } ?: return@withContext readIndexFromDisk(diskFile)
                    ?: Result.failure(Exception("تعذر قراءة فهرس الشروحات"))
                cachedIndex = parsed
                try {
                    diskFile.parentFile?.mkdirs()
                    diskFile.writeText(text, Charsets.UTF_8)
                } catch (_: Exception) {
                }
                Result.success(parsed)
            }
        } catch (e: Exception) {
            readIndexFromDisk(diskFile) ?: Result.failure(e)
        }
    }

    private fun readIndexFromDisk(diskFile: File): Result<ExplanationIndex?>? {
        if (!diskFile.exists()) return null
        return try {
            val parsed = indexAdapter.fromJson(diskFile.readText(Charsets.UTF_8))
            if (parsed == null) {
                null
            } else {
                cachedIndex = parsed
                Result.success(parsed)
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun loadShard(shardName: String): Result<List<ExplanationRecordDto>> {
        shardCache[shardName]?.let { return Result.success(it) }
        cleanupLegacyCache()

        val cacheFile = shardCacheFile(shardName)
        if (cacheFile.exists()) {
            try {
                val records = parseShard(cacheFile.readText(Charsets.UTF_8))
                if (records.isNotEmpty()) {
                    shardCache[shardName] = records
                    return Result.success(records)
                }
                // Empty parse of a non-empty file = corrupt cache: drop it
                // and re-download instead of serving a false empty shard.
                cacheFile.delete()
            } catch (_: Exception) {
                cacheFile.delete()
            }
        }

        val downloadResult = downloadShard(shardName)
        if (downloadResult.isFailure) {
            return Result.failure(downloadResult.exceptionOrNull()!!)
        }
        val text = downloadResult.getOrNull()!!
        val records = parseShard(text)
        if (records.isEmpty()) {
            // Corrupt download: do NOT persist it, so a retry re-downloads.
            return Result.failure(Exception("تعذر قراءة ملف الشرح — أعد المحاولة"))
        }
        persistShardCache(shardName, text)
        shardCache[shardName] = records
        return Result.success(records)
    }

    /**
     * Resumable shard download: sends Range from the kept partial file
     * (the gateway serves 206 from its cache) and KEEPS the partial file
     * on failure, so the next attempt resumes instead of restarting an
     * 8MB shard from zero on a slow connection.
     */
    private suspend fun downloadShard(shardName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Same switch point (see fetchLayerIndex). contentRequest
            // encodes path segments itself, so the raw shard name is passed.
            val baseRequest = WorkerActivationApi.contentRequest(context, "$layerBasePath$shardName")
                ?: return@withContext Result.failure(Exception("هذا المحتوى يحتاج كود تفعيل"))
            val tempFile = shardTempFile(shardName)
            var attempt = 0
            while (attempt < 2) {
                attempt++
                val existingBytes = if (tempFile.exists()) tempFile.length() else 0L
                val builder = baseRequest.newBuilder()
                if (existingBytes > 0) builder.header("Range", "bytes=$existingBytes-")
                val resp = client.newCall(builder.build()).execute()
                resp.use {
                    if (it.code == 401 || it.code == 403) {
                        // M3: same revocation path as the index fetch.
                        WorkerActivationApi.notifyActivationLost(context)
                        return@withContext Result.failure(Exception("انتهى التفعيل"))
                    }
                    if (it.code == 416) {
                        // Stale Range — drop the temp and retry from zero once.
                        tempFile.delete()
                        if (attempt < 2) return@use
                        return@withContext Result.failure(Exception("تعذر استئناف تحميل الشرح — أعد المحاولة"))
                    }
                    if (!it.isSuccessful && it.code != 206) {
                        return@withContext Result.failure(Exception("تعذر تحميل الشرح — تحقق من الاتصال"))
                    }
                    val isPartial = it.code == 206
                    val startBytes = if (isPartial) existingBytes else {
                        if (tempFile.exists()) tempFile.delete()
                        0L
                    }
                    val body = it.body
                        ?: return@withContext Result.failure(Exception("استجابة فارغة من الخادم"))
                    try {
                        body.byteStream().use { input ->
                            FileOutputStream(tempFile, startBytes > 0).use { output ->
                                val buf = ByteArray(16384)
                                var read: Int
                                while (input.read(buf).also { read = it } != -1) {
                                    output.write(buf, 0, read)
                                }
                                output.flush()
                            }
                        }
                    } catch (e: Exception) {
                        // Keep the partial file for resume; retry once
                        // inside this call, then report failure (the next
                        // question still resumes from the partial file).
                        if (attempt >= 2) {
                            return@withContext Result.failure(e)
                        } else {
                            return@use
                        }
                    }
                    return@withContext Result.success(tempFile.readText(Charsets.UTF_8))
                }
            }
            Result.failure(Exception("تعذر تحميل الشرح"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseShard(text: String): List<ExplanationRecordDto> =
        try {
            recordListAdapter.fromJson(text).orEmpty()
        } catch (_: Exception) {
            emptyList()
        }

    private fun persistShardCache(shardName: String, text: String) {
        try {
            val file = shardCacheFile(shardName)
            file.writeText(text, Charsets.UTF_8)
            // The resume temp is no longer needed once the full shard is cached.
            val temp = shardTempFile(shardName)
            if (temp.exists()) temp.delete()
        } catch (_: Exception) {}
    }

    private fun explanationDir(): File {
        val dir = File(context.filesDir, "explanation_layers")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun indexCacheFile(): File = File(explanationDir(), "index.json")

    /**
     * Hash-based cache names: the old sanitization replaced every Arabic
     * letter with '_' so two different subjects could collide onto one
     * cache file (e.g. two 5-letter names → the same "_____.json").
     */
    private fun shardHash(shardName: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(shardName.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(16)

    private fun shardCacheFile(shardName: String): File =
        File(explanationDir(), "${shardHash(shardName)}.json")

    private fun shardTempFile(shardName: String): File =
        File(explanationDir(), "${shardHash(shardName)}.tmp")

    /** One-time cleanup of legacy sanitized cache files (they are caches,
     * never user data, and are re-downloaded on demand if still needed). */
    private fun cleanupLegacyCache() {
        if (legacyCleaned) return
        legacyCleaned = true
        try {
            val dir = explanationDir()
            val keep = Regex("^[0-9a-f]{16}\\.(json|tmp)$")
            dir.listFiles()?.forEach { f ->
                if (f.name == "index.json") return@forEach
                if (!keep.matches(f.name)) {
                    try {
                        f.delete()
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
    }
}
