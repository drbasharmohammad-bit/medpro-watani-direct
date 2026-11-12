package sy.medpro.watani.data

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.text.Charsets

class WataniRepository(private val context: Context) {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // A2: Remove body-level HttpLoggingInterceptor from shared OkHttp client (Level.NONE)
    // Phase-2 hardening: SPKI pinning for the content/gateway hosts.
    private val client = sy.medpro.watani.security.NetworkPinning.apply(
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.NONE
            })
    ).build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(WataniApi.BASE_URL)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val api = retrofit.create(WataniApi::class.java)

    private val prefs = context.getSharedPreferences("watani_downloaded_sections", Context.MODE_PRIVATE)
    private val _downloadedSectionsFlow = MutableStateFlow<List<DownloadedSectionEntity>>(loadDownloadedSections())
    val downloadedSectionsFlow: Flow<List<DownloadedSectionEntity>> = _downloadedSectionsFlow.asStateFlow()

    private fun loadDownloadedSections(): List<DownloadedSectionEntity> {
        val allEntries = prefs.all
        val list = mutableListOf<DownloadedSectionEntity>()
        for ((path, value) in allEntries) {
            if (value is String) {
                val parts = value.split("|")
                if (parts.size >= 4) {
                    list.add(
                        DownloadedSectionEntity(
                            sectionPath = path,
                            version = parts[0],
                            hash = parts[1],
                            count = parts[2].toIntOrNull() ?: 0,
                            downloadedAt = parts[3].toLongOrNull() ?: 0L
                        )
                    )
                }
            }
        }
        return list
    }

    private fun saveDownloadedSection(entity: DownloadedSectionEntity) {
        val value = "${entity.version}|${entity.hash}|${entity.count}|${entity.downloadedAt}"
        prefs.edit().putString(entity.sectionPath, value).apply()
        _downloadedSectionsFlow.value = loadDownloadedSections()
    }

    /** Reloads the downloaded-sections flow from prefs (after an
     * external wipe, e.g. sign-out or deactivation from another screen). */
    fun refreshDownloadedSections() {
        _downloadedSectionsFlow.value = loadDownloadedSections()
    }

    /**
     * Gateway 401/403 handler (M3/M4): clears the activation, wipes
     * restricted downloads and returns the typed revocation failure.
     * Callers must surface [ActivationRevokedException] unwrapped (the
     * MainActivity prompt event fires from [WorkerActivationApi] itself).
     */
    private fun gatewayAuthFailure(context: Context): ActivationRevokedException {
        WorkerActivationApi.notifyActivationLost(context)
        refreshDownloadedSections()
        return ActivationRevokedException("انتهى التفعيل على هذا الجهاز — أعد إدخال كود التفعيل.")
    }

    /**
     * Offline-activation fix (Nov 2026, doctor's order): the manifest is
     * a FREE path (fetched directly from Pages, never through the
     * gateway), so a copy is cached on disk. An activated account must
     * keep seeing its sections offline — without a cache, every cold
     * start offline failed the fetch and HomeScreen showed only a
     * "connection error" wall even though the sections were downloaded.
     * On any fetch failure the cached manifest is served instead; only
     * when there is no cache at all does the failure reach the UI (and
     * MainViewModel then falls back to the downloaded-sections registry).
     * The cache never grants access by itself: restricted tabs and quiz
     * entry still re-check the local activation, and restricted fetches
     * still go through the gateway.
     */
    private fun manifestCacheFile(): File = File(context.filesDir, MANIFEST_CACHE_FILENAME)

    companion object {
        /** Disk file of the manifest cache (see manifestCacheFile()).
         * Deleted by [RestrictedInventory.wipeRestrictedDownloads] on a
         * proven revocation: a stale copy would keep listing restricted
         * sections after the activation ended. */
        const val MANIFEST_CACHE_FILENAME = "manifest_cache.json"
    }

    private fun saveManifestCache(manifest: ManifestResponse) {
        try {
            // Phase-2b hardening: the disk copy lists restricted
            // sections and is already wiped on revocation
            // (RestrictedInventory) — it is now also stored ENCRYPTED
            // through the central point, after serialization of the
            // already-fetched (verified) manifest above.
            SectionCacheCrypto.writeText(
                context, manifestCacheFile(),
                moshi.adapter(ManifestResponse::class.java).toJson(manifest)
            )
        } catch (_: Exception) {
            // Best-effort: a failed cache write must never fail the fetch.
        }
    }

    private fun loadManifestCache(): ManifestResponse? {
        return try {
            val file = manifestCacheFile()
            if (!file.exists()) return null
            // Phase-2b: central decrypting read — a legacy plaintext
            // manifest_cache.json is migrated to encrypted form here.
            val text = SectionCacheCrypto.readText(context, file) ?: return null
            moshi.adapter(ManifestResponse::class.java).fromJson(text)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchManifest(): Result<ManifestResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.getManifest()
            if (response.isSuccessful && response.body() != null) {
                val manifest = response.body()!!
                saveManifestCache(manifest)
                Result.success(manifest)
            } else {
                loadManifestCache()?.let { return@withContext Result.success(it) }
                Result.failure(Exception("تعذر جلب الفهرس — تحقق من الاتصال وحاول مجددًا."))
            }
        } catch (e: Exception) {
            loadManifestCache()?.let { return@withContext Result.success(it) }
            Result.failure(Exception("تعذر الاتصال بخادم الفهرس — تحقق من الشبكة وحاول مجددًا.", e))
        }
    }

    private fun getSectionFile(path: String): File {
        val dir = File(context.filesDir, "sections_cache")
        if (!dir.exists()) dir.mkdirs()
        val sanitized = path.replace('/', '_').replace('\\', '_')
        return File(dir, "$sanitized.json")
    }

    /**
     * Silent best-effort refresh of an already-downloaded section
     * (build 1.2.6): the self-loading pack screens call this AFTER a
     * local copy loaded successfully, so a republished pack reaches
     * the device without the user revisiting the sections manager.
     *
     * Contract: returns true only when a newer file was downloaded
     * and installed (the caller then re-reads the pack from disk).
     * ANY failure — offline, manifest fetch failure, stale cached
     * manifest, download/verification failure — returns false and
     * leaves the local copy untouched; this function never throws,
     * so callers can invoke it inside their existing load flow with
     * no error surfacing and no UI flicker.
     */
    suspend fun refreshSectionIfStale(path: String): Boolean {
        return try {
            val manifest = fetchManifest().getOrNull() ?: return false
            val entry = manifest.files?.get(path) ?: return false
            val entity = _downloadedSectionsFlow.value
                .firstOrNull { it.sectionPath == path }
            if (!sectionNeedsRefresh(entity, entry)) return false
            downloadSection(path, entry).isSuccess
        } catch (e: Exception) {
            false
        }
    }

    // ------------------------------------------------------------------
    // Phase-2 hardening (Nov 2026): encrypted sections_cache.
    // THE central read/write point for section files — every getter
    // below reads through [readSectionText] and every save goes
    // through [writeSectionBytes]/[writeSectionText], which store
    // AES-GCM ciphertext (see SectionCacheCrypto). Legacy plaintext
    // files are migrated to encrypted form on first read there.
    // ------------------------------------------------------------------

    private fun readSectionText(path: String): String? =
        SectionCacheCrypto.readText(context, getSectionFile(path))

    private fun writeSectionBytes(path: String, bytes: ByteArray): Boolean =
        SectionCacheCrypto.writeBytes(context, getSectionFile(path), bytes)

    private fun writeSectionText(path: String, text: String): Boolean =
        SectionCacheCrypto.writeText(context, getSectionFile(path), text)

    suspend fun downloadSection(
        path: String,
        entry: ManifestFileEntry,
        onProgress: (Float) -> Unit = {}
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            // M8: device-integrity gate at the restricted-download entry.
            // Blocked devices never reach the gateway (and never get a
            // partial file to keep).
            if (WorkerActivationApi.isRestrictedPath(path)) {
                val gate = sy.medpro.watani.ui.security.RestrictedContentGate.check(context)
                if (!gate.allowed) {
                    return@withContext Result.failure(Exception(gate.userMessage))
                }
            }
            val file = getSectionFile(path)
            val tempFile = File(file.parentFile, "${file.name}.tmp")

            val existingBytes = if (tempFile.exists()) tempFile.length() else 0L
            // THE fetch-layer switch point (Team 1): restricted paths
            // (questions/, dawrat/, dawrat_jami3at/, uworld/,
            // explanations/, books/, library.json) are fetched via the
            // Worker gateway with the activation token; free paths keep
            // fetching directly from Pages, unchanged. Null = restricted
            // but no usable token -> activation prompt, never a direct fetch.
            val baseRequest = WorkerActivationApi.contentRequest(context, path)
                ?: return@withContext Result.failure(
                    Exception("هذا القسم يحتاج كود تفعيل — فعّل حسابك من نافذة التفعيل أولاً.")
                )
            val requestBuilder = baseRequest.newBuilder()
            if (existingBytes > 0) {
                requestBuilder.header("Range", "bytes=$existingBytes-")
            }

            var resp = client.newCall(requestBuilder.build()).execute()
            if (resp.code == 416) {
                // Detector fix (b5): a stale Range (temp longer than a
                // shrunk origin file) must restart from zero, not loop
                // on the same bad Range forever (the gateway now passes
                // 416 through instead of masking it as 502).
                if (tempFile.exists()) tempFile.delete()
                resp.close()
                resp = client.newCall(baseRequest).execute()
            }
            // F-13: the response and both streams are closed on EVERY
            // exit path from here on (use{}), including the early
            // 401/403 and HTTP-failure returns below — an interrupted
            // download on a weak network must not leak connections or
            // leave the resume temp file unflushed.
            resp.use {
            if (resp.code == 401 || resp.code == 403) {
                // M3: no generic HTTP message, no cached fallback —
                // activation is lost (cleared + wiped + prompt event).
                return@withContext Result.failure(gatewayAuthFailure(context))
            }
            if (!resp.isSuccessful && resp.code != 206) {
                return@withContext Result.failure(Exception("فشل التحميل — تأكد من اتصال الإنترنت أو صحة الرابط."))
            }

            // A1: Resume safety - if server answered HTTP 200 instead of 206 with full body, discard partial temp file
            val isPartial = (resp.code == 206)
            val actualExistingBytes = if (isPartial) existingBytes else {
                if (tempFile.exists()) tempFile.delete()
                0L
            }

            val body = resp.body ?: return@withContext Result.failure(Exception("استجابة فارغة من الخادم."))
            val contentLength = body.contentLength()
            val totalBytes = if (contentLength > 0) contentLength + actualExistingBytes else (entry.size ?: 1L)

            body.byteStream().use { inputStream ->
                java.io.FileOutputStream(tempFile, actualExistingBytes > 0).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesCopied = actualExistingBytes
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                        bytesCopied += read
                        val progress = (bytesCopied.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        onProgress(progress)
                    }
                    outputStream.flush()
                }
            }
            } // resp.use (F-13)

            // Phase-2 hardening (Nov 2026) — verification order is
            // UNCHANGED, only the final persisted form changed:
            // F-01/F-02 verification still runs on the PLAINTEXT bytes
            // (the downloaded temp file), BEFORE anything is saved;
            // the section is then stored ENCRYPTED (SectionCacheCrypto)
            // and the plaintext temp is deleted, so no plaintext
            // question JSON ever persists in sections_cache. The old
            // working copy is replaced only by the checked encrypted
            // write (F-07 spirit): a failed verification or a failed
            // encryption leaves the previously installed copy untouched.
            val installedBytes = tempFile.readBytes()
            val mismatch = SectionVerification.manifestMismatch(
                path, installedBytes, entry.size, entry.hash
            )
            if (mismatch != null) {
                tempFile.delete()
                return@withContext Result.failure(Exception(mismatch))
            }
            val computedHash = SectionVerification.manifestHashHex(path, installedBytes)

            // F-02/F-03/F-04: content verification (pure core in
            // SectionVerification): real parse + honest declared-count
            // comparison, no coerced counts anywhere.
            val jsonString = String(installedBytes, Charsets.UTF_8)
            val actualCount = try {
                SectionVerification.verifySectionCount(path, jsonString, moshi)
            } catch (e: IllegalStateException) {
                tempFile.delete()
                return@withContext Result.failure(Exception(e.message))
            }

            if (!writeSectionBytes(path, installedBytes)) {
                tempFile.delete()
                return@withContext Result.failure(Exception("تعذر حفظ ملف القسم على الجهاز — أعد المحاولة."))
            }
            tempFile.delete()

            // F-01: the registry stores the hash COMPUTED from the
            // verified plaintext (never a verbatim copy of entry.hash),
            // so isSectionUpToDate compares two independently derived
            // values and a swapped/corrupt file cannot look current.
            val entity = DownloadedSectionEntity(
                sectionPath = path,
                version = entry.version ?: "1.0",
                hash = computedHash,
                count = actualCount,
                downloadedAt = System.currentTimeMillis()
            )
            saveDownloadedSection(entity)
            // Gentle lease: a successful restricted download is a
            // successful server verification.
            if (WorkerActivationApi.isRestrictedPath(path)) {
                WorkerActivationApi.noteServerVerified(context)
            }
            Result.success(actualCount)
        } catch (e: Exception) {
            // Typed revocation passes through unwrapped (M3): callers
            // distinguish it from ordinary download failures.
            if (e is ActivationRevokedException) {
                Result.failure(e)
            } else {
                Result.failure(Exception("تعذر التحميل — تحقق من اتصال الإنترنت وحاول مجددًا.", e))
            }
        }
    }

    suspend fun getSubjectEnvelope(path: String): SubjectEnvelope? = withContext(Dispatchers.IO) {
        try {
            // Gentle lease: an expired lease makes downloaded
            // restricted sections behave as unavailable (free paths
            // are never gated here).
            if (WorkerActivationApi.isRestrictedPath(path) &&
                WorkerActivationApi.isRestrictedLeaseExpired(context)
            ) return@withContext null
            val jsonString = readSectionText(path) ?: return@withContext null
            moshi.adapter(SubjectEnvelope::class.java).fromJson(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getCasesEnvelope(): CasesEnvelope? = withContext(Dispatchers.IO) {
        try {
            val jsonString = readSectionText("cases.json") ?: return@withContext null
            moshi.adapter(CasesEnvelope::class.java).fromJson(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    // الحالات الألمانية (نقل 9 نوفمبر): نفس غلاف الحالات تمامًا، ملف مستقل.
    suspend fun getCasesDeEnvelope(): CasesEnvelope? = withContext(Dispatchers.IO) {
        try {
            val jsonString = readSectionText("cases_de.json") ?: return@withContext null
            moshi.adapter(CasesEnvelope::class.java).fromJson(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getGuideEnvelope(): GuideEnvelope? = withContext(Dispatchers.IO) {
        try {
            val jsonString = readSectionText("guide.json") ?: return@withContext null
            moshi.adapter(GuideEnvelope::class.java).fromJson(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getOsceEnvelope(): OsceEnvelope? = withContext(Dispatchers.IO) {
        try {
            val jsonString = readSectionText("osce.json") ?: return@withContext null
            moshi.adapter(OsceEnvelope::class.java).fromJson(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getAuscultationEnvelope(): AuscultationEnvelope? = withContext(Dispatchers.IO) {
        try {
            val jsonString = readSectionText("auscultation.json") ?: return@withContext null
            moshi.adapter(AuscultationEnvelope::class.java).fromJson(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * ECG mini-course pack (1.2.4) — mirrors [getAuscultationEnvelope]
     * exactly: the downloaded "ecg.json" section file from
     * sections_cache, parsed leniently; null when the pack has not been
     * downloaded yet (the course screen then pulls it through the
     * manifest + downloadSection path, like the auscultation trainer).
     */
    suspend fun getEcgEnvelope(): EcgEnvelope? = withContext(Dispatchers.IO) {
        try {
            // Encrypted sections_cache (phase-2 hardening): read through
            // the central crypto point like every other section getter.
            val jsonString = readSectionText("ecg.json") ?: return@withContext null
            moshi.adapter(EcgEnvelope::class.java).fromJson(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Bundled fallback (assets/ecg_sample.json): a small sample pack so
     * the ECG course opens and teaches even before the pipeline's real
     * ecg.json is published to the manifest. Used ONLY when neither the
     * downloaded pack nor a manifest download is available — the server
     * pack always wins when present.
     */
    suspend fun getBundledEcgEnvelope(): EcgEnvelope? = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.assets.open("ecg_sample.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            moshi.adapter(EcgEnvelope::class.java).fromJson(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getDdxEnvelope(): DdxEnvelope? = withContext(Dispatchers.IO) {
        try {
            val jsonString = readSectionText("ddx.json") ?: return@withContext null
            moshi.adapter(DdxEnvelope::class.java).fromJson(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getReverseEnvelope(): ReverseEnvelope? = withContext(Dispatchers.IO) {
        try {
            val jsonString = readSectionText("reverse.json") ?: return@withContext null
            moshi.adapter(ReverseEnvelope::class.java).fromJson(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * OSCE procedures pack (build 1.2.6): the downloaded
     * "osce_procedures.json" section file from sections_cache — a BARE
     * ARRAY of procedures (see SkillsModels), parsed leniently; null
     * when the pack has not been downloaded yet (the procedures wing
     * then pulls it through the manifest + downloadSection path, like
     * the OSCE stations screen).
     */
    suspend fun getOsceProcedures(): List<ProcedureDto>? = withContext(Dispatchers.IO) {
        try {
            // Encrypted sections_cache (phase-2 hardening): read through
            // the central crypto point like every other section getter.
            val jsonString = readSectionText("osce_procedures.json") ?: return@withContext null
            val listType = com.squareup.moshi.Types.newParameterizedType(
                List::class.java, ProcedureDto::class.java
            )
            moshi.adapter<List<ProcedureDto>>(listType).fromJson(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Bundled fallback (assets/osce_procedures_sample.json): one full
     * sample procedure so the procedures wing opens and teaches even
     * before the pipeline's real pack is published to the manifest.
     * Used ONLY when neither the downloaded pack nor a manifest
     * download is available — the server pack always wins when present.
     */
    suspend fun getBundledOsceProcedures(): List<ProcedureDto>? = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.assets.open("osce_procedures_sample.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val listType = com.squareup.moshi.Types.newParameterizedType(
                List::class.java, ProcedureDto::class.java
            )
            moshi.adapter<List<ProcedureDto>>(listType).fromJson(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Dosing drills pack (build 1.2.6): the downloaded
     * "dosing_drills.json" section file from sections_cache — a BARE
     * ARRAY of drills (see SkillsModels); null when not downloaded yet
     * (the drills screen then pulls it through the manifest +
     * downloadSection path).
     */
    suspend fun getDosingDrills(): List<DosingDrillDto>? = withContext(Dispatchers.IO) {
        try {
            // Encrypted sections_cache (phase-2 hardening): read through
            // the central crypto point like every other section getter.
            val jsonString = readSectionText("dosing_drills.json") ?: return@withContext null
            val listType = com.squareup.moshi.Types.newParameterizedType(
                List::class.java, DosingDrillDto::class.java
            )
            moshi.adapter<List<DosingDrillDto>>(listType).fromJson(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Bundled fallback (assets/dosing_drills_sample.json): two sample
     * drills (adult + pediatric) used ONLY when neither the downloaded
     * pack nor a manifest download is available.
     */
    suspend fun getBundledDosingDrills(): List<DosingDrillDto>? = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.assets.open("dosing_drills_sample.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val listType = com.squareup.moshi.Types.newParameterizedType(
                List::class.java, DosingDrillDto::class.java
            )
            moshi.adapter<List<DosingDrillDto>>(listType).fromJson(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getAuscultationArEnvelope(): AuscultationArEnvelope? = withContext(Dispatchers.IO) {
        try {
            val jsonString = readSectionText("auscultation_ar.json") ?: return@withContext null
            moshi.adapter(AuscultationArEnvelope::class.java).fromJson(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun downloadAuscultationAr(): Result<AuscultationArEnvelope> = withContext(Dispatchers.IO) {
        val file = getSectionFile("auscultation_ar.json")
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        try {
            val fileUrl = "${WataniApi.BASE_URL}auscultation_ar.json"
            val resp = client.newCall(okhttp3.Request.Builder().url(fileUrl).build()).execute()
            if (!resp.isSuccessful) {
                // Auscultation AR layer (Nov 2026): the Arabic
                // description file is an OPTIONAL overlay that is
                // currently absent server-side (404). Its absence is
                // fully silent — no failure is surfaced to the user;
                // the caller treats any non-success as "no Arabic
                // overlay" and the audio material + cache layer are
                // untouched. (Result.failure is kept as the internal
                // signal only; the screen never displays it.)
                resp.close()
                if (tempFile.exists()) tempFile.delete()
                return@withContext Result.failure(Exception("auscultation_ar unavailable (HTTP ${resp.code})"))
            }
            val body = resp.body
                ?: return@withContext Result.failure(Exception("استجابة فارغة من الخادم."))
            body.byteStream().use { inputStream ->
                java.io.FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            val jsonString = tempFile.readText(Charsets.UTF_8)
            val parsed = moshi.adapter(AuscultationArEnvelope::class.java).fromJson(jsonString)
            val clips = parsed?.clips
            if (parsed == null || clips.isNullOrEmpty() || parsed.count != clips.size) {
                if (tempFile.exists()) tempFile.delete()
                return@withContext Result.failure(Exception("محتوى ملف الوصف العربي غير مطابق"))
            }

            // Phase-2 hardening: validated plaintext is stored
            // encrypted through the central section write point.
            if (!writeSectionText("auscultation_ar.json", jsonString)) {
                if (tempFile.exists()) tempFile.delete()
                return@withContext Result.failure(Exception("تعذر حفظ ملف الوصف العربي"))
            }
            if (tempFile.exists()) tempFile.delete()
            Result.success(parsed)
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            Result.failure(Exception("تعذر تحميل الوصف العربي — تحقق من الاتصال وحاول مجددًا.", e))
        }
    }

    fun getAuscultationClipFile(fileName: String): File {
        val dir = File(context.filesDir, "auscultation_clips")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, fileName.replace('/', '_'))
    }

    suspend fun downloadAuscultationClip(fileName: String, expectedSize: Long = 0L, onProgress: (Float) -> Unit = {}): Result<File> = withContext(Dispatchers.IO) {
        val targetFile = getAuscultationClipFile(fileName)
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        try {
            if (targetFile.exists() && targetFile.length() > 0 && (expectedSize <= 0 || targetFile.length() == expectedSize)) {
                return@withContext Result.success(targetFile)
            }
            val fileUrl = "${WataniApi.BASE_URL}$fileName"
            val request = okhttp3.Request.Builder().url(fileUrl).build()
            val resp = client.newCall(request).execute()
            if (!resp.isSuccessful) {
                if (tempFile.exists()) tempFile.delete()
                return@withContext Result.failure(Exception("فشل تحميل ملف الصوت (HTTP ${resp.code})"))
            }
            val body = resp.body ?: return@withContext Result.failure(Exception("استجابة صوتية فارغة"))
            val inputStream = body.byteStream()
            val outputStream = java.io.FileOutputStream(tempFile)
            val buffer = ByteArray(8192)
            var read: Int
            var downloaded = 0L
            val total = body.contentLength().coerceAtLeast(1L)
            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
                downloaded += read
                onProgress((downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f))
            }
            outputStream.close()
            inputStream.close()
            if (expectedSize > 0 && tempFile.length() != expectedSize) {
                if (tempFile.exists()) tempFile.delete()
                return@withContext Result.failure(Exception("حجم الملف غير مطابق للمتوقع"))
            }
            if (tempFile.exists()) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
            }
            Result.success(targetFile)
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            if (targetFile.exists()) targetFile.delete()
            Result.failure(Exception("تعذر تحميل الصوت — تحقق من الاتصال وحاول مجددًا.", e))
        }
    }

    // ---------------- Library (library.json + books/videos) ----------------

    private val libraryAdapter get() = moshi.adapter(LibraryEnvelope::class.java)

    suspend fun getLibraryEnvelope(): LibraryEnvelope? = withContext(Dispatchers.IO) {
        try {
            // Gentle lease: library.json is a restricted file.
            if (WorkerActivationApi.isRestrictedLeaseExpired(context)) return@withContext null
            val text = readSectionText("library.json") ?: return@withContext null
            // Books never surface in-app (see LibraryEnvelope.videosOnly).
            libraryAdapter.fromJson(text)?.videosOnly()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchLibrary(retries: Int = 3): Result<LibraryEnvelope?> = withContext(Dispatchers.IO) {
        // library.json is a restricted path (contract): via the Worker
        // gateway with the activation token. Null = not activated.
        val libraryRequest = WorkerActivationApi.contentRequest(context, "library.json")
            ?: return@withContext Result.failure(Exception("المكتبة تحتاج كود تفعيل — فعّل حسابك أولاً."))
        var lastError: Exception? = null
        for (attempt in 1..retries) {
            try {
                val resp = client.newCall(libraryRequest).execute()
                if (resp.code == 404) {
                    return@withContext Result.success(null)
                }
                if (resp.code == 401 || resp.code == 403) {
                    // M3: revoked activation — no retry, no cached
                    // fallback, prompt event fires from the gateway path.
                    return@withContext Result.failure(gatewayAuthFailure(context))
                }
                if (!resp.isSuccessful) {
                    lastError = Exception("فشل جلب المكتبة — تأكد من اتصال الإنترنت.")
                    if (attempt < retries) {
                        Thread.sleep(700L * attempt)
                        continue
                    }
                    break
                }
                val body = resp.body ?: run {
                    lastError = Exception("استجابة فارغة من الخادم.")
                    null
                } ?: continue
                val text = body.string()
                val parsed = libraryAdapter.fromJson(text)
                    ?: throw Exception("ملف المكتبة غير صالح")
                // Phase-2 hardening: the index is verified (parsed)
                // on plaintext above, then stored ENCRYPTED through
                // the central section write point. F-07 spirit kept:
                // a failed save surfaces as a failure, never as
                // success, and the old copy stays as fallback.
                if (!writeSectionText("library.json", text)) {
                    throw Exception("تعذر حفظ ملف المكتبة على الجهاز")
                }
                WorkerActivationApi.noteServerVerified(context)
                // Books never surface in-app (see LibraryEnvelope.videosOnly);
                // the stored copy stays complete — only the returned
                // envelope is filtered.
                return@withContext Result.success(parsed.videosOnly())
            } catch (e: Exception) {
                lastError = e
                if (attempt < retries) Thread.sleep(700L * attempt)
            }
        }
        // Offline fallback: if we have a cached copy, serve it rather than failing.
        val cached = getLibraryEnvelope()
        if (cached != null) return@withContext Result.success(cached)
        Result.failure(Exception("تعذر تحميل المكتبة — تحقق من الاتصال وحاول مجددًا.", lastError))
    }

    fun getLibraryFile(item: LibraryItem): File {
        val dir = File(context.filesDir, "library_files_plain")
        if (!dir.exists()) dir.mkdirs()
        val rawName = item.id ?: item.fileUrl?.substringAfterLast('/') ?: "item"
        val safe = rawName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val ext = item.fileUrl?.substringAfterLast('.', "")?.takeIf { it.length in 1..5 && it.none { c -> !c.isLetterOrDigit() } }
        val name = if (ext != null && !safe.endsWith(".$ext")) "$safe.$ext" else safe
        return File(dir, name)
    }

    /** Plain side-car SRT file for a video, next to its plain file.
     * Paired strictly by the same item (same base name), never by guesswork:
     * the SRT belongs to this item's id/file only. Stored in the app's
     * private filesDir (not external storage) and deleted with the item.
     * LEGACY layout for HLS items (pre-phase-3): new HLS downloads keep
     * the SRT encrypted inside the encrypted tree (see
     * [getLibraryEncryptedSubtitleFile]); this path remains for the
     * non-HLS flow and for migration/deletion of legacy copies. */
    fun getLibrarySubtitleFile(item: LibraryItem): File {
        val plain = getLibraryFile(item)
        return File(plain.parentFile, "${plain.nameWithoutExtension}.srt")
    }

    private fun libraryItemSafeName(item: LibraryItem): String {
        val rawName = item.id ?: item.fileUrl?.substringAfterLast('/')?.substringBefore('?') ?: "item"
        return rawName.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    /** Encrypted SRT side-car of an HLS item, inside its encrypted tree:
     * same file name as the legacy plain side-car, but the CONTENT is in
     * [sy.medpro.watani.security.ContentEncryption] format (phase-3 task
     * decision: SRT side-cars are encrypted too, closing the documented
     * U3 gap for HLS items). */
    fun getLibraryEncryptedSubtitleFile(item: LibraryItem): File =
        File(getLibraryHlsEncDir(item), "${libraryItemSafeName(item)}.srt")

    /** Cache temp the encrypted SRT is decrypted into at presentation
     * time only. Name carries the legacy `decrypt_temp_` prefix, so the
     * app-close sweep (TempDecryptedFiles) collects it even after a
     * process kill; [closeLibraryItem] deletes it deterministically. */
    private fun getLibrarySubtitleTempFile(item: LibraryItem): File {
        val file = File(context.cacheDir, "decrypt_temp_${libraryItemSafeName(item)}_srt.srt")
        sy.medpro.watani.ui.security.TempDecryptedFiles.track(file)
        return file
    }

    fun getLibrarySubtitleFileIfExists(item: LibraryItem): File? {
        if (isHlsItem(item)) {
            val enc = getLibraryEncryptedSubtitleFile(item)
            if (enc.exists()) {
                // Decrypted ONLY when presented to the player (§4.2
                // principle, same as the playlists): a small text file
                // in cacheDir, never a plaintext copy in filesDir.
                val bytes = sy.medpro.watani.security.ContentEncryption
                    .decryptFileToBytes(enc) ?: return null
                val temp = getLibrarySubtitleTempFile(item)
                return try {
                    temp.writeBytes(bytes)
                    temp
                } catch (_: Exception) {
                    null
                }
            }
        }
        val f = getLibrarySubtitleFile(item)
        return if (f.exists()) f else null
    }

    /** HLS items: fileUrl ends with .m3u8. The offline copy is the whole
     * playlist tree (master + variants + segments) in one directory, so a
     * local master playlist plays offline with its relative segments. */
    fun isHlsItem(item: LibraryItem): Boolean =
        item.fileUrl?.substringBefore('?')?.endsWith(".m3u8", ignoreCase = true) == true

    /** LEGACY plain HLS tree dir (pre-phase-3 downloads). Kept only so
     * migration and deletion can find old trees; nothing new is written
     * here for HLS items. */
    fun getLibraryHlsDir(item: LibraryItem): File {
        val dir = File(context.filesDir, "library_files_plain")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, libraryItemSafeName(item))
    }

    fun getLibraryHlsMasterFile(item: LibraryItem): File {
        val name = item.fileUrl?.substringAfterLast('/')?.substringBefore('?')?.takeIf { it.isNotBlank() } ?: "master.m3u8"
        return File(getLibraryHlsDir(item), name)
    }

    /** Phase-3 (§4.2): the encrypted HLS tree root for an item —
     * `filesDir/library_hls_enc/<itemId>/`, same relative file names as
     * the origin tree, every file's content in ContentEncryption format,
     * plus a small `hls_meta.json` (names + PLAINTEXT sizes) that is the
     * integrity basis replacing the old whole-tree byte-sum check. */
    fun getLibraryHlsEncDir(item: LibraryItem): File {
        val root = File(context.filesDir, "library_hls_enc")
        if (!root.exists()) root.mkdirs()
        return File(root, libraryItemSafeName(item))
    }

    fun getLibraryHlsEncMasterFile(item: LibraryItem): File {
        val name = item.fileUrl?.substringAfterLast('/')?.substringBefore('?')?.takeIf { it.isNotBlank() } ?: "master.m3u8"
        return File(getLibraryHlsEncDir(item), name)
    }

    private fun hlsMetaFile(dir: File): File = File(dir, "hls_meta.json")

    /** Reads `hls_meta.json`: file name -> plaintext size. Null when the
     * meta file is missing or unparseable (tree treated as absent). */
    private fun readHlsMeta(dir: File): Map<String, Long>? {
        val meta = hlsMetaFile(dir)
        if (!meta.exists()) return null
        return try {
            val filesJson = org.json.JSONObject(meta.readText(Charsets.UTF_8))
                .getJSONObject("files")
            val out = mutableMapOf<String, Long>()
            val keys = filesJson.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                out[k] = filesJson.getLong(k)
            }
            out
        } catch (_: Exception) {
            null
        }
    }

    private fun writeHlsMeta(dir: File, files: Map<String, Long>): Boolean {
        return try {
            val filesJson = org.json.JSONObject()
            var total = 0L
            for ((name, size) in files) {
                filesJson.put(name, size)
                total += size
            }
            val root = org.json.JSONObject()
                .put("plainTotal", total)
                .put("files", filesJson)
            hlsMetaFile(dir).writeText(root.toString())
            true
        } catch (_: Exception) {
            false
        }
    }

    /** §4.3 downloaded check for the encrypted tree: meta present, the
     * master listed, and every listed file present at exactly
     * plaintext size + 28 (12-byte IV + 16-byte GCM tag). A segment that
     * fails the GCM tag later, at open, fails playback explicitly and
     * the tree is purged there — the same level the book flow uses
     * (size at rest, header/tag verified at open). */
    fun isHlsEncTreeValid(item: LibraryItem): Boolean {
        val dir = getLibraryHlsEncDir(item)
        if (!dir.exists()) return false
        val meta = readHlsMeta(dir) ?: return false
        val masterName = getLibraryHlsEncMasterFile(item).name
        if (!meta.containsKey(masterName)) return false
        return meta.all { (name, plainSize) ->
            val f = File(dir, name)
            f.exists() && f.length() == plainSize + 28L
        }
    }

    private fun hlsDirTotalBytes(dir: File): Long =
        if (!dir.exists()) 0L else dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun isHlsItemDownloaded(item: LibraryItem): Boolean {
        if (isHlsEncTreeValid(item)) return true
        // Legacy plain tree, still downloadable-looking until the
        // scheduled migration (§4.4) re-encrypts or invalidates it.
        val dir = getLibraryHlsDir(item)
        if (!dir.exists() || !getLibraryHlsMasterFile(item).exists()) return false
        val expected = item.size ?: 0L
        return expected <= 0L || hlsDirTotalBytes(dir) == expected
    }

    /**
     * Phase-3 §4.4 migration of ONE legacy plain HLS tree, modelled on
     * [migrateAnyPlainLibraryFile]: the plain tree is first verified
     * complete by the old rule (whole-tree byte sum == item.size), then
     * re-encrypted file by file into a temp dir, the meta is written,
     * the temp dir is renamed into place atomically, the plain SRT
     * side-car is encrypted into the tree, and only then is the plain
     * tree deleted. On ANY failure both copies are dropped and the item
     * shows as not downloaded (§4.4) — never a half-encrypted tree.
     * Runs on IO (called from LibraryViewModel.loadLibrary and lazily
     * from [openLibraryItem]); NEVER from a UI state check.
     */
    fun migratePlainHlsTree(item: LibraryItem): Boolean {
        if (!isHlsItem(item)) return false
        val plainDir = getLibraryHlsDir(item)
        val plainSrt = getLibrarySubtitleFile(item)
        if (isHlsEncTreeValid(item)) {
            // Already migrated earlier; drop any plain leftover.
            if (plainDir.exists()) plainDir.deleteRecursively()
            if (plainSrt.exists()) plainSrt.delete()
            return true
        }
        if (!plainDir.exists() || !getLibraryHlsMasterFile(item).exists()) return false
        val expected = item.size ?: 0L
        if (expected > 0 && hlsDirTotalBytes(plainDir) != expected) {
            // Incomplete/corrupt legacy tree: §4.4 — invalidate, do not bless.
            plainDir.deleteRecursively()
            if (plainSrt.exists()) plainSrt.delete()
            return false
        }
        val encDir = getLibraryHlsEncDir(item)
        val tmpDir = File(encDir.parentFile, encDir.name + ".migtmp")
        fun failMigration(): Boolean {
            tmpDir.deleteRecursively()
            encDir.deleteRecursively()
            plainDir.deleteRecursively()
            if (plainSrt.exists()) plainSrt.delete()
            return false
        }
        try {
            if (tmpDir.exists()) tmpDir.deleteRecursively()
            tmpDir.mkdirs()
            val meta = mutableMapOf<String, Long>()
            val plainFiles = plainDir.walkTopDown().filter { it.isFile }.toList()
            for (plainFile in plainFiles) {
                val target = File(tmpDir, plainFile.name)
                if (!sy.medpro.watani.security.ContentEncryption
                        .encryptFile(context, plainFile, target)
                ) return failMigration()
                meta[plainFile.name] = plainFile.length()
            }
            if (plainSrt.exists()) {
                val srtTarget = File(tmpDir, plainSrt.name)
                if (!sy.medpro.watani.security.ContentEncryption
                        .encryptFile(context, plainSrt, srtTarget)
                ) return failMigration()
            }
            if (!writeHlsMeta(tmpDir, meta)) return failMigration()
            if (encDir.exists()) encDir.deleteRecursively()
            if (!tmpDir.renameTo(encDir)) return failMigration()
        } catch (_: Exception) {
            return failMigration()
        }
        plainDir.deleteRecursively()
        if (plainSrt.exists()) plainSrt.delete()
        return isHlsEncTreeValid(item)
    }

    /** Scheduled §4.4 migration sweep for every HLS item in the index.
     * Called once from LibraryViewModel.loadLibrary on IO — heavy disk
     * work that must never run inside a UI downloaded-state check. */
    suspend fun migratePlainHlsTrees(items: List<LibraryItem>) = withContext(Dispatchers.IO) {
        for (item in items) {
            try {
                migratePlainHlsTree(item)
            } catch (_: Exception) {
                // One bad tree must not stop the rest of the sweep.
            }
        }
    }

    private fun resolvePlaylistUrl(baseUrl: String, ref: String): String {
        if (ref.startsWith("http://") || ref.startsWith("https://")) return ref
        return java.net.URL(java.net.URL(baseUrl), ref).toString()
    }

    private fun playlistFileName(url: String): String =
        url.substringAfterLast('/').substringBefore('?').takeIf { it.isNotBlank() } ?: "file"

    /** Downloads an HLS tree: master + variant playlists + all segments,
     * preserving relative names. No Range resume in this version (fresh
     * temp dir per attempt); the PLAINTEXT byte total must equal
     * item.size, exactly like the single-file flow.
     *
     * Phase-3 (§4.2): every fetched file is verified as plaintext and
     * then stored ENCRYPTED — segments land in a `.part` plaintext file
     * inside the tree's temp dir only for the moments between fetch and
     * [sy.medpro.watani.security.ContentEncryption.encryptFile], which
     * are deleted immediately; playlists are encrypted from memory. The
     * finished tree (temp dir + `hls_meta.json`) is renamed into
     * `library_hls_enc/<itemId>/` atomically, so a half-encrypted tree
     * never appears as downloaded. No plaintext segment persists. */
    private suspend fun downloadHlsItem(
        item: LibraryItem,
        onProgress: (Float) -> Unit
    ): LibraryDownloadResult {
        val url = item.fileUrl ?: return LibraryDownloadResult.fail("رابط الملف غير متوفر في محتوى المكتبة")
        if (isHlsEncTreeValid(item)) {
            return LibraryDownloadResult.ok(getLibraryHlsEncMasterFile(item))
        }
        // A legacy plain tree still on disk: migrate it instead of
        // re-downloading (§4.4); a failed migration invalidates it and
        // the fresh download below proceeds from zero.
        if (getLibraryHlsDir(item).exists()) {
            migratePlainHlsTree(item)
            if (isHlsEncTreeValid(item)) {
                return LibraryDownloadResult.ok(getLibraryHlsEncMasterFile(item))
            }
        }
        val encDir = getLibraryHlsEncDir(item)
        val tmpDir = File(encDir.parentFile, encDir.name + ".dltmp")
        if (tmpDir.exists()) tmpDir.deleteRecursively()
        if (encDir.exists()) encDir.deleteRecursively()
        tmpDir.mkdirs()
        var downloaded = 0L
        val total = item.size ?: 0L
        val meta = mutableMapOf<String, Long>()
        fun fail(msg: String): LibraryDownloadResult {
            tmpDir.deleteRecursively()
            return LibraryDownloadResult.fail(msg)
        }
        /** Fetches one segment to a transient `.part`, encrypts it to
         * its final name inside the temp tree, deletes the `.part`. */
        fun fetchSegmentEncrypted(targetUrl: String, fileName: String): Boolean {
            val request = try {
                WorkerActivationApi.contentRequestForUrl(context, targetUrl)
            } catch (_: IllegalArgumentException) {
                return false
            } ?: return false
            val partFile = File(tmpDir, "$fileName.part")
            try {
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return false
                    val body = resp.body ?: return false
                    partFile.parentFile?.mkdirs()
                    body.byteStream().use { input ->
                        java.io.FileOutputStream(partFile).use { output -> input.copyTo(output) }
                    }
                }
            } catch (_: Exception) {
                partFile.delete()
                return false
            }
            val plainSize = partFile.length()
            val ok = sy.medpro.watani.security.ContentEncryption
                .encryptFile(context, partFile, File(tmpDir, fileName))
            partFile.delete()
            if (!ok) return false
            meta[fileName] = plainSize
            downloaded += plainSize
            if (total > 0) onProgress((downloaded.toFloat() / total).coerceIn(0f, 0.9f))
            return true
        }
        /** Encrypts playlist text (already held in memory) into the tree. */
        fun storePlaylistEncrypted(text: String, fileName: String): Boolean {
            val bytes = text.toByteArray(Charsets.UTF_8)
            if (!sy.medpro.watani.security.ContentEncryption
                    .encryptBytesToFile(bytes, File(tmpDir, fileName))
            ) return false
            meta[fileName] = bytes.size.toLong()
            downloaded += bytes.size
            return true
        }
        fun readText(targetUrl: String): String? {
            val request = try {
                WorkerActivationApi.contentRequestForUrl(context, targetUrl)
            } catch (_: IllegalArgumentException) {
                return null
            } ?: return null
            return try {
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return null
                    resp.body?.string()
                }
            } catch (_: Exception) {
                null
            }
        }
        val masterText = readText(url)
            ?: return fail("فشل التحميل — تأكد من اتصال الإنترنت.")
        if (!storePlaylistEncrypted(masterText, playlistFileName(url))) {
            return fail("فشل تشفير الملف")
        }
        val variantUrls = mutableListOf<String>()
        val masterLines = masterText.lines()
        for (i in masterLines.indices) {
            if (masterLines[i].startsWith("#EXT-X-STREAM-INF")) {
                val next = masterLines.getOrNull(i + 1)?.trim().orEmpty()
                if (next.isNotEmpty() && !next.startsWith("#")) {
                    variantUrls.add(resolvePlaylistUrl(url, next))
                }
            }
        }
        val playlistsToScan = if (variantUrls.isEmpty()) listOf(url to masterText) else emptyList()
        for (variantUrl in variantUrls) {
            val variantText = readText(variantUrl)
                ?: return fail("فشل التحميل — تأكد من اتصال الإنترنت.")
            if (!storePlaylistEncrypted(variantText, playlistFileName(variantUrl))) {
                return fail("فشل تشفير الملف")
            }
            for (ref in HlsPlaylistRewrite.mediaReferences(variantText)) {
                val segUrl = resolvePlaylistUrl(variantUrl, ref)
                if (!fetchSegmentEncrypted(segUrl, playlistFileName(segUrl))) {
                    return fail("فشل التحميل — تأكد من اتصال الإنترنت.")
                }
            }
        }
        for ((playlistUrl, playlistText) in playlistsToScan) {
            for (ref in HlsPlaylistRewrite.mediaReferences(playlistText)) {
                val segUrl = resolvePlaylistUrl(playlistUrl, ref)
                if (!fetchSegmentEncrypted(segUrl, playlistFileName(segUrl))) {
                    return fail("فشل التحميل — تأكد من اتصال الإنترنت.")
                }
            }
        }
        // Integrity, §4.3: plaintext byte total (recorded per file in
        // the meta) must equal item.size — the on-disk ciphertext total
        // is larger by 28 bytes per file and is never compared here.
        if (total > 0 && downloaded != total) {
            return fail("حجم الملف غير مطابق للمتوقع")
        }
        if (!writeHlsMeta(tmpDir, meta)) {
            return fail("تعذر حفظ ملف المكتبة على الجهاز")
        }
        if (!tmpDir.renameTo(encDir)) {
            return fail("تعذر حفظ الملف على الجهاز")
        }
        downloadLibrarySubtitle(item)
        onProgress(1f)
        return LibraryDownloadResult.ok(getLibraryHlsEncMasterFile(item))
    }

    /** Best-effort download of the item's SRT side-car. Never fails the
     * video download: a missing SRT only means subtitles are unavailable
     * for that video until the next download attempt.
     *
     * M10: subtitles go through the same switch point
     * ([WorkerActivationApi.contentRequestForUrl]), never a raw link —
     * so a restricted subtitle URL is gateway-checked like any other
     * restricted file, and a malformed (double-encoded) URL is rejected
     * instead of fetched. */
    private fun downloadLibrarySubtitle(item: LibraryItem) {
        val url = item.subtitleUrl?.takeIf { it.isNotBlank() } ?: return
        try {
            val request = try {
                WorkerActivationApi.contentRequestForUrl(context, url)
            } catch (_: IllegalArgumentException) {
                return
            } ?: return
            if (isHlsItem(item)) {
                // Phase-3: an HLS item's SRT is stored ENCRYPTED inside
                // its encrypted tree. It is fetched to a transient temp,
                // encrypted into place, and the temp is deleted — no
                // plaintext SRT persists in filesDir.
                val target = getLibraryEncryptedSubtitleFile(item)
                target.parentFile?.mkdirs()
                val tmp = java.io.File(target.parentFile, "${target.name}.tmp")
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return
                    val body = resp.body ?: return
                    body.byteStream().use { input ->
                        java.io.FileOutputStream(tmp).use { output -> input.copyTo(output) }
                    }
                    sy.medpro.watani.security.ContentEncryption
                        .encryptFile(context, tmp, target)
                    tmp.delete()
                }
                return
            }
            val target = getLibrarySubtitleFile(item)
            // Detector fix (b9): write to a temp file and rename — a
            // direct write interrupted mid-stream left a partial SRT
            // that later counted as "existing".
            val tmp = java.io.File(target.parentFile, "${target.name}.tmp")
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return
                val body = resp.body ?: return
                body.byteStream().use { input ->
                    java.io.FileOutputStream(tmp).use { output -> input.copyTo(output) }
                }
                tmp.renameTo(target)
            }
        } catch (_: Exception) {
            // SRT download failure is non-fatal by design.
        }
    }

    fun getLibraryEncryptedFile(item: LibraryItem): File {
        val dir = sy.medpro.watani.security.ContentEncryption.getEncryptedDir(context)
        val rawName = item.id ?: item.fileUrl?.substringAfterLast('/') ?: "item"
        val safe = rawName.replace(Regex("[^A-Za-z0-9._-]"), "_").replace(".", "_")
        return File(dir, "$safe.enc")
    }

    private fun getLibraryTempFile(target: File): File = File(target.parentFile, "${target.name}.part")

    private fun isItemDownloadedAndEncrypted(item: LibraryItem): Boolean {
        val encFile = getLibraryEncryptedFile(item)
        if (!encFile.exists()) return false
        // Encrypted-aware existence check: length >= 28 (12 IV + 16 tag)
        // and matches plaintext size + 28 when plaintext size is declared.
        // Hash check is not valid for ciphertext (integrity was verified on plaintext before encryption at download).
        val len = encFile.length()
        if (len < 28) return false
        val expectedSize = item.size ?: 0L
        if (expectedSize > 0 && len != expectedSize + 28) return false
        return true
    }

    private fun migrateAnyPlainLibraryFile(item: LibraryItem): Boolean {
        val plainFile = getLibraryFile(item)
        if (!plainFile.exists()) return false
        // Never bless a stale/corrupt plain copy: an unverified plain file
        // becomes "downloaded but broken" after migration. Drop it so the
        // user re-downloads a clean copy instead of being stuck.
        if (verifyLibraryFile(plainFile, item) != null) {
            plainFile.delete()
            return false
        }
        if (isPdfItem(item) && !hasPdfHeader(plainFile)) {
            plainFile.delete()
            return false
        }
        val encryptedFile = getLibraryEncryptedFile(item)
        return sy.medpro.watani.security.ContentEncryption.encryptFile(context, plainFile, encryptedFile).also {
            if (it) plainFile.delete()
        }
    }

    private fun expectedHashFormat(hash: String?): String? {
        if (hash.isNullOrBlank()) return null
        val h = hash.trim().lowercase()
        return when {
            h.matches(Regex("^[a-f0-9]{64}$")) -> "sha256"
            h.matches(Regex("^[a-f0-9]{40}$")) -> "sha1"
            h.matches(Regex("^[a-f0-9]{32}$")) -> "md5"
            else -> null
        }
    }

    private fun hashFile(file: File, algo: String): String {
        val digest = java.security.MessageDigest.getInstance(algo)
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            var read: Int
            while (input.read(buf).also { read = it } != -1) digest.update(buf, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Verifies an existing file against library.json. Returns null when verified, otherwise a reason. */
    private fun verifyLibraryFile(file: File, item: LibraryItem): String? {
        if (!file.exists()) return "الملف غير موجود"
        val expectedSize = item.size ?: 0L
        if (expectedSize > 0 && file.length() != expectedSize) return "حجم الملف غير مطابق"
        val algo = expectedHashFormat(item.hash)
        if (algo != null) {
            val actual = hashFile(file, algo)
            if (!actual.equals(item.hash!!.trim(), ignoreCase = true)) return "بصمة الملف غير مطابقة"
        }
        return null
    }

    /** A downloaded book must start with the PDF magic. Gateway/error
     * bytes that happen to match the declared size must never be blessed
     * as a downloaded book — they only surface later as a corrupt open. */
    private fun hasPdfHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 5) return false
        return try {
            val head = ByteArray(5)
            java.io.FileInputStream(file).use { it.read(head) == 5 } && head.decodeToString() == "%PDF-"
        } catch (_: Exception) {
            false
        }
    }

    private fun isPdfItem(item: LibraryItem): Boolean =
        item.fileUrl?.substringBefore('?')?.endsWith(".pdf", ignoreCase = true) == true

    fun hashVerificationFor(item: LibraryItem): HashVerification {
        if (item.hash.isNullOrBlank()) return HashVerification.SIZE_ONLY
        return if (expectedHashFormat(item.hash) != null) HashVerification.VERIFIED else HashVerification.UNKNOWN_FORMAT
    }

    fun isLibraryItemDownloaded(item: LibraryItem): Boolean {
        if (isHlsItem(item)) return isHlsItemDownloaded(item)
        if (isItemDownloadedAndEncrypted(item)) return true
        // Migration succeeds → returns encrypted state result directly
        if (migrateAnyPlainLibraryFile(item)) return isItemDownloadedAndEncrypted(item)
        val file = getLibraryFile(item)
        return file.exists() && verifyLibraryFile(file, item) == null
    }

    fun listDownloadedLibraryItems(items: List<LibraryItem>): List<LibraryItem> =
        items.filter { it.fileUrl != null && isLibraryItemDownloaded(it) }

    fun deleteLibraryItem(item: LibraryItem): Boolean {
        val encFile = getLibraryEncryptedFile(item)
        val plainFile = getLibraryFile(item)
        val plainTemp = getLibraryTempFile(plainFile)
        if (plainTemp.exists()) plainTemp.delete()
        plainFile.delete()
        encFile.delete()
        getLibrarySubtitleFile(item).delete()
        // Playback-session temps first: their names are derived from
        // the tree's meta, which is about to be deleted.
        deleteHlsSessionTemps(item)
        // Legacy plain HLS tree + the phase-3 encrypted tree (same
        // recursive delete — the encrypted layout changes only file
        // contents, never the tree shape, so every existing deletion
        // path keeps working on it).
        val hlsDir = getLibraryHlsDir(item)
        if (hlsDir.exists()) hlsDir.deleteRecursively()
        val hlsEncDir = getLibraryHlsEncDir(item)
        if (hlsEncDir.exists()) hlsEncDir.deleteRecursively()
        val srtTemp = getLibrarySubtitleTempFile(item)
        if (srtTemp.exists()) srtTemp.delete()
        return !plainFile.exists() && !encFile.exists() && !hlsDir.exists() && !hlsEncDir.exists()
    }

    suspend fun downloadLibraryItem(
        item: LibraryItem,
        onProgress: (Float) -> Unit = {}
    ): LibraryDownloadResult = withContext(Dispatchers.IO) {
        val integrityResult = sy.medpro.watani.security.DeviceIntegrityCheck.checkDeviceIntegrity(context)
        if (integrityResult is sy.medpro.watani.security.DeviceIntegrityCheck.IntegrityResult.Compromised) {
            return@withContext LibraryDownloadResult.fail(sy.medpro.watani.security.DeviceIntegrityCheck.getBlockMessage(integrityResult))
        }

        val url = item.fileUrl
            ?: return@withContext LibraryDownloadResult.fail("رابط الملف غير متوفر في محتوى المكتبة")

        if (isHlsItem(item)) {
            return@withContext downloadHlsItem(item, onProgress)
        }

        if (isItemDownloadedAndEncrypted(item)) {
            return@withContext LibraryDownloadResult.ok(getLibraryEncryptedFile(item))
        }

        val plainTarget = getLibraryFile(item)
        val plainTemp = getLibraryTempFile(plainTarget)

        var attempt = 0
        while (attempt < 2) {
            attempt++
            try {
                val existingBytes = if (plainTemp.exists()) plainTemp.length() else 0L
                // Same switch point for absolute file URLs: Pages /v1/
                // URLs under a restricted prefix (e.g. books/) go via the
                // Worker gateway; other hosts stay direct, unchanged.
                val itemRequest = try {
                    WorkerActivationApi.contentRequestForUrl(context, url)
                } catch (_: IllegalArgumentException) {
                    return@withContext LibraryDownloadResult.fail("رابط الملف غير صالح")
                } ?: return@withContext LibraryDownloadResult.fail("هذا الملف يحتاج كود تفعيل — فعّل حسابك أولاً.")
                val builder = itemRequest.newBuilder()
                if (existingBytes > 0) builder.header("Range", "bytes=$existingBytes-")
                client.newCall(builder.build()).execute().use { resp ->
                    if (resp.code == 401 || resp.code == 403) {
                        // M3: revoked activation — the prompt itself
                        // fires from the activationLost event; the
                        // failure text stands alone, no HTTP generic.
                        WorkerActivationApi.notifyActivationLost(context)
                        refreshDownloadedSections()
                        return@withContext LibraryDownloadResult.fail(
                            "انتهى التفعيل على هذا الجهاز — أعد إدخال كود التفعيل."
                        )
                    }
                    if (resp.code == 416) {
                        // Detector fix (b5): stale Range — drop the temp
                        // and let the attempt loop retry from zero.
                        plainTemp.delete()
                        if (attempt < 2) continue
                        return@withContext LibraryDownloadResult.fail("تعذر استئناف التحميل — أعد المحاولة.")
                    }
                    if (!resp.isSuccessful && resp.code != 206) {
                        return@withContext LibraryDownloadResult.fail("فشل التحميل — تأكد من اتصال الإنترنت.")
                    }
                    val isPartial = resp.code == 206
                    val startBytes = if (isPartial) existingBytes else {
                        if (plainTemp.exists()) plainTemp.delete()
                        0L
                    }
                    val body = resp.body
                        ?: return@withContext LibraryDownloadResult.fail("استجابة فارغة من الخادم.")
                    val contentLength = body.contentLength()
                    val total = when {
                        contentLength > 0 -> contentLength + startBytes
                        (item.size ?: 0L) > 0 -> item.size!! + startBytes
                        else -> 0L
                    }
                    body.byteStream().use { input ->
                        java.io.FileOutputStream(plainTemp, startBytes > 0).use { output ->
                            val buf = ByteArray(16384)
                            var copied = startBytes
                            var read: Int
                            while (input.read(buf).also { read = it } != -1) {
                                output.write(buf, 0, read)
                                copied += read
                                if (total > 0) onProgress((copied.toFloat() / total).coerceIn(0f, 0.9f))
                            }
                            output.flush()
                        }
                    }
                }

                if (item.size != null && item.size > 0 && plainTemp.length() != item.size) {
                    plainTemp.delete()
                    if (attempt < 2) continue
                    return@withContext LibraryDownloadResult.fail("حجم الملف غير مطابق للمتوقع")
                }

                if (plainTarget.exists()) plainTarget.delete()
                if (!plainTemp.renameTo(plainTarget)) {
                    plainTemp.delete()
                    return@withContext LibraryDownloadResult.fail("تعذر حفظ الملف على الجهاز")
                }

                val reason = verifyLibraryFile(plainTarget, item)
                if (reason != null) {
                    plainTarget.delete()
                    if (attempt < 2) continue
                    return@withContext LibraryDownloadResult.fail("الملف تالف ($reason) — أعد المحاولة")
                }

                if (isPdfItem(item) && !hasPdfHeader(plainTarget)) {
                    plainTarget.delete()
                    if (attempt < 2) continue
                    return@withContext LibraryDownloadResult.fail("الملف المحمّل ليس PDF سليمًا — أعد المحاولة")
                }

                onProgress(0.95f)
                val encFile = getLibraryEncryptedFile(item)
                if (!sy.medpro.watani.security.ContentEncryption.encryptFile(context, plainTarget, encFile) { p ->
                        onProgress(0.95f + p * 0.04f)
                    }) {
                    plainTarget.delete()
                    encFile.delete()
                    return@withContext LibraryDownloadResult.fail("فشل تشفير الملف")
                }

                plainTarget.delete()
                // Side-car SRT travels with the video; its failure never
                // fails the video download (see downloadLibrarySubtitle).
                downloadLibrarySubtitle(item)
                onProgress(1f)
                return@withContext LibraryDownloadResult.ok(encFile)
            } catch (e: Exception) {
                if (attempt >= 2) {
                    // Book-download fix: KEEP the partial temp file on a
                    // network failure so the next tap resumes via Range
                    // (the gateway now serves ranges from cache) instead
                    // of restarting a 20MB book from zero every time.
                    return@withContext LibraryDownloadResult.fail(
                        "تعذر التحميل — تحقق من اتصال الإنترنت وحاول مجددًا (سيُستأنف من حيث توقف)."
                    )
                }
            }
        }
        LibraryDownloadResult.fail("تعذر إكمال التحميل")
    }

    private fun getDecryptTempFile(item: LibraryItem): File {
        val rawName = item.id ?: item.fileUrl?.substringAfterLast('/') ?: "item"
        val safe = rawName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(context.cacheDir, "decrypt_temp_$safe")
    }

    // ------------------------------------------------------------------
    // Phase-3 (§4.2): HLS playback session.
    //
    // openLibraryItem for an HLS item no longer returns a stored file:
    // the three playlists (master + variants, each < a few KB) are
    // decrypted IN MEMORY ONLY, rewritten, and written as small temp
    // playlists in cacheDir (registered in TempDecryptedFiles, so the
    // app-close sweep collects them even after a process kill):
    //  - master: variant references become the bare temp file names;
    //  - variants: segment references become watani-enc:/// URIs that
    //    EncryptedHlsDataSource decrypts in memory while streaming.
    // Segments are NEVER written decrypted anywhere. The returned File
    // keeps the exact signature/call chain the UI already uses.
    // ------------------------------------------------------------------

    /** Deterministic session temp playlist for [fileName] of [item]. */
    private fun getHlsSessionTempFile(item: LibraryItem, fileName: String): File {
        val file = sy.medpro.watani.ui.security.TempDecryptedFiles.newTempFile(
            context, "hls_${libraryItemSafeName(item)}_$fileName"
        )
        return file
    }

    /** Every session temp this item could have produced (master +
    * variants listed in its meta). Used by close/delete. */
    private fun hlsSessionTempFiles(item: LibraryItem): List<File> {
        val names = mutableSetOf<String>()
        names += getLibraryHlsEncMasterFile(item).name
        readHlsMeta(getLibraryHlsEncDir(item))?.keys
            ?.filter { it.endsWith(".m3u8", ignoreCase = true) }
            ?.let { names += it }
        return names.map { getHlsSessionTempFile(item, it) }
    }

    private fun deleteHlsSessionTemps(item: LibraryItem) {
        for (file in hlsSessionTempFiles(item)) {
            sy.medpro.watani.ui.security.TempDecryptedFiles.delete(file)
        }
    }

    /** Builds the playback session for an encrypted HLS tree. Returns
     * the temp master playlist file, or null on any failure — including
     * a segment-independent failure here (playlist GCM failure): the
     * caller then purges the tree so the next tap re-downloads, the
     * same open-time integrity behaviour as the book flow. */
    private fun buildHlsPlaybackSession(item: LibraryItem): File? {
        val encDir = getLibraryHlsEncDir(item)
        val masterEnc = getLibraryHlsEncMasterFile(item)
        val masterBytes = sy.medpro.watani.security.ContentEncryption
            .decryptFileToBytes(masterEnc) ?: return null
        val masterText = String(masterBytes, Charsets.UTF_8)
        deleteHlsSessionTemps(item)
        // Decrypt + rewrite each variant playlist referenced by the master.
        val variantNames = HlsPlaylistRewrite.mediaReferences(masterText)
            .map { HlsPlaylistRewrite.fileNameOf(it) }
            .filter { it.endsWith(".m3u8", ignoreCase = true) }
        val itemKey = libraryItemSafeName(item)
        for (variantName in variantNames) {
            val variantBytes = sy.medpro.watani.security.ContentEncryption
                .decryptFileToBytes(File(encDir, variantName)) ?: return null
            val rewritten = HlsPlaylistRewrite.rewriteVariant(
                String(variantBytes, Charsets.UTF_8), itemKey
            )
            val temp = getHlsSessionTempFile(item, variantName)
            try {
                temp.writeText(rewritten, Charsets.UTF_8)
            } catch (_: Exception) {
                return null
            }
        }
        // A master that itself carries segments (single-playlist tree):
        // rewrite its segment references the same way.
        val rewrittenMaster = if (variantNames.isEmpty()) {
            HlsPlaylistRewrite.rewriteVariant(masterText, itemKey)
        } else {
            HlsPlaylistRewrite.rewriteMaster(masterText)
        }
        val masterTemp = getHlsSessionTempFile(item, masterEnc.name)
        return try {
            masterTemp.writeText(rewrittenMaster, Charsets.UTF_8)
            masterTemp
        } catch (_: Exception) {
            null
        }
    }

    // Serializes opens: two concurrent opens of the same book used to
    // decrypt into the same cache temp path and interleave, producing a
    // corrupt plaintext that the reader then rejected as "not in PDF
    // format".
    private val openMutex = Mutex()

    suspend fun openLibraryItem(item: LibraryItem): Result<File> = withContext(Dispatchers.IO) {
        openMutex.withLock {
        if (!isLibraryItemDownloaded(item)) {
            return@withContext Result.failure(Exception("العنصر غير محمّل"))
        }

        if (isHlsItem(item)) {
            // Phase-3 (§4.2/§4.4): a legacy plain tree still on disk is
            // migrated on this first open after the update if the
            // scheduled sweep has not reached it yet.
            if (!isHlsEncTreeValid(item) && getLibraryHlsDir(item).exists()) {
                migratePlainHlsTree(item)
            }
            if (!isHlsEncTreeValid(item)) {
                return@withContext Result.failure(Exception("تعذر العثور على الملف المحمّل"))
            }
            val sessionMaster = buildHlsPlaybackSession(item)
            if (sessionMaster == null || !sessionMaster.exists()) {
                // Playlist integrity failed at open: purge the tree so
                // the next tap re-downloads (book-flow behaviour).
                deleteLibraryItem(item)
                return@withContext Result.failure(
                    Exception("النسخة المحفوظة تالفة — حُذفت، أعد تحميل الفيديو")
                )
            }
            return@withContext Result.success(sessionMaster)
        }

        val encFile = getLibraryEncryptedFile(item)
        if (!encFile.exists()) {
            return@withContext Result.failure(Exception("تعذر العثور على الملف المحمّل"))
        }

        val tempFile = getDecryptTempFile(item)
        if (tempFile.exists()) tempFile.delete()

        val success = sy.medpro.watani.security.ContentEncryption.decryptFile(context, encFile, tempFile)
        if (!success || !tempFile.exists()) {
            tempFile.delete()
            return@withContext Result.failure(Exception("تعذر فتح الملف المحمّل — أعد تحميله"))
        }

        // Open-time integrity gate (Nov 2026, doctor's order): a stored
        // copy is trusted by size alone, so a damaged-but-right-sized
        // copy used to reach the reader as "file not in PDF format or
        // corrupted" again and again. A decrypted copy that fails the
        // size or the PDF-header check is purged together with its
        // stored copy, so the next tap cleanly re-downloads instead of
        // reopening the same corruption.
        val expectedSize = item.size ?: 0L
        val badDecrypted = (expectedSize > 0 && tempFile.length() != expectedSize) ||
            (isPdfItem(item) && !hasPdfHeader(tempFile))
        if (badDecrypted) {
            tempFile.delete()
            encFile.delete()
            return@withContext Result.failure(Exception("النسخة المحفوظة تالفة — حُذفت، أعد تحميل الكتاب"))
        }

        Result.success(tempFile)
        }
    }

    fun closeLibraryItem(item: LibraryItem) {
        val tempFile = getDecryptTempFile(item)
        if (tempFile.exists()) tempFile.delete()
        if (isHlsItem(item)) {
            // Phase-3 (§4.2): closing an HLS reader deletes its session
            // playlist temps and the decrypted SRT temp — after close,
            // no plaintext of the item remains anywhere (segments were
            // never written at all).
            deleteHlsSessionTemps(item)
            val srtTemp = getLibrarySubtitleTempFile(item)
            if (srtTemp.exists()) srtTemp.delete()
        }
    }
}
