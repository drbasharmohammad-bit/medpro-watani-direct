package sy.medpro.watani.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Bounded-parallel map for independent fetches (build 1.2.48,
 * performance list item 9 — independent requests in parallel).
 *
 * Before this helper, every multi-file download in the data layer
 * ran strictly sequentially: the subject+years pair staged one file
 * after the other, the First Aid warm-up pulled unit packages one
 * by one, and an HLS video fetched its segments in a single-file
 * chain. [mapBounded] runs the per-item blocks concurrently with a
 * hard in-flight cap — fast on multi-core phones and multi-path
 * networks, gentle on memory and on the server — and returns the
 * results in input order. A failing block fails the whole map
 * (callers that tolerate per-item failure catch inside the block).
 *
 * Pure coroutine logic (no Android, no I/O of its own) so the cap
 * and ordering are unit-testable off-device.
 */
object ParallelFetch {

    /** Default in-flight cap for section downloads. */
    const val DOWNLOAD_CONCURRENCY = 3

    /** In-flight cap for HLS segment downloads (many small files). */
    const val SEGMENT_CONCURRENCY = 4

    suspend fun <T, R> mapBounded(
        items: List<T>,
        maxConcurrency: Int,
        block: suspend (T) -> R
    ): List<R> {
        require(maxConcurrency >= 1) { "maxConcurrency must be >= 1" }
        if (items.isEmpty()) return emptyList()
        val semaphore = Semaphore(maxConcurrency)
        return coroutineScope {
            items.map { item ->
                async { semaphore.withPermit { block(item) } }
            }.awaitAll()
        }
    }
}
