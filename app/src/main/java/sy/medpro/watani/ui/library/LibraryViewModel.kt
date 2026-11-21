package sy.medpro.watani.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sy.medpro.watani.data.*

data class LibraryUiState(
    val isLoading: Boolean = false,
    val library: LibraryEnvelope? = null,
    val error: String? = null,
    val downloadingIds: Set<String> = emptySet(),
    // Book-download fix: live progress per item (0f..1f) so a slow
    // download shows a percentage instead of an endless spinner,
    // and the last failure message per item instead of silent return
    // to the download button.
    val downloadingProgress: Map<String, Float> = emptyMap(),
    val downloadErrors: Map<String, String> = emptyMap()
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WataniRepository(application)

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init {
        loadLibrary()
    }

    fun loadLibrary() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = repository.fetchLibrary()
            result.fold(
                onSuccess = { envelope ->
                    _state.update { it.copy(isLoading = false, library = envelope) }
                    // Phase-3 §4.4: schedule the one-time migration of
                    // legacy PLAIN HLS trees to the encrypted layout.
                    // It runs off the UI path (repository switches to
                    // IO) and never inside a downloaded-state check —
                    // isItemDownloaded is called from derivedStateOf in
                    // the UI and must stay a cheap read.
                    val allItems = envelope?.subjects.orEmpty()
                        .flatMap { (it.books.orEmpty()) + (it.videos.orEmpty()) }
                    if (allItems.isNotEmpty()) {
                        viewModelScope.launch {
                            repository.migratePlainHlsTrees(allItems)
                            _state.update { it }
                        }
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(isLoading = false, error = error.localizedMessage ?: "تعذر تحميل المكتبة") }
                }
            )
        }
    }

    fun downloadItem(item: LibraryItem) {
        val id = item.id ?: item.fileUrl ?: ""
        viewModelScope.launch {
            _state.update {
                it.copy(
                    downloadingIds = it.downloadingIds + id,
                    downloadingProgress = it.downloadingProgress + (id to 0f),
                    downloadErrors = it.downloadErrors - id
                )
            }
            val result = repository.downloadLibraryItem(item) { p ->
                _state.update { st -> st.copy(downloadingProgress = st.downloadingProgress + (id to p)) }
            }
            _state.update {
                it.copy(
                    downloadingIds = it.downloadingIds - id,
                    downloadingProgress = it.downloadingProgress - id,
                    downloadErrors = if (result.success) it.downloadErrors - id
                        else it.downloadErrors + (id to (result.errorMessage ?: "تعذر التحميل — أعد المحاولة."))
                )
            }
        }
    }

    fun deleteItem(item: LibraryItem) {
        viewModelScope.launch {
            repository.deleteLibraryItem(item)
            _state.update { it }
        }
    }

    fun isItemDownloaded(item: LibraryItem): Boolean {
        return repository.isLibraryItemDownloaded(item)
    }

    /**
     * Watch-progress fraction (0f..1f) of one subject for the
     * subjects grid (build 1.2.37, design 01's gold bar).
     *
     * INVESTIGATED before building (the design asks for the mean of
     * the subject's per-video watched fractions): the ONLY watch
     * position store in the app is VideoPlayerScreen's
     * `savedPositionMs` — a `rememberSaveable(videoUri)` composition
     * state that lives and dies with the player instance. It is not
     * persisted to disk, it is not readable from the Library, and no
     * video duration is stored anywhere beside it, so NO per-video
     * fraction can be computed from any existing source. Inventing
     * a number would be fabrication, so this returns 0f for every
     * subject and the grid renders its «لم تبدأ بعد» state — the
     * exact behaviour the 1.2.37 spec prescribes when no source
     * exists. When a durable position+duration store lands, this
     * function is the single place to wire it: mean of the videos'
     * fractions, a missing video counting as 0.
     */
    fun subjectWatchFraction(videos: List<LibraryItem>): Float = 0f

    fun getLocalFile(item: LibraryItem): java.io.File {
        return repository.getLibraryFile(item)
    }

    /** Local SRT side-car for a downloaded video, or null when not downloaded. */
    fun getSubtitleFile(item: LibraryItem): java.io.File? {
        return repository.getLibrarySubtitleFileIfExists(item)
    }

    suspend fun openItem(item: LibraryItem): Result<java.io.File> {
        return repository.openLibraryItem(item)
    }

    fun closeItem(item: LibraryItem) {
        repository.closeLibraryItem(item)
    }
}
