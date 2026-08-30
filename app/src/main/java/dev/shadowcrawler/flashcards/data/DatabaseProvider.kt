package dev.shadowcrawler.flashcards.data

import android.content.Context
import androidx.room3.Room
import dev.shadowcrawler.flashcards.model.FlashcardDatabase

object DatabaseProvider {

    @Volatile
    private var INSTANCE: FlashcardDatabase? = null

    fun get(context: Context): FlashcardDatabase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                FlashcardDatabase::class.java,
                "flashcards.db"
            )
                // No migrations exist yet for this pre-release schema; recreate on version bumps.
                .fallbackToDestructiveMigration()
                .build().also {
                    INSTANCE = it
                }
        }
    }
}
