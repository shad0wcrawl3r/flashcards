package dev.shadowcrawler.flashcards.data

import dev.shadowcrawler.flashcards.model.FlashcardDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class DeckExporter(
    private val database: FlashcardDatabase
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Serializes the given decks (and their cards) into a bulk import payload. */
    suspend fun exportDecks(deckIds: List<Long>): String = withContext(Dispatchers.IO) {
        val deckDao = database.deckDao()
        val flashcardDao = database.flashcardDao()

        val payloads = deckDao.getByIds(deckIds).map { deck ->
            val cards = flashcardDao.getByDeck(deck.id)

            // Older or never-shared decks may not have a hash yet; generate and persist one now
            // so it's stable across future exports/imports of this same deck.
            val hash = deck.hash ?: DeckHasher.compute(deck.name, cards.toHashable()).also { generated ->
                deckDao.update(deck.copy(hash = generated))
            }

            ImportPayload(
                deck = ImportDeck(
                    name = deck.name,
                    description = deck.description,
                    source = deck.source,
                    tags = deck.tags,
                    hash = hash
                ),
                cards = cards.map { card ->
                    ImportCard(
                        type = card.type,
                        question = card.question,
                        answer = card.answer,
                        choices = card.choices,
                        difficulty = card.difficulty,
                        explanation = card.explanation,
                        metadata = card.metadata
                    )
                }
            )
        }

        require(payloads.isNotEmpty()) { "No decks selected to share." }

        json.encodeToString(BulkImportPayload.serializer(), BulkImportPayload(payloads))
    }
}
