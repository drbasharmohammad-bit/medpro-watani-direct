package sy.medpro.watani.ui.security

import android.content.Context
import sy.medpro.watani.security.ContentEncryption
import java.io.File

/**
 * Generalized encrypted-at-rest store for ALL downloaded restricted files
 * — Team 3, device-protection branch.
 *
 * WHAT IT GENERALIZES: the existing library-only pattern in
 * data/WataniRepository (getLibraryEncryptedFile + openLibraryItem +
 * closeLibraryItem, built on security/ContentEncryption AES/GCM with the
 * Android Keystore key `watani_content_key`) is extended here to every
 * restricted download, with ONE storage layout and ONE temp discipline:
 *
 *  - At rest: `<filesDir>/encrypted_content/<sanitized-key>.enc`
 *    (the same dir ContentEncryption.getEncryptedDir() already uses, so
 *    library files keep their existing location scheme and no second
 *    keystore key is introduced — the existing key mechanism is REUSED).
 *  - In use: plaintext ONLY as a cacheDir temp via TempDecryptedFiles,
 *    deleted on close (see the close-path list below).
 *
 * COVERED FILE TYPES (restricted set = Questions section + Library):
 *  - Question banks: questions/<name>.json, dawrat/<name>.json,
 *    dawrat_jami3at/<sub>/<name>.json, uworld/<name>.json (via
 *    [isRestrictedPath]).
 *  - Explanations: explanations/<name>.json.
 *  NOTE: do not write glob patterns with a slash followed by a star
 *  inside this KDoc — Kotlin block comments NEST, so such a sequence
 *  opens a nested comment and breaks compilation (this file shipped
 *  with that exact latent bug: it never compiled before this fix).
 *  - Library index: library.json.
 *  - Library items (books/videos): already encrypted by the repository's
 *    library path; this store gives them the same temp registry so the
 *    app-close sweep covers them too. (Their on-disk names stay
 *    repository-owned; do not double-encrypt them through this store.)
 *  - NOT covered here (free sections, intentionally plaintext):
 *    cases.json, guide.json, osce.json, auscultation*.json, eponyms and
 *    other free content — encrypting free content would add cost with
 *    no protection benefit, and the brief restricts the set.
 *
 * INTEGRATION STATUS — READ HONESTLY:
 * This module implements the full generalized mechanism (store, read,
 * migrate, temp discipline) and is wired to app close via SecurityChecks.
 * The repository call-site swap for question JSONs (downloadSection
 * writing .enc + getSubjectEnvelope/getCasesEnvelope decrypting through
 * here) is NOT applied in this branch: swapping the section readers
 * without a compiling build to verify against risks breaking question
 * loading outright, and local/CI builds are unavailable on this branch
 * (no push to main). The swap is a small, enumerated follow-up — see
 * "REPOSITORY SWAP (pending)" below — and until it lands, question JSONs
 * and library.json remain plaintext in sections_cache (pre-existing
 * state, unchanged by this branch). Library items are encrypted today
 * (pre-existing) and stay encrypted. Nothing in this file claims the
 * question-JSON swap is done.
 *
 * REPOSITORY SWAP (pending, for the follow-up that has a build to test):
 *  1. WataniRepository.downloadSection: after the count verification,
 *     encrypt the verified file with [storePlaintext] instead of leaving
 *     the plaintext file, and record the section as downloaded.
 *  2. WataniRepository.getSubjectEnvelope/getCasesEnvelope/getGuideEnvelope
 *     (and getLibraryEnvelope for library.json): replace the direct
 *     readText with [readRestrictedText], which decrypts to a registered
 *     temp, reads it, and deletes the temp in a finally block.
 *  3. WataniRepository.getSectionFile callers that read the file directly
 *     (ui/drawer/AllDrawerScreens sections_cache scans, ExamSimulatorScreen)
 *     must go through the repository instead of the raw dir, or the raw
 *     dir stays as a plaintext bypass.
 *  4. One-time migration on first launch after the swap: [migratePlainFile]
 *     for every sections_cache file whose path [isRestrictedPath] matches,
 *     then delete the plaintext original (migrate only deletes the plain
 *     file after encryptFile returns true).
 *
 * TEMP-DELETION PATHS (implemented in this branch):
 *  P1. Reader back-navigation: LibraryScreen.onReaderBack already calls
 *      LibraryViewModel.closeItem -> repository.closeLibraryItem, which
 *      deletes the library decrypt temp. (Pre-existing; unchanged.)
 *  P2. Reader dispose: PdfViewerScreen DisposableEffect closes the
 *      renderer/bitmap; the temp itself is deleted by P1. VideoPlayerScreen
 *      releases the player on dispose; temp deleted by P1.
 *  P3. App close: SecurityChecks registers an ActivityLifecycleCallbacks
 *      that calls TempDecryptedFiles.deleteAll on MainActivity destroy —
 *      this sweeps tracked temps AND stale legacy decrypt_temp_* files.
 *  P4. This store's own reads: [readRestrictedText] deletes its temp in
 *      a finally block, so section reads never leave a temp behind even
 *      if parsing throws.
 *  P5. Item delete: repository.deleteLibraryItem deletes plain + enc +
 *      subtitle (pre-existing; unchanged).
 *
 * PATHS NOT COVERED (documented, not hidden):
 *  U1. Process kill (crash / force-stop / swiped from recents with the
 *      process killed): no finally block or lifecycle callback runs, so a
 *      temp open at that moment survives until the next app START sweep
 *      (M13: SecurityChecks.install sweeps once per process) or the next
 *      open of the same item (openLibraryItem deletes a stale temp
 *      first). The destroy sweep itself is isFinishing-guarded (M13),
 *      so rotation never wipes a live reader's temp.
 *  U2. A debugger attached DURING an open reader: the gate blocks the
 *      NEXT restricted entry, but does not force-close an already-open
 *      reader or wipe its temp mid-read. Closing in-flight readers on
 *      attach needs a foreground watcher (out of scope here).
 *  U3. SRT subtitle side-cars: stored plaintext next to library items
 *      (pre-existing design; subtitles are text tracks, not the video).
 *      Encrypting them is possible through this store but was not done,
 *      to avoid breaking the player's subtitle loading unverified.
 *  U4. Direct sections_cache readers (AllDrawerScreens, ExamSimulatorScreen)
 *      bypass this store until the repository swap (step 3 above) lands.
 *
 * HONEST LIMIT (applies to the whole encryption layer): AES/GCM via the
 * Android Keystore protects files at rest against casual copying (another
 * app, a file-manager browse, a backup restore onto another device — the
 * key does not leave the device). Against a determined attacker WITH a
 * rooted device, the key is usable by any process running as the app UID
 * and plaintext can be dumped from memory while a reader is open. This
 * layer stops direct download + casual copying; it does not make content
 * unextractable from a compromised device. Never describe it as such.
 */
