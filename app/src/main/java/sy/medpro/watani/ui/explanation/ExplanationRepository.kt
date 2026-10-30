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
import java.util.concurrent.TimeUnit
import kotlin.text.Charsets

/**
 * Loads an explanation layer from the content network using the same pattern as other
 * downloaded content: index → per-shard downloads → cached on device, offline after
 * download. The layer base path is configurable (default: `explanations/` under v1/,
 * the live layer) so a future layer plugs in by changing the path, not code. Failures
 * yield empty state — never fake content, never crash.
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

    /**
     * Looks up the explanation for a question id. `questionId` must already be normalized
     * to string by the caller ([normalizeExplanationKey]). Returns `Result.success(null)`
     * for "no explanation" (missing layer, 404, question not covered) — never a crash,
     * never fabricated content. Returns `Result.failure` only for unexpected errors the
     * caller may want to distinguish from "not found", but callers should still render
     * "no explanation" on failure rather than surfacing an error to the quiz UI.
     */
    suspend fun fetchExplanation(questionId: String?): Result<ExplanationContent?> =
        withContext(Dispatchers.IO) {
            if (questionId.isNullOrBlank()) return@withContext Result.success(null)
            try {
                val index = fetchLayerIndex() ?: return@withContext Result.success(null)
                // Fast path: qid_index maps the question key straight to its shard, so only
                // that one shard is downloaded instead of every shard in the layer.
                val fastShard = index.qidIndex?.get(questionId)
                if (fastShard != null) {
                    val match = loadShard(fastShard).firstOrNull { it.lookupKey() == questionId }
                    return@withContext Result.success(match?.toContent())
                }
                // Fallback: no qid_index entry (or no qid_index at all) — scan the files map.
                val shardNames = index.files?.keys.orEmpty()
                for (shardName in shardNames) {
                    val records = loadShard(shardName)
                    val match = records.firstOrNull { it.lookupKey() == questionId }
                    if (match != null) {
                        return@withContext Result.success(match.toContent())
                    }
                }
                Result.success(null)
            } catch (e: Exception) {
                Result.success(null)
            }
        }

    private suspend fun fetchLayerIndex(): ExplanationIndex? = withContext(Dispatchers.IO) {
        try {
            // Same switch point as the question bank: explanations/ is a
            // restricted prefix, so the index goes via the Worker gateway
            // with the activation token when activated, and yields null
            // (no explanation) when not activated — never a crash.
            val request = WorkerActivationApi.contentRequest(context, "${layerBasePath}index.json")
                ?: return@withContext null
            client.newCall(request).execute().use { resp ->
                if (resp.code == 401 || resp.code == 403) {
                    // M3: revoked activation — clear + wipe + prompt
                    // event; never fall back to cached explanations.
                    WorkerActivationApi.notifyActivationLost(context)
                    return@withContext null
                }
                if (!resp.isSuccessful) return@withContext null
                val text = resp.body?.string() ?: return@withContext null
                indexAdapter.fromJson(text)
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun loadShard(shardName: String): List<ExplanationRecordDto> {
        shardCache[shardName]?.let { return it }

        val cacheFile = shardCacheFile(shardName)
        val cachedText = if (cacheFile.exists()) {
            try { cacheFile.readText(Charsets.UTF_8) } catch (_: Exception) { null }
        } else null

        val text = cachedText ?: downloadShard(shardName)
        val records = text?.let { parseShard(it) }.orEmpty()
        shardCache[shardName] = records
        return records
    }

    private suspend fun downloadShard(shardName: String): String? = withContext(Dispatchers.IO) {
        try {
            // Same switch point (see fetchLayerIndex). contentRequest
            // encodes path segments itself, so the raw shard name is passed.
            val request = WorkerActivationApi.contentRequest(context, "$layerBasePath$shardName")
                ?: return@withContext null
            client.newCall(request).execute().use { resp ->
                if (resp.code == 401 || resp.code == 403) {
                    // M3: same revocation path as the index fetch.
                    WorkerActivationApi.notifyActivationLost(context)
                    return@withContext null
                }
                if (!resp.isSuccessful) return@withContext null
                val text = resp.body?.string() ?: return@withContext null
                persistShardCache(shardName, text)
                text
            }
        } catch (_: Exception) {
            null
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
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(text, Charsets.UTF_8)
            if (file.exists()) file.delete()
            temp.renameTo(file)
        } catch (_: Exception) {}
    }

    private fun shardCacheFile(shardName: String): File {
        val dir = File(context.filesDir, "explanation_layers")
        if (!dir.exists()) dir.mkdirs()
        val safe = shardName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(dir, safe)
    }
}
