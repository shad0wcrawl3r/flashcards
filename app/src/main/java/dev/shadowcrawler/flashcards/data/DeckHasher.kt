package dev.shadowcrawler.flashcards.data

import dev.shadowcrawler.flashcards.model.CardType
import dev.shadowcrawler.flashcards.model.Flashcard
import java.security.MessageDigest

/**
 * Derives a stable content hash for a deck so the same deck can be recognized
 * (and re-import skipped) regardless of who exported it or how its cards are ordered.
 */
object DeckHasher {

    fun compute(deckName: String, cards: List<HashableCard>): String {
        val separator = 1.toChar()
        val canonical = buildString {
            append(deckName.trim().lowercase())
            cards
                .sortedWith(compareBy({ it.question }, { it.answer }))
                .forEach { card ->
                    append(separator).append(card.type.name)
                    append(separator).append(card.question.trim())
                    append(separator).append(card.answer.trim())
                }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

data class HashableCard(
    val type: CardType,
    val question: String,
    val answer: String
)

fun List<Flashcard>.toHashable(): List<HashableCard> =
    map { HashableCard(it.type, it.question, it.answer) }
