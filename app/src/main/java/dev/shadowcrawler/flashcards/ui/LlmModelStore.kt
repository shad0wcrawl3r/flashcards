package dev.shadowcrawler.flashcards.ui

import android.content.Context
import java.io.File

data class LocalLlmModel(
    val name: String,
    val file: File
) {
    val sizeMb: Long get() = file.length() / (1024 * 1024)
}

private val SUPPORTED_EXTENSIONS = setOf("litertlm")

/**
 * Model files are too large to bundle or fetch automatically — `adb push` a `.litertlm` file
 * (a LiteRT-LM build of Gemma/Llama/Phi/Qwen, etc.) into [modelsDir] and refresh. App-specific
 * external storage needs no runtime permission on modern Android.
 */
object LlmModelStore {

    fun modelsDir(context: Context): File =
        File(context.getExternalFilesDir(null), "llm_models").apply { mkdirs() }

    fun listModels(context: Context): List<LocalLlmModel> {
        val dir = modelsDir(context)
        return dir.listFiles { file -> file.isFile && file.extension in SUPPORTED_EXTENSIONS }
            ?.sortedBy { it.name }
            ?.map { LocalLlmModel(name = it.name, file = it) }
            .orEmpty()
    }
}
