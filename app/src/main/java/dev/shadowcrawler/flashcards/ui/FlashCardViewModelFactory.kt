package dev.shadowcrawler.flashcards.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.shadowcrawler.flashcards.model.FlashcardDatabase

class FlashcardViewModelFactory(
    private val database: FlashcardDatabase,
    private val deckId: Long
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FlashcardViewModel::class.java)) {
            return FlashcardViewModel(database, deckId) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
