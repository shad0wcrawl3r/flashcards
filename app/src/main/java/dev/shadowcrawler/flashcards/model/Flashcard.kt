package dev.shadowcrawler.flashcards.model
import androidx.room3.ColumnTypeConverters
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable


@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Deck::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["deckId"])
    ]
)
@Serializable
@ColumnTypeConverters(Converters::class)
data class Flashcard(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deckId: Long,
    val type: CardType,
    val question: String,
    val answer: String,
    val choices: List<String> = emptyList(),
    val difficulty: Int,
    val explanation: String? = null,
    val metadata: Map<String, String> = emptyMap()
)
@Serializable
enum class CardType {
    QA,
    MCQ
}

data class CardDisplayState(
    val card: Flashcard,
    val showingAnswer: Boolean
)