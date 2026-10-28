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
    val downloadingIds: Set<String> = emptySet()
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
                },
                onFailure = { error ->
                    _state.update { it.copy(isLoading = false, error = error.localizedMessage ?: "تعذر تحميل المكتبة") }
                }
            )
        }
    }

    fun downloadItem(item: LibraryItem, onProgress: (Float) -> Unit = {}) {
        val id = item.id ?: item.fileUrl ?: ""
        viewModelScope.launch {
            _state.update { it.copy(downloadingIds = it.downloadingIds + id) }
            val result = repository.downloadLibraryItem(item, onProgress)
            _state.update { it.copy(downloadingIds = it.downloadingIds - id) }
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
