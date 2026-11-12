package sy.medpro.watani.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Phase-3 (§4.2): byte-format proof for encrypted HLS segments.
 *
 * The on-disk format is owned by security/ContentEncryption
 * (AES/GCM/NoPadding, 128-bit tag, 12-byte IV prepended:
 * `IV ‖ ciphertext ‖ tag`, i.e. plaintext size + 28 bytes) and read back
 * by EncryptedHlsDataSource through ContentEncryption.decryptFileToBytes,
 * which verifies the GCM tag with doFinal BEFORE serving any byte.
 *
 * ContentEncryption itself needs the Android Keystore and cannot run on
 * the JVM, so this test pins the FORMAT with a plain JCE key: it
 * encrypts/decrypts with exactly the same transformation, IV size and
 * tag length the production code uses, and proves (a) a segment-sized
 * payload round-trips byte-identically (including an in-segment seek
 * offset, which the DataSource serves as a plain array offset), (b) the
 * size overhead is exactly 28 bytes — the constant the at-rest check
 * (isHlsEncTreeValid) is built on, and (c) flipping one ciphertext byte
 * fails decryption with AEADBadTagException instead of yielding
 * corrupted plaintext.
 */
class HlsSegmentCryptoFormatTest {

    private fun newKey(): SecretKey =
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    /** Mirrors ContentEncryption.encryptFile's byte layout. */
    private fun encryptLikeContentEncryption(key: SecretKey, plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.iv + cipher.doFinal(plain)
    }

    /** Mirrors ContentEncryption.decryptFileToBytes' read path. */
    private fun decryptLikeContentEncryption(key: SecretKey, stored: ByteArray): ByteArray {
        val iv = stored.copyOfRange(0, 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(stored.copyOfRange(12, stored.size))
    }

    @Test
    fun segment_roundTripsByteIdentical_andSeekOffsetMatches() {
        val key = newKey()
        // Segment-sized payload (~1.6MB, the library's average segment).
        val plain = ByteArray(1_600_000)
        SecureRandom().nextBytes(plain)
        val stored = encryptLikeContentEncryption(key, plain)
        val back = decryptLikeContentEncryption(key, stored)
        assertArrayEquals(plain, back)
        // In-segment seek (EncryptedHlsDataSource serves from an offset):
        val offset = 123_457
        assertArrayEquals(
            plain.copyOfRange(offset, plain.size),
            back.copyOfRange(offset, back.size)
        )
    }

    @Test
    fun storedSize_isPlainPlus28() {
        val key = newKey()
        for (size in listOf(0, 1, 775, 4096, 1_600_000)) {
            val stored = encryptLikeContentEncryption(key, ByteArray(size))
            assertEquals("overhead must be IV(12) + tag(16)", size + 28, stored.size)
        }
    }

    @Test
    fun tamperedSegment_failsTagCheck_noPlaintextServed() {
        val key = newKey()
        val stored = encryptLikeContentEncryption(key, ByteArray(8192) { it.toByte() })
        val tampered = stored.copyOf()
        tampered[tampered.size / 2] = (tampered[tampered.size / 2].toInt() xor 0x01).toByte()
        assertThrows(AEADBadTagException::class.java) {
            decryptLikeContentEncryption(key, tampered)
        }
    }
}
