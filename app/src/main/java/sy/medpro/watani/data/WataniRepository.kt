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
import java.net.URLEncoder
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

    suspend fun fetchManifest(): Result<ManifestResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.getManifest()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("خطأ في جلب الفهرس (رمز HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("تعذر الاتصال بخادم الفهرس: ${e.localizedMessage ?: "خطأ في الشبكة"}"))
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
            val encodedPath = path.split("/").joinToString("/") { seg ->
                URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
            }
            val fileUrl = "${WataniApi.BASE_URL}$encodedPath"

            val file = getSectionFile(path)
            val tempFile = File(file.parentFile, "${file.name}.tmp")

            val existingBytes = if (tempFile.exists()) tempFile.length() else 0L
            val requestBuilder = okhttp3.Request.Builder().url(fileUrl)
            if (existingBytes > 0) {
                requestBuilder.header("Range", "bytes=$existingBytes-")
            }

            val resp = client.newCall(requestBuilder.build()).execute()
            if (!resp.isSuccessful && resp.code != 206) {
                return@withContext Result.failure(Exception("فشل التحميل (HTTP ${resp.code}): تأكد من اتصال الإنترنت أو صحة الرابط."))
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
            Result.failure(Exception("خطأ في التحميل: ${e.localizedMessage ?: "تحقق من اتصال الإنترنت"}"))
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
            Result.failure(Exception("خطأ في تحميل الوصف العربي: ${e.localizedMessage ?: "تحقق من اتصال الإنترنت"}"))
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
            Result.failure(Exception("خطأ في تحميل الصوت: ${e.localizedMessage}"))
        }
    }
}
