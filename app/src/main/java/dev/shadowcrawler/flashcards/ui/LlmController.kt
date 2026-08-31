package dev.shadowcrawler.flashcards.ui

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.InputData
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.SessionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wraps LiteRT-LM's on-device LLM engine (`.litertlm` model files) for a single loaded model.
 * Used by the LLM benchmark sandbox — not yet wired into answer grading. Successor to MediaPipe's
 * LLM Inference API, which is now in maintenance-only mode; LiteRT-LM adds support for model
 * families beyond Gemma's SentencePiece tokenizer (Llama, Phi-4, Qwen).
 */
class LlmController private constructor(
    private val engine: Engine
) {

    /**
     * Single-shot generation: opens a fresh raw [com.google.ai.edge.litertlm.Session] per call
     * (rather than a chat-templated [com.google.ai.edge.litertlm.Conversation]) so the prompt is
     * sent as-is, sampling settings are explicit, and no state leaks between calls. `topK = 1`
     * makes decoding greedy/deterministic regardless of `temperature` or `randomSeed` — useful
     * for telling "this model is unreliable" apart from "the default sampling just got unlucky."
     */
    suspend fun generate(
        prompt: String,
        temperature: Float = 0f,
        topK: Int = 1,
        randomSeed: Int = 0
    ): String = withContext(Dispatchers.IO) {
        val sessionConfig = SessionConfig(
            samplerConfig = SamplerConfig(
                topK = topK,
                topP = 1.0,
                temperature = temperature.toDouble(),
                seed = randomSeed
            )
        )
        engine.createSession(sessionConfig).use { session ->
            session.generateContent(listOf(InputData.Text(prompt)))
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
            val engineConfig = EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
                maxNumTokens = maxTokens,
                cacheDir = context.cacheDir.path
            )
            val engine = Engine(engineConfig)
            engine.initialize()
            LlmController(engine)
        }
    }
}
