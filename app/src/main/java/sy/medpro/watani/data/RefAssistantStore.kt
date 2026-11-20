package sy.medpro.watani.data

import android.content.Context
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * «الباحث المعتمد» store (build 1.2.33): the reference bundle on the
 * device, the offline weighted search over it, and the online ask.
 *
 * The bundle is a restricted content family (`refsearch.json` +
 * `refsearch/*.json.gz`, gateway-served from R2 like the banks): the
 * curated corpus of 178 references / 14,350 chunks, pre-chunked and
 * pre-indexed at preparation time (build_refsearch.py). The device
 * downloads it ONCE through the gateway with the activation token,
 * verifies every file's sha256[:16] against the bundle's own table
 * (and the bundle against the signed content manifest), and then the
 * text search works fully offline — the last rung of the SPEC §7
 * «جواب دائمًا» ladder. The smart tier lives on the gateway
 * (/v1/assistant/ask); any failure there falls back to this local
 * search instead of an error.
 */

/** One corpus chunk as displayed and cited. */
data class RefChunk(
    val idx: Int,
    val id: String?,
    val reference: String?,
    val publisher: String?,
    val year: Int?,
    val specialty: String?,
    val section: String?,
    val pageStart: Int?,
    val pageEnd: Int?,
    val evidenceLevel: String?,
    val recommendationGrade: String?,
    val text: String
)

/** One downloadable shard of the bundle (chunk pack or index pack). */
data class RefShard(val path: String, val size: Long, val hash: String, val count: Int)

/** A reference carried by the bundle (for the «المراجع المتاحة» list). */
data class RefInfo(val reference: String?, val publisher: String?, val year: Int?, val specialty: String?, val chunks: Int)

/** The parsed bundle index (refsearch.json). */
data class RefBundle(
    val version: Int,
    val totalRefs: Int,
    val totalChunks: Int,
    val chunkShards: List<RefShard>,
    val indexShards: List<RefShard>,
    val refs: List<RefInfo>
)

/** The gateway's answer to one question, exactly as displayed. */
sealed class RefAskResult {
    data class Smart(val answer: String, val sources: List<RefChunk>, val smartExhausted: Boolean) : RefAskResult()
    data class TextResults(val chunks: List<RefChunk>, val smartExhausted: Boolean, val offline: Boolean) : RefAskResult()
    data class NotInRefs(val smartExhausted: Boolean) : RefAskResult()
    data class Failed(val reason: String) : RefAskResult()
}

/** Download state of the one bundle, shared by every screen. */
data class RefPackDownloadState(
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val failed: Boolean = false,
    val completedRuns: Int = 0
)

object RefAssistantPack {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _state = MutableStateFlow(RefPackDownloadState())
    val state: StateFlow<RefPackDownloadState> = _state.asStateFlow()

    private val client: OkHttpClient = sy.medpro.watani.security.NetworkPinning.apply(
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
    )

    fun bundleDir(context: Context): File = File(context.filesDir, "refsearch").apply { mkdirs() }

    fun installedBundle(context: Context): RefBundle? {
        val f = File(bundleDir(context), "refsearch.json")
        if (!f.exists()) return null
        return try {
            parseBundle(JSONObject(f.readText(Charsets.UTF_8)))
        } catch (_: Exception) {
            null
        }
    }

    fun isInstalled(context: Context): Boolean = installedBundle(context) != null

    /**
     * Starts the bundle download/update unless one is already running
     * (same contract as ConceptsPackManager: a refresh never cancels
     * an in-flight download; the result lands on [state]).
     */
    fun startDownload(context: Context) {
        if (_state.value.downloading) return
        val appContext = context.applicationContext
        _state.update { it.copy(downloading = true, progress = 0f, failed = false) }
        job = scope.launch {
            val ok = try {
                downloadBundle(appContext) { p ->
                    _state.update { st -> st.copy(progress = p) }
                }
            } catch (e: Exception) {
                false
            }
            _state.update { st ->
                st.copy(
                    downloading = false,
                    progress = if (ok) 1f else st.progress,
                    failed = !ok,
                    completedRuns = if (ok) st.completedRuns + 1 else st.completedRuns
                )
            }
        }
    }

