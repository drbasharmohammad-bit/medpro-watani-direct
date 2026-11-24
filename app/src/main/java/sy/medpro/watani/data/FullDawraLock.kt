package sy.medpro.watani.data

/**
 * «دورة كاملة» HARD LOCK (build 1.2.49, doctor's order 24 Nov
 * 2026): the full-dawra feature is closed for EVERYONE — signed
 * in, activated, owner; no exceptions.
 *
 * Why this exists: up to 1.2.48 the only gate on the feature was
 * the activation check inside the start action, and the list and
 * detail screens were not gated at all — so any activated student
 * (the doctor included) walked straight in and ran a dawra, while
 * the release record described the feature as "locked". The lock
 * the doctor ordered is a FEATURE lock, not an activation lock:
 * this predicate therefore takes NO state at all — there is no
 * input (activation, role, cache) that can turn it off. Every
 * entry point (entry card tap, list, detail, start, the standalone
 * dawra-file download) consults it FIRST, before any activation
 * logic.
 *
 * To reopen the feature in a future build, flip [isLocked] — the
 * single switch — and re-check every consumer.
 */
object FullDawraLock {

    /** The one switch. True = «دورة كاملة» is closed for all. */
    val isLocked: Boolean = true

    /** The badge text on the entry card, exactly as the doctor
     * worded it: the lock must be visible from OUTSIDE, before
     * any tap. */
    const val LOCK_BADGE = "مقفلة حاليًا"

    /** The only thing a tap on the locked entry may produce: this
     * message (shown in the standard gate dialog). No navigation,
     * no download, no questions. */
    const val LOCK_MESSAGE = "«دورة كاملة» مقفلة حاليًا."
}
