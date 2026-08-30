package dev.shadowcrawler.flashcards.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Thin wrapper around [TextToSpeech]. Initialization is asynchronous, so a [speak] call made
 * before the engine reports ready (e.g. the very first card of a deck) is queued and flushed
 * once it is, rather than silently dropped.
 */
class TtsController internal constructor(context: Context) {

    private var ready = false
    private var pendingText: String? = null

    private val engine: TextToSpeech = TextToSpeech(context) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            pendingText?.let { engine.speak(it, TextToSpeech.QUEUE_FLUSH, null, "flashcard-tts") }
            pendingText = null
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        if (ready) {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "flashcard-tts")
        } else {
            pendingText = text
        }
    }

    fun stop() {
        pendingText = null
        if (ready) engine.stop()
    }

    fun shutdown() {
        engine.stop()
        engine.shutdown()
    }
}

@Composable
fun rememberTtsController(): TtsController {
    val context = LocalContext.current
    val controller = remember { TtsController(context.applicationContext) }
    DisposableEffect(controller) {
        onDispose { controller.shutdown() }
    }
    return controller
}
