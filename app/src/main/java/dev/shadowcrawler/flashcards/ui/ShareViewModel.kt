package dev.shadowcrawler.flashcards.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.shadowcrawler.flashcards.data.DeckExporter
import dev.shadowcrawler.flashcards.data.PastebinService
import dev.shadowcrawler.flashcards.data.QrCodeGenerator
import dev.shadowcrawler.flashcards.model.FlashcardDatabase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ShareUiState {
    data object Idle : ShareUiState
    data object Loading : ShareUiState
    data class QrReady(val url: String, val qrBitmap: Bitmap) : ShareUiState
    data class Error(val message: String) : ShareUiState
}

sealed interface ShareEvent {
    data class OpenShareSheet(val url: String) : ShareEvent
}

class ShareViewModel(
    database: FlashcardDatabase
) : ViewModel() {

    private val exporter = DeckExporter(database)
    private val pastebin = PastebinService()

    private val _state = MutableStateFlow<ShareUiState>(ShareUiState.Idle)
    val state: StateFlow<ShareUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ShareEvent>()
    val events: SharedFlow<ShareEvent> = _events.asSharedFlow()

    fun shareAsLink(deckIds: List<Long>) {
        _state.value = ShareUiState.Loading
        viewModelScope.launch {
            try {
                val url = pastebin.upload(exporter.exportDecks(deckIds))
                _state.value = ShareUiState.Idle
                _events.emit(ShareEvent.OpenShareSheet(url))
            } catch (e: Exception) {
                _state.value = ShareUiState.Error(e.message ?: "Share failed.")
            }
        }
    }

    fun shareAsQrCode(deckIds: List<Long>) {
        _state.value = ShareUiState.Loading
        viewModelScope.launch {
            try {
                val url = pastebin.upload(exporter.exportDecks(deckIds))
                val bitmap = QrCodeGenerator.generate(url)
                _state.value = ShareUiState.QrReady(url, bitmap)
            } catch (e: Exception) {
                _state.value = ShareUiState.Error(e.message ?: "Share failed.")
            }
        }
    }

    fun requestShareSheet(url: String) {
        viewModelScope.launch { _events.emit(ShareEvent.OpenShareSheet(url)) }
    }

    fun dismiss() {
        _state.value = ShareUiState.Idle
    }
}

class ShareViewModelFactory(
    private val database: FlashcardDatabase
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShareViewModel::class.java)) {
            return ShareViewModel(database) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
