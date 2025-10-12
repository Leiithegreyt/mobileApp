package com.example.drivebroom.network

import com.google.gson.*
import java.lang.reflect.Type

class TripDeserializer : JsonDeserializer<Trip> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): Trip {
        val jsonObject = json?.asJsonObject ?: throw JsonParseException("Expected JSON object")
        
        // Handle ID field - can be string or integer
        val idElement = jsonObject.get("id")
        val id = when {
            idElement != null && !idElement.isJsonNull && idElement.isJsonPrimitive && idElement.asJsonPrimitive.isString -> {
                // Extract numeric part from string IDs like "shared_2"
                val idStr = idElement.asString
                val numericPart = idStr.replace(Regex("[^0-9]"), "")
                if (numericPart.isNotEmpty()) numericPart.toIntOrNull() ?: 0 else 0
            }
            idElement != null && !idElement.isJsonNull && idElement.isJsonPrimitive && idElement.asJsonPrimitive.isNumber -> {
                idElement.asInt
            }
            else -> 0
        }
        
        return Trip(
            id = id,
            destination = safeGetString(jsonObject, "destination"),
            purpose = safeGetString(jsonObject, "purpose"),
            travel_date = safeGetString(jsonObject, "travel_date"),
            travel_time = safeGetString(jsonObject, "travel_time"),
            status = safeGetString(jsonObject, "status"),
            pickup_location = safeGetString(jsonObject, "pickup_location"),
            requestedBy = safeGetString(jsonObject, "requested_by"),
            trip_type = safeGetString(jsonObject, "trip_type"),
            key = safeGetString(jsonObject, "key"),
            is_shared_trip = safeGetInt(jsonObject, "is_shared_trip"),
            shared_trip_id = safeGetInt(jsonObject, "shared_trip_id")
        )
    }
    
    private fun safeGetString(jsonObject: JsonObject, key: String): String? {
        val element = jsonObject.get(key)
        return if (element != null && !element.isJsonNull && element.isJsonPrimitive && element.asJsonPrimitive.isString) {
            element.asString
        } else null
    }
    
    private fun safeGetInt(jsonObject: JsonObject, key: String): Int? {
        val element = jsonObject.get(key)
        return if (element != null && !element.isJsonNull && element.isJsonPrimitive && element.asJsonPrimitive.isNumber) {
            element.asInt
        } else null
    }
}
