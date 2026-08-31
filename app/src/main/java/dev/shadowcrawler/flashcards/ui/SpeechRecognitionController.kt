package dev.shadowcrawler.flashcards.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.ModelDownloadListener
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

private const val TAG = "SpeechRecognition"

/** Near-universally present on-device language pack; last-resort fallback only. */
private const val FALLBACK_LANGUAGE_TAG = "en-US"

/**
 * Thin wrapper around the on-device [SpeechRecognizer]. Used by the settings sandbox to test
 * recognition; not yet wired into practicing.
 */
class SpeechRecognitionController internal constructor(
    private val context: Context,
    private val onPartialResult: (String) -> Unit,
    private val onFinalResult: (String) -> Unit,
    private val onListeningChanged: (Boolean) -> Unit,
    private val onErrorMessage: (String) -> Unit,
    private val onStatusMessage: (String) -> Unit
) {

    private val recognizer: SpeechRecognizer? =
        if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            null
        }

    init {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                onListeningChanged(true)
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() {
                onListeningChanged(false)
            }

            override fun onError(error: Int) {
                onListeningChanged(false)
                val message = describeError(error)
                Log.w(TAG, "Recognition error $error: $message")
                onErrorMessage(message)
            }

            override fun onResults(results: Bundle?) {
                onListeningChanged(false)
                bestText(results)?.let(onFinalResult)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                bestText(partialResults)?.let(onPartialResult)
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }

    /**
     * Leaving EXTRA_LANGUAGE unset makes the recognizer fall back to the device's system
     * locale — which errors with ERROR_LANGUAGE_NOT_SUPPORTED (12) on devices whose system
     * locale (e.g. en-NP) has no on-device speech pack installed, even when a common one like
     * en-US is. So we ask the recognizer what's actually installed first, rather than assuming
     * the system locale works.
     */
    fun startListening() {
        val activeRecognizer = recognizer ?: return
        val probeIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        activeRecognizer.checkRecognitionSupport(
            probeIntent,
            context.mainExecutor,
            object : RecognitionSupportCallback {
                override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                    val installed = recognitionSupport.installedOnDeviceLanguages
                    val supported = recognitionSupport.supportedOnDeviceLanguages
                    val languageTag = installed.firstOrNull() ?: supported.firstOrNull() ?: FALLBACK_LANGUAGE_TAG
                    Log.d(TAG, "checkRecognitionSupport: installed=$installed supported=$supported -> using '$languageTag'")

                    if (languageTag in installed) {
                        beginListening(languageTag)
                    } else {
                        // Nothing downloaded yet (seen on a device reporting installed=[] with
                        // 30+ languages "supported") — starting would just fail with
                        // ERROR_LANGUAGE_UNAVAILABLE, so request the download instead of
                        // guaranteeing a failed attempt.
                        Log.w(TAG, "'$languageTag' isn't installed on-device; requesting a download instead of listening")
                        requestLanguageDownload(languageTag)
                    }
                }

                override fun onError(error: Int) {
                    Log.w(TAG, "checkRecognitionSupport failed (${describeError(error)}, $error); falling back to $FALLBACK_LANGUAGE_TAG")
                    beginListening(FALLBACK_LANGUAGE_TAG)
                }
            }
        )
    }

    private fun beginListening(languageTag: String) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        }
        recognizer?.startListening(intent)
    }

    /**
     * The plain `triggerModelDownload(Intent)` overload is fire-and-forget — no progress, no
     * completion signal anywhere (confirmed: a real download completed silently with nothing in
     * the system Downloads notification or the app). This overload reports back via
     * [SpeechRecognizer.ModelDownloadListener] instead, so the sandbox can show real progress
     * and a completion message rather than leaving the user to guess and retry blindly.
     */
    private fun requestLanguageDownload(languageTag: String) {
        val downloadIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        }
        onListeningChanged(false)
        onStatusMessage("Requesting on-device language pack for '$languageTag'…")
        recognizer?.triggerModelDownload(
            downloadIntent,
            context.mainExecutor,
            object : ModelDownloadListener {
                override fun onProgress(completedPercent: Int) {
                    Log.d(TAG, "Model download progress for '$languageTag': $completedPercent%")
                    onStatusMessage("Downloading '$languageTag' language pack: $completedPercent%")
                }

                override fun onSuccess() {
                    Log.i(TAG, "Model download succeeded for '$languageTag'")
                    onStatusMessage("'$languageTag' language pack is ready — tap Start listening again.")
                }

                override fun onScheduled() {
                    Log.i(TAG, "Model download scheduled for '$languageTag' (deferred, e.g. waiting for Wi-Fi)")
                    onStatusMessage("Download for '$languageTag' scheduled — it'll run when conditions allow (e.g. on Wi-Fi).")
                }

                override fun onError(error: Int) {
                    val message = describeError(error)
                    Log.w(TAG, "Model download failed for '$languageTag' ($error): $message")
                    onErrorMessage("Language pack download failed: $message")
                }
            }
        )
    }

    fun stopListening() {
        recognizer?.stopListening()
    }

    fun destroy() {
        recognizer?.destroy()
    }

    private fun bestText(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun describeError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Missing microphone permission"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
        SpeechRecognizer.ERROR_SERVER -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Too many requests"
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "Server disconnected"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Language not supported for on-device recognition"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Language supported but not currently installed on-device"
        SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT -> "Unable to check on-device language support"
        SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS -> "Cannot listen for model download events"
        else -> "Unknown recognition error ($error)"
    }
}
