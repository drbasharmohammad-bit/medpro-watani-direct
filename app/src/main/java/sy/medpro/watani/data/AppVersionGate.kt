package sy.medpro.watani.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Phase-2 hardening (Nov 2026): min-version enforcement.
 *
 * The gateway exposes the minimum supported versionCode as
 * `minVersion` from GET /v1/config (app_config key `min_version` on
 * the Worker; verified live — the endpoint answers
 * {"paymentMode":…,"maintenance":…,"minVersion":<int>,"welcomeMessage":…}).
 *
 * Rules (doctor's constraints):
 *  - The last successfully fetched value is persisted (in the
 *    encrypted activation prefs, via [WorkerActivationApi]) and is
 *    re-checked at every cold start and after every successful fetch.
 *  - A user who has NEVER learned a value is never blocked offline.
 *  - A user whose last known value is above their versionCode stays
 *    blocked offline too — the stored value decides, not the network.
 *  - Supported versions are never blocked, so the offline daily entry
 *    of a signed-in user keeps working exactly as before.
 */
object AppVersionGate {

    /** This build's versionCode from PackageInfo (never a hardcoded
     * copy of the Gradle value, so the gate cannot drift from the
     * installed build). */
    fun currentVersionCode(context: Context): Long {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        } catch (_: Exception) {
            0L
        }
    }

    /** Pure decision (unit-tested): blocked only when a value was
     * learned (knownMin > 0) and this build is below it. */
    fun isBlocked(currentVersionCode: Long, knownMinVersion: Int): Boolean =
        knownMinVersion > 0 && currentVersionCode > 0L && currentVersionCode < knownMinVersion.toLong()

    /** Synchronous check from the persisted last-known value — safe
     * at cold start, online or offline. */
    fun isUpdateRequired(context: Context): Boolean =
        isBlocked(currentVersionCode(context), WorkerActivationApi.knownMinVersion(context))

    /** Fetches GET /v1/config, persists a learned minVersion, and
     * returns the resulting block state. On any failure the persisted
     * state decides (offline semantics above). */
    suspend fun refreshFromServer(context: Context): Boolean {
        val config = WorkerActivationApi.fetchAppConfig()
        val min = config?.optInt("minVersion", 0) ?: 0
        if (min > 0) WorkerActivationApi.saveKnownMinVersion(context, min)
        return isUpdateRequired(context)
    }
}
