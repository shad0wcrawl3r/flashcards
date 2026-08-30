package dev.shadowcrawler.flashcards.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Success(val results: List<HfModelSummary>) : SearchState
    data class Error(val message: String) : SearchState
}

sealed interface FilesState {
    data object Idle : FilesState
    data object Loading : FilesState
    data class Success(val repoId: String, val files: List<HfRepoFile>) : FilesState
    data class Error(val message: String) : FilesState
}

sealed interface DownloadUiState {
    data object Idle : DownloadUiState
    data class InProgress(val filename: String, val bytesDownloaded: Long, val totalBytes: Long) : DownloadUiState
    data class Success(val filename: String) : DownloadUiState
    data class Error(val message: String) : DownloadUiState
}

class ModelDownloadViewModel(
    private val appContext: Context
) : ViewModel() {

    private val hfService = HuggingFaceModelService()
    private val downloader = ModelDownloader()

    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private val _filesState = MutableStateFlow<FilesState>(FilesState.Idle)
    val filesState: StateFlow<FilesState> = _filesState.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadUiState>(DownloadUiState.Idle)
    val downloadState: StateFlow<DownloadUiState> = _downloadState.asStateFlow()

    private var downloadJob: Job? = null

    fun search(query: String) {
        if (query.isBlank()) return
        _searchState.value = SearchState.Loading
        _filesState.value = FilesState.Idle
        viewModelScope.launch {
            try {
                _searchState.value = SearchState.Success(hfService.searchModels(query))
            } catch (e: Exception) {
                _searchState.value = SearchState.Error(e.message ?: "Search failed.")
            }
        }
    }

    fun openRepo(repoId: String) {
        _filesState.value = FilesState.Loading
        viewModelScope.launch {
            try {
                _filesState.value = FilesState.Success(repoId, hfService.listModelFiles(repoId))
            } catch (e: Exception) {
                _filesState.value = FilesState.Error(e.message ?: "Failed to list files.")
            }
        }
    }

    fun download(repoId: String, filename: String, authToken: String?) {
        downloadJob?.cancel()
        _downloadState.value = DownloadUiState.InProgress(filename, 0, 0)
        downloadJob = viewModelScope.launch {
            try {
                val destination = File(LlmModelStore.modelsDir(appContext), filename)
                val url = hfService.downloadUrl(repoId, filename)
                downloader.download(url, destination, authToken?.ifBlank { null }) { progress ->
                    _downloadState.value = when (progress) {
                        is DownloadProgress.InProgress ->
                            DownloadUiState.InProgress(filename, progress.bytesDownloaded, progress.totalBytes)
                        DownloadProgress.Complete -> DownloadUiState.Success(filename)
                    }
                }
            } catch (e: CancellationException) {
                _downloadState.value = DownloadUiState.Idle
                throw e
            } catch (e: Exception) {
                _downloadState.value = DownloadUiState.Error(e.message ?: "Download failed.")
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }
}

class ModelDownloadViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ModelDownloadViewModel::class.java)) {
            return ModelDownloadViewModel(context.applicationContext) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
