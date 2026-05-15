package com.getgymdone.app.data.db

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun stringListToJson(value: List<String>?): String =
        Json.encodeToString(ListSerializer(String.serializer()), value ?: emptyList())

    @TypeConverter
    fun jsonToStringList(value: String?): List<String> {
        if (value.isNullOrEmpty()) return emptyList()
        return Json.decodeFromString(ListSerializer(String.serializer()), value)
    }
}