    /**
     * Downloads the bundle index and every shard through the gateway,
     * verifying sha256[:16] of the index against the content manifest
     * and of each shard against the index itself. Files land in temp
     * names and are renamed only when complete and verified, and the
     * index is installed LAST — a half-downloaded bundle never reads
     * as installed. Returns false on any failure (nothing is wiped:
     * a previously installed bundle keeps working).
     */
    private suspend fun downloadBundle(context: Context, onProgress: (Float) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            val dir = bundleDir(context)
            // 1) The bundle index, verified against the signed manifest.
            val indexReq = WorkerActivationApi.contentRequest(context, "refsearch.json")
                ?: return@withContext false
            val indexBytes = fetchBytes(indexReq) ?: return@withContext false
            val manifest = WataniRepository(context).fetchManifest().getOrNull()
            val entry = manifest?.files?.get("refsearch.json")
            if (entry != null) {
                if (entry.size != null && entry.size != indexBytes.size.toLong()) return@withContext false
                if (entry.hash != null && entry.hash != sha256Hex16(indexBytes)) return@withContext false
            }
            val bundle = try {
                parseBundle(JSONObject(String(indexBytes, Charsets.UTF_8)))
            } catch (_: Exception) {
                return@withContext false
            }
            // 2) Every shard, verified against the index's own table.
            val shards = bundle.chunkShards + bundle.indexShards
            val totalBytes = shards.sumOf { it.size }.coerceAtLeast(1)
            var doneBytes = 0L
            for (shard in shards) {
                val name = shard.path.substringAfterLast('/')
                val target = File(dir, name)
                if (target.exists() && target.length() == shard.size &&
                    sha256Hex16(target.readBytes()) == shard.hash
                ) {
                    doneBytes += shard.size
                    onProgress(doneBytes.toFloat() / totalBytes.toFloat())
                    continue
                }
                val req = WorkerActivationApi.contentRequest(context, shard.path)
                    ?: return@withContext false
                val bytes = fetchBytes(req) ?: return@withContext false
                if (bytes.size.toLong() != shard.size) return@withContext false
                if (sha256Hex16(bytes) != shard.hash) return@withContext false
                val tmp = File(dir, "$name.tmp")
                tmp.writeBytes(bytes)
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }
                doneBytes += shard.size
                onProgress(doneBytes.toFloat() / totalBytes.toFloat())
            }
            // 3) Install the index last (the install marker).
            val tmpIndex = File(dir, "refsearch.json.tmp")
            tmpIndex.writeBytes(indexBytes)
            val targetIndex = File(dir, "refsearch.json")
            if (!tmpIndex.renameTo(targetIndex)) {
                tmpIndex.copyTo(targetIndex, overwrite = true)
                tmpIndex.delete()
            }
            true
        }

    private fun fetchBytes(request: okhttp3.Request): ByteArray? {
        return try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.bytes()
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Offline weighted search over the installed bundle — the same
     * spec and the same data as the gateway's retrieval. */
    suspend fun offlineSearch(context: Context, query: String, limit: Int = 10): List<RefChunk> =
        withContext(Dispatchers.IO) {
            val bundle = installedBundle(context) ?: return@withContext emptyList()
            val terms = RefAssistantLogic.tokenizeRefQuery(query)
            if (terms.isEmpty() || bundle.totalChunks <= 0) return@withContext emptyList()
            val dir = bundleDir(context)
            val byShard = terms.groupBy { RefAssistantLogic.indexShardOf(it) }
            val entries = ArrayList<Pair<Int, List<Pair<Int, Int>>>>()
            for ((shardNo, shardTerms) in byShard) {
                val shard = bundle.indexShards.getOrNull(shardNo) ?: continue
                val obj = readShardJson(dir, shard) as? JSONObject ?: continue
                for (t in shardTerms) {
                    val e = obj.optJSONArray(t) ?: continue
                    val df = e.optInt(0, 0)
                    val postsArr = e.optJSONArray(1) ?: continue
                    val posts = ArrayList<Pair<Int, Int>>(postsArr.length())
                    for (i in 0 until postsArr.length()) {
                        val p = postsArr.optJSONArray(i) ?: continue
                        posts += p.optInt(0) to p.optInt(1)
                    }
                    entries += df to posts
                }
            }
            val ranked = RefAssistantLogic.scoreCandidates(entries, bundle.totalChunks).take(30)
            if (ranked.isEmpty()) return@withContext emptyList()
            val wanted = ranked.map { it.first }
            val chunks = loadChunks(dir, bundle, wanted)
            wanted.mapNotNull { chunks[it] }.take(limit)
        }

    // Parsed-shard caches (files are immutable per bundle version).
    private val shardJsonCache = HashMap<String, Any>()
    private val chunkCache = HashMap<Int, RefChunk>()

    private fun readShardJson(dir: File, shard: RefShard): Any? {
        shardJsonCache[shard.path]?.let { return it }
        val f = File(dir, shard.path.substringAfterLast('/'))
        if (!f.exists()) return null
        return try {
            val text = GZIPInputStream(f.inputStream().buffered()).use {
                it.readBytes().toString(Charsets.UTF_8)
            }
            val parsed: Any = if (text.startsWith("[")) JSONArray(text) else JSONObject(text)
            shardJsonCache[shard.path] = parsed
            parsed
        } catch (_: Exception) {
            null
        }
    }

    private fun loadChunks(dir: File, bundle: RefBundle, idxs: List<Int>): Map<Int, RefChunk> {
        val out = HashMap<Int, RefChunk>()
        val missing = idxs.filter { it !in chunkCache }
        for ((shardNo, group) in missing.groupBy { RefAssistantLogic.chunkShardOf(it) }) {
            val shard = bundle.chunkShards.getOrNull(shardNo) ?: continue
            val arr = readShardJson(dir, shard) as? JSONArray ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val c = parseChunk(o)
                chunkCache[c.idx] = c
            }
            group.forEach { idx -> chunkCache[idx]?.let { out[idx] = it } }
        }
        for (idx in idxs) {
            if (idx !in out) chunkCache[idx]?.let { out[idx] = it }
        }
        return out
    }

    private fun parseChunk(o: JSONObject): RefChunk = RefChunk(
        idx = o.optInt("i"),
        id = o.optString("id").takeIf { it.isNotBlank() },
        reference = o.optString("ref").takeIf { it.isNotBlank() },
        publisher = o.optString("pub").takeIf { it.isNotBlank() },
        year = if (o.isNull("yr")) null else o.optInt("yr"),
        specialty = o.optString("sp").takeIf { it.isNotBlank() },
        section = o.optString("sec").takeIf { it.isNotBlank() },
        pageStart = if (o.isNull("p0")) null else o.optInt("p0"),
        pageEnd = if (o.isNull("p1")) null else o.optInt("p1"),
        evidenceLevel = o.optString("el").takeIf { it.isNotBlank() },
        recommendationGrade = o.optString("rg").takeIf { it.isNotBlank() },
        text = o.optString("tx")
    )

    private fun parseBundle(root: JSONObject): RefBundle {
        fun shards(arr: JSONArray?): List<RefShard> {
            if (arr == null) return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                RefShard(
                    path = o.optString("path"),
                    size = o.optLong("size"),
                    hash = o.optString("hash"),
                    count = o.optInt("count")
                )
            }
        }
        val refsArr = root.optJSONArray("refs")
        val refs = if (refsArr == null) emptyList() else (0 until refsArr.length()).mapNotNull { i ->
            val o = refsArr.optJSONObject(i) ?: return@mapNotNull null
            RefInfo(
                reference = o.optString("ref").takeIf { it.isNotBlank() },
                publisher = o.optString("pub").takeIf { it.isNotBlank() },
                year = if (o.isNull("yr")) null else o.optInt("yr"),
                specialty = o.optString("sp").takeIf { it.isNotBlank() },
                chunks = o.optInt("chunks")
            )
        }
        return RefBundle(
            version = root.optInt("version", 1),
            totalRefs = root.optInt("totalRefs"),
            totalChunks = root.optInt("totalChunks"),
            chunkShards = shards(root.optJSONArray("chunkShards")),
            indexShards = shards(root.optJSONArray("indexShards")),
            refs = refs
        )
    }

    private fun sha256Hex16(bytes: ByteArray): String {
        val d = MessageDigest.getInstance("SHA-256").digest(bytes)
        return d.joinToString("") { "%02x".format(it) }.substring(0, 16)
    }
}

