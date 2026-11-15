package sy.medpro.watani.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Shared state of the one «ادرس مع ميد برو» pack download.
 * [completedRuns] counts SUCCESSFUL installs: screens watch it to
 * reload the pack from disk exactly once per finished download,
 * without the download itself living in any screen.
 */
data class ConceptsPackDownloadState(
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val failed: Boolean = false,
    val completedRuns: Int = 0
)

/**
 * «ادرس مع ميد برو» pack download manager (concepts fix, build
 * 1.2.21 — root-cause fix for the section never appearing on slow
 * connections).
 *
 * Why this exists: the pack's only download used to run inside a
 * screen-scoped coroutine (a LaunchedEffect in
 * rememberConceptUnits, later a rememberCoroutineScope in
 * LibraryScreen), so leaving the Library tab, an ON_RESUME refresh
 * bump, or process death killed it mid-flight — and because the
 * Pages origin ignores Range requests (verified live: a Range
 * request is answered 200 with the full 5.2MB body), every killed
 * attempt restarted from zero and never finished.
 *
 * The bank sections solved the «download must outlive the screen»
 * half of this by running downloads in MainViewModel's
 * viewModelScope with progress in a shared StateFlow. This manager
 * follows the same pattern one level lower — a process-wide
 * singleton scope (SupervisorJob, like FocusTimerEngine) — because
 * the Library's own ViewModel is created inside the Library
 * composition and the pack state is also read from the quiz screen;
 * the download must survive ALL of those compositions coming and
 * going. A screen only STARTS the download and OBSERVES [state];
 * navigating away and back shows the live progress/completion as
 * it is, and an ON_RESUME re-check never touches a running job
 * (it only refreshes the manifest status in rememberConceptUnits).
 *
 * Resume note (verified 15 Nov 2026): true byte-range resume is
 * NOT possible for this pack today — the Pages origin ignores
 * Range (200 + full body), and the only Range-capable route, the
 * gateway's R2-backed /v1/content, requires an activation token
 * and serves only its restricted allow-list, which the free
 * concepts.json is deliberately not part of. So an interrupted
 * download still restarts the transfer — but it now restarts and
 * COMPLETES in the background instead of dying silently with the
 * screen, which is the behaviour the doctor ordered («تنزيل كامل
 * صريح ثم تصفح بلا إنترنت», exactly like the banks).
 */
object ConceptsPackManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _state = MutableStateFlow(ConceptsPackDownloadState())
    val state: StateFlow<ConceptsPackDownloadState> = _state.asStateFlow()

    /**
     * Starts the pack download/update unless one is already
     * running (extra taps and re-checks are no-ops — a refresh must
     * never cancel or restart an in-flight download). The result is
     * published on [state]: success bumps
     * [ConceptsPackDownloadState.completedRuns], failure sets
     * [ConceptsPackDownloadState.failed] so the card can offer a
     * retry. Never throws.
     */
    fun startDownload(context: Context) {
        if (_state.value.downloading) return
        val appContext = context.applicationContext
        _state.update {
            it.copy(downloading = true, progress = 0f, failed = false)
        }
        job = scope.launch {
            val ok = try {
                WataniRepository(appContext).downloadConceptsPack { p ->
                    _state.update { st -> st.copy(progress = p) }
                }
            } catch (e: Exception) {
                false
            }
            _state.update { st ->
                st.copy(
                    downloading = false,
                    progress = if (ok) 1f else st.progress,
                    failed = !ok,
                    completedRuns = if (ok) st.completedRuns + 1 else st.completedRuns
                )
            }
        }
    }
}
