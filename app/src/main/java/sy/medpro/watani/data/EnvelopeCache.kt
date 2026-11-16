package sy.medpro.watani.data

/**
 * In-memory parsed-envelope cache (build 1.2.27, doctor's device
 * report 16 Nov 2026: «يبقى وقتًا حتى يبدأ الاختبار»).
 *
 * Before this cache, [WataniRepository.getSubjectEnvelope] re-read,
 * re-decrypted (AES-GCM) and re-parsed the WHOLE subject shard on
 * every call — and entering «دورة كاملة» swept all 27 shards twice
 * (list build + start), ~110MB of JSON parsed sequentially before
 * the quiz could open. This cache keeps the parsed envelope keyed
 * by section path, stamped with the downloaded-section registry
 * hash (computed from the verified plaintext at download time):
 * a re-download changes the hash and the stale entry can never be
 * served. A total-weight LRU bound (weight = JSON length in chars,
 * a faithful size proxy) keeps the cache from swelling on low-end
 * devices; an entry heavier than the whole budget is never stored.
 *
 * Pure (no Android, no I/O) so the invalidation and eviction rules
 * are unit-testable off-device.
 */
class EnvelopeCache<V>(
    private val maxWeight: Int = DEFAULT_MAX_WEIGHT
) {
    private class Entry<V>(val hash: String, val weight: Int, val value: V)

    // Access-ordered: iteration order is least-recently-used first.
    private val entries = LinkedHashMap<String, Entry<V>>(0, 0.75f, true)
    private var totalWeight = 0

    /** The cached value for [path] ONLY when it was parsed from
     * bytes whose registry hash is exactly [hash]; a hash mismatch
     * drops the stale entry and returns null. */
    @Synchronized
    fun get(path: String, hash: String): V? {
        val entry = entries[path] ?: return null
        if (entry.hash != hash) {
            removeLocked(path)
            return null
        }
        return entry.value
    }

    /** Stores [value] for [path] under [hash]; evicts LRU entries
     * until the total weight fits the budget. An entry heavier than
     * the budget itself is not stored (and evicts nothing). */
    @Synchronized
    fun put(path: String, hash: String, weight: Int, value: V) {
        if (weight > maxWeight) {
            removeLocked(path)
            return
        }
        removeLocked(path)
        entries[path] = Entry(hash, weight, value)
        totalWeight += weight
        val iterator = entries.entries.iterator()
        while (totalWeight > maxWeight && iterator.hasNext()) {
            val eldest = iterator.next()
            if (eldest.key == path) break
            totalWeight -= eldest.value.weight
            iterator.remove()
        }
    }

    @Synchronized
    fun evict(path: String) = removeLocked(path)

    @Synchronized
    fun clear() {
        entries.clear()
        totalWeight = 0
    }

    @Synchronized
    fun size(): Int = entries.size

    private fun removeLocked(path: String) {
        entries.remove(path)?.let { totalWeight -= it.weight }
    }

    companion object {
        /** ~64M chars of JSON ≈ the six largest subject shards —
         * generous headroom for repeat entries while staying far
         * below a low-end heap. */
        const val DEFAULT_MAX_WEIGHT = 64_000_000
    }
}