/** Online side of «الباحث المعتمد»: status + ask on the gateway. */
object RefAssistantApi {

    data class Status(
        val totalRefs: Int,
        val totalChunks: Int,
        val smartAvailable: Boolean
    )

    suspend fun fetchStatus(user: FirebaseUser): Status? {
        val res = WorkerActivationApi.getFromWorker(user, "/v1/assistant/status")
        if (res.code != 200) return null
        val root = try {
            JSONObject(res.body ?: "")
        } catch (_: Exception) {
            return null
        }
        if (!root.optBoolean("ok", false)) return null
        return Status(
            totalRefs = root.optInt("totalRefs"),
            totalChunks = root.optInt("totalChunks"),
            smartAvailable = root.optBoolean("smartAvailable", false)
        )
    }

    /**
     * Asks the gateway. Null means the call itself failed (network,
     * session, server) — the screen then falls back to the offline
     * search over the downloaded bundle instead of showing an error.
     */
    suspend fun ask(user: FirebaseUser, question: String): RefAskResult? {
        val body = JSONObject().put("q", question)
        val res = WorkerActivationApi.postToWorker(user, "/v1/assistant/ask", body)
        if (res.code == 429) return RefAskResult.Failed("rate_limited")
        if (res.code != 200) return null
        val root = try {
            JSONObject(res.body ?: "")
        } catch (_: Exception) {
            return null
        }
        if (!root.optBoolean("ok", false)) return null
        val exhausted = root.optBoolean("smartExhausted", false)
        fun chunksOf(arr: JSONArray?): List<RefChunk> {
            if (arr == null) return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                RefChunk(
                    idx = -1,
                    id = o.optString("chunkId").takeIf { it.isNotBlank() },
                    reference = o.optString("reference").takeIf { it.isNotBlank() },
                    publisher = o.optString("publisher").takeIf { it.isNotBlank() },
                    year = if (o.isNull("year")) null else o.optInt("year"),
                    specialty = o.optString("specialty").takeIf { it.isNotBlank() },
                    section = o.optString("section").takeIf { it.isNotBlank() },
                    pageStart = if (o.isNull("pageStart")) null else o.optInt("pageStart"),
                    pageEnd = if (o.isNull("pageEnd")) null else o.optInt("pageEnd"),
                    evidenceLevel = o.optString("evidenceLevel").takeIf { it.isNotBlank() },
                    recommendationGrade = o.optString("recommendationGrade").takeIf { it.isNotBlank() },
                    text = o.optString("text")
                )
            }
        }
        return when (root.optString("mode")) {
            "smart" -> RefAskResult.Smart(
                answer = root.optString("answer"),
                sources = chunksOf(root.optJSONArray("sources")),
                smartExhausted = exhausted
            )
            "text" -> RefAskResult.TextResults(
                chunks = chunksOf(root.optJSONArray("chunks")),
                smartExhausted = exhausted,
                offline = false
            )
            "none" -> RefAskResult.NotInRefs(smartExhausted = exhausted)
            else -> null
        }
    }
}
