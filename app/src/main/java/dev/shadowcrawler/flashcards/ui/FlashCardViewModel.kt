package dev.shadowcrawler.flashcards.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.shadowcrawler.flashcards.model.Flashcard
import dev.shadowcrawler.flashcards.model.FlashcardDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FlashcardViewModel(
    private val database: FlashcardDatabase,
    private val deckId: Long
) : ViewModel() {

    private val _flashcards = MutableStateFlow<List<Flashcard>>(emptyList())
    val flashcards: StateFlow<List<Flashcard>> = _flashcards.asStateFlow()

    init {
        initialize()
    }

    private fun initialize() {
        viewModelScope.launch {
            _flashcards.value = database.flashcardDao().getByDeck(deckId)
        }
    }
}
