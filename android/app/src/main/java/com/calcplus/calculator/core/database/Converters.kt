package com.calcplus.calculator.core.database

import androidx.room.TypeConverter
import com.calcplus.calculator.core.database.entity.LabeledValue
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun labeledValuesToJson(values: List<LabeledValue>): String =
        json.encodeToString(values)

    @TypeConverter
    fun labeledValuesFromJson(encoded: String): List<LabeledValue> = try {
        json.decodeFromString(encoded)
    } catch (_: Exception) {
        emptyList()
    }
}
