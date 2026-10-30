package sy.medpro.watani.ui.security

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import sy.medpro.watani.security.ContentProtection

/**
 * One-call install point for device-side protections — Team 3.
 *
 * MainActivity calls exactly one line: `SecurityChecks.install(this)`.
 * That line (a) applies FLAG_SECURE to the Activity window — which, in
 * this single-Activity app, covers ALL screens — and (b) registers a
 * lifecycle callback that sweeps plaintext temp files when the Activity
 * is destroyed (app close; see TempDecryptedFiles path P3).
 *
 * FLAG_SECURE audit (see the report for grep evidence): the flag was
 * ALREADY applied at window level in MainActivity.onCreate via
 * ContentProtection.enableScreenshotPrevention(this) before this branch,
 * and this app has exactly one Activity (see AndroidManifest). This
 * install function delegates to that same ContentProtection call, so it
 * changes no FLAG_SECURE behaviour — it only gives the flag a single
 * install point shared with the temp-file sweep. If a second Activity
 * is ever added, it must call install() too; the flag does not follow
 * Activities automatically.
 *
 * Registration is guarded so repeated install() calls (e.g. after a
 * configuration-change recreation) do not stack callbacks.
 */
object SecurityChecks {

    @Volatile
    private var lifecycleRegistered = false

    /**
     * Process-once startup sweep flag (M13). Static state lives for the
     * process lifetime, so a configuration-change recreation (which
     * re-runs onCreate -> install) does NOT re-sweep, while a fresh
     * process start (after a process kill) DOES sweep once.
     */
    @Volatile
    private var startupSweepDone = false

    /** Window-only install (for any future Activity/window without a sweep).
     * Same flag ContentProtection applies; set directly here so this
     * branch adds no edits to the pre-existing ContentProtection file. */
    fun install(window: Window) {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
    }

    /**
     * Full install for MainActivity: window flag + temp sweeps.
     * M13: (a) a ONE-TIME sweep at process start closes the process-death
     * window (a temp surviving a crash/force-stop is deleted on the next
     * start, not left until the next app close); (b) the destroy sweep
     * below is guarded by isFinishing so a rotation (destroy without
     * finish) never wipes a temp its reader still needs.
     */
    fun install(activity: Activity) {
        ContentProtection.enableScreenshotPrevention(activity)
        if (!startupSweepDone) {
            startupSweepDone = true
            try {
                TempDecryptedFiles.deleteAll(activity)
            } catch (_: Exception) {
            }
        }
        registerTempSweep(activity)
    }

    private fun registerTempSweep(activity: Activity) {
        if (lifecycleRegistered) return
        val app = activity.application ?: return
        app.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(a: Activity, b: Bundle?) {}
                override fun onActivityStarted(a: Activity) {}
                override fun onActivityResumed(a: Activity) {}
                override fun onActivityPaused(a: Activity) {}
                override fun onActivityStopped(a: Activity) {}
                override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
                override fun onActivityDestroyed(a: Activity) {
                    // App close path P3: wipe any plaintext temp that a
                    // reader close path missed. M13 guard: ONLY when the
                    // Activity is finishing (real app close). A destroy
                    // without finish is a configuration change (rotation):
                    // sweeping there would delete temps whose readers
                    // are merely being recreated.
                    if (a is sy.medpro.watani.MainActivity && a.isFinishing) {
                        TempDecryptedFiles.deleteAll(a)
                    }
                }
            }
        )
        lifecycleRegistered = true
    }
}
