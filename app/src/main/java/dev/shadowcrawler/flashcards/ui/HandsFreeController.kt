package dev.shadowcrawler.flashcards.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import dev.shadowcrawler.flashcards.ui.laya.LayaEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HandsFreeState {
    data object Idle : HandsFreeState
    data object Listening : HandsFreeState
    /** Covers both a first-use Laya load (can take a couple of seconds) and the judge call itself. */
    data class Processing(val transcript: String) : HandsFreeState
    /** QA cards: graded via Laya. */
    data class Result(val transcript: String, val judgeResult: LayaJudgeResult) : HandsFreeState
    /** MCQ cards: matched locally against the choice list, no model needed. Null if unmatched. */
    data class ChoiceResult(val transcript: String, val matchedChoice: String?) : HandsFreeState
    /** The user said "pass" — skip grading/matching and just give the answer. */
    data object Passed : HandsFreeState
    data class Error(val message: String) : HandsFreeState
}

/** Only an exact "pass" (ignoring case/trailing punctuation) counts — a real spoken answer
 * that happens to contain the word "pass" (e.g. about a network pass-through) shouldn't trigger it. */
internal fun isPassCommand(rawTranscript: String): Boolean =
    rawTranscript.trim().trimEnd('.', '!', '?').equals("pass", ignoreCase = true)

private val NUMBER_WORDS = mapOf(
    "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
    "first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5
)

/** "2", "option 2", "number two", "the second one" all resolve to choices[1]; otherwise falls
 * back to matching the spoken text against the choice strings themselves. */
internal fun matchSpokenChoice(rawTranscript: String, choices: List<String>): String? {
    val normalized = rawTranscript.trim().lowercase().trimEnd('.', '!', '?')
    if (normalized.isBlank()) return null
    val stripped = normalized
        .removePrefix("option ")
        .removePrefix("choice ")
        .removePrefix("number ")
        .removePrefix("answer ")
        .removePrefix("the ")
        .removeSuffix(" one")
        .trim()

    val index = stripped.toIntOrNull() ?: NUMBER_WORDS[stripped]
    if (index != null && index in 1..choices.size) return choices[index - 1]

    choices.firstOrNull { it.trim().lowercase() == normalized }?.let { return it }
    choices.firstOrNull { normalized.contains(it.trim().lowercase()) }?.let { return it }
    choices.firstOrNull { it.trim().lowercase().contains(normalized) }?.let { return it }
    return null
}

/**
 * Orchestrates hands-free answering: once told a question (and for MCQ, its choices) was just
 * read aloud, listens for a spoken answer. QA answers are graded against the card's answer with
 * a lazily-loaded [LayaController]; MCQ answers are matched locally against the choice list — no
 * model needed, since MCQ correctness is exact-match, not paraphrase judging. Mirrors
 * [TtsController]'s remember-and-dispose lifecycle (a per-screen engine-backed helper) rather
 * than a ViewModel, for the same reason TTS isn't one.
 */
class HandsFreeController internal constructor(
    private val context: Context,
    private val scope: CoroutineScope
) {

    private val _state = MutableStateFlow<HandsFreeState>(HandsFreeState.Idle)
    val state: StateFlow<HandsFreeState> = _state.asStateFlow()

    private var layaController: LayaController? = null
    private var pendingQuestion: String? = null
    private var pendingCorrectAnswer: String? = null
    private var pendingChoices: List<String>? = null

    private val speechController = SpeechRecognitionController(
        context = context,
        onPartialResult = {},
        onFinalResult = { transcript -> onTranscript(transcript) },
        onListeningChanged = {},
        onErrorMessage = { message -> _state.value = HandsFreeState.Error(message) },
        onStatusMessage = {}
    )

    fun isModelDownloaded(): Boolean = LayaModelStore.isComplete(context)

    fun startListeningForAnswer(question: String, correctAnswer: String) {
        if (!isModelDownloaded()) {
            _state.value = HandsFreeState.Error(
                "Laya model not downloaded — open Laya judge in Settings to download it."
            )
            return
        }
        pendingQuestion = question
        pendingCorrectAnswer = correctAnswer
        pendingChoices = null
        _state.value = HandsFreeState.Listening
        speechController.startListening()
    }

    fun startListeningForChoice(choices: List<String>) {
        pendingChoices = choices
        pendingQuestion = null
        pendingCorrectAnswer = null
        _state.value = HandsFreeState.Listening
        speechController.startListening()
    }

    private fun onTranscript(transcript: String) {
        if (isPassCommand(transcript)) {
            _state.value = HandsFreeState.Passed
            return
        }
        pendingChoices?.let { choices ->
            _state.value = HandsFreeState.ChoiceResult(transcript, matchSpokenChoice(transcript, choices))
            return
        }
        val question = pendingQuestion ?: return
        val correctAnswer = pendingCorrectAnswer ?: return
        _state.value = HandsFreeState.Processing(transcript)
        scope.launch {
            try {
                val controller = layaController ?: LayaController.load(context, LayaEngine.Backend.GPU).also {
                    layaController = it
                }
                val result = controller.judge(question, correctAnswer, transcript)
                _state.value = HandsFreeState.Result(transcript, result)
            } catch (e: Exception) {
                _state.value = HandsFreeState.Error(e.message ?: "Grading failed.")
            }
        }
    }

    /** Called on every new card so a previous card's result/error doesn't linger into this one. */
    fun reset() {
        // Only stop a session that might actually be active — calling stopListening() with no
        // preceding startListening() (e.g. on the very first card) logs a spurious ERROR_CLIENT.
        if (_state.value == HandsFreeState.Listening) {
            speechController.stopListening()
        }
        pendingQuestion = null
        pendingCorrectAnswer = null
        pendingChoices = null
        _state.value = HandsFreeState.Idle
    }

    fun close() {
        speechController.destroy()
        layaController?.close()
    }
}

@Composable
fun rememberHandsFreeController(): HandsFreeController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { HandsFreeController(context.applicationContext, scope) }
    DisposableEffect(controller) {
        onDispose { controller.close() }
    }
    return controller
}
