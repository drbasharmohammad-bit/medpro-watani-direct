package sy.medpro.watani.data

/**
 * Pure planning for the First Aid unit warm-up (1.2.45).
 *
 * The doctor's professional-cache standard (23 Nov 2026): a section
 * opens instantly from cache, content warms in the background, and no
 * student ever watches a bare spinner for content the app could have
 * fetched ahead of time. Unit packages are NOT bundled and NOT seeded
 * (valuable content never ships inside the APK), so before this
 * warm-up the FIRST open of every unit was a genuine network fetch
 * (manifest + package) behind the player's loading state — on a slow
 * connection that spinner sat on screen for a long time even though
 * the content itself was healthy.
 *
 * The warm-up walks the step index in course order and pulls every
 * package that is not already in the section cache, silently (with
 * bounded parallelism since 1.2.48), the moment the section's
 * indexes resolve. This planner decides which ids those are.
 */
object FirstAidPrefetchRules {

    /**
     * Ids still needing a fetch, in the given (course) order: blank
     * ids dropped, duplicates collapsed to their first occurrence,
     * and ids the [isCached] probe reports as present skipped — a
     * cached unit's getter returns instantly, so re-planning it into
     * the warm-up would only waste the student's bandwidth.
     */
    fun missing(ids: List<String>, isCached: (String) -> Boolean): List<String> =
        ids.filter { it.isNotBlank() }.distinct().filter { !isCached(it) }
}
