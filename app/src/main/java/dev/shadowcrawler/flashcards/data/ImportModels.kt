package dev.shadowcrawler.flashcards.data

import dev.shadowcrawler.flashcards.model.CardType
import kotlinx.serialization.Serializable

@Serializable
data class ImportPayload(
    val deck: ImportDeck,
    val cards: List<ImportCard> = emptyList()
)

@Serializable
data class BulkImportPayload(
    val decks: List<ImportPayload> = emptyList()
)

@Serializable
data class ImportDeck(
    val name: String,
    val description: String? = null,
    val source: String = "import",
    val tags: List<String> = emptyList(),
    /** Content hash used to recognize this deck on import and skip re-importing it. */
    val hash: String? = null
)

@Serializable
data class ImportCard(
    val type: CardType,
    val question: String,
    val answer: String,
    val choices: List<String> = emptyList(),
    val difficulty: Int = 5,
    val explanation: String? = null,
    val metadata: Map<String, String> = emptyMap()
)
