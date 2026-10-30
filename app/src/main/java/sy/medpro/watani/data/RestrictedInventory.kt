package sy.medpro.watani.data

import android.content.Context
import sy.medpro.watani.ui.security.TempDecryptedFiles
import java.io.File

/**
 * Restricted-download inventory wipe (M4 — decided by the doctor:
 * when activation ends or is cancelled, restricted content downloaded
 * on the device is wiped for certain and must not keep working offline).
 *
 * WHAT IS WIPED (restricted set only; free sections are untouched):
 *  - sections_cache: restricted section JSONs + their .tmp files
 *    (questions/, dawrat/, dawrat_jami3at/, uworld/, explanations/,
 *    books/, library.json). Free files (cases.json, guide.json,
 *    osce.json, auscultation*.json) are never deleted here.
 *  - The downloaded-sections prefs entries for restricted paths, so
 *    the UI stops listing them as downloaded.
 *  - library_files_plain (plain library files + SRT side-cars; the
 *    directory holds library items only).
 *  - encrypted_content (the ContentEncryption dir; it holds restricted
 *    library ciphertext and, after the pending repository swap,
 *    restricted section ciphertext — nothing free).
 *  - explanation_layers (explanation shard cache: M3 "no cache display"
 *    for revoked activation).
 *  - All registered decrypt temps (TempDecryptedFiles sweep).
 *
 * WHAT IS NOT WIPED: free-section files, the activation prefs' device
 * UUID fallback (it identifies the device, not content), and any
 * user progress/notes (separate stores).
 *
 * Callers: [WorkerActivationApi.notifyActivationLost] (gateway 401/403
 * and revoked refresh) and [WorkerActivationApi.clearActivationAndWipe]
 * (sign-out, deliberate deactivation). NEVER call this on a mere
 * network failure — only on a detected activation loss.
 */
object RestrictedInventory {

    /** Sanitized sections_cache name prefixes for restricted sections. */
    private val SECTION_PREFIXES = listOf(
        "questions_",
        "dawrat_",
        "dawrat_jami3at_",
        "uworld_",
        "explanations_",
        "books_"
    )

    /** True when a sections_cache file name belongs to restricted content. */
    fun isRestrictedSectionFile(fileName: String): Boolean {
        val base = fileName.removeSuffix(".tmp")
        if (base == "library.json") return true
        return SECTION_PREFIXES.any { base.startsWith(it) }
    }

    fun wipeRestrictedDownloads(context: Context) {
        // 1. sections_cache restricted files (+ .tmp leftovers).
        val sectionsDir = File(context.filesDir, "sections_cache")
        sectionsDir.listFiles()?.forEach { file ->
            if (file.isFile && isRestrictedSectionFile(file.name)) {
                try {
                    file.delete()
                } catch (_: Exception) {
                }
            }
        }
        // 2. Downloaded-sections prefs: drop restricted path entries.
        val dlPrefs = context.getSharedPreferences(
            "watani_downloaded_sections",
            Context.MODE_PRIVATE
        )
        val editor = dlPrefs.edit()
        for (key in dlPrefs.all.keys) {
            if (WorkerActivationApi.isRestrictedPath(key)) editor.remove(key)
        }
        try {
            editor.apply()
        } catch (_: Exception) {
        }
        // 3. Library dirs (library-only contents).
        deleteDirContents(File(context.filesDir, "library_files_plain"))
        try {
            deleteDirContents(
                sy.medpro.watani.security.ContentEncryption.getEncryptedDir(context)
            )
        } catch (_: Exception) {
        }
        // 4. Explanation shard cache.
        deleteDirContents(File(context.filesDir, "explanation_layers"))
        // 5. Decrypt temps (tracked + stale legacy names).
        try {
            TempDecryptedFiles.deleteAll(context)
        } catch (_: Exception) {
        }
    }

    private fun deleteDirContents(dir: File) {
        try {
            dir.listFiles()?.forEach { file ->
                try {
                    if (file.isFile) file.delete()
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }
}
