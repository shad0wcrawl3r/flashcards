package dev.shadowcrawler.flashcards.model

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update

@Dao
interface DeckDao {

    @Query("SELECT * FROM Deck")
    suspend fun getAll(): List<Deck>

    @Query("SELECT * FROM Deck WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<Deck>

    @Query("SELECT * FROM Deck WHERE id = :id")
    suspend fun getById(id: Long): Deck?

    @Query("SELECT * FROM Deck WHERE hash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): Deck?

    @Query("SELECT COUNT(*) FROM Deck")
    suspend fun count(): Int

    @Insert
    suspend fun insert(deck: Deck): Long

    @Update
    suspend fun update(deck: Deck)

    @Delete
    suspend fun delete(deck: Deck)

    @Query("DELETE FROM Deck WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
