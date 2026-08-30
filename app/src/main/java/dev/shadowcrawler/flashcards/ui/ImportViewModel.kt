package dev.shadowcrawler.flashcards.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.shadowcrawler.flashcards.data.DeckImportResult
import dev.shadowcrawler.flashcards.data.DeckImporter
import dev.shadowcrawler.flashcards.model.FlashcardDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Loading : ImportUiState
    data class Success(
        val importedNames: List<String>,
        val skippedNames: List<String>,
        val cardCount: Int
    ) : ImportUiState
    data class Error(val message: String) : ImportUiState
}

class ImportViewModel(
    private val database: FlashcardDatabase
) : ViewModel() {

    private val importer = DeckImporter(database)

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    fun importFromUrl(url: String) {
        if (url.isBlank()) return
        runImport { importer.importFromUrl(url.trim()) }
    }

    fun importFromJsonText(jsonText: String) {
        runImport { importer.importFromJson(jsonText) }
    }

    fun reportError(message: String) {
        _state.value = ImportUiState.Error(message)
    }

    /** Handles both single-deck and bulk imports, and decks already present (matched by hash). */
    private fun runImport(block: suspend () -> List<DeckImportResult>) {
        _state.value = ImportUiState.Loading
        viewModelScope.launch {
            try {
                val results = block()
                val flashcardDao = database.flashcardDao()

                val importedNames = mutableListOf<String>()
                val skippedNames = mutableListOf<String>()
                var cardCount = 0

                results.forEach { result ->
                    when (result) {
                        is DeckImportResult.Imported -> {
                            importedNames += result.deck.name
                            cardCount += flashcardDao.countForDeck(result.deck.id)
                        }
                        is DeckImportResult.Skipped -> skippedNames += result.existingDeck.name
                    }
                }

                _state.value = ImportUiState.Success(importedNames, skippedNames, cardCount)
            } catch (e: Exception) {
                _state.value = ImportUiState.Error(e.message ?: "Import failed.")
            }
        }
    }
}

class ImportViewModelFactory(
    private val database: FlashcardDatabase
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ImportViewModel::class.java)) {
            return ImportViewModel(database) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
