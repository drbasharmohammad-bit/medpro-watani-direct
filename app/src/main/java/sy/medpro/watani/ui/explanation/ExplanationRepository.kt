package sy.medpro.watani.ui.explanation

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import sy.medpro.watani.data.SectionCacheCrypto
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
 *
 * Phase-2b hardening (Nov 2026 — cache completion): the on-disk cache in
 * `filesDir/explanation_layers` (index.json + hashed shard files) is
 * RESTRICTED content and is now stored AES-GCM encrypted through the
 * same central point as sections_cache ([SectionCacheCrypto] over
 * security/ContentEncryption, same Keystore key and byte format), under
 * the UNCHANGED file names — so the revocation wipe in
 * data/RestrictedInventory (whole-directory delete) works exactly as
 * before. Writes happen only after the existing parse validation
 * (index parsed / shard records non-empty), reads go through the
 * central decrypting read, and a legacy plaintext cache file is
 * migrated to encrypted form on first read (shard JSON starts with
 * '[', index JSON with '{' — both pass SectionCacheCrypto's legacy
 * check). The only plaintext left in the directory is the resumable
 * download temp (`<hash>.tmp`): Range resume appends raw bytes, so it
 * cannot be ciphertext — the exact same discipline as the
 * sections_cache download temp. It is deleted the moment the full
 * shard is persisted encrypted, and the whole directory (temps
 * included) is wiped on activation revocation.
 *
 * G1 fix (build 1.2.21 — shard cache never invalidated): a cached shard
 * used to be served forever once it parsed, because the index entry's
 * hash/size were never read. Now the fingerprint the CURRENT index
 * declares for a shard (`files[shard].hash` + `.size`) is stored next
 * to its cache (a small `<hash>.fp` sidecar, metadata only — no
 * content) at persist time, and a cached shard — in memory or on
 * disk — is served ONLY when its stored fingerprint still matches
 * the current index entry. A mismatch, or a legacy cache with no
 * stored fingerprint while the index declares one, drops the cache
 * and re-downloads through the same resumable path. When the index
 * entry itself carries no hash/size there is nothing to match
 * against, so the previous serve-from-cache behaviour is kept for
 * that shard. The absent-layer contract is untouched: a 404 index
 * still reads as silent "no explanation".
 */
/**
 * F-09: carries the HTTP status of a failed explanation-index fetch so
 * callers can distinguish a permanently absent layer (404 — the layer
 * was retired server-side and creates no obligation) from a transient
 * failure (network error / 5xx / parse error — a real incompleteness
 * that must surface, never be swallowed as success).
 */
