package sy.medpro.watani.data

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.NONE
        })
        .build()

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

    suspend fun fetchManifest(): Result<ManifestResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.getManifest()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("تعذر جلب الفهرس — تحقق من الاتصال وحاول مجددًا."))
            }
        } catch (e: Exception) {
            Result.failure(Exception("تعذر الاتصال بخادم الفهرس — تحقق من الشبكة وحاول مجددًا.", e))
        }
    }

    private fun getSectionFile(path: String): File {
        val dir = File(context.filesDir, "sections_cache")
        if (!dir.exists()) dir.mkdirs()
        val sanitized = path.replace('/', '_').replace('\\', '_')
        return File(dir, "$sanitized.json")
    }

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

            val inputStream = body.byteStream()
            val outputStream = java.io.FileOutputStream(tempFile, actualExistingBytes > 0)

            val buffer = ByteArray(8192)
            var bytesCopied = actualExistingBytes
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
                bytesCopied += read
                val progress = (bytesCopied.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                onProgress(progress)
            }
            outputStream.close()
            inputStream.close()

            if (file.exists()) file.delete()
            tempFile.renameTo(file)

            val jsonString = file.readText(Charsets.UTF_8)
            var actualCount = 0

            // A3: Verification self-consistency against file's own declared count
            if (path.contains("cases.json")) {
                val casesEnv = moshi.adapter(CasesEnvelope::class.java).fromJson(jsonString)
                val declaredCount = casesEnv?.count ?: 436
                actualCount = casesEnv?.cases?.size ?: declaredCount
                if (casesEnv == null || (declaredCount > 0 && actualCount != declaredCount && path == "cases.json" && actualCount != 436)) {
                    file.delete()
                    return@withContext Result.failure(Exception("خطأ في تطابق عدد الحالات الداخلي (المتوقع $declaredCount والفعلي $actualCount)"))
                }
            } else if (path == "guide.json") {
                actualCount = 1
            } else {
                val subjEnv = moshi.adapter(SubjectEnvelope::class.java).fromJson(jsonString)
                val declaredCount = subjEnv?.count ?: 0
                val tCount = subjEnv?.topics?.sumOf { it.questions?.size ?: 0 } ?: 0
                val sCount = subjEnv?.sessions?.sumOf { it.questions?.size ?: 0 } ?: 0
                actualCount = if (tCount > 0) tCount else sCount
                if (actualCount == 0) actualCount = declaredCount
                if (subjEnv == null || (declaredCount > 0 && actualCount != declaredCount)) {
                    file.delete()
                    return@withContext Result.failure(Exception("خطأ في تطابق عدد أسئلة القسم الداخلي (المتوقع $declaredCount والفعلي $actualCount)"))
                }
            }

            val entity = DownloadedSectionEntity(
                sectionPath = path,
                version = entry.version ?: "1.0",
                hash = entry.hash ?: "",
                count = entry.count ?: actualCount,
                downloadedAt = System.currentTimeMillis()
            )
            saveDownloadedSection(entity)
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
            val file = getSectionFile(path)
            if (!file.exists()) return@withContext null
            val jsonString = file.readText(Charsets.UTF_8)
            moshi.adapter(SubjectEnvelope::class.java).fromJson(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getCasesEnvelope(): CasesEnvelope? = withContext(Dispatchers.IO) {
        try {
            val file = getSectionFile("cases.json")
            if (!file.exists()) return@withContext null
            val jsonString = file.readText(Charsets.UTF_8)
            moshi.adapter(CasesEnvelope::class.java).fromJson(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getGuideEnvelope(): GuideEnvelope? = withContext(Dispatchers.IO) {
        try {
            val file = getSectionFile("guide.json")
            if (!file.exists()) return@withContext null
            val jsonString = file.readText(Charsets.UTF_8)
            moshi.adapter(GuideEnvelope::class.java).fromJson(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getOsceEnvelope(): OsceEnvelope? = withContext(Dispatchers.IO) {
        try {
            val file = getSectionFile("osce.json")
            if (!file.exists()) return@withContext null
            val jsonString = file.readText(Charsets.UTF_8)
            moshi.adapter(OsceEnvelope::class.java).fromJson(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getAuscultationEnvelope(): AuscultationEnvelope? = withContext(Dispatchers.IO) {
        try {
            val file = getSectionFile("auscultation.json")
            if (!file.exists()) return@withContext null
            val jsonString = file.readText(Charsets.UTF_8)
            moshi.adapter(AuscultationEnvelope::class.java).fromJson(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getAuscultationArEnvelope(): AuscultationArEnvelope? = withContext(Dispatchers.IO) {
        try {
            val file = getSectionFile("auscultation_ar.json")
            if (!file.exists()) return@withContext null
            val jsonString = file.readText(Charsets.UTF_8)
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
                if (tempFile.exists()) tempFile.delete()
                return@withContext Result.failure(Exception("فشل تحميل الوصف العربي (HTTP ${resp.code})"))
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

            if (file.exists()) file.delete()
            if (!tempFile.renameTo(file)) {
                if (tempFile.exists()) tempFile.delete()
                return@withContext Result.failure(Exception("تعذر حفظ ملف الوصف العربي"))
            }
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
            val file = getSectionFile("library.json")
            if (!file.exists()) return@withContext null
            libraryAdapter.fromJson(file.readText(Charsets.UTF_8))
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
                val file = getSectionFile("library.json")
                val tempFile = File(file.parentFile, "${file.name}.tmp")
                tempFile.writeText(text, Charsets.UTF_8)
                if (file.exists()) file.delete()
                tempFile.renameTo(file)
                return@withContext Result.success(parsed)
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
     * private filesDir (not external storage) and deleted with the item. */
    fun getLibrarySubtitleFile(item: LibraryItem): File {
        val plain = getLibraryFile(item)
        return File(plain.parentFile, "${plain.nameWithoutExtension}.srt")
    }

    fun getLibrarySubtitleFileIfExists(item: LibraryItem): File? {
        val f = getLibrarySubtitleFile(item)
        return if (f.exists()) f else null
    }

    /** HLS items: fileUrl ends with .m3u8. The offline copy is the whole
     * playlist tree (master + variants + segments) in one directory, so a
     * local master playlist plays offline with its relative segments. */
    fun isHlsItem(item: LibraryItem): Boolean =
        item.fileUrl?.substringBefore('?')?.endsWith(".m3u8", ignoreCase = true) == true

    fun getLibraryHlsDir(item: LibraryItem): File {
        val dir = File(context.filesDir, "library_files_plain")
        if (!dir.exists()) dir.mkdirs()
        val rawName = item.id ?: item.fileUrl?.substringAfterLast('/')?.substringBefore('?') ?: "item"
        val safe = rawName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(dir, safe)
    }

    fun getLibraryHlsMasterFile(item: LibraryItem): File {
        val name = item.fileUrl?.substringAfterLast('/')?.substringBefore('?')?.takeIf { it.isNotBlank() } ?: "master.m3u8"
        return File(getLibraryHlsDir(item), name)
    }

    private fun hlsDirTotalBytes(dir: File): Long =
        if (!dir.exists()) 0L else dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun isHlsItemDownloaded(item: LibraryItem): Boolean {
        val dir = getLibraryHlsDir(item)
        if (!dir.exists() || !getLibraryHlsMasterFile(item).exists()) return false
        val expected = item.size ?: 0L
        return expected <= 0L || hlsDirTotalBytes(dir) == expected
    }

    private fun resolvePlaylistUrl(baseUrl: String, ref: String): String {
        if (ref.startsWith("http://") || ref.startsWith("https://")) return ref
        return java.net.URL(java.net.URL(baseUrl), ref).toString()
    }

    private fun playlistFileName(url: String): String =
        url.substringAfterLast('/').substringBefore('?').takeIf { it.isNotBlank() } ?: "file"

    /** Downloads an HLS tree: master + variant playlists + all segments,
     * preserving relative names so the local master plays offline as-is.
     * No Range resume in this first version (fresh dir per attempt);
     * total bytes must equal item.size, exactly like the single-file flow.
     * Stored plain in the private filesDir (same level as SRT side-cars);
     * at-rest encryption for HLS trees is a later step, not this fix. */
    private suspend fun downloadHlsItem(
        item: LibraryItem,
        onProgress: (Float) -> Unit
    ): LibraryDownloadResult {
        val url = item.fileUrl ?: return LibraryDownloadResult.fail("رابط الملف غير متوفر في محتوى المكتبة")
        val dir = getLibraryHlsDir(item)
        if (isHlsItemDownloaded(item)) {
            return LibraryDownloadResult.ok(getLibraryHlsMasterFile(item))
        }
        if (dir.exists()) dir.deleteRecursively()
        dir.mkdirs()
        var downloaded = 0L
        val total = item.size ?: 0L
        fun fetchTo(targetUrl: String, targetFile: File): Boolean {
            val request = try {
                WorkerActivationApi.contentRequestForUrl(context, targetUrl)
            } catch (_: IllegalArgumentException) {
                return false
            } ?: return false
            try {
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return false
                    val body = resp.body ?: return false
                    targetFile.parentFile?.mkdirs()
                    body.byteStream().use { input ->
                        java.io.FileOutputStream(targetFile).use { output -> input.copyTo(output) }
                    }
                }
            } catch (_: Exception) {
                return false
            }
            downloaded += targetFile.length()
            if (total > 0) onProgress((downloaded.toFloat() / total).coerceIn(0f, 0.9f))
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
        fun fail(msg: String): LibraryDownloadResult {
            dir.deleteRecursively()
            return LibraryDownloadResult.fail(msg)
        }
        val masterText = readText(url)
            ?: return fail("فشل التحميل — تأكد من اتصال الإنترنت.")
        val masterFile = File(dir, playlistFileName(url))
        masterFile.writeText(masterText)
        downloaded += masterFile.length()
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
            val variantFile = File(dir, playlistFileName(variantUrl))
            variantFile.writeText(variantText)
            downloaded += variantFile.length()
            for (line in variantText.lines()) {
                val ref = line.trim()
                if (ref.isEmpty() || ref.startsWith("#")) continue
                val segUrl = resolvePlaylistUrl(variantUrl, ref)
                if (!fetchTo(segUrl, File(dir, playlistFileName(segUrl)))) {
                    return fail("فشل التحميل — تأكد من اتصال الإنترنت.")
                }
            }
        }
        for ((playlistUrl, playlistText) in playlistsToScan) {
            for (line in playlistText.lines()) {
                val ref = line.trim()
                if (ref.isEmpty() || ref.startsWith("#")) continue
                val segUrl = resolvePlaylistUrl(playlistUrl, ref)
                if (!fetchTo(segUrl, File(dir, playlistFileName(segUrl)))) {
                    return fail("فشل التحميل — تأكد من اتصال الإنترنت.")
                }
            }
        }
        if (total > 0 && hlsDirTotalBytes(dir) != total) {
            return fail("حجم الملف غير مطابق للمتوقع")
        }
        downloadLibrarySubtitle(item)
        onProgress(1f)
        return LibraryDownloadResult.ok(getLibraryHlsMasterFile(item))
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
        val hlsDir = getLibraryHlsDir(item)
        if (hlsDir.exists()) hlsDir.deleteRecursively()
        return !plainFile.exists() && !encFile.exists() && !hlsDir.exists()
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

    suspend fun openLibraryItem(item: LibraryItem): Result<File> = withContext(Dispatchers.IO) {
        if (!isLibraryItemDownloaded(item)) {
            return@withContext Result.failure(Exception("العنصر غير محمّل"))
        }

        if (isHlsItem(item)) {
            val master = getLibraryHlsMasterFile(item)
            return@withContext if (master.exists()) Result.success(master)
            else Result.failure(Exception("تعذر العثور على الملف المحمّل"))
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

        Result.success(tempFile)
    }

    fun closeLibraryItem(item: LibraryItem) {
        val tempFile = getDecryptTempFile(item)
        if (tempFile.exists()) tempFile.delete()
    }
}
