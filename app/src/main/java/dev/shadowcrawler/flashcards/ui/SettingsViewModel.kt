package dev.shadowcrawler.flashcards.ui

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "settings"
private const val KEY_TTS_ENABLED = "tts_enabled"
private const val KEY_ON_DEVICE_RECOGNITION_ENABLED = "on_device_recognition_enabled"
private const val KEY_HANDS_FREE_ASSESSMENT_ENABLED = "hands_free_assessment_enabled"
private const val KEY_HUGGING_FACE_TOKEN = "hugging_face_token"

/**
 * Holds user-facing speech settings. Backed by SharedPreferences so choices survive
 * process death; kept at the Activity/NavHost level so both the deck list's settings
 * menu and the practice screen see the same live values.
 */
class SettingsViewModel(context: Context) : ViewModel() {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _ttsEnabled = MutableStateFlow(prefs.getBoolean(KEY_TTS_ENABLED, true))
    val ttsEnabled: StateFlow<Boolean> = _ttsEnabled.asStateFlow()

    private val _onDeviceRecognitionEnabled =
        MutableStateFlow(prefs.getBoolean(KEY_ON_DEVICE_RECOGNITION_ENABLED, false))
    val onDeviceRecognitionEnabled: StateFlow<Boolean> = _onDeviceRecognitionEnabled.asStateFlow()

    fun setTtsEnabled(enabled: Boolean) {
        _ttsEnabled.value = enabled
        prefs.edit { putBoolean(KEY_TTS_ENABLED, enabled) }
    }

    fun setOnDeviceRecognitionEnabled(enabled: Boolean) {
        _onDeviceRecognitionEnabled.value = enabled
        prefs.edit { putBoolean(KEY_ON_DEVICE_RECOGNITION_ENABLED, enabled) }
    }

    // Deliberately a separate toggle from TTS/on-device recognition rather than implied by
    // "both are on" — the automated listen-and-grade loop is a bigger behavior change than
    // either alone, so it gets its own explicit opt-in.
    private val _handsFreeAssessmentEnabled =
        MutableStateFlow(prefs.getBoolean(KEY_HANDS_FREE_ASSESSMENT_ENABLED, false))
    val handsFreeAssessmentEnabled: StateFlow<Boolean> = _handsFreeAssessmentEnabled.asStateFlow()

    fun setHandsFreeAssessmentEnabled(enabled: Boolean) {
        _handsFreeAssessmentEnabled.value = enabled
        prefs.edit { putBoolean(KEY_HANDS_FREE_ASSESSMENT_ENABLED, enabled) }
    }

    // Stored in plain SharedPreferences, not encrypted — acceptable for a personal read-scoped
    // token typed in by the device's own user, but don't reuse this pattern for anything more
    // sensitive.
    private val _huggingFaceToken = MutableStateFlow(prefs.getString(KEY_HUGGING_FACE_TOKEN, "").orEmpty())
    val huggingFaceToken: StateFlow<String> = _huggingFaceToken.asStateFlow()

    fun setHuggingFaceToken(token: String) {
        _huggingFaceToken.value = token
        prefs.edit { putString(KEY_HUGGING_FACE_TOKEN, token) }
    }
}

class SettingsViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(context) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
