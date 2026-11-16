package sy.medpro.watani.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * In-app update download + install (build 1.2.28, doctor's order
 * 16 Nov 2026): the update-required screen carries a «تحميل التحديث
 * وتثبيته» button that downloads the new APK itself and hands it to
 * the system installer, which installs it OVER the current build —
 * same signing certificate, so no data is lost and no Telegram trip
 * is needed.
 *
 * The APK lives at ONE fixed URL on the content site; every release
 * replaces the file at that path (see release-prep/BUILD_1_2_28.md
 * for the per-release procedure). The gateway also carries the URL
 * as `apkUrl` in GET /v1/config (persisted like minVersion via
 * [WorkerActivationApi]), but the app only ever trusts an https URL
 * on the content host itself — a poisoned config can never point
 * the installer at a foreign file; anything else falls back to the
 * fixed URL.
 */
object AppUpdate {

    /** The one fixed update address; replaced in place per release. */
    const val FALLBACK_APK_URL = "https://watani-content.pages.dev/apk/watani-latest.apk"

    private const val ALLOWED_HOST = "watani-content.pages.dev"
    private const val UPDATE_DIR = "updates"
    private const val UPDATE_FILE = "watani-update.apk"

    /** Pure (unit-tested): only an https URL whose host is exactly
     * the content host is installable. */
    fun isApkUrlAllowed(url: String?): Boolean {
        val u = url?.trim().orEmpty()
        if (!u.startsWith("https://")) return false
        val host = u.removePrefix("https://")
            .substringBefore('/')
            .substringBefore(':')
        return host == ALLOWED_HOST
    }

    /** Pure (unit-tested): the config value when it is trustworthy,
     * the fixed URL otherwise. */
    fun resolveApkUrl(configValue: String?): String =
        if (isApkUrlAllowed(configValue)) configValue!!.trim() else FALLBACK_APK_URL

    fun updateFile(context: Context): File =
        File(File(context.cacheDir, UPDATE_DIR), UPDATE_FILE)

    /** Streams the APK to the cache updates folder (any previous
     * partial copy is discarded first), reporting byte progress. */
    suspend fun downloadApk(
        context: Context,
        url: String,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val target = updateFile(context)
        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()
        val request = Request.Builder().url(url).get().build()
        OkHttpClient().newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            val body = resp.body ?: throw IllegalStateException("empty body")
            val total = body.contentLength()
            body.byteStream().use { input ->
                FileOutputStream(target).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        downloaded += n
                        onProgress(downloaded, total)
                    }
                }
            }
        }
        if (target.length() <= 0L) throw IllegalStateException("empty file")
        target
    }

    /** Android 8+: sideload installs need the per-app «install
     * unknown apps» grant; older versions install directly. */
    fun canInstallPackages(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    /** Opens the system settings page where the user grants this
     * app the install permission (one-time). */
    fun installPermissionIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** The system installer over the current build (same cert). */
    fun installIntent(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
