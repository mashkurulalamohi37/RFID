package com.rfid.rolltrack.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object LenientJson {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun parseObject(raw: String): JsonObject {
        return try {
            json.parseToJsonElement(raw).jsonObject
        } catch (_: Exception) {
            JsonObject(emptyMap())
        }
    }

    fun find(obj: JsonObject, vararg candidateKeys: String): JsonElement? {
        val normalized = obj.mapKeys { normalizeKey(it.key) }
        for (candidate in candidateKeys) {
            val v = normalized[normalizeKey(candidate)]
            if (v != null) return v
        }
        return null
    }

    fun findString(obj: JsonObject, vararg candidateKeys: String): String? {
        val el = find(obj, *candidateKeys) ?: return null
        return (el as? JsonPrimitive)?.content
    }

    fun findInt(obj: JsonObject, vararg candidateKeys: String): Int? {
        val s = findString(obj, *candidateKeys) ?: return null
        return s.toIntOrNull()
    }

    fun findDouble(obj: JsonObject, vararg candidateKeys: String): Double? {
        val s = findString(obj, *candidateKeys) ?: return null
        return s.toDoubleOrNull()
    }

    fun findArray(obj: JsonObject, vararg candidateKeys: String): JsonArray? {
        val el = find(obj, *candidateKeys) ?: return null
        return el as? JsonArray
    }

    private fun normalizeKey(key: String): String {
        return key.replace("_", "").replace("-", "").lowercase()
    }
}
