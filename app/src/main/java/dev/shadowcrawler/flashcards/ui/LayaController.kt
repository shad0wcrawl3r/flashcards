package dev.shadowcrawler.flashcards.ui

import android.content.Context
import dev.shadowcrawler.flashcards.ui.laya.LayaEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Matches the "choice" question posed by tools/llm_judge_eval/laya_harness.py so on-device and
 * Python-harness results stay directly comparable. */
private val VERDICT_QUESTION: Map<String, Any?> = linkedMapOf(
    "type" to "choice",
    "instructions" to (
        "Does `spoken_answer` correctly demonstrate the knowledge described in " +
            "`correct_answer`, as an answer to `question`? Accept paraphrases with " +
            "different wording as long as the core meaning matches."
        ),
    "criteria" to linkedMapOf(
        "correct" to "spoken_answer captures the same core idea as correct_answer, even if worded very differently",
        "incorrect" to "spoken_answer misses the key idea, is factually wrong, or is too vague to tell",
    ),
)

data class LayaJudgeResult(
    val verdict: String,
    val probabilities: Map<String, Double>,
    val confidence: Double,
    val actProbability: Double,
    val elapsedMs: Double,
)

/**
 * Wraps the ported [LayaEngine] typed-decision model for the flashcards answer-grading
 * experiment. Unlike [LlmController] (a freeform generative prompt), Laya always returns a
 * typed choice — no output parsing, no sampling, no repeats-for-consistency needed.
 */
class LayaController private constructor(
    private val engine: LayaEngine
) {

    suspend fun judge(
        question: String,
        correctAnswer: String,
        spokenAnswer: String,
        backend: LayaEngine.Backend = LayaEngine.Backend.GPU
    ): LayaJudgeResult = withContext(Dispatchers.IO) {
        val state = linkedMapOf(
            "question" to question,
            "correct_answer" to correctAnswer,
            "spoken_answer" to spokenAnswer,
        )
        val result = engine.answer(state, VERDICT_QUESTION, backend)
        val answer = result.answer
        @Suppress("UNCHECKED_CAST")
        val probabilities = (answer.getValue("probabilities") as Map<String, Number>)
            .mapValues { it.value.toDouble() }
        @Suppress("UNCHECKED_CAST")
        val action = answer.getValue("action") as Map<String, Number>
        LayaJudgeResult(
            verdict = (answer.getValue("choice") as String).uppercase(),
            probabilities = probabilities,
            confidence = (answer.getValue("confidence") as Number).toDouble(),
            actProbability = action.getValue("act_probability").toDouble(),
            elapsedMs = result.totalMs,
        )
    }

    fun close() {
        engine.close()
    }

    companion object {
        suspend fun load(
            context: Context,
            backend: LayaEngine.Backend = LayaEngine.Backend.GPU
        ): LayaController = withContext(Dispatchers.IO) {
            val engine = LayaEngine(LayaModelStore.modelDir(context), LayaEngine.Storage.WFP16)
            engine.initialize(backend)
            LayaController(engine)
        }
    }
}
