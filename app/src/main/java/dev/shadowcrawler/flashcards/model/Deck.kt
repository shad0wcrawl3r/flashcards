package dev.shadowcrawler.flashcards.model

import androidx.room3.ColumnTypeConverters
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable

@Entity
@Serializable
@ColumnTypeConverters(Converters::class)
data class Deck(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val source: String,
    val tags: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val hash: String? = null
)
