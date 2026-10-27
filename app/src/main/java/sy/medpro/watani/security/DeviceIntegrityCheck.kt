package sy.medpro.watani.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.File

object DeviceIntegrityCheck {
    private const val TAG = "DeviceIntegrityCheck"

    sealed class IntegrityResult {
        data object Safe : IntegrityResult()
        data class Compromised(val reason: String) : IntegrityResult()
    }

    fun checkDeviceIntegrity(context: Context): IntegrityResult {
        if (isEmulator()) return IntegrityResult.Compromised("جهاز افتراضي")
        if (isRooted()) return IntegrityResult.Compromised("جهاز مجذور")
        if (hasAttachedDebugger()) return IntegrityResult.Compromised("معالج معلق")
        if (!isSignatureValid(context)) return IntegrityResult.Compromised("توقيع غير صحيح")

        return IntegrityResult.Safe
    }

    private fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.contains("generic") ||
                Build.FINGERPRINT.contains("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.DEVICE.contains("generic") ||
                Build.DEVICE.contains("google_sdk") ||
                Build.HARDWARE.contains("ranchu") ||
                Build.PRODUCT.contains("sdk_google") ||
                Build.PRODUCT.contains("google_sdk"))
    }

    private fun isRooted(): Boolean {
        val rootIndicators = listOf(
            "/system/app/Superuser.apk",
            "/system/xbin/su",
            "/system/bin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/xbin/daemonsu",
            "/cache/su",
            "/data/su",
            "/dev/com.koushikdutta.superuser.daemon/"
        )

        for (indicator in rootIndicators) {
            if (File(indicator).exists()) {
                return true
            }
        }

        return try {
            Runtime.getRuntime().exec("su").inputStream.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun hasAttachedDebugger(): Boolean {
        return android.os.Debug.isDebuggerConnected()
    }

    private fun isSignatureValid(context: Context): Boolean {
        // Presence-only: CI keystores rotate per build, so a pinned fingerprint would break legitimate builds.
        // We only assert the app carries some signature; we do not claim mismatch detection.
        return try {
            val packageManager = context.packageManager
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNATURES_FLAG.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }

            packageInfo.signatures.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Signature validation error: ${e.message}")
            false
        }
    }

    fun getBlockMessage(result: IntegrityResult.Compromised): String {
        return when {
            result.reason.contains("افتراضي") -> "التطبيق لا يعمل على أجهزة افتراضية"
            result.reason.contains("مجذور") -> "التطبيق لا يعمل على أجهزة مجذورة"
            result.reason.contains("معالج") -> "يُكتشف معالج معلق"
            result.reason.contains("توقيع") -> "توقيع التطبيق غير واضح (غير موقّع أو لا يمكن التحقق منه)"
            else -> "لا يمكن الوصول إلى محتوى محمي: جهاز غير آمن"
        }
    }
}
