package dev.shadowcrawler.flashcards.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.shadowcrawler.flashcards.data.DeckHasher
import dev.shadowcrawler.flashcards.data.HashableCard
import dev.shadowcrawler.flashcards.model.CardType
import dev.shadowcrawler.flashcards.model.Deck
import dev.shadowcrawler.flashcards.model.Flashcard
import dev.shadowcrawler.flashcards.model.FlashcardDatabase
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A card being authored in the editor. MCQ is the default and primary-path type. */
data class DraftCard(
    val localId: String = UUID.randomUUID().toString(),
    val type: CardType = CardType.MCQ,
    val question: String = "",
    val choices: List<String> = listOf("", "", "", ""),
    val correctChoiceIndex: Int? = null,
    val qaAnswer: String = "",
    val explanation: String = "",
    val difficulty: Int = 10
) {
    /** The answer text stored on the Flashcard, regardless of card type. */
    val resolvedAnswer: String
        get() = when (type) {
            CardType.MCQ -> correctChoiceIndex?.let { choices.getOrNull(it) }.orEmpty()
            CardType.QA -> qaAnswer
        }

    val isValid: Boolean
        get() = when (type) {
            CardType.MCQ -> {
                val nonBlankChoices = choices.map { it.trim() }.filter { it.isNotEmpty() }
                question.isNotBlank() &&
                    nonBlankChoices.size >= 2 &&
                    correctChoiceIndex != null &&
                    choices.getOrNull(correctChoiceIndex).orEmpty().isNotBlank()
            }
            CardType.QA -> question.isNotBlank() && qaAnswer.isNotBlank()
        }

    val summary: String
        get() = question.ifBlank { "Untitled question" }
}

data class DeckEditorUiState(
    val isNewDeck: Boolean = true,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val deckName: String = "",
    val description: String = "",
    val tagsText: String = "",
    val cards: List<DraftCard> = emptyList(),
    val errorMessage: String? = null,
    val saved: Boolean = false
) {
    val tags: List<String>
        get() = tagsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    val canSave: Boolean
        get() = deckName.isNotBlank() && cards.isNotEmpty() && cards.all { it.isValid }
}

class DeckEditorViewModel(
    private val database: FlashcardDatabase,
    private val deckId: Long
) : ViewModel() {

    private val isNewDeck = deckId == 0L

    private val _state = MutableStateFlow(DeckEditorUiState(isNewDeck = isNewDeck))
    val state: StateFlow<DeckEditorUiState> = _state.asStateFlow()

    init {
        if (isNewDeck) {
            _state.update { it.copy(isLoading = false) }
        } else {
            loadExistingDeck()
        }
    }

    private fun loadExistingDeck() {
        viewModelScope.launch {
            val deck = database.deckDao().getById(deckId)
            if (deck == null) {
                _state.update { it.copy(isLoading = false, errorMessage = "Deck not found.") }
                return@launch
            }
            val cards = database.flashcardDao().getByDeck(deckId).map { it.toDraft() }
            _state.update {
                it.copy(
                    isLoading = false,
                    deckName = deck.name,
                    description = deck.description.orEmpty(),
                    tagsText = deck.tags.joinToString(", "),
                    cards = cards
                )
            }
        }
    }

    fun updateName(name: String) = _state.update { it.copy(deckName = name) }
    fun updateDescription(description: String) = _state.update { it.copy(description = description) }
    fun updateTagsText(tagsText: String) = _state.update { it.copy(tagsText = tagsText) }

    fun upsertCard(card: DraftCard) {
        _state.update { current ->
            val existingIndex = current.cards.indexOfFirst { it.localId == card.localId }
            val newCards = if (existingIndex >= 0) {
                current.cards.toMutableList().apply { set(existingIndex, card) }
            } else {
                current.cards + card
            }
            current.copy(cards = newCards)
        }
    }

    fun removeCard(localId: String) {
        _state.update { current ->
            current.copy(cards = current.cards.filterNot { it.localId == localId })
        }
    }

    fun save() {
        val snapshot = _state.value
        if (!snapshot.canSave || snapshot.isSaving) return

        _state.update { it.copy(isSaving = true, errorMessage = null) }

        viewModelScope.launch {
            val cards = snapshot.cards
            val hash = DeckHasher.compute(snapshot.deckName, cards.map { it.toHashable() })

            val resolvedDeckId = if (isNewDeck) {
                database.deckDao().insert(
                    Deck(
                        name = snapshot.deckName.trim(),
                        description = snapshot.description.trim().ifBlank { null },
                        source = "manual",
                        tags = snapshot.tags,
                        hash = hash
                    )
                )
            } else {
                database.deckDao().update(
                    Deck(
                        id = deckId,
                        name = snapshot.deckName.trim(),
                        description = snapshot.description.trim().ifBlank { null },
                        source = "manual",
                        tags = snapshot.tags,
                        hash = hash
                    )
                )
                database.flashcardDao().deleteByDeck(deckId)
                deckId
            }

            database.flashcardDao().insertAll(cards.map { it.toFlashcard(resolvedDeckId) })

            _state.update { it.copy(isSaving = false, saved = true) }
        }
    }
}

private fun Flashcard.toDraft(): DraftCard = when (type) {
    CardType.MCQ -> DraftCard(
        type = CardType.MCQ,
        question = question,
        choices = choices.ifEmpty { listOf("", "", "", "") },
        correctChoiceIndex = choices.indexOf(answer).takeIf { it >= 0 },
        explanation = explanation.orEmpty(),
        difficulty = difficulty
    )
    CardType.QA -> DraftCard(
        type = CardType.QA,
        question = question,
        qaAnswer = answer,
        explanation = explanation.orEmpty(),
        difficulty = difficulty
    )
}

private fun DraftCard.toFlashcard(deckId: Long): Flashcard = Flashcard(
    deckId = deckId,
    type = type,
    question = question.trim(),
    answer = resolvedAnswer.trim(),
    choices = if (type == CardType.MCQ) choices.map { it.trim() }.filter { it.isNotEmpty() } else emptyList(),
    difficulty = difficulty,
    explanation = explanation.trim().ifBlank { null }
)

private fun DraftCard.toHashable() = HashableCard(
    type = type,
    question = question,
    answer = resolvedAnswer
)

class DeckEditorViewModelFactory(
    private val database: FlashcardDatabase,
    private val deckId: Long
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DeckEditorViewModel::class.java)) {
            return DeckEditorViewModel(database, deckId) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