object RestrictedFileStore {

    /** Manifest-path prefixes (and the library index) that are restricted. */
    private val RESTRICTED_PREFIXES = listOf(
        "questions/",
        "dawrat/",
        "dawrat_jami3at/",
        "uworld/",
        "explanations/",
        // M11: books/ was missing here (the gateway list already had it),
        // so book files were not classified as restricted by this store.
        "books/",
    )

    /** True for manifest paths whose downloads must be stored encrypted. */
    fun isRestrictedPath(path: String): Boolean {
        val p = path.trimStart('/')
        if (p == "library.json") return true
        return RESTRICTED_PREFIXES.any { p.startsWith(it) }
    }

    /** Encrypted-at-rest file for a manifest path, in the shared enc dir. */
    fun encryptedFileFor(context: Context, path: String): File {
        val dir = ContentEncryption.getEncryptedDir(context)
        val safe = path.replace('/', '_').replace('\\', '_')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(dir, "$safe.enc")
    }

    /**
     * Store a verified plaintext file encrypted, then delete the plaintext
     * original ONLY if encryption succeeded. Returns true on success.
     * This is the download-completion step for the pending repository swap.
     */
    fun storePlaintext(context: Context, plainFile: File, path: String): Boolean {
        val encFile = encryptedFileFor(context, path)
        val ok = ContentEncryption.encryptFile(context, plainFile, encFile)
        if (ok) {
            plainFile.delete()
        }
        return ok
    }

    /** One-time migration of an existing plaintext download to encrypted. */
    fun migratePlainFile(context: Context, plainFile: File, path: String): Boolean {
        if (!plainFile.exists()) return false
        val encFile = encryptedFileFor(context, path)
        if (encFile.exists()) {
            // Already migrated; drop the plaintext leftover.
            plainFile.delete()
            return !plainFile.exists()
        }
        return storePlaintext(context, plainFile, path)
    }

    /** True when an encrypted copy exists and is non-trivially sized. */
    fun isStoredEncrypted(context: Context, path: String): Boolean {
        val encFile = encryptedFileFor(context, path)
        // 12-byte IV + 16-byte GCM tag minimum, as in the library path.
        return encFile.exists() && encFile.length() >= 28
    }

    /**
     * Decrypt a stored restricted file to a registered temp, read it as
     * text, and delete the temp in a finally block (path P4). Returns null
     * when no encrypted copy exists or decryption/parsing fails.
     * Callers must NOT keep the temp: only the returned String escapes.
     */
    fun readRestrictedText(context: Context, path: String): String? {
        val encFile = encryptedFileFor(context, path)
        if (!encFile.exists()) return null
        val temp = TempDecryptedFiles.newTempFile(context, path)
        try {
            if (temp.exists()) temp.delete()
            val ok = ContentEncryption.decryptFile(context, encFile, temp)
            if (!ok || !temp.exists()) return null
            return temp.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            return null
        } finally {
            TempDecryptedFiles.delete(temp)
        }
    }

    /**
     * Decrypt for a reader that needs a FILE (PDF/video), not a String.
     * The caller OWNS deletion: it must call [TempDecryptedFiles.delete]
     * (or the item's close path) when the reader closes. The temp is
     * registered so the app-close sweep (P3) still catches it if the
     * caller forgets.
     */
    fun decryptForReader(context: Context, path: String): File? {
        val encFile = encryptedFileFor(context, path)
        if (!encFile.exists()) return null
        val temp = TempDecryptedFiles.newTempFile(context, path)
        if (temp.exists()) temp.delete()
        val ok = ContentEncryption.decryptFile(context, encFile, temp)
        if (!ok || !temp.exists()) {
            TempDecryptedFiles.delete(temp)
            return null
        }
        return temp
    }
}
