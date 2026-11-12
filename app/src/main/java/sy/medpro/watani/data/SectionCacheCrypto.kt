package sy.medpro.watani.data

import android.content.Context
import sy.medpro.watani.security.ContentEncryption
import java.io.File

/**
 * Phase-2 hardening (Nov 2026): THE central read/write point for
 * `filesDir/sections_cache` files.
 *
 * At rest, every section file is AES-GCM ciphertext written by
 * [ContentEncryption] (Android Keystore key `watani_content_key` — the
 * same key and byte format as the library's encrypted files). The file
 * NAMES are unchanged, so the downloaded-sections registry, the
 * restricted-wipe inventory and every existing path computation keep
 * working; only the bytes on disk change. After a section is downloaded
 * and read through this store, no plaintext question JSON remains
 * anywhere inside sections_cache.
 *
 * Verification order is preserved from the pre-hardening flow:
 * [WataniRepository.downloadSection] verifies size/hash/count on the
 * downloaded PLAINTEXT bytes first and only then calls [writeBytes] —
 * ciphertext is never verified, plaintext is never persisted.
 *
 * Legacy migration (chosen over invalidation, so already-downloaded
 * sections keep working offline after the update): on read, if GCM
 * decryption fails, the file is tried as legacy plaintext. A file that
 * parses as JSON text (starts with '{' or '[') is re-saved encrypted
 * on the spot and its plaintext is returned; anything else is treated
 * as corrupt and returns null (the section then re-downloads).
 */
object SectionCacheCrypto {

    /** Encrypts verified plaintext [bytes] into [file] (atomic). */
    fun writeBytes(context: Context, file: File, bytes: ByteArray): Boolean {
        file.parentFile?.mkdirs()
        return ContentEncryption.encryptBytesToFile(bytes, file)
    }

    /** Encrypts verified plaintext [text] into [file] (atomic). */
    fun writeText(context: Context, file: File, text: String): Boolean =
        writeBytes(context, file, text.toByteArray(Charsets.UTF_8))

    /**
     * The single section-read path: decrypts [file], migrating a legacy
     * plaintext JSON file to encrypted form on first read. Returns null
     * when the file is missing, undecryptable and not legacy JSON.
     */
    fun readText(context: Context, file: File): String? {
        if (!file.exists()) return null
        ContentEncryption.decryptFileToText(file)?.let { return it }
        // Legacy plaintext (pre-hardening cache): accept only real JSON
        // text, then immediately re-save it encrypted.
        val plain = try {
            file.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            return null
        }
        val trimmed = plain.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return null
        writeText(context, file, plain)
        return plain
    }
}
