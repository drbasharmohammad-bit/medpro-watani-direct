package sy.medpro.watani.data

import android.content.Context
import java.io.File

/**
 * Bounded lifetime for resumable PLAINTEXT download temps
 * (audit fix, 21 Nov 2026 — SEC-S3 / SEC-S4 / SEC-101).
 *
 * Section downloads keep a plaintext `<name>.tmp` beside the
 * encrypted cache so an interrupted download can resume, and
 * non-HLS library downloads keep a plaintext `<target>.part` for
 * the same reason. That resume is a deliberate, doctor-ordered
 * behaviour on Syrian connections (a 20 MB+ section must not
 * restart from zero because the process died) — but the temps used
 * to live FOREVER when a download was abandoned: restricted
 * content sitting in plaintext with no time bound at all.
 *
 * The bound: at process start (wired into the SecurityChecks
 * startup sweep, next to TempDecryptedFiles), any such temp older
 * than [MAX_AGE_MS] is deleted. A download the student is actually
 * resuming within a day keeps its resume; an abandoned partial
 * dies within a day instead of never. Temps younger than the bound
 * remain in app-private storage only — the same exposure class as
 * the already-accepted decrypt-temp design (TempDecryptedFiles),
 * and cloud backup of the whole app is disabled (SEC-S2), so they
 * cannot leave the device through backup either.
 */
object StalePlaintextTemps {

    /** 24 hours: long enough for any realistic same-student resume,
     * short enough that abandoned restricted plaintext cannot age. */
    const val MAX_AGE_MS: Long = 24L * 60L * 60L * 1000L

    /** Pure decision (unit-testable): a temp is stale when its age
     * exceeds the bound. A future mtime (clock moved backwards) is
     * NOT stale — never delete a temp that looks younger than now. */
    fun isStale(lastModifiedMs: Long, nowMs: Long): Boolean =
        lastModifiedMs in 1L..(nowMs - MAX_AGE_MS)

    /** Deletes stale resumable plaintext temps. Returns how many
     * files were deleted. Never throws. */
    fun sweep(context: Context, nowMs: Long = System.currentTimeMillis()): Int {
        var deleted = 0
        deleted += sweepDir(
            File(context.filesDir, "sections_cache"),
            suffix = ".tmp",
            nowMs = nowMs
        )
        deleted += sweepDir(
            File(context.filesDir, "library_files_plain"),
            suffix = ".part",
            nowMs = nowMs
        )
        return deleted
    }

    private fun sweepDir(dir: File, suffix: String, nowMs: Long): Int {
        val files = try {
            dir.listFiles()?.toList()
        } catch (_: Exception) {
            null
        } ?: return 0
        return files.count { file -> isStaleTemp(file, suffix, nowMs) && deleteQuietly(file) }
    }

    private fun isStaleTemp(file: File, suffix: String, nowMs: Long): Boolean =
        try {
            file.isFile && file.name.endsWith(suffix) && isStale(file.lastModified(), nowMs)
        } catch (_: Exception) {
            false
        }

    private fun deleteQuietly(file: File): Boolean =
        try {
            file.delete()
        } catch (_: Exception) {
            false
        }
}
