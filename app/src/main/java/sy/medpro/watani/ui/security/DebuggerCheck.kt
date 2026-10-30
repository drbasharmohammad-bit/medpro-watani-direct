package sy.medpro.watani.ui.security

import android.os.Debug
import java.io.File

/**
 * Debugger detection — Team 3, device-protection branch.
 *
 * STRUCTURE: pure decision ([isDebuggerAttached]) over two injectable
 * inputs, plus a thin Android adapter ([DebuggerCheckAndroid]).
 *
 * SIGNALS:
 *  1. Debug.isDebuggerConnected() — a JDWP debugger attached right now.
 *  2. TracerPid in /proc/self/status — nonzero while ANY tracer (debugger,
 *     strace-style tracer) is attached, including tracers that JDWP does
 *     not report. TracerPid is read fresh on every check; it is not cached.
 *
 * BEHAVIOUR: while a debugger/tracer is attached, RESTRICTED content
 * (Questions + Library) is blocked via [RestrictedContentGate]; free
 * sections keep working. The block is transient by design: detach the
 * debugger and the next gate check passes again. The check is evaluated
 * at each restricted-content entry (open/download), not once at launch,
 * so attaching a debugger mid-session is caught at the next entry.
 * (A debugger attached DURING an already-open reader is not force-closed
 * by this module — see the uncovered-path note in RestrictedFileStore and
 * the device-test checklist; closing an in-flight reader on attach would
 * need a foreground watcher, which is out of scope for this branch.)
 *
 * HONEST LIMIT: a debugger that detaches before the check, or a tracer
 * that hides TracerPid, is not detected. Native (ptrace) debuggers that
 * attach to a child process rather than this process are also outside
 * this check. This stops casual debugging, not a determined reverser.
 */
object DebuggerCheck {

    /** Pure decision: attached when JDWP reports it OR TracerPid != 0. */
    fun isDebuggerAttached(isDebuggerConnected: Boolean, tracerPid: Int?): Boolean {
        if (isDebuggerConnected) return true
        return tracerPid != null && tracerPid != 0
    }

    /** Pure helper: parse the TracerPid value out of /proc/self/status text. */
    fun parseTracerPid(statusText: String): Int? {
        for (line in statusText.lines()) {
            if (line.startsWith("TracerPid:")) {
                return line.substringAfter(":").trim().toIntOrNull()
            }
        }
        return null
    }
}

/** Thin Android adapter: reads live state, contains no decision logic. */
object DebuggerCheckAndroid {

    fun isDebuggerAttached(): Boolean {
        return DebuggerCheck.isDebuggerAttached(
            isDebuggerConnected = Debug.isDebuggerConnected(),
            tracerPid = readTracerPid(),
        )
    }

    fun readTracerPid(): Int? {
        return try {
            val text = File("/proc/self/status").readText()
            DebuggerCheck.parseTracerPid(text)
        } catch (_: Exception) {
            null
        }
    }
}