class ExplanationIndexException(val httpCode: Int?, message: String) : Exception(message)

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

    private val client = sy.medpro.watani.security.NetworkPinning.apply(
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.NONE })
    ).build()

    /** In-memory cache of shard contents for the current process lifetime, keyed by shard path. */
    private val shardCache = mutableMapOf<String, List<ExplanationRecordDto>>()

    /** G1: the index fingerprint each in-memory cached shard was loaded
     * under (see [indexFingerprintOf]); a shard whose current index
     * fingerprint no longer matches is evicted instead of served. */
    private val shardCacheFingerprints = mutableMapOf<String, String?>()

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

    /**
     * Bundle rule (Oct 2026): a section download is not complete until its
     * explanations are on the device too — downloading questions and leaving
     * their explanations behind is not a complete download. After a section's
     * questions land, the caller passes their normalized keys here; only the
     * shards that actually hold those questions are fetched (via the same
     * resumable, cached [loadShard] path as lazy lookup), so a section's
     * explanations work offline without re-downloading the whole layer.
     * Keys with no qid_index entry have no explanation in the layer and are
     * skipped (a content gap, not a download failure). Any needed shard
     * failing to load fails the whole prefetch so the caller reports the
     * section as incomplete instead of silently succeeding.
     */
    suspend fun prefetchForQuestionIds(questionIds: List<String>): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val keys = questionIds.mapNotNull { normalizeExplanationKey(it) }.distinct()
                if (keys.isEmpty()) return@withContext Result.success(Unit)
                val indexResult = fetchLayerIndex()
                if (indexResult.isFailure) {
                    // F-09 (refines the Nov 2026 unified rule): only a
                    // PERMANENTLY ABSENT layer (HTTP 404 — the old layer
                    // was retired server-side) creates no prefetch
                    // obligation. A transient index failure (network
                    // error, 5xx, parse failure) is a genuine
                    // incompleteness: the prefetch FAILS so the caller
                    // keeps the "explanations incomplete" banner + retry
                    // for sections that really depend on the layer. The
                    // 74272 structural rule is untouched: questions with
                    // a non-blank embedded explanation never reach this
                    // function at all (MainViewModel filters them out),
                    // so the unified bank can never show the banner.
                    val cause = indexResult.exceptionOrNull()
                    val httpCode = (cause as? ExplanationIndexException)?.httpCode
                    return@withContext if (httpCode == 404) {
                        Result.success(Unit)
                    } else {
                        Result.failure(cause ?: Exception("تعذر تحميل فهرس الشروحات"))
                    }
                }
                val index = indexResult.getOrNull() ?: return@withContext Result.success(Unit)
                val qidIndex = index.qidIndex ?: return@withContext Result.success(Unit)
                val shards = keys.mapNotNull { qidIndex[it] }.distinct()
                for (shardName in shards) {
                    val shardResult = loadShard(shardName)
                    if (shardResult.isFailure) {
                        return@withContext Result.failure(shardResult.exceptionOrNull()!!)
                    }
                }
                Result.success(Unit)
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
                        ?: Result.failure(ExplanationIndexException(resp.code, "تعذر تحميل فهرس الشروحات"))
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
                    // Phase-2b: stored ENCRYPTED via the central point,
                    // after the parse validation just above (verified
                    // plaintext only — ciphertext is never validated).
                    SectionCacheCrypto.writeText(context, diskFile, text)
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
            // Phase-2b: central decrypting read — a legacy plaintext
            // index.json is migrated to encrypted form on this read.
            val text = SectionCacheCrypto.readText(context, diskFile) ?: return null
            val parsed = indexAdapter.fromJson(text)
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

    /**
     * G1: the fingerprint the current layer index declares for
     * [shardName] — its entry's `hash` and `size` in
     * `explanations/index.json` — or null when there is no index in
     * memory yet, no entry for the shard, or the entry carries neither
     * field (nothing to match a cache against). Callers always fetch
     * the index before loading shards, so the in-memory index is the
     * current one.
     */
    private fun indexFingerprintOf(shardName: String): String? {
        val entry = cachedIndex?.files?.get(shardName) ?: return null
        val hash = entry.hash?.trim()?.takeIf { it.isNotEmpty() }
        val size = entry.size
        if (hash == null && size == null) return null
        return "${hash.orEmpty()}|${size ?: -1L}"
    }

    private fun shardFingerprintFile(shardName: String): File =
        File(explanationDir(), "${shardHash(shardName)}.fp")

    /** The fingerprint stored next to a shard's disk cache at persist
     * time; null for a legacy cache written before the G1 fix. */
    private fun readStoredShardFingerprint(shardName: String): String? =
        try {
            val file = shardFingerprintFile(shardName)
            if (!file.exists()) null
            else file.readText(Charsets.UTF_8).trim().takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }

    private fun storeShardFingerprint(shardName: String, fingerprint: String?) {
        try {
            val file = shardFingerprintFile(shardName)
            if (fingerprint == null) {
                if (file.exists()) file.delete()
            } else {
                file.writeText(fingerprint, Charsets.UTF_8)
            }
        } catch (_: Exception) {
        }
    }

    private suspend fun loadShard(shardName: String): Result<List<ExplanationRecordDto>> {
        // G1: every cached copy of this shard — memory or disk — is
        // served only while its stored fingerprint matches the one the
        // current index declares; otherwise it is dropped and the
        // shard is re-downloaded below.
        val expectedFingerprint = indexFingerprintOf(shardName)
        shardCache[shardName]?.let { cached ->
            if (expectedFingerprint == null ||
                shardCacheFingerprints[shardName] == expectedFingerprint
            ) {
                return Result.success(cached)
            }
            shardCache.remove(shardName)
            shardCacheFingerprints.remove(shardName)
        }
        cleanupLegacyCache()

        val cacheFile = shardCacheFile(shardName)
        if (cacheFile.exists()) {
            val storedFingerprint = readStoredShardFingerprint(shardName)
            if (expectedFingerprint != null && storedFingerprint != expectedFingerprint) {
                // Stale shard (republished under the same name) or a
                // legacy cache with no fingerprint on record: never
                // serve it — drop it and fall through to the download.
                cacheFile.delete()
                storeShardFingerprint(shardName, null)
            } else {
                try {
                    // Phase-2b: central decrypting read — a legacy plaintext
                    // shard is migrated to encrypted form on this read.
                    val cachedText = SectionCacheCrypto.readText(context, cacheFile)
                    val records = if (cachedText == null) emptyList() else parseShard(cachedText)
                    if (records.isNotEmpty()) {
                        shardCache[shardName] = records
                        shardCacheFingerprints[shardName] = storedFingerprint ?: expectedFingerprint
                        return Result.success(records)
                    }
                    // Empty parse of a non-empty file = corrupt cache: drop it
                    // and re-download instead of serving a false empty shard.
                    cacheFile.delete()
                } catch (_: Exception) {
                    cacheFile.delete()
                }
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
        // Audit fix (21 Nov 2026, SEC-C1): verify the downloaded BYTES
        // against the index entry before anything is persisted (see
        // shardBytesMismatch). A mismatch also deletes the resume
        // temp: a corrupt partial must never be resumed into a
        // "complete" file.
        val mismatch = shardBytesMismatch(shardName, text)
        if (mismatch != null) {
            try { shardTempFile(shardName).delete() } catch (_: Exception) { }
            return Result.failure(Exception(mismatch))
        }
        persistShardCache(shardName, text)
        shardCache[shardName] = records
        shardCacheFingerprints[shardName] = expectedFingerprint
        return Result.success(records)
    }

    /** Audit fix (21 Nov 2026, SEC-C1): verifies the downloaded
     * shard BYTES against the index entry (size + sha256[:16] — the
     * exact contract SectionVerification enforces for section
     * downloads, and the format the explanations pipeline
     * publishes). Returns the mismatch message, or null when the
     * bytes verify or the entry declares nothing to verify against.
     * Before this fix the G1 fingerprint only ever compared the
     * index's declared values against a stored copy of those same
     * declared values — the bytes themselves were never hashed, so
     * a substituted body with a parseable shape passed. */
    private fun shardBytesMismatch(shardName: String, text: String): String? {
        val entry = cachedIndex?.files?.get(shardName) ?: return null
        if (entry.hash.isNullOrBlank() && entry.size == null) return null
        return sy.medpro.watani.data.SectionVerification.manifestMismatch(
            "explanations/$shardName",
            text.toByteArray(Charsets.UTF_8),
            entry.size,
            entry.hash
        )
    }

    /**
     * Resumable shard download: sends Range from the kept partial file
     * (the gateway serves 206 from its cache) and KEEPS the partial file
     * on failure, so the next attempt resumes instead of restarting an
     * 8MB shard from zero on a slow connection.
     *
     * Phase-2b note: the temp is a raw-byte resume buffer and therefore
     * stays plaintext while a download is in flight / awaiting resume —
     * Range appends cannot target GCM ciphertext. This is the same
     * accepted discipline as the sections_cache download temp; the
     * PERSISTED shard is always encrypted (see [persistShardCache]),
     * the temp is deleted on persist, and RestrictedInventory wipes
     * the whole explanation_layers directory (temps included) on
     * activation revocation.
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
            // Phase-2b: ENCRYPTED via the central point. The caller
            // (loadShard) has already validated the text — it parsed
            // to a non-empty record list — so only verified plaintext
            // is ever encrypted, exactly like sections_cache.
            SectionCacheCrypto.writeText(context, file, text)
            // G1: record the fingerprint this copy was downloaded
            // under, next to the cache — loadShard serves the cache
            // only while the current index still declares it.
            storeShardFingerprint(shardName, indexFingerprintOf(shardName))
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
            // G1: the ".fp" fingerprint sidecars are part of the cache
            // and survive the sweep alongside the shard files.
            val keep = Regex("^[0-9a-f]{16}\\.(json|tmp|fp)$")
            // Phase-2b: the encrypted store writes atomically via a
            // "<name>.enc.tmp" sibling (ContentEncryption) — never let
            // this sweep delete an in-flight encrypted write.
            val keepEncTmp = Regex("^[0-9a-f]{16}\\.json\\.enc\\.tmp$")
            dir.listFiles()?.forEach { f ->
                if (f.name == "index.json" || f.name == "index.json.enc.tmp") return@forEach
                if (!keep.matches(f.name) && !keepEncTmp.matches(f.name)) {
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
