package sy.medpro.watani.ui.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

/**
 * Whole-app root block (doctor's order, build 1.2.8): unlike
 * [RestrictedContentGate] (which gates only the restricted sections),
 * this block refuses to run the app AT ALL on a rooted device — the
 * verdict is computed in MainActivity.onCreate before any content
 * wiring, and a blocked device sees only the block screen.
 *
 * STRUCTURE (mirrors [RootCheck]): a pure decision over an injectable
 * category set ([isBlocked]), plus a thin Android collector
 * ([RootBlockAndroid]) that gathers the signals. Signals are grouped
 * into INDEPENDENT categories in the RootBeer tradition: su binaries,
 * root-manager packages, Magisk filesystem artifacts, build/properties
 * anomalies, busybox, and writable system paths.
 *
 * FALSE-POSITIVE FIREWALL (the doctor's hard constraint): one category
 * NEVER blocks. Blocking requires hits in at least TWO independent
 * categories, so a legitimate student on a stock ROM — including the
 * budget devices that ship with test-keys alone — is never locked
 * out, while every mainstream root method (Magisk, SuperSU,
 * KernelSU, APatch) trips three or more categories at once.
 *
 * DELIBERATE OMISSIONS (same stance as [RootCheck]): never EXECUTES
 * `su` (can hang/prompt/proves nothing), and no Play Integrity (the
 * app is sideloaded, not distributed via Play). Root-hiding tools
 * (Shamiko / Zygisk denylist) can still hide these signals — this
 * layer stops casual rooted devices, it is not "unhackable".
 */
object RootBlock {

    enum class Category {
        SU_BINARY,
        ROOT_PACKAGE,
        MAGISK_ARTIFACT,
        BUILD_PROPS,
        BUSYBOX,
        WRITABLE_SYSTEM,
    }

    /** Minimum independent categories required to block the app. */
    const val MIN_CATEGORIES_TO_BLOCK = 2

    /** Pure decision: blocked iff at least [MIN_CATEGORIES_TO_BLOCK]
     * distinct signal categories hit. Unit-tested on the JVM. */
    fun isBlocked(categories: Set<Category>): Boolean =
        categories.size >= MIN_CATEGORIES_TO_BLOCK

    /** Classic `su` binary locations (RootBeer set). */
    val SU_PATHS: Set<String> = setOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/su",
        "/system/bin/.ext/.su",
        "/system/usr/we-need-root/su",
        "/cache/su",
        "/data/local/su",
        "/data/local/bin/su",
        "/data/local/xbin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/su/bin/su",
        "/vendor/bin/su",
    )

    /** Magisk filesystem artifacts. */
    val MAGISK_PATHS: Set<String> = setOf(
        "/data/adb/magisk",
        "/data/adb/modules",
        "/data/adb/modules_update",
        "/sbin/.magisk",
        "/sbin/magisk",
        "/debug_ramdisk/.magisk",
        "/system/bin/magisk",
    )

    /** Busybox locations (never present on a stock retail ROM). */
    val BUSYBOX_PATHS: Set<String> = setOf(
        "/system/bin/busybox",
        "/system/xbin/busybox",
        "/sbin/busybox",
        "/vendor/bin/busybox",
        "/data/local/bin/busybox",
        "/data/local/xbin/busybox",
    )

    /** Root-manager / root-app package names. Declared in the
     * manifest's <queries> block so Android 11+ package visibility
     * lets the collector actually see them. */
    val ROOT_PACKAGES: Set<String> = setOf(
        "com.topjohnwu.magisk",
        "io.github.vvb2060.magisk",
        "eu.chainfire.supersu",
        "com.noshufou.android.su",
        "com.koushikdutta.superuser",
        "com.thirdparty.superuser",
        "com.yellowes.su",
        "com.kingroot.kinguser",
        "com.kingo.root",
        "me.weishu.kernelsu",
        "me.bmax.apatch",
    )

    /** System paths that are strictly read-only on a stock ROM. */
    val WRITABLE_WATCH_PATHS: Set<String> = setOf(
        "/system",
        "/system/bin",
        "/system/xbin",
        "/vendor/bin",
        "/sbin",
        "/etc",
    )
}

/** Thin Android collector: gathers the category set, no decisions. */
object RootBlockAndroid {

    fun collectCategories(context: Context): Set<RootBlock.Category> {
        val found = mutableSetOf<RootBlock.Category>()

        if (RootBlock.SU_PATHS.any { File(it).exists() }) {
            found += RootBlock.Category.SU_BINARY
        }
        if (RootBlock.MAGISK_PATHS.any { File(it).exists() }) {
            found += RootBlock.Category.MAGISK_ARTIFACT
        }
        if (RootBlock.BUSYBOX_PATHS.any { File(it).exists() }) {
            found += RootBlock.Category.BUSYBOX
        }
        if (RootBlock.ROOT_PACKAGES.any { isPackageInstalled(context, it) }) {
            found += RootBlock.Category.ROOT_PACKAGE
        }
        if (hasDangerousBuildProps()) {
            found += RootBlock.Category.BUILD_PROPS
        }
        if (RootBlock.WRITABLE_WATCH_PATHS.any { File(it).canWrite() }) {
            found += RootBlock.Category.WRITABLE_SYSTEM
        }
        return found
    }

    fun isBlocked(context: Context): Boolean =
        RootBlock.isBlocked(collectCategories(context))

    private fun isPackageInstalled(context: Context, packageName: String): Boolean =
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }

    /**
     * BUILD_PROPS category: test-keys build tags, or a dangerous
     * property pair member (ro.debuggable=1 / ro.secure=0) read via
     * the framework's SystemProperties reflection (no shell-out).
     * Any one of these lights the single category — never more.
     */
    private fun hasDangerousBuildProps(): Boolean {
        if (Build.TAGS?.contains("test-keys") == true) return true
        val debuggable = systemProperty("ro.debuggable")
        val secure = systemProperty("ro.secure")
        return debuggable == "1" || secure == "0"
    }

    private fun systemProperty(key: String): String? = try {
        val clazz = Class.forName("android.os.SystemProperties")
        val get = clazz.getMethod("get", String::class.java)
        get.invoke(null, key) as? String
    } catch (_: Exception) {
        null
    }
}
