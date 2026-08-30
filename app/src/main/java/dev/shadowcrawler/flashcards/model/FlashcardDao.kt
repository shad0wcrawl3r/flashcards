package dev.shadowcrawler.flashcards.model

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update

@Dao
interface FlashcardDao {

    @Query("SELECT * FROM Flashcard")
    suspend fun getAll(): List<Flashcard>

    @Query("SELECT * FROM Flashcard WHERE deckId = :deckId")
    suspend fun getByDeck(deckId: Long): List<Flashcard>

    @Query("SELECT COUNT(*) FROM Flashcard")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM Flashcard WHERE deckId = :deckId")
    suspend fun countForDeck(deckId: Long): Int

    @Insert
    suspend fun insert(flashcard: Flashcard): Long

    @Insert
    suspend fun insertAll(flashcards: List<Flashcard>)
    @Update
    suspend fun update(flashcard: Flashcard)

    @Delete
    suspend fun delete(flashcard: Flashcard)

    @Query("DELETE FROM Flashcard WHERE deckId = :deckId")
    suspend fun deleteByDeck(deckId: Long)
}

