package sy.medpro.watani.ui.security

import android.content.Context
import java.io.File

/**
 * Registry for on-device plaintext temp files — Team 3, device-protection.
 *
 * INVARIANT: plaintext of restricted content may exist ONLY as a temp file
 * in the app's private cacheDir, and it must be deleted when its reader
 * closes. This registry is the safety net under the per-reader close paths:
 *  - every temp file created through [newTempFile]/[track] is registered;
 *  - [delete] removes one file (called by reader close paths);
 *  - [deleteAll] removes every tracked temp plus a sweep of the legacy
 *    `decrypt_temp_*` names the library path already used, and is called
 *    from [SecurityChecks] when MainActivity is destroyed (app close).
 *
 * NOTE on backgrounding: [deleteAll] is deliberately NOT called on
 * onStop/onPause. Deleting a temp while its PDF/video reader is merely
 * backgrounded would break resume (the reader holds the path, not the
 * bytes). Backgrounded temps are covered instead by: reader onDispose /
 * back-navigation close, ViewModel close, and the destroy sweep. The
 * remaining window — a temp surviving a process kill — is documented as
 * uncovered in RestrictedFileStore: cacheDir is private to the app UID,
 * but a rooted device can still read it. Nothing here claims otherwise.
 */
object TempDecryptedFiles {

    private const val PREFIX = "restricted_tmp_"
    private const val LEGACY_PREFIX = "decrypt_temp_"

    private val tracked = mutableSetOf<File>()
    private val lock = Any()

    /** Create + register a temp file in cacheDir for [key] (sanitized). */
    fun newTempFile(context: Context, key: String): File {
        val safe = key.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(context.cacheDir, "$PREFIX$safe")
        synchronized(lock) { tracked += file }
        return file
    }

    /** Register an already-created temp file (e.g. a legacy decrypt temp). */
    fun track(file: File) {
        synchronized(lock) { tracked += file }
    }

    /** Delete one temp file; returns true if it is gone afterwards. */
    fun delete(file: File): Boolean {
        synchronized(lock) { tracked.remove(file) }
        return try {
            if (file.exists()) file.delete()
            !file.exists()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Delete every tracked temp + sweep stale `restricted_tmp_*` and legacy
     * `decrypt_temp_*` files from cacheDir. Returns the number deleted.
     * Called on app close (see SecurityChecks) and safe to call any time.
     */
    fun deleteAll(context: Context): Int {
        var deleted = 0
        val snapshot: List<File>
        synchronized(lock) {
            snapshot = tracked.toList()
            tracked.clear()
        }
        for (file in snapshot) {
            try {
                if (file.exists() && file.delete()) deleted++
            } catch (_: Exception) {
            }
        }
        try {
            val cache = context.cacheDir
            val stale = cache?.listFiles { f ->
                f.isFile && (f.name.startsWith(PREFIX) || f.name.startsWith(LEGACY_PREFIX))
            } ?: emptyArray()
            for (file in stale) {
                try {
                    if (file.delete()) deleted++
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
        return deleted
    }

    fun trackedCount(): Int = synchronized(lock) { tracked.size }
}
