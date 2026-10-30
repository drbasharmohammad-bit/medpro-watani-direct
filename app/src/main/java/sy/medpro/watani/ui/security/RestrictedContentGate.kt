package sy.medpro.watani.ui.security

import android.content.Context

/**
 * The single gating point for restricted content — Team 3, device-protection.
 *
 * RESTRICTED = the Questions section + the Library (question banks,
 * explanations, books, library files). FREE sections are never gated by
 * this object and keep working on any device.
 *
 * EXPECTED HOOK (for Team 1 / MainActivity integration):
 *  - Call [isRestrictedContentAllowed] (or [check]) before opening a
 *    restricted screen or starting a restricted download: QuizScreen,
 *    LibraryScreen, ExamSimulatorScreen, and the section-download entry
 *    points in MainViewModel. On a Blocked result, show
 *    [GateDecision.userMessage] and do NOT open the screen / start the
 *    download. Re-check on every entry, not only at launch, so a debugger
 *    attached mid-session is caught at the next entry.
 *  - If Team 1's auth-gate region provides a shared pre-screen hook, call
 *    this gate from that hook; the gate itself is standalone and does not
 *    depend on Team 1's code existing, so either wiring order works.
 *  - NOTE: this gate is NOT yet called from any screen in this branch —
 *    screens are Team 1 / MainActivity territory and this branch was
 *    limited to one MainActivity line (SecurityChecks.install). Wiring
 *    the gate into Quiz/Library entry points is the named follow-up in
 *    the report. The pre-existing library-download gate
 *    (DeviceIntegrityCheck in WataniRepository.downloadLibraryItem) keeps
 *    blocking library downloads on compromised devices in the meantime.
 *
 * EVALUATION ORDER (first hit wins, so the message names one cause):
 * emulator -> rooted -> debugger -> tamper-blocked. Tamper warnings that
 * do not block (unconfigured pin, unknown installer, debug build) never
 * produce a Blocked decision on their own.
 */
object RestrictedContentGate {

    enum class BlockReason { EMULATOR, ROOTED, DEBUGGER, TAMPERED }

    data class GateDecision(
        val allowed: Boolean,
        val reason: BlockReason?,
        val userMessage: String?,
    ) {
        companion object {
            fun allowed() = GateDecision(true, null, null)
        }
    }

    /** User-facing Arabic messages: name the cause, name what still works. */
    fun messageFor(reason: BlockReason): String = when (reason) {
        BlockReason.EMULATOR ->
            "المحتوى المحمي (الأسئلة والمكتبة) غير متاح على المحاكيات — " +
                "الأقسام المجانية تعمل كالمعتاد."
        BlockReason.ROOTED ->
            "المحتوى المحمي (الأسئلة والمكتبة) غير متاح على الأجهزة " +
                "المجذّرة (root) — الأقسام المجانية تعمل كالمعتاد."
        BlockReason.DEBUGGER ->
            "المحتوى المحمي (الأسئلة والمكتبة) غير متاح أثناء اتصال " +
                "أداة تنقيح (debugger) — افصلها ثم أعد المحاولة؛ " +
                "الأقسام المجانية تعمل كالمعتاد."
        BlockReason.TAMPERED ->
            "تعذّرت مطابقة توقيع التطبيق، فالمحتوى المحمي (الأسئلة " +
                "والمكتبة) غير متاح — الأقسام المجانية تعمل كالمعتاد."
    }

    /** Pure aggregation over the four layer verdicts (testable, no Android). */
    fun evaluate(
        isEmulator: Boolean,
        isRooted: Boolean,
        isDebuggerAttached: Boolean,
        tamperBlocked: Boolean,
    ): GateDecision {
        val reason = when {
            isEmulator -> BlockReason.EMULATOR
            isRooted -> BlockReason.ROOTED
            isDebuggerAttached -> BlockReason.DEBUGGER
            tamperBlocked -> BlockReason.TAMPERED
            else -> null
        }
        return if (reason == null) {
            GateDecision.allowed()
        } else {
            GateDecision(false, reason, messageFor(reason))
        }
    }

    /** Android entry point: runs all four layer adapters and aggregates. */
    fun check(context: Context): GateDecision {
        return evaluate(
            isEmulator = EmulatorCheckAndroid.isEmulator(),
            isRooted = RootCheckAndroid.isRooted(),
            isDebuggerAttached = DebuggerCheckAndroid.isDebuggerAttached(),
            tamperBlocked = TamperCheckAndroid.evaluate(context).blocked,
        )
    }

    /**
     * Standalone hook for MainActivity / screens / Team 1's auth gate:
     * true when restricted content may be opened or downloaded.
     */
    fun isRestrictedContentAllowed(context: Context): Boolean =
        check(context).allowed
}
