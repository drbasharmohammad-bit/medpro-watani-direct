package sy.medpro.watani.data

/**
 * Pure-Kotlin BLAKE2b (RFC 7693), unkeyed, sequential mode.
 *
 * Needed for manifest verification (F-01): the `questions/` family
 * records its manifest hash as blake2b with an 8-byte digest
 * (Python `hashlib.blake2b(data, digest_size=8).hexdigest()` on the
 * publishing side — see phase-4 audit), and Android's platform crypto
 * providers do not expose BLAKE2b, so the digest is computed here.
 * Correctness is pinned by unit tests against Python-generated
 * vectors (Blake2bTest) and against the live unified shards.
 */
object Blake2b {

    private val IV = longArrayOf(
        0x6a09e667f3bcc908uL.toLong(), 0xbb67ae8584caa73buL.toLong(),
        0x3c6ef372fe94f82buL.toLong(), 0xa54ff53a5f1d36f1uL.toLong(),
        0x510e527fade682d1uL.toLong(), 0x9b05688c2b3e6c1fuL.toLong(),
        0x1f83d9abfb41bd6buL.toLong(), 0x5be0cd19137e2179uL.toLong()
    )

    private val SIGMA = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
        intArrayOf(14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3),
        intArrayOf(11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4),
        intArrayOf(7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8),
        intArrayOf(9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13),
        intArrayOf(2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9),
        intArrayOf(12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11),
        intArrayOf(13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10),
        intArrayOf(6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5),
        intArrayOf(10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0)
    )

    private fun rotr(x: Long, n: Int): Long = (x ushr n) or (x shl (64 - n))

    private fun littleEndianLong(buf: ByteArray, offset: Int): Long {
        var v = 0L
        for (i in 0 until 8) {
            v = v or ((buf[offset + i].toLong() and 0xFF) shl (8 * i))
        }
        return v
    }

    /** One-shot digest of [data] with [digestSize] bytes of output (1..64). */
    fun hash(data: ByteArray, digestSize: Int = 8): ByteArray {
        require(digestSize in 1..64) { "digestSize out of range" }
        val h = IV.copyOf()
        // Parameter block (unkeyed, fanout 1, depth 1):
        // h[0] ^= 0x01010000 ^ digestSize
        h[0] = h[0] xor (0x01010000L or digestSize.toLong())

        var offset = 0
        var bytesCompressed = 0L
        // All blocks except the last are compressed with last=false.
        while (data.size - offset > 128) {
            bytesCompressed += 128
            compress(h, data, offset, bytesCompressed, last = false)
            offset += 128
        }
        val remaining = data.size - offset
        val lastBlock = ByteArray(128)
        System.arraycopy(data, offset, lastBlock, 0, remaining)
        bytesCompressed += remaining
        compress(h, lastBlock, 0, bytesCompressed, last = true)

        val out = ByteArray(digestSize)
        var produced = 0
        var word = 0
        while (produced < digestSize) {
            val le = h[word]
            var b = 0
            while (b < 8 && produced < digestSize) {
                out[produced] = ((le ushr (8 * b)) and 0xFF).toByte()
                produced++
                b++
            }
            word++
        }
        return out
    }

    fun hashHex(data: ByteArray, digestSize: Int = 8): String =
        hash(data, digestSize).joinToString("") { "%02x".format(it) }

    private fun compress(h: LongArray, block: ByteArray, blockOffset: Int, counter: Long, last: Boolean) {
        val m = LongArray(16) { littleEndianLong(block, blockOffset + it * 8) }
        val v = LongArray(16)
        for (i in 0 until 8) {
            v[i] = h[i]
            v[i + 8] = IV[i]
        }
        v[12] = v[12] xor counter
        // Counter high word is zero for any realistic file size.
        if (last) v[14] = v[14] xor -1L

        fun g(a: Int, b: Int, c: Int, d: Int, x: Long, y: Long) {
            v[a] = v[a] + v[b] + x
            v[d] = rotr(v[d] xor v[a], 32)
            v[c] = v[c] + v[d]
            v[b] = rotr(v[b] xor v[c], 24)
            v[a] = v[a] + v[b] + y
            v[d] = rotr(v[d] xor v[a], 16)
            v[c] = v[c] + v[d]
            v[b] = rotr(v[b] xor v[c], 63)
        }

        for (round in 0 until 12) {
            val s = SIGMA[round % 10]
            g(0, 4, 8, 12, m[s[0]], m[s[1]])
            g(1, 5, 9, 13, m[s[2]], m[s[3]])
            g(2, 6, 10, 14, m[s[4]], m[s[5]])
            g(3, 7, 11, 15, m[s[6]], m[s[7]])
            g(0, 5, 10, 15, m[s[8]], m[s[9]])
            g(1, 6, 11, 12, m[s[10]], m[s[11]])
            g(2, 7, 8, 13, m[s[12]], m[s[13]])
            g(3, 4, 9, 14, m[s[14]], m[s[15]])
        }
        for (i in 0 until 8) {
            h[i] = h[i] xor v[i] xor v[i + 8]
        }
    }
}
