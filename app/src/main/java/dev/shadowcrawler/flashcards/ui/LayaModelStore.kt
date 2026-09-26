package dev.shadowcrawler.flashcards.ui

import android.content.Context
import java.io.File

/**
 * A required file in the litert-community/Laya-Multilingual-LiteRT bundle, keyed by its exact
 * repo filename (see https://huggingface.co/litert-community/Laya-Multilingual-LiteRT/tree/main).
 * Two separate `.tflite` graphs are required — the main encoder ("embeds") and a small separate
 * "act head" graph that LayaEngine.runRaw() combines into one result (markerLogits + actLogits).
 */
data class LayaModelFile(val filename: String, val sizeBytes: Long)

const val LAYA_REPO_ID = "litert-community/Laya-Multilingual-LiteRT"

val LAYA_REQUIRED_FILES = listOf(
    LayaModelFile("laya_ml_s256_embeds_wfp16.tflite", 250_889_408L),
    LayaModelFile("laya_ml_act_head_fp32.tflite", 795_816L),
    LayaModelFile("laya_ml_calibration.json", 9_156L),
    LayaModelFile("tokenizer.json", 34_363_188L),
    LayaModelFile("token_embeddings_fp16.bin", 393_216_000L),
    LayaModelFile("token_embeddings.json", 801L),
)

/**
 * Laya isn't a single user-picked file like [LlmModelStore]'s `.litertlm` models — it's one fixed
 * multi-file bundle (two `.tflite` graphs + tokenizer + embedding lookup table + calibration),
 * so this store just checks whether that whole bundle is present rather than listing arbitrary
 * files. Lives in its own `llm_models/laya/` subdirectory so it never collides with `.litertlm`
 * files sitting in the parent `llm_models/` dir that [LlmModelStore] scans.
 */
object LayaModelStore {

    fun modelDir(context: Context): File =
        File(LlmModelStore.modelsDir(context), "laya").apply { mkdirs() }

    fun missingFiles(context: Context): List<LayaModelFile> {
        val dir = modelDir(context)
        return LAYA_REQUIRED_FILES.filter { !File(dir, it.filename).isFile }
    }

    fun isComplete(context: Context): Boolean = missingFiles(context).isEmpty()
}
