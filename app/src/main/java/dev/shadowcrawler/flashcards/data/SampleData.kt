package dev.shadowcrawler.flashcards.data

import dev.shadowcrawler.flashcards.model.Flashcard
import dev.shadowcrawler.flashcards.model.Deck
import dev.shadowcrawler.flashcards.model.CardType

object SampleData {
    val sampleDeck = Deck(
        name = "System Samples",
        description = "Sample cards provided with the application.",
        source = "app://sample",
        tags = listOf("sample", "system")
    )
    val sampleCards = cardsForDeck(1)
    fun cardsForDeck(deckId: Long): List<Flashcard> {
        return listOf(
            Flashcard(
                type = CardType.QA,
                question = "What port does HTTPS normally use?",
                answer = "443",
                difficulty = 5,
                explanation = "HTTPS normally uses TCP port 443.",
                deckId = deckId,
                metadata = mapOf(
                    "topic" to "Networking"
                )
            ),
            Flashcard(
                type = CardType.QA,
                question = "What does DNS stand for?",
                answer = "Domain Name System",
                difficulty = 8,
                explanation = "DNS translates domain names into IP addresses.",
                deckId = deckId,
                metadata = mapOf(
                    "topic" to "Networking"
                )
            ),
            Flashcard(
                type = CardType.QA,
                question = "What does CIDR stand for?",
                answer = "Classless Inter-Domain Routing",
                difficulty = 15,
                deckId = deckId,
                metadata = mapOf(
                    "topic" to "Networking"
                )
            ),
            Flashcard(
                type = CardType.MCQ,
                question = "Which HTTP status code means \"Not Found\"?",
                answer = "404",
                choices = listOf("200", "301", "404", "500"),
                difficulty = 5,
                explanation = "404 means the server couldn't find the requested resource.",
                deckId = deckId,
                metadata = mapOf(
                    "topic" to "Networking"
                )
            ),
            Flashcard(
                type = CardType.MCQ,
                question = "Which layer of the OSI model do routers primarily operate at?",
                answer = "Network",
                choices = listOf("Physical", "Data Link", "Network", "Transport"),
                difficulty = 10,
                explanation = "Routers forward packets based on IP addresses, which live at the Network layer.",
                deckId = deckId,
                metadata = mapOf(
                    "topic" to "Networking"
                )
            )
        )
    }

}