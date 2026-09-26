package dev.shadowcrawler.flashcards.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.shadowcrawler.flashcards.ui.laya.LayaEngine
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LayaModelState {
    data object NotDownloaded : LayaModelState
    data class Downloading(
        val filename: String,
        val fileIndex: Int,
        val totalFiles: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : LayaModelState
    data object Ready : LayaModelState
    data class Error(val message: String) : LayaModelState
}

sealed interface LayaEngineState {
    data object NotLoaded : LayaEngineState
    data object Loading : LayaEngineState
    data object Loaded : LayaEngineState
    data class Error(val message: String) : LayaEngineState
}

sealed interface LayaJudgeState {
    data object Idle : LayaJudgeState
    data object Judging : LayaJudgeState
    data class Result(val result: LayaJudgeResult) : LayaJudgeState
    data class Error(val message: String) : LayaJudgeState
}

class LayaBenchmarkViewModel(
    private val appContext: Context
) : ViewModel() {

    private var controller: LayaController? = null
    private val hfService = HuggingFaceModelService()
    private val downloader = ModelDownloader()
    private var downloadJob: Job? = null

    private val _modelState = MutableStateFlow<LayaModelState>(currentModelState())
    val modelState: StateFlow<LayaModelState> = _modelState.asStateFlow()

    private val _engineState = MutableStateFlow<LayaEngineState>(LayaEngineState.NotLoaded)
    val engineState: StateFlow<LayaEngineState> = _engineState.asStateFlow()

    private val _judgeState = MutableStateFlow<LayaJudgeState>(LayaJudgeState.Idle)
    val judgeState: StateFlow<LayaJudgeState> = _judgeState.asStateFlow()

    val modelDirPath: String = LayaModelStore.modelDir(appContext).absolutePath

    private fun currentModelState(): LayaModelState =
        if (LayaModelStore.isComplete(appContext)) LayaModelState.Ready else LayaModelState.NotDownloaded

    fun refreshModelState() {
        _modelState.value = currentModelState()
    }

    /**
     * Downloads the fixed Laya-Multilingual-LiteRT file set sequentially (unlike
     * [ModelDownloadViewModel], which downloads one user-picked `.litertlm` file at a time) —
     * there's exactly one supported Laya checkpoint, so no repo/file browser is needed.
     */
    fun downloadModel(authToken: String?) {
        downloadJob?.cancel()
        val missing = LayaModelStore.missingFiles(appContext)
        if (missing.isEmpty()) {
            _modelState.value = LayaModelState.Ready
            return
        }
        val dir = LayaModelStore.modelDir(appContext)
        downloadJob = viewModelScope.launch {
            try {
                missing.forEachIndexed { index, file ->
                    _modelState.value = LayaModelState.Downloading(
                        filename = file.filename,
                        fileIndex = index + 1,
                        totalFiles = missing.size,
                        bytesDownloaded = 0L,
                        totalBytes = file.sizeBytes
                    )
                    val destination = File(dir, file.filename)
                    val url = hfService.downloadUrl(LAYA_REPO_ID, file.filename)
                    downloader.download(url, destination, authToken?.ifBlank { null }) { progress ->
                        if (progress is DownloadProgress.InProgress) {
                            _modelState.value = LayaModelState.Downloading(
                                filename = file.filename,
                                fileIndex = index + 1,
                                totalFiles = missing.size,
                                bytesDownloaded = progress.bytesDownloaded,
                                totalBytes = progress.totalBytes
                            )
                        }
                    }
                }
                _modelState.value = LayaModelState.Ready
            } catch (e: CancellationException) {
                refreshModelState()
                throw e
            } catch (e: Exception) {
                _modelState.value = LayaModelState.Error(e.message ?: "Download failed.")
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    fun loadEngine(backend: LayaEngine.Backend) {
        _engineState.value = LayaEngineState.Loading
        _judgeState.value = LayaJudgeState.Idle
        viewModelScope.launch {
            controller?.close()
            controller = null
            try {
                controller = LayaController.load(appContext, backend)
                _engineState.value = LayaEngineState.Loaded
            } catch (e: Exception) {
                _engineState.value = LayaEngineState.Error(e.message ?: "Failed to load Laya model.")
            }
        }
    }

    fun judge(
        question: String,
        correctAnswer: String,
        spokenAnswer: String,
        backend: LayaEngine.Backend
    ) {
        val activeController = controller ?: return
        if (question.isBlank() || correctAnswer.isBlank()) return

        viewModelScope.launch {
            _judgeState.value = LayaJudgeState.Judging
            try {
                val result = activeController.judge(question, correctAnswer, spokenAnswer, backend)
                _judgeState.value = LayaJudgeState.Result(result)
            } catch (e: Exception) {
                _judgeState.value = LayaJudgeState.Error(e.message ?: "Judging failed.")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        controller?.close()
    }
}

class LayaBenchmarkViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LayaBenchmarkViewModel::class.java)) {
            return LayaBenchmarkViewModel(context.applicationContext) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
