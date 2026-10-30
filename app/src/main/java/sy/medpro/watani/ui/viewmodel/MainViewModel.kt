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
        _uiState.update { it.copy(currentTab = tab, screenState = ScreenState.Home) }
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
                    _uiState.update {
                        it.copy(
                            downloadingPaths = it.downloadingPaths - path,
                            downloadingProgress = it.downloadingProgress - path
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

    fun clearGateMessage() {
        _uiState.update { it.copy(gateMessage = null) }
    }

    fun navigateToHome() {
        _uiState.update { it.copy(screenState = ScreenState.Home) }
    }

    fun openSubject(path: String, subjectName: String, tab: AppTab) {
        viewModelScope.launch {
            when (tab) {
                AppTab.BANK, AppTab.UNIVERSITIES, AppTab.UWORLD -> {
                    _uiState.update { it.copy(screenState = ScreenState.TopicSelection(path, subjectName)) }
                }
                else -> {
                    _uiState.update { it.copy(screenState = ScreenState.TopicSelection(path, subjectName)) }
                }
            }
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
            _uiState.update { it.copy(screenState = ScreenState.Quiz(topicName, questions)) }
        }
    }

    fun openUniversity(universityKey: String, universityName: String) {
        _uiState.update { it.copy(screenState = ScreenState.UniversitySubjects(universityKey, universityName)) }
    }

    fun openCasePlayer(caseItem: ClinicalCaseDto) {
        _uiState.update { it.copy(screenState = ScreenState.CasePlayer(caseItem)) }
    }

    suspend fun getSubjectEnvelope(path: String): SubjectEnvelope? {
        return repository.getSubjectEnvelope(path)
    }

    suspend fun getCasesEnvelope(): CasesEnvelope? {
        return repository.getCasesEnvelope()
    }
}
