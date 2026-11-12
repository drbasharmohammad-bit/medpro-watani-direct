package sy.medpro.watani.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object ContentEncryption {
    private const val KEY_ALIAS = "watani_content_key"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val IV_SIZE = 12

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

        if (keyStore.containsAlias(KEY_ALIAS)) {
            return keyStore.getKey(KEY_ALIAS, null) as SecretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun encryptFile(context: Context, plainFile: File, encryptedFile: File, onProgress: ((Float) -> Unit)? = null): Boolean {
        return try {
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val total = plainFile.length().coerceAtLeast(1L)
            var processed = 0L
            var lastReported = 0L

            FileOutputStream(encryptedFile).use { output ->
                output.write(iv)
                FileInputStream(plainFile).use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        val encrypted = cipher.update(buffer, 0, bytesRead)
                        if (encrypted != null) {
                            output.write(encrypted)
                        }
                        processed += bytesRead
                        if (processed - lastReported >= 1024 * 1024) {
                            lastReported = processed
                            onProgress?.invoke((processed.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                    val finalBytes = cipher.doFinal()
                    if (finalBytes != null) {
                        output.write(finalBytes)
                    }
                }
            }
            onProgress?.invoke(1f)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun decryptFile(context: Context, encryptedFile: File, outputFile: File): Boolean {
        return try {
            val key = getOrCreateKey()

            FileInputStream(encryptedFile).use { input ->
                val iv = ByteArray(IV_SIZE)
                if (input.read(iv) != IV_SIZE) {
                    return false
                }

                val cipher = Cipher.getInstance(TRANSFORMATION)
                val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                cipher.init(Cipher.DECRYPT_MODE, key, spec)

                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        val decrypted = cipher.update(buffer, 0, bytesRead)
                        if (decrypted != null) {
                            output.write(decrypted)
                        }
                    }
                    val finalBytes = cipher.doFinal()
                    if (finalBytes != null) {
                        output.write(finalBytes)
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ------------------------------------------------------------------
    // Phase-2 hardening (Nov 2026): in-memory byte helpers, same format
    // as encryptFile/decryptFile (12-byte IV prepended, AES/GCM/NoPadding,
    // 128-bit tag, same Android Keystore key). Used by the sections_cache
    // store (data/SectionCacheCrypto), which verifies plaintext BEFORE
    // saving and therefore encrypts bytes it already holds instead of
    // round-tripping a plaintext file on disk.
    // ------------------------------------------------------------------

    /** Encrypts [plain] and writes IV+ciphertext to [out] atomically
     * (temp file in the same directory + checked rename). */
    fun encryptBytesToFile(plain: ByteArray, out: File): Boolean {
        return try {
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val tmp = File(out.parentFile, out.name + ".enc.tmp")
            FileOutputStream(tmp).use { output ->
                output.write(cipher.iv)
                output.write(cipher.doFinal(plain))
            }
            if (out.exists()) out.delete()
            if (!tmp.renameTo(out)) {
                tmp.delete()
                return false
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** Decrypts a file written by [encryptBytesToFile] / [encryptFile].
     * Returns null on ANY failure — including a legacy plaintext file,
     * whose bytes fail the GCM tag check; callers use that signal for
     * one-time plaintext migration (see SectionCacheCrypto). */
    fun decryptFileToBytes(encryptedFile: File): ByteArray? {
        return try {
            if (!encryptedFile.exists() || encryptedFile.length() <= IV_SIZE) return null
            val key = getOrCreateKey()
            FileInputStream(encryptedFile).use { input ->
                val iv = ByteArray(IV_SIZE)
                if (input.read(iv) != IV_SIZE) return null
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
                val rest = input.readBytes()
                cipher.doFinal(rest)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun encryptTextToFile(text: String, out: File): Boolean =
        encryptBytesToFile(text.toByteArray(Charsets.UTF_8), out)

    fun decryptFileToText(encryptedFile: File): String? =
        decryptFileToBytes(encryptedFile)?.let { String(it, Charsets.UTF_8) }

    fun getEncryptedDir(context: Context): File {
        val dir = File(context.filesDir, "encrypted_content")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
}
