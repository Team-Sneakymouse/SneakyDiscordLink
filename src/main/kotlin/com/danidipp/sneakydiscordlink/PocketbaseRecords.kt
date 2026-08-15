package com.danidipp.sneakydiscordlink

import com.danidipp.sneakypocketbase.PocketbaseProvider
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject

object PocketbaseRecords {
    val json = Json { ignoreUnknownKeys = true }

    inline fun <reified T> getOne(collection: String, recordId: String): T =
        json.decodeFromString(PocketbaseProvider.getApi().getOne(collection, recordId).join())

    inline fun <reified T> getFullList(collection: String, batchSize: Int, filter: String): List<T> =
        PocketbaseProvider.getApi().getFullList(collection, batchSize, "", filter).join()
            .map(json::decodeFromString)

    inline fun <reified T> create(collection: String, data: String): T =
        json.decodeFromString(PocketbaseProvider.getApi().create(collection, data).join())

    inline fun <reified T> update(collection: String, recordId: String, data: String): T =
        json.decodeFromString(PocketbaseProvider.getApi().update(collection, recordId, data).join())

    fun delete(collection: String, recordId: String): Boolean =
        PocketbaseProvider.getApi().delete(collection, recordId).join()

    inline fun <reified T> encodeCreate(value: T): String {
        val fields = Json.encodeToJsonElement(serializer<T>(), value).jsonObject.toMutableMap()
        if (fields["id"] == JsonNull) fields.remove("id")
        return JsonObject(fields).toString()
    }

    inline fun <reified T> encodeUpdate(value: T): String {
        val fields = Json.encodeToJsonElement(serializer<T>(), value).jsonObject.toMutableMap()
        fields.remove("id")
        return JsonObject(fields).toString()
    }
}
