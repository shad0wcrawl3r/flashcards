package dev.shadowcrawler.flashcards.ui

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession.LlmInferenceSessionOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wraps MediaPipe's on-device LLM Inference engine (`.task` model files) for a single loaded
 * model. Used by the LLM benchmark sandbox — not yet wired into answer grading.
 */
class LlmController private constructor(
    private val engine: LlmInference
) {

    /**
     * Single-shot generation: opens a fresh [LlmInferenceSession] per call (rather than the
     * engine's shared implicit session) so sampling settings are explicit and no conversation
     * state leaks between calls. `topK = 1` makes decoding greedy/deterministic regardless of
     * `temperature` or `randomSeed` — useful for telling "this model is unreliable" apart from
     * "the default sampling (temperature 0.8) just got unlucky."
     */
    suspend fun generate(
        prompt: String,
        temperature: Float = 0f,
        topK: Int = 1,
        randomSeed: Int = 0
    ): String = withContext(Dispatchers.IO) {
        val sessionOptions = LlmInferenceSessionOptions.builder()
            .setTemperature(temperature)
            .setTopK(topK)
            .setRandomSeed(randomSeed)
            .build()
        LlmInferenceSession.createFromOptions(engine, sessionOptions).use { session ->
            session.addQueryChunk(prompt)
            session.generateResponse()
        }
    }

    fun close() {
        engine.close()
    }

    companion object {
        suspend fun load(
            context: Context,
            modelPath: String,
            maxTokens: Int = 512
        ): LlmController = withContext(Dispatchers.IO) {
            // topK/temperature/randomSeed live on LlmInferenceSession's options in this API
            // version, not here; generateResponse() below uses the engine's implicit default
            // session, so we don't need to manage a session explicitly for this sandbox.
            val options = LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(maxTokens)
                .build()
            LlmController(LlmInference.createFromOptions(context, options))
        }
    }
}
