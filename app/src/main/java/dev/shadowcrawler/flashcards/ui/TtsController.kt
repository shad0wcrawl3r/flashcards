package dev.shadowcrawler.flashcards.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Thin wrapper around [TextToSpeech]. Initialization is asynchronous, so a [speak] call made
 * before the engine reports ready (e.g. the very first card of a deck) is queued and flushed
 * once it is, rather than silently dropped.
 */
class TtsController internal constructor(context: Context) {

    private var ready = false
    private var pendingText: String? = null
    private var pendingOnDone: (() -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val utteranceCounter = AtomicInteger()
    /** Keyed by utteranceId; QUEUE_FLUSH means at most one entry is ever genuinely pending. */
    private val pendingCallbacks = ConcurrentHashMap<String, () -> Unit>()

    private val engine: TextToSpeech = TextToSpeech(context) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = complete(utteranceId)
                override fun onError(utteranceId: String?) = complete(utteranceId)
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?, errorCode: Int) = complete(utteranceId)
            })
            val text = pendingText
            val onDone = pendingOnDone
            pendingText = null
            pendingOnDone = null
            if (text != null) speak(text, onDone)
        }
    }

    private fun complete(utteranceId: String?) {
        val callback = utteranceId?.let { pendingCallbacks.remove(it) } ?: return
        mainHandler.post { callback() }
    }

    /**
     * [onDone] fires once this utterance finishes (or errors) — used to sequence hands-free
     * listening after the question is read, and to sequence the answer after a spoken verdict.
     * Every call flushes the queue (see [TextToSpeech.QUEUE_FLUSH]), so any not-yet-fired
     * callback from a prior call is dropped along with it rather than firing late.
     */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        pendingCallbacks.clear()
        if (text.isBlank()) {
            onDone?.invoke()
            return
        }
        if (!ready) {
            pendingText = text
            pendingOnDone = onDone
            return
        }
        val utteranceId = "flashcard-tts-${utteranceCounter.incrementAndGet()}"
        if (onDone != null) pendingCallbacks[utteranceId] = onDone
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    /** Suspends until [text] finishes — lets hands-free sequencing (verdict, then answer, then
     * a pause) read as a plain top-to-bottom coroutine instead of nested callbacks. */
    suspend fun speakAndAwait(text: String) = suspendCancellableCoroutine<Unit> { cont ->
        speak(text) { if (cont.isActive) cont.resume(Unit) }
    }

    fun stop() {
        pendingText = null
        pendingOnDone = null
        pendingCallbacks.clear()
        if (ready) engine.stop()
    }

    fun shutdown() {
        stop()
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
