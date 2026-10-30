package sy.medpro.watani.ui.security

import android.os.Build
import java.io.File

/**
 * Root detection — Team 3, device-protection branch.
 *
 * STRUCTURE: pure decision over injectable inputs ([isRooted]/[rootReasons]:
 * the set of paths that exist on the device + the build tags string), plus a
 * thin Android adapter ([RootCheckAndroid]) that supplies the real inputs.
 *
 * BEHAVIOUR: a positive result blocks RESTRICTED content only (Questions +
 * Library) via [RestrictedContentGate]; free sections keep working.
 *
 * DELIBERATE OMISSION: this module never EXECUTES `su`. Spawning `su` can
 * hang, prompt the user, or be intercepted, and a failed exec proves
 * nothing. (The pre-existing security/DeviceIntegrityCheck.isRooted() does
 * exec `su`; this module does not modify it.) Path + Magisk-indicator +
 * test-keys checks are the standard non-invasive signals.
 *
 * HONEST LIMIT: root hiding tools (Magisk Hide / Shamiko / Zygisk denylist)
 * can hide every signal checked here from an app-level scan. A rooted
 * device that hides itself well will pass this check. This layer stops
 * casual rooted devices and emulator/root farms, not a determined hider.
 * On a rooted device, on-device encryption keys are also extractable —
 * see RestrictedFileStore's header. Nothing here is "unhackable".
 */
object RootCheck {

    /** Well-known su binaries, Superuser apps, and Magisk indicators. */
    val KNOWN_ROOT_PATHS: Set<String> = setOf(
        "/system/app/Superuser.apk",
        "/system/app/SuperSU.apk",
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/bin/failsafe/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/data/local/su",
        "/system/sd/xbin/su",
        "/su/bin/su",
        "/system/xbin/daemonsu",
        "/system/bin/daemonsu",
        "/cache/su",
        "/data/su",
        "/dev/com.koushikdutta.superuser.daemon/",
        // Magisk-specific indicators.
        "/sbin/.magisk",
        "/sbin/magisk",
        "/system/bin/magisk",
        "/data/adb/magisk",
        "/data/adb/modules",
    )

    /** Pure decision over injectable inputs. */
    fun isRooted(existingPaths: Set<String>, buildTags: String?): Boolean =
        rootReasons(existingPaths, buildTags).isNotEmpty()

    /** Pure decision with reasons (stable strings, for tests and logging). */
    fun rootReasons(existingPaths: Set<String>, buildTags: String?): List<String> {
        val reasons = mutableListOf<String>()
        val hits = existingPaths.intersect(KNOWN_ROOT_PATHS)
        if (hits.isNotEmpty()) {
            reasons += "root_path:${hits.sorted().first()}"
        }
        if (buildTags?.contains("test-keys") == true) {
            reasons += "test_keys"
        }
        return reasons
    }
}

/** Thin Android adapter: supplies real inputs, contains no decision logic. */
object RootCheckAndroid {

    fun existingRootPaths(): Set<String> =
        RootCheck.KNOWN_ROOT_PATHS.filter { File(it).exists() }.toSet()

    fun isRooted(): Boolean =
        RootCheck.isRooted(existingRootPaths(), Build.TAGS)

    fun reasons(): List<String> =
        RootCheck.rootReasons(existingRootPaths(), Build.TAGS)
}
