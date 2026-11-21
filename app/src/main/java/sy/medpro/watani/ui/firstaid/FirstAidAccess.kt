package sy.medpro.watani.ui.firstaid

/**
 * Pure access + resume rules for the First Aid section (build 1.2.38) —
 * kept free of Android so the JVM tests pin them exactly.
 *
 * Activation gate (doctor's decision, 21 Nov 2026, supersedes the
 * section's original fully-free model): the first
 * [FREE_PATH_UNITS_PER_STEP] path units of EACH step, in course
 * order, are free for any signed-in student; every other path unit,
 * every integration session, and the printed banks work only for a
 * code-activated account (checked by the host through the app's own
 * WorkerActivationApi.isActivated — the same entitlement used by the
 * other paid sections, offline-honoring). A unit's booklet follows
 * its unit's gate; system booklets follow the gate of the EARLIEST
 * path unit of their system (see [bookletGateUnitId]).
 *
 * Resume derivation (doctor's defect report on 1.2.37): the home
 * «تابع من حيث توقفت» card must come from the student's OWN state —
 * the last unit they actually opened (recorded by
 * [FirstAidProgress.recordUnitOpened]) and their done set — never
 * from a fixed course position. A student with zero progress gets NO
 * resume target at all (the home shows a start card instead).
 */
object FirstAidAccessRules {

    const val FREE_PATH_UNITS_PER_STEP = 3

    /** One path's ordered ids + the student's done subset. */
    data class PathState(
        val orderedPathUnitIds: List<String>,
        val doneIds: Set<String>
    )

    data class Resume(val step: Int, val unitId: String)

    /** The free set of one path: its first [FREE_PATH_UNITS_PER_STEP]
     * units in course order. */
    fun freePathUnitIds(orderedPathUnitIds: List<String>): Set<String> =
        orderedPathUnitIds.take(FREE_PATH_UNITS_PER_STEP).toSet()

    /**
     * Can this unit open? Activated accounts open everything. Others
     * open only free path units; sessions are never free.
     */
    fun canOpenUnit(
        orderedPathUnitIds: List<String>,
        unitId: String,
        isSession: Boolean,
        activated: Boolean
    ): Boolean {
        if (activated) return true
        if (isSession) return false
        return unitId in freePathUnitIds(orderedPathUnitIds)
    }

    /** The printed banks are never free. */
    fun canOpenBanks(activated: Boolean): Boolean = activated

    /**
     * A booklet opens when its gate unit is free or the account is
     * activated. A booklet with NO gate unit (matches no system) is
     * treated as gated content: activated only.
     */
    fun canOpenBooklet(
        gateUnitId: String?,
        orderedPathUnitIds: List<String>,
        activated: Boolean
    ): Boolean {
        if (activated) return true
        val gate = gateUnitId ?: return false
        return gate in freePathUnitIds(orderedPathUnitIds)
    }

    /** Minimal system shape for booklet matching (mirrors the index DTO). */
    data class SystemInfo(
        val code: String?,
        val nameEn: String?,
        val unitIds: List<String>
    )

    /**
     * The gate unit of a system booklet: the earliest unit — in path
     * order — of the system the booklet belongs to. Matching mirrors
     * [bookletForSystem]: the booklet file name embeds the system
     * code ("-CODE-" / "_CODE.") or the system's English name
     * (normalized containment). Returns null when no system matches.
     */
    fun bookletGateUnitId(
        bookletFile: String?,
        systems: List<SystemInfo>,
        orderedPathUnitIds: List<String>
    ): String? {
        val file = bookletFile?.takeIf { it.isNotBlank() } ?: return null
        val name = file.substringAfterLast('/')
        fun norm(s: String) = s.lowercase().replace(" ", "").replace("-", "").replace("_", "")
        val matched = systems.firstOrNull { sys ->
            val code = sys.code?.takeIf { it.isNotBlank() }
            code != null && (
                name.contains("-$code-", ignoreCase = true) ||
                    name.contains("_$code.", ignoreCase = true)
                )
        } ?: systems.firstOrNull { sys ->
            val en = sys.nameEn?.takeIf { it.isNotBlank() } ?: return@firstOrNull false
            norm(file).contains(norm(en))
        } ?: return null
        val pathOrder = orderedPathUnitIds.withIndex().associate { it.value to it.index }
        return matched.unitIds
            .filter { it in pathOrder }
            .minByOrNull { pathOrder.getValue(it) }
    }

    /**
     * The resume candidate INSIDE one path, given the unit the
     * student last opened in it (null when never opened): the
     * last-opened unit itself when it is not done yet; when it IS
     * done, the next undone unit after it (wrapping to the first
     * undone unit overall); with no last-opened record, the first
     * undone unit. Null when the path is fully done or empty.
     */
    fun resumeInPath(state: PathState, lastOpenedId: String?): String? {
        val ids = state.orderedPathUnitIds
        if (ids.isEmpty()) return null
        val last = lastOpenedId?.takeIf { it in ids }
        if (last != null) {
            if (last !in state.doneIds) return last
            val idx = ids.indexOf(last)
            return ids.drop(idx + 1).firstOrNull { it !in state.doneIds }
                ?: ids.firstOrNull { it !in state.doneIds }
        }
        return ids.firstOrNull { it !in state.doneIds }
    }

    /**
     * The home resume target, or null when there is nothing to
     * resume: the student has no done unit in either path AND never
     * opened any unit (a brand-new student gets the start card, not
     * a resume card — the 1.2.37 defect). Otherwise the path the
     * student last touched wins; when that path yields no candidate
     * (fully done), or there is no last-opened record at all
     * (progress recorded before 1.2.38), the legacy order applies:
     * Step 2's candidate first, then Step 1's.
     */
    fun resumeTarget(
        lastOpenedStep: Int?,
        lastOpenedId: String?,
        step1: PathState,
        step2: PathState
    ): Resume? {
        val anyProgress = step1.doneIds.isNotEmpty() || step2.doneIds.isNotEmpty() ||
            lastOpenedId != null
        if (!anyProgress) return null
        if (lastOpenedStep == 1 || lastOpenedStep == 2) {
            val primary = if (lastOpenedStep == 1) step1 else step2
            val candidate = resumeInPath(primary, lastOpenedId)
            if (candidate != null) return Resume(lastOpenedStep, candidate)
        }
        resumeInPath(step2, null)?.let { return Resume(2, it) }
        resumeInPath(step1, null)?.let { return Resume(1, it) }
        return null
    }
}
