package dev.shadowcrawler.flashcards.data

import dev.shadowcrawler.flashcards.model.Deck
import dev.shadowcrawler.flashcards.model.Flashcard
import dev.shadowcrawler.flashcards.model.FlashcardDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.net.URL

sealed interface DeckImportResult {
    data class Imported(val deck: Deck) : DeckImportResult
    /** A deck with the same content hash already exists locally; nothing was inserted. */
    data class Skipped(val existingDeck: Deck) : DeckImportResult
}

class DeckImporter(
    private val database: FlashcardDatabase
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun importFromUrl(urlString: String): List<DeckImportResult> = withContext(Dispatchers.IO) {
        val jsonText = URL(urlString).readText()
        importFromJson(jsonText)
    }

    /**
     * Accepts either a single-deck payload (`{ "deck": ..., "cards": [...] }`)
     * or a bulk payload (`{ "decks": [ { "deck": ..., "cards": [...] }, ... ] }`),
     * the latter produced by the deck-sharing feature when multiple decks are shared at once.
     */
    suspend fun importFromJson(jsonText: String): List<DeckImportResult> = withContext(Dispatchers.IO) {
        val payloads = parsePayloads(jsonText)
        require(payloads.isNotEmpty()) { "No decks found to import." }
        payloads.map { importPayload(it) }
    }

    private fun parsePayloads(jsonText: String): List<ImportPayload> {
        val root = json.parseToJsonElement(jsonText).jsonObject
        return if ("decks" in root) {
            json.decodeFromString(BulkImportPayload.serializer(), jsonText).decks
        } else {
            listOf(json.decodeFromString(ImportPayload.serializer(), jsonText))
        }
    }

    private suspend fun importPayload(payload: ImportPayload): DeckImportResult {
        require(payload.deck.name.isNotBlank()) { "Deck must have a name." }
        require(payload.cards.isNotEmpty()) { "Deck \"${payload.deck.name}\" must contain at least one card." }

        val hash = payload.deck.hash ?: DeckHasher.compute(payload.deck.name, payload.cards.toHashable())

        database.deckDao().findByHash(hash)?.let { existing ->
            return DeckImportResult.Skipped(existing)
        }

        val deckId = database.deckDao().insert(
            Deck(
                name = payload.deck.name,
                description = payload.deck.description,
                source = payload.deck.source,
                tags = payload.deck.tags,
                hash = hash
            )
        )

        database.flashcardDao().insertAll(
            payload.cards.map { card ->
                Flashcard(
                    deckId = deckId,
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

        return DeckImportResult.Imported(
            Deck(
                id = deckId,
                name = payload.deck.name,
                description = payload.deck.description,
                source = payload.deck.source,
                tags = payload.deck.tags,
                hash = hash
            )
        )
    }
}

fun List<ImportCard>.toHashable(): List<HashableCard> =
    map { HashableCard(it.type, it.question, it.answer) }
