package dev.shadowcrawler.flashcards.model

import androidx.room3.ColumnTypeConverter
import kotlinx.serialization.json.Json

class Converters {

    private val json = Json

    @ColumnTypeConverter
    fun fromCardType(type: CardType): String {
        return type.name
    }

    @ColumnTypeConverter
    fun toCardType(value: String): CardType {
        return CardType.valueOf(value)
    }

    @ColumnTypeConverter
    fun fromChoices(choices: List<String>): String {
        return json.encodeToString(choices)
    }

    @ColumnTypeConverter
    fun toChoices(value: String): List<String> {
        return json.decodeFromString(value)
    }

    @ColumnTypeConverter
    fun fromMetadata(metadata: Map<String, String>): String {
        return json.encodeToString(metadata)
    }

    @ColumnTypeConverter
    fun toMetadata(value: String): Map<String, String> {
        return json.decodeFromString(value)
    }
}
