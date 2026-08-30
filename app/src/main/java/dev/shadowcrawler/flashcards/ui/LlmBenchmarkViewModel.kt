package dev.shadowcrawler.flashcards.ui

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ModelLoadState {
    data object NotLoaded : ModelLoadState
    data object Loading : ModelLoadState
    data class Loaded(val modelName: String) : ModelLoadState
    data class Error(val message: String) : ModelLoadState
}

data class RunResult(val response: String, val elapsedMs: Long)

sealed interface GenerationState {
    data object Idle : GenerationState
    data class Generating(val completed: Int, val total: Int) : GenerationState
    data class Result(val runs: List<RunResult>) : GenerationState
    data class Error(val message: String) : GenerationState
}

class LlmBenchmarkViewModel(
    private val appContext: Context
) : ViewModel() {

    private var controller: LlmController? = null

    private val _availableModels = MutableStateFlow<List<LocalLlmModel>>(emptyList())
    val availableModels: StateFlow<List<LocalLlmModel>> = _availableModels.asStateFlow()

    private val _modelLoadState = MutableStateFlow<ModelLoadState>(ModelLoadState.NotLoaded)
    val modelLoadState: StateFlow<ModelLoadState> = _modelLoadState.asStateFlow()

    private val _generationState = MutableStateFlow<GenerationState>(GenerationState.Idle)
    val generationState: StateFlow<GenerationState> = _generationState.asStateFlow()

    val modelsDirPath: String = LlmModelStore.modelsDir(appContext).absolutePath

    init {
        refreshModels()
    }

    fun refreshModels() {
        _availableModels.value = LlmModelStore.listModels(appContext)
    }

    fun loadModel(model: LocalLlmModel) {
        _modelLoadState.value = ModelLoadState.Loading
        _generationState.value = GenerationState.Idle
        viewModelScope.launch {
            controller?.close()
            controller = null
            try {
                controller = LlmController.load(appContext, model.file.absolutePath)
                _modelLoadState.value = ModelLoadState.Loaded(model.name)
            } catch (e: Exception) {
                val rawMessage = e.message ?: "Failed to load model."
                val message = if (rawMessage.contains("Sentencepiece tokenizer not found", ignoreCase = true)) {
                    "$rawMessage\n\nThis engine version expects a SentencePiece tokenizer " +
                        "(what Gemma models use). Non-Gemma families like Qwen use a different " +
                        "tokenizer format and may not load even from an official litert-community " +
                        "build — try a Gemma model instead."
                } else {
                    rawMessage
                }
                _modelLoadState.value = ModelLoadState.Error(message)
            }
        }
    }

    /**
     * Runs the prompt [repeatCount] times, incrementing the seed each time (0-based offset from
     * [baseSeed]), so at `topK > 1` / `temperature > 0` you see the actual spread of answers the
     * model can give instead of just one sample — greedy decoding (`topK = 1`) collapses all
     * runs to the same deterministic output regardless of seed, which is itself a useful signal.
     */
    fun runPrompt(
        prompt: String,
        temperature: Float,
        topK: Int,
        baseSeed: Int,
        repeatCount: Int
    ) {
        val activeController = controller ?: return
        if (prompt.isBlank() || repeatCount < 1) return

        viewModelScope.launch {
            val runs = mutableListOf<RunResult>()
            for (i in 0 until repeatCount) {
                _generationState.value = GenerationState.Generating(completed = i, total = repeatCount)
                try {
                    val startMs = SystemClock.elapsedRealtime()
                    val response = activeController.generate(
                        prompt = prompt,
                        temperature = temperature,
                        topK = topK,
                        randomSeed = baseSeed + i
                    )
                    val elapsedMs = SystemClock.elapsedRealtime() - startMs
                    runs += RunResult(response, elapsedMs)
                } catch (e: Exception) {
                    _generationState.value = GenerationState.Error(e.message ?: "Generation failed.")
                    return@launch
                }
            }
            _generationState.value = GenerationState.Result(runs)
        }
    }

    override fun onCleared() {
        super.onCleared()
        controller?.close()
    }
}

class LlmBenchmarkViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LlmBenchmarkViewModel::class.java)) {
            return LlmBenchmarkViewModel(context.applicationContext) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
