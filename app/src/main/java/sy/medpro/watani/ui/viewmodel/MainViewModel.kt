package sy.medpro.watani.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sy.medpro.watani.data.*

enum class AppTab(val title: String) {
    HOME("الرئيسية"),
    BANK("بنك الأسئلة"),
    UNIVERSITIES("الجامعات"),
    UWORLD("UWorld"),
    CASES("المريض الافتراضي"),
    LIBRARY("المكتبة"),
    LEADERBOARD("الصدارة"),
    PROGRESS("تقدمي")
}

sealed class ScreenState {
    object Home : ScreenState()
    data class TopicSelection(val path: String, val subjectName: String) : ScreenState()
    data class UniversitySubjects(val universityKey: String, val universityName: String) : ScreenState()
    data class Quiz(val title: String, val questions: List<QuestionDto>) : ScreenState()
    data class CasePlayer(val caseItem: ClinicalCaseDto) : ScreenState()
}

data class UiState(
    val currentTab: AppTab = AppTab.HOME,
    val manifestVersion: String? = null,
    val files: Map<String, ManifestFileEntry> = emptyMap(),
    val isLoadingManifest: Boolean = false,
    val manifestError: String? = null,
    val downloadingPaths: Set<String> = emptySet(),
    /** Live download progress per section path (0f..1f), so a slow
     * section download shows a percentage instead of an endless
     * spinner — the same fix as the library book downloads. */
    val downloadingProgress: Map<String, Float> = emptyMap(),
    val downloadError: String? = null,
    /** Section path whose questions downloaded but whose explanations did
     * not finish prefetching (bundle rule) — the Home error banner offers
     * a retry for this path only, without re-downloading the questions. */
    val explanationFailedPath: String? = null,
    /** Back-stack of detail screens (Quiz → TopicSelection → … → Home),
     * so back steps through the menus the user came through instead of
     * jumping straight to Home. Cleared on tab switch and on Home. */
    val backStack: List<ScreenState> = emptyList(),
    /** Device-gate block message (M8), shown as a dialog by MainActivity. */
    val gateMessage: String? = null,
    val screenState: ScreenState = ScreenState.Home
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WataniRepository(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val downloadedSections: StateFlow<List<DownloadedSectionEntity>> = repository.downloadedSectionsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        fetchManifest()
    }

    fun fetchManifest() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingManifest = true, manifestError = null) }
            val result = repository.fetchManifest()
            result.fold(
                onSuccess = { manifest ->
                    _uiState.update {
                        it.copy(
                            isLoadingManifest = false,
                            manifestVersion = manifest.version,
                            files = manifest.files ?: emptyMap()
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingManifest = false,
                            manifestError = error.localizedMessage ?: "تعذر جلب الفهرس"
                        )
                    }
                }
            )
        }
    }

    fun setTab(tab: AppTab) {
        _uiState.update { it.copy(currentTab = tab, backStack = emptyList(), screenState = ScreenState.Home) }
    }

    fun downloadSection(path: String, entry: ManifestFileEntry) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    downloadingPaths = it.downloadingPaths + path,
                    downloadingProgress = it.downloadingProgress + (path to 0f),
                    downloadError = null
                )
            }
            val result = repository.downloadSection(path, entry) { p ->
                _uiState.update { st -> st.copy(downloadingProgress = st.downloadingProgress + (path to p)) }
            }
            result.fold(
                onSuccess = { _ ->
                    // Bundle rule: a section download is not complete until
                    // its explanations are on the device too.
                    val prefetchError = prefetchExplanations(path)
                    _uiState.update { st ->
                        st.copy(
                            downloadingPaths = st.downloadingPaths - path,
                            downloadingProgress = st.downloadingProgress - path,
                            downloadError = if (prefetchError != null) prefetchError
                                else if (st.explanationFailedPath == path) null else st.downloadError,
                            explanationFailedPath = if (prefetchError != null) path
                                else if (st.explanationFailedPath == path) null else st.explanationFailedPath
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            downloadingPaths = it.downloadingPaths - path,
                            downloadingProgress = it.downloadingProgress - path,
                            // M3: revocation text stands alone (Arabic,
                            // no "download failed" prefix); the prompt
                            // itself fires from the repository event.
                            downloadError = if (error is ActivationRevokedException) {
                                error.localizedMessage
                            } else {
                                // M14: Arabic only — the raw exception text
                                // stays in the failure cause, never in UI.
                                "فشل تحميل $path — تحقق من الاتصال وحاول مجددًا."
                            }
                        )
                    }
                }
            )
        }
    }

    /** Reloads the downloaded list after an external wipe (sign-out,
     * deactivation, revocation) so badges stop showing wiped sections. */
    fun refreshDownloadedSections() {
        repository.refreshDownloadedSections()
    }

    /** Prefetches the explanation shards for a downloaded section's questions.
     * Returns null when explanations are complete (or the section has no
     * questions / no layer), else the Arabic incomplete message for the banner. */
    private suspend fun prefetchExplanations(path: String): String? {
        val incomplete =
            "تم تحميل أسئلة القسم، لكن شروحاتها لم تكتمل — اضغط «إعادة محاولة الشروحات» لإكمالها."
        return try {
            val envelope = repository.getSubjectEnvelope(path) ?: return null
            val ids = (envelope.topics.orEmpty().flatMap { it.questions.orEmpty() } +
                envelope.sessions.orEmpty().flatMap { it.questions.orEmpty() })
                .mapNotNull { it.id }
            if (ids.isEmpty()) return null
            val repo = sy.medpro.watani.ui.explanation.ExplanationRepository(
                getApplication<Application>().applicationContext
            )
            if (repo.prefetchForQuestionIds(ids).isSuccess) null else incomplete
        } catch (_: Exception) {
            incomplete
        }
    }

    /** Retries only the explanation prefetch for the failed path — the
     * questions are already on disk and are never re-downloaded. */
    fun retryExplanationPrefetch() {
        val path = _uiState.value.explanationFailedPath ?: return
        viewModelScope.launch {
            val error = prefetchExplanations(path)
            _uiState.update { st ->
                st.copy(
                    downloadError = if (error != null) error
                        else if (st.explanationFailedPath == path) null else st.downloadError,
                    explanationFailedPath = if (error != null) path
                        else if (st.explanationFailedPath == path) null else st.explanationFailedPath
                )
            }
        }
    }

    /** Step-by-step back navigation: every detail screen pushes the screen
     * it came from, so back restores it instead of jumping to Home. */
    private fun navigateTo(state: ScreenState) {
        _uiState.update { current ->
            if (current.screenState == state) current
            else current.copy(
                backStack = (current.backStack + current.screenState).takeLast(24),
                screenState = state
            )
        }
    }

    fun goBack() {
        _uiState.update { current ->
            val previous = current.backStack.lastOrNull()
            if (previous != null) current.copy(
                backStack = current.backStack.dropLast(1),
                screenState = previous
            )
            else current.copy(backStack = emptyList(), screenState = ScreenState.Home)
        }
    }

    fun clearGateMessage() {
        _uiState.update { it.copy(gateMessage = null) }
    }

    fun navigateToHome() {
        _uiState.update { it.copy(backStack = emptyList(), screenState = ScreenState.Home) }
    }

    fun openSubject(path: String, subjectName: String, tab: AppTab) {
        viewModelScope.launch {
            navigateTo(ScreenState.TopicSelection(path, subjectName))
        }
    }

    /**
     * Quiz-entry activation prompt requests (M4/M8): MainActivity
     * collects this and opens the activation prompt. A restricted quiz
     * tapped without a live activation must prompt, never open from
     * downloaded files that outlived the activation.
     */
    private val _activationPromptRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val activationPromptRequests: SharedFlow<Unit> = _activationPromptRequests.asSharedFlow()

    fun openTopicQuiz(path: String, topicName: String) {
        viewModelScope.launch {
            // M8: re-check the device gate at quiz entry (a debugger
            // attached mid-session is caught here, not only at tab entry).
            // The quiz only opens restricted bank content, so the gate
            // applies whenever the path is restricted.
            if (WorkerActivationApi.isRestrictedPath(path)) {
                val gate = sy.medpro.watani.ui.security.RestrictedContentGate.check(
                    getApplication<Application>().applicationContext
                )
                if (!gate.allowed) {
                    _uiState.update { it.copy(gateMessage = gate.userMessage) }
                    return@launch
                }
                // M4: re-check activation too — an expired token with no
                // network fetch yet must not keep opening quizzes offline
                // from files downloaded under the old activation.
                if (!WorkerActivationApi.isActivated(getApplication<Application>().applicationContext)) {
                    _activationPromptRequests.tryEmit(Unit)
                    return@launch
                }
            }
            val envelope = repository.getSubjectEnvelope(path)
            val topic = envelope?.topics?.find { it.name == topicName }
            val questions = topic?.questions ?: emptyList()
            navigateTo(ScreenState.Quiz(topicName, questions))
        }
    }

    fun openUniversity(universityKey: String, universityName: String) {
        navigateTo(ScreenState.UniversitySubjects(universityKey, universityName))
    }

    fun openCasePlayer(caseItem: ClinicalCaseDto) {
        navigateTo(ScreenState.CasePlayer(caseItem))
    }

    suspend fun getSubjectEnvelope(path: String): SubjectEnvelope? {
        return repository.getSubjectEnvelope(path)
    }

    suspend fun getCasesEnvelope(): CasesEnvelope? {
        return repository.getCasesEnvelope()
    }
}
