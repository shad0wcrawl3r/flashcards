package dev.shadowcrawler.flashcards.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.shadowcrawler.flashcards.data.DatabaseInitializer
import dev.shadowcrawler.flashcards.model.Deck
import dev.shadowcrawler.flashcards.model.FlashcardDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DeckSummary(
    val deck: Deck,
    val cardCount: Int
)

class DeckListViewModel(
    private val database: FlashcardDatabase
) : ViewModel() {

    private val _decks = MutableStateFlow<List<DeckSummary>>(emptyList())
    val decks: StateFlow<List<DeckSummary>> = _decks.asStateFlow()

    private val _selectedDeckIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedDeckIds: StateFlow<Set<Long>> = _selectedDeckIds.asStateFlow()

    init {
        loadDecks()
    }

    fun loadDecks() {
        viewModelScope.launch {
            DatabaseInitializer(database).initialize()

            val deckDao = database.deckDao()
            val flashcardDao = database.flashcardDao()

            _decks.value = deckDao.getAll().map { deck ->
                DeckSummary(
                    deck = deck,
                    cardCount = flashcardDao.countForDeck(deck.id)
                )
            }
        }
    }

    /** Toggles a deck's selection. Selecting the first deck enters selection mode. */
    fun toggleSelection(deckId: Long) {
        _selectedDeckIds.update { current ->
            if (deckId in current) current - deckId else current + deckId
        }
    }

    fun clearSelection() {
        _selectedDeckIds.value = emptySet()
    }

    fun deleteSelected() {
        val ids = _selectedDeckIds.value.toList()
        if (ids.isEmpty()) return

        viewModelScope.launch {
            // Flashcard rows cascade-delete with their parent Deck (see Flashcard's ForeignKey).
            database.deckDao().deleteByIds(ids)
            _selectedDeckIds.value = emptySet()
            loadDecks()
        }
    }
}

class DeckListViewModelFactory(
    private val database: FlashcardDatabase
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DeckListViewModel::class.java)) {
            return DeckListViewModel(database) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
