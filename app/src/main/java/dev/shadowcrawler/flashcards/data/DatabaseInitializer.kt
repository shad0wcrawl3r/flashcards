package dev.shadowcrawler.flashcards.data

import dev.shadowcrawler.flashcards.model.FlashcardDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DatabaseInitializer(
    private val database: FlashcardDatabase
) {

    suspend fun initialize() {
        // Guards the check-then-insert below: DeckListViewModel calls initialize() both on
        // creation and on every ON_RESUME, and those can race on first launch before the
        // sample deck's insert has committed, each seeing count() == 0 and inserting it twice.
        mutex.withLock {
            val deckDao = database.deckDao()

            if (deckDao.count() > 0) {
                return
            }

            // Give the sample deck a content hash up front, same as any imported deck, so a
            // later import of the same deck (e.g. a shared export containing it) is recognized
            // as a duplicate instead of being inserted again.
            val hash = DeckHasher.compute(SampleData.sampleDeck.name, SampleData.cardsForDeck(0).toHashable())

            val deckId = deckDao.insert(SampleData.sampleDeck.copy(hash = hash))

            val cards = SampleData.cardsForDeck(deckId)

            database.flashcardDao().insertAll(cards)
        }
    }

    private companion object {
        val mutex = Mutex()
    }
}
