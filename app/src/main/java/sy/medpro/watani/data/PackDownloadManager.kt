package sy.medpro.watani.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Shared state of the self-loading packs' first downloads, keyed
 * by section path. [completedRuns] counts SUCCESSFUL installs per
 * path: screens watch their path's counter to reload the pack from
 * disk exactly once per finished download, without the download
 * itself living in any screen.
 */
data class PackDownloadState(
    val downloading: Set<String> = emptySet(),
    val progress: Map<String, Float> = emptyMap(),
    val failed: Set<String> = emptySet(),
    val completedRuns: Map<String, Int> = emptyMap()
)

/**
 * First-download manager for the self-loading packs (build 1.2.45,
 * cache audit fix F6) — DDx, «اقلبها», the ECG course, OSCE
 * stations + procedures, dosing drills, auscultation, the study
 * guide and the German cases.
 *
 * Why this exists: each pack's first download used to run inside
 * its screen's own coroutine (a LaunchedEffect / rememberCoroutine
 * Scope in the screen), so leaving the screen mid-download killed
 * the job, and the next entry restarted the transfer (the Range
 * resume in downloadSection only softened this). That is the exact
 * defect class the concepts pack already solved with
 * ConceptsPackManager and the banks solved with MainViewModel's
 * scope. This manager applies the same pattern to the packs: a
 * process-wide singleton scope (SupervisorJob) owns the jobs, a
 * screen only STARTS its pack's download and OBSERVES [state] —
 * navigating away and back shows the live progress, and completion
 * is announced on [state] for whichever composition is present.
 *
 * Contract: [startDownload] is a no-op while the path is already
 * downloading (a re-entry or re-check never restarts an in-flight
 * job); success bumps the path's completedRuns and clears its
 * failure mark; failure marks the path failed and NEVER bumps
 * completedRuns. The job itself is the packs' historical first-
 * download flow — manifest, then the verified atomic
 * [WataniRepository.downloadSection] — unchanged.
 */
object PackDownloadManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(PackDownloadState())
    val state: StateFlow<PackDownloadState> = _state.asStateFlow()

    /** Starts [path]'s first download on the process scope unless
     * one is already running for it. Never throws. */
    fun startDownload(context: Context, path: String) {
        if (!markStarted(path)) return
        val appContext = context.applicationContext
        scope.launch {
            val ok = try {
                val repo = WataniRepository(appContext)
                val entry = repo.fetchManifest().getOrNull()?.files?.get(path)
                entry != null && repo.downloadSection(path, entry) { p ->
                    markProgress(path, p)
                }.isSuccess
            } catch (e: Exception) {
                false
            }
            markFinished(path, ok)
        }
    }

    /** Marks [path] downloading; false when it already was (the
     * caller must then NOT start a second job). CAS loop so two
     * near-simultaneous starts can never both win. */
    internal fun markStarted(path: String): Boolean {
        while (true) {
            val current = _state.value
            if (path in current.downloading) return false
            val next = current.copy(
                downloading = current.downloading + path,
                progress = current.progress + (path to 0f),
                failed = current.failed - path
            )
            if (_state.compareAndSet(current, next)) return true
        }
    }

    /** Publishes the live progress of [path]'s running download. */
    internal fun markProgress(path: String, value: Float) {
        _state.update { st -> st.copy(progress = st.progress + (path to value)) }
    }

    /** Settles [path]'s download: success bumps its completedRuns
     * (the screens' reload trigger) and clears the failure mark;
     * failure marks it failed and leaves completedRuns untouched. */
    internal fun markFinished(path: String, success: Boolean) {
        _state.update { st ->
            st.copy(
                downloading = st.downloading - path,
                progress = st.progress - path,
                failed = if (success) st.failed - path else st.failed + path,
                completedRuns = if (success) {
                    st.completedRuns + (path to ((st.completedRuns[path] ?: 0) + 1))
                } else {
                    st.completedRuns
                }
            )
        }
    }

    /** Test-only reset of the shared bookkeeping. */
    internal fun resetForTest() {
        _state.value = PackDownloadState()
    }
}
