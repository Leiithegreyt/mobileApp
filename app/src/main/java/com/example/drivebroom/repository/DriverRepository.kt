package com.example.drivebroom.repository

import com.example.drivebroom.network.ApiService
import com.example.drivebroom.network.LoginRequest
import com.example.drivebroom.network.LoginResponse
import com.example.drivebroom.network.FcmTokenRequest
import com.example.drivebroom.network.DepartureBody
import com.example.drivebroom.network.ArrivalBody
import com.example.drivebroom.network.ReturnBody
import com.example.drivebroom.network.TripDetails
import com.example.drivebroom.network.CompletedTrip
import com.example.drivebroom.network.DriverProfile
import com.example.drivebroom.network.SharedTripLeg
import com.example.drivebroom.network.LegDepartureRequest
import com.example.drivebroom.network.LegArrivalRequest
import com.example.drivebroom.network.LegCompletionRequest
import retrofit2.Response
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import com.google.gson.JsonParser
import android.util.Log

class DriverRepository(val apiService: ApiService) {
    suspend fun loginDriver(email: String, password: String): Result<LoginResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val json = apiService.loginDriver(LoginRequest(email, password))
                // Accept both wrapped and direct login responses
                val gson = Gson()
                val response: LoginResponse = if (json.isJsonObject) {
                    // Could be { access_token, user } or { data: { access_token, user } }
                    val obj = json.asJsonObject
                    if (obj.has("access_token") || obj.has("user")) {
                        gson.fromJson(obj, LoginResponse::class.java)
                    } else if (obj.has("data")) {
                        gson.fromJson(obj.get("data"), LoginResponse::class.java)
                    } else {
                        // Fallback attempt
                        gson.fromJson(obj, LoginResponse::class.java)
                    }
                } else {
                    // If backend ever returns a string token, treat as token only
                    val token = json.asString
                    LoginResponse(access_token = token, user = null)
                }
                if (response.access_token != null) {
                    Result.success(response)
                } else {
                    Result.failure(Exception("Login failed: Invalid credentials or missing token"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun updateFcmToken(token: String): Result<Unit> {
        return try {
            val response = apiService.updateFcmToken(FcmTokenRequest(token))
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed to update FCM token"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logDeparture(tripId: Int, body: DepartureBody): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("DriverRepository", "Logging departure for trip $tripId with body: $body")
                val response = apiService.logDeparture(tripId, body)
                Log.d("DriverRepository", "Departure response code: ${response.code()}")
                Log.d("DriverRepository", "Departure response headers: ${response.headers()}")
                if (response.isSuccessful) {
                    Log.d("DriverRepository", "Departure successful")
                    Result.success(Unit)
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("DriverRepository", "Departure failed with code ${response.code()}: $errorBody")
                    Result.failure(Exception("Departure failed: ${response.code()} - $errorBody"))
                }
            } catch (e: Exception) {
                Log.e("DriverRepository", "Departure exception", e)
                Result.failure(e)
            }
        }
    }

    suspend fun logArrival(tripId: Int, body: ArrivalBody): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.logArrival(tripId, body)
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception(response.errorBody()?.string() ?: "Arrival failed"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun logReturn(tripId: Int, body: ReturnBody): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.logReturn(tripId, body)
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception(response.errorBody()?.string() ?: "Return failed"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getTripDetails(tripId: Int): Result<TripDetails> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("DriverRepository", "Getting trip details for trip ID: $tripId")
                val json = apiService.getTripDetails(tripId)
                Log.d("DriverRepository", "Raw trip details JSON: $json")
                
                val gson = Gson()
                var tripDetails: TripDetails = if (json.isJsonObject) {
                    val obj = json.asJsonObject
                    Log.d("DriverRepository", "Trip details object keys: ${obj.keySet()}")
                    
                    when {
                        obj.has("trip") -> {
                            Log.d("DriverRepository", "Found 'trip' key, parsing nested object")
                            val tripObj = obj.get("trip").asJsonObject
                            Log.d("DriverRepository", "Trip object keys: ${tripObj.keySet()}")
                            Log.d("DriverRepository", "Trip purpose: ${tripObj.get("purpose")}")
                            Log.d("DriverRepository", "Trip passengers: ${tripObj.get("passengers")}")
                            Log.d("DriverRepository", "Trip legs: ${tripObj.get("legs")}")
                            gson.fromJson(tripObj, TripDetails::class.java)
                        }
                        obj.has("data") -> {
                            Log.d("DriverRepository", "Found 'data' key, parsing nested object (unified API)")
                            val dataObj = obj.get("data").asJsonObject
                            Log.d("DriverRepository", "Data object keys: ${dataObj.keySet()}")
                            // Try Gson first
                            val parsed: TripDetails = try {
                                gson.fromJson(dataObj, TripDetails::class.java)
                            } catch (e: Exception) {
                                Log.w("DriverRepository", "Gson parse failed for unified data: ${e.message}")
                                // Determine trip type based on actual data structure, not just the field
                                val hasTripStops = dataObj.has("trip_stops") && dataObj.get("trip_stops").isJsonArray && dataObj.get("trip_stops").asJsonArray.size() > 1
                                val hasLegs = dataObj.has("legs") && dataObj.get("legs").isJsonArray && dataObj.get("legs").asJsonArray.size() > 1
                                val declaredTripType = dataObj.get("trip_type")?.asString
                                
                                // If it has multiple stops/legs, it's definitely a shared trip
                                // Otherwise, respect the declared trip_type or default to "single"
                                val determinedTripType = when {
                                    hasTripStops || hasLegs -> "shared"
                                    declaredTripType != null -> declaredTripType
                                    else -> "single"
                                }
                                
                                // Special handling for duplicate trip IDs - prioritize single trips when data doesn't match
                                val finalTripType = if (determinedTripType == "shared" && !hasTripStops && !hasLegs) {
                                    Log.w("DriverRepository", "Trip ID $tripId declared as 'shared' but has no multiple stops/legs - treating as single trip")
                                    "single"
                                } else {
                                    determinedTripType
                                }
                                
                                Log.d("DriverRepository", "Trip type determination: declared='$declaredTripType', hasStops=$hasTripStops, hasLegs=$hasLegs, determined='$finalTripType'")
                                
                                TripDetails(
                                    id = 0,
                                    status = dataObj.get("status")?.asString ?: "pending",
                                    travel_date = dataObj.get("travel_date")?.asString ?: "",
                                    date_of_request = dataObj.get("date_of_request")?.asString ?: dataObj.get("created_at")?.asString ?: "",
                                    travel_time = dataObj.get("departure_time")?.asString ?: "",
                                    destination = dataObj.get("destination")?.asString ?: if (hasTripStops) "Multiple Destinations" else "",
                                    purpose = dataObj.get("utilization_type")?.asString,
                                    passengers = dataObj.get("passengers") ?: com.google.gson.JsonArray(),
                                    vehicle = dataObj.get("vehicle")?.let { gson.fromJson(it, com.example.drivebroom.network.Vehicle::class.java) },
                                    trip_type = finalTripType,
                                    stops = null,
                                    legs = null,
                                    current_leg = 0
                                )
                            }
                            
                            // Validate and correct trip type even when Gson parsing succeeds
                            val hasTripStops = dataObj.has("trip_stops") && dataObj.get("trip_stops").isJsonArray && dataObj.get("trip_stops").asJsonArray.size() > 1
                            val hasLegs = dataObj.has("legs") && dataObj.get("legs").isJsonArray && dataObj.get("legs").asJsonArray.size() > 1
                            val declaredTripType = dataObj.get("trip_type")?.asString
                            
                            // Determine correct trip type based on actual data structure
                            val correctTripType = when {
                                hasTripStops || hasLegs -> "shared"
                                declaredTripType != null -> declaredTripType
                                else -> "single"
                            }
                            
                            // Special handling for duplicate trip IDs - use data structure to determine correct type
                            val finalTripType = when {
                                hasTripStops || hasLegs -> "shared"
                                correctTripType == "shared" && !hasTripStops && !hasLegs -> {
                                    Log.w("DriverRepository", "Trip ID $tripId declared as 'shared' but has no multiple stops/legs - treating as single trip")
                                    "single"
                                }
                                else -> correctTripType
                            }
                            
                            // Log trip type validation
                            if (parsed.trip_type != finalTripType) {
                                Log.w("DriverRepository", "Trip type mismatch corrected: declared='${parsed.trip_type}', actual='$finalTripType' (hasStops=$hasTripStops, hasLegs=$hasLegs)")
                            }
                            
                            // If id still 0, synthesize minimal fields from data
                            val finalParsed = if (parsed.id == 0 && dataObj.has("id")) {
                                Log.w("DriverRepository", "Parsed id was 0; overriding from data.id")
                                val rawId = dataObj.get("id")
                                val numericId = when {
                                    rawId.isJsonPrimitive && rawId.asJsonPrimitive.isString -> {
                                        // Handle string IDs like "shared_2" by extracting the numeric part
                                        val idStr = if (rawId.isJsonPrimitive && rawId.asJsonPrimitive.isString) rawId.asString else ""
                                        val numericPart = idStr.replace(Regex("[^0-9]"), "")
                                        if (numericPart.isNotEmpty()) numericPart.toIntOrNull() ?: 0 else 0
                                    }
                                    rawId.isJsonPrimitive && rawId.asJsonPrimitive.isNumber -> {
                                        rawId.asInt
                                    }
                                    else -> 0
                                }
                                parsed.copy(
                                    id = numericId,
                                    purpose = parsed.purpose ?: dataObj.get("utilization_type")?.asString,
                                    destination = when {
                                        !parsed.destination.isNullOrEmpty() -> parsed.destination
                                        hasTripStops -> "Multiple Destinations"
                                        else -> dataObj.get("destination")?.asString ?: ""
                                    },
                                    trip_type = finalTripType
                                )
                            } else {
                                parsed.copy(trip_type = finalTripType)
                            }
                            
                            finalParsed
                        }
                        // server may return bare TripDetails
                        obj.has("id") && obj.has("status") -> {
                            Log.d("DriverRepository", "Found direct TripDetails structure")
                            Log.d("DriverRepository", "Direct purpose: ${obj.get("purpose")}")
                            Log.d("DriverRepository", "Direct passengers: ${obj.get("passengers")}")
                            gson.fromJson(obj, TripDetails::class.java)
                        }
                        else -> {
                            Log.d("DriverRepository", "Using fallback parsing")
                            gson.fromJson(obj, TripDetails::class.java)
                        }
                    }
                } else {
                    throw IllegalStateException("Unexpected JSON for trip details")
                }
                
                // Enrich vehicle info if backend uses alternate key names
                try {
                    if (tripDetails.vehicle == null && json.isJsonObject) {
                        val rootObj = json.asJsonObject
                        val sourceObj = when {
                            rootObj.has("trip") -> rootObj.get("trip").asJsonObject
                            rootObj.has("data") -> rootObj.get("data").asJsonObject
                            else -> null
                        }
                        if (sourceObj != null) {
                            val vehEl = when {
                                sourceObj.has("vehicle") -> sourceObj.get("vehicle")
                                sourceObj.has("vehicle_info") -> sourceObj.get("vehicle_info")
                                else -> null
                            }
                            if (vehEl != null && vehEl.isJsonObject) {
                                val veh = gson.fromJson(vehEl, com.example.drivebroom.network.Vehicle::class.java)
                                tripDetails = tripDetails.copy(vehicle = veh)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("DriverRepository", "Vehicle enrichment failed: ${e.message}")
                }

                Log.d("DriverRepository", "Parsed trip details - ID: ${tripDetails.id}, Purpose: ${tripDetails.purpose}, Passengers: ${tripDetails.passengers}")
                Log.d("DriverRepository", "Trip details - travel_date: '${tripDetails.travel_date}', travel_time: '${tripDetails.travel_time}'")
                Log.d("DriverRepository", "Trip details - trip_type: '${tripDetails.trip_type}', destination: '${tripDetails.destination}'")
                Log.d("DriverRepository", "Passengers type: ${tripDetails.passengers?.javaClass?.simpleName}")
                Log.d("DriverRepository", "Passengers isJsonArray: ${tripDetails.passengers?.isJsonArray}")
                Log.d("DriverRepository", "Passengers isJsonPrimitive: ${tripDetails.passengers?.isJsonPrimitive}")
                if (tripDetails.passengers?.isJsonPrimitive == true) {
                    Log.d("DriverRepository", "Passengers as string: ${tripDetails.passengers?.asString}")
                }
                if (tripDetails.passengers?.isJsonArray == true) {
                    Log.d("DriverRepository", "Passengers array size: ${tripDetails.passengers?.asJsonArray?.size()}")
                    tripDetails.passengers?.asJsonArray?.forEachIndexed { index, element ->
                        Log.d("DriverRepository", "Passenger $index: $element")
                    }
                }
                Result.success(tripDetails)
            } catch (e: Exception) {
                Log.e("DriverRepository", "Error getting trip details: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    suspend fun restartTrip(tripId: Int, token: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // This should call an API endpoint for restarting a trip (for testing only)
                // For now, simulate success
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getDriverProfile(): Result<DriverProfile> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("DriverRepository", "getDriverProfile() called")
                val response = apiService.getDriverProfile()
                Log.d("DriverRepository", "Driver profile - ID: ${response.id}, Name: ${response.name}")
                Result.success(response)
            } catch (e: Exception) {
                Log.e("DriverRepository", "Error in getDriverProfile: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun getCompletedTrips(): Result<List<CompletedTrip>> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("DriverRepository", "getCompletedTrips() called")

                val response = apiService.getCompletedTrips() // Token handled by interceptor
                Log.d("Repository", "API call successful")
                Log.d("Repository", "Raw response: $response")
                Log.d("Repository", "Count: ${response.count}")
                Log.d("Repository", "Message: ${response.message}")
                Log.d("Repository", "Trips size: ${response.trips.size}")
                Log.d("Repository", "First trip: ${response.trips.firstOrNull()}")

                if (response.trips.isEmpty()) {
                    Log.w("Repository", "WARNING - Trips list is empty!")
                    Log.w("Repository", "This suggests the backend is not finding completed trips for this driver")
                } else {
                    Log.d("Repository", "SUCCESS - Found ${response.trips.size} trips")
                    response.trips.forEachIndexed { index, trip ->
                        Log.d("Repository", "Trip $index: ID=${trip.id}, Destination=${trip.destination}, Status=${trip.status}")
                    }
                }

                Result.success(response.trips)
            } catch (e: Exception) {
                Log.e("Repository", "Error in getCompletedTrips: ${e.message}")
                Log.e("Repository", "Exception type: ${e.javaClass.simpleName}")
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    // Debug endpoint to check raw database state
    suspend fun getDebugTripLegs(tripId: Int): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("DriverRepository", "=== DEBUG TRIP LEGS ===")
                Log.d("DriverRepository", "Calling debug endpoint for trip $tripId")
                val response = apiService.getDebugTripLegs(tripId)
                Log.d("DriverRepository", "Debug response code: ${response.code()}")
                val responseBody = if (response.isSuccessful) {
                    "Success - Response code: ${response.code()} - Debug endpoint working"
                } else {
                    "Error - Response code: ${response.code()}"
                }
                Log.d("DriverRepository", "Debug response body: $responseBody")
                responseBody
            } catch (e: Exception) {
                Log.e("DriverRepository", "Error calling debug endpoint: ${e.message}")
                e.printStackTrace()
                "Error: ${e.message}"
            }
        }
    }

    // Unified trip legs (single + shared)
    suspend fun getTripLegs(tripId: Int): List<SharedTripLeg> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("DriverRepository", "=== GET TRIP LEGS (UNIFIED) ===")
                Log.d("DriverRepository", "Getting trip legs for trip $tripId")
                val rawResponse = apiService.getTripLegs(tripId)
                Log.d("DriverRepository", "Raw API response: $rawResponse")
                Log.d("DriverRepository", "Response size: ${rawResponse.size}")
                
                // Log each leg's raw data
                rawResponse.forEachIndexed { index, leg ->
                    Log.d("DriverRepository", "=== RAW LEG $index ===")
                    Log.d("DriverRepository", "ID: ${leg.leg_id}")
                    Log.d("DriverRepository", "Status: ${leg.status}")
                    Log.d("DriverRepository", "Odometer End: ${leg.odometer_end}")
                    Log.d("DriverRepository", "Fuel End: ${leg.fuel_end}")
                    Log.d("DriverRepository", "Fuel Used: ${leg.fuel_used}")
                    Log.d("DriverRepository", "Fuel Purchased: ${leg.fuel_purchased}")
                    Log.d("DriverRepository", "Notes: ${leg.notes}")
                    Log.d("DriverRepository", "Passengers raw: ${leg.passengers}")
                }
                
                // Normalize passengers: backend returns objects like [{"name":"Sergie","count":1}]
                rawResponse.map { rawLeg ->
                    val normalizedPassengers: List<String> = try {
                        rawLeg.passengers?.let { jsonElement ->
                            Log.d("DriverRepository", "Processing passengers for leg ${rawLeg.leg_id}: $jsonElement")
                            Log.d("DriverRepository", "Is JsonArray: ${jsonElement.isJsonArray}")
                            Log.d("DriverRepository", "Is JsonPrimitive: ${jsonElement.isJsonPrimitive}")
                            
                            when {
                                jsonElement.isJsonArray -> {
                                    Log.d("DriverRepository", "Processing as JsonArray")
                                    val names = jsonElement.asJsonArray.mapNotNull { el ->
                                        Log.d("DriverRepository", "Array element: $el")
                                        Log.d("DriverRepository", "Is JsonObject: ${el.isJsonObject}")
                                        Log.d("DriverRepository", "Is JsonPrimitive: ${el.isJsonPrimitive}")
                                        
                                        if (el.isJsonPrimitive && el.asJsonPrimitive.isString) {
                                            val name = el.asString
                                            Log.d("DriverRepository", "Extracted string name: $name")
                                            name
                                        } else if (el.isJsonObject && el.asJsonObject.has("name")) {
                                            val nameElement = el.asJsonObject.get("name")
                                            val name = if (nameElement != null && !nameElement.isJsonNull && nameElement.isJsonPrimitive && nameElement.asJsonPrimitive.isString) {
                                                nameElement.asString
                                            } else null
                                            Log.d("DriverRepository", "Extracted object name: $name")
                                            name
                                        } else {
                                            Log.d("DriverRepository", "Could not extract name from element: $el")
                                            null
                                        }
                                    }
                                    Log.d("DriverRepository", "Final names from array: $names")
                                    names
                                }
                                jsonElement.isJsonPrimitive && jsonElement.asJsonPrimitive.isString -> {
                                    val raw = jsonElement.asString
                                    Log.d("DriverRepository", "Processing as JsonPrimitive string: $raw")
                                    try {
                                        // Try parsing as JSON array of objects
                                        val gson = com.google.gson.Gson()
                                        val type = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
                                        val objects = gson.fromJson<List<Map<String, Any>>>(raw, type)
                                        val names = objects.mapNotNull { it["name"] as? String }
                                        Log.d("DriverRepository", "Parsed objects names: $names")
                                        names
                                    } catch (_: Exception) {
                                        // Try parsing as JSON array of strings
                                        try {
                                            val gson = com.google.gson.Gson()
                                            val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                                            val strings = gson.fromJson<List<String>>(raw, type)
                                            Log.d("DriverRepository", "Parsed string names: $strings")
                                            strings
                                        } catch (_: Exception) {
                                            Log.d("DriverRepository", "Failed to parse as JSON, returning empty list")
                                            emptyList()
                                        }
                                    }
                                }
                                else -> {
                                    Log.d("DriverRepository", "Unknown JsonElement type, returning empty list")
                                    emptyList()
                                }
                            }
                        } ?: emptyList()
                    } catch (e: Exception) {
                        Log.e("DriverRepository", "Error parsing passengers: ${e.message}")
                        e.printStackTrace()
                        emptyList()
                    }
                    
                    Log.d("DriverRepository", "Normalized passengers for leg ${rawLeg.leg_id}: $normalizedPassengers")
                    
                    SharedTripLeg(
                        leg_id = rawLeg.leg_id,
                        stop_id = rawLeg.stop_id,
                        team_name = rawLeg.team_name,
                        destination = rawLeg.destination,
                        purpose = rawLeg.purpose,
                        passengers = normalizedPassengers,
                        odometer_start = rawLeg.odometer_start,
                        odometer_end = rawLeg.odometer_end,
                        fuel_start = rawLeg.fuel_start,
                        fuel_end = rawLeg.fuel_end,
                        fuel_used = rawLeg.fuel_used,
                        fuel_purchased = rawLeg.fuel_purchased,
                        notes = rawLeg.notes,
                        departure_time = rawLeg.departure_time,
                        arrival_time = rawLeg.arrival_time,
                        departure_location = rawLeg.departure_location,
                        arrival_location = rawLeg.arrival_location,
                        status = rawLeg.status,
                        return_to_base = rawLeg.return_to_base
                    )
                }
            } catch (e: Exception) {
                Log.e("DriverRepository", "Error getting trip legs: ${e.message}")
                e.printStackTrace()
                emptyList()
            }
        }
    }

    suspend fun logLegDeparture(tripId: Int, legId: Int, request: LegDepartureRequest): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("DriverRepository", "=== SENDING LEG DEPARTURE TO BACKEND ===")
                Log.d("DriverRepository", "Trip ID: $tripId, Leg ID: $legId")
                Log.d("DriverRepository", "Request data:")
                Log.d("DriverRepository", "  - odometer_start: ${request.odometer_start}")
                Log.d("DriverRepository", "  - fuel_start: ${request.fuel_start}")
                Log.d("DriverRepository", "  - departure_time: '${request.departure_time}'")
                Log.d("DriverRepository", "  - departure_location: '${request.departure_location}'")
                Log.d("DriverRepository", "  - passengers_confirmed: ${request.passengers_confirmed}")
                Log.d("DriverRepository", "  - manifest_override_reason: '${request.manifest_override_reason}'")
                val response = apiService.logLegDeparture(tripId, legId, request)
                Log.d("DriverRepository", "Backend response code: ${response.code()}")
                Log.d("DriverRepository", "Backend response headers: ${response.headers()}")
                if (response.isSuccessful) {
                    Log.d("DriverRepository", "✅ Leg departure successful - backend accepted the data")
                    Result.success(Unit)
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("DriverRepository", "❌ Leg departure failed: ${response.code()}")
                    Log.e("DriverRepository", "Error body: $errorBody")
                    Result.failure(Exception("Leg departure failed: ${response.code()} - $errorBody"))
                }
            } catch (e: Exception) {
                Log.e("DriverRepository", "Leg departure exception", e)
                Result.failure(e)
            }
        }
    }

    suspend fun logLegArrival(tripId: Int, legId: Int, request: LegArrivalRequest): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("DriverRepository", "Logging leg arrival for trip $tripId, leg $legId")
                val response = apiService.logLegArrival(tripId, legId, request)
                if (response.isSuccessful) {
                    Log.d("DriverRepository", "Leg arrival successful")
                    Result.success(Unit)
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("DriverRepository", "Leg arrival failed: ${response.code()} - $errorBody")
                    Result.failure(Exception("Leg arrival failed: ${response.code()} - $errorBody"))
                }
            } catch (e: Exception) {
                Log.e("DriverRepository", "Leg arrival exception", e)
                Result.failure(e)
            }
        }
    }

    suspend fun completeLeg(tripId: Int, legId: Int, request: LegCompletionRequest): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("DriverRepository", "Completing leg for trip $tripId, leg $legId")
                val response = apiService.completeLeg(tripId, legId, request)
                if (response.isSuccessful) {
                    Log.d("DriverRepository", "Leg completion successful")
                    Result.success(Unit)
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("DriverRepository", "Leg completion failed: ${response.code()} - $errorBody")
                    Result.failure(Exception("Leg completion failed: ${response.code()} - $errorBody"))
                }
            } catch (e: Exception) {
                Log.e("DriverRepository", "Leg completion exception", e)
                Result.failure(e)
            }
        }
    }

    suspend fun submitSharedTrip(tripId: Int, request: com.example.drivebroom.network.TripSubmissionRequest): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("DriverRepository", "=== SUBMIT SHARED TRIP ===")
                android.util.Log.d("DriverRepository", "Trip ID: $tripId")
                android.util.Log.d("DriverRepository", "Request data: final_odometer=${request.final_odometer}, final_fuel=${request.final_fuel}")
                android.util.Log.d("DriverRepository", "Total distance: ${request.total_distance}, Total fuel used: ${request.total_fuel_used}")
                
                val response = apiService.submitSharedTrip(tripId, request)
                if (response.isSuccessful) {
                    android.util.Log.d("DriverRepository", "Trip submitted successfully")
                    Result.success(Unit)
                } else {
                    val errorBody = response.errorBody()?.string()
                    android.util.Log.e("DriverRepository", "Submit shared trip failed: ${response.code()} - $errorBody")
                    Result.failure(Exception("Submit shared trip failed: ${response.code()} - $errorBody"))
                }
            } catch (e: Exception) {
                android.util.Log.e("DriverRepository", "Submit shared trip exception: ${e.message}")
                Result.failure(e)
            }
        }
    }

    // New methods for return flow
    suspend fun startReturn(tripId: Int, legId: Int, request: com.example.drivebroom.network.ReturnStartRequest): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("DriverRepository", "=== STARTING RETURN TO BASE ===")
                Log.d("DriverRepository", "Trip ID: $tripId, Leg ID: $legId")
                Log.d("DriverRepository", "Request data:")
                Log.d("DriverRepository", "  - odometer_start: ${request.odometer_start}")
                Log.d("DriverRepository", "  - fuel_start: ${request.fuel_start}")
                Log.d("DriverRepository", "  - return_start_time: '${request.return_start_time}'")
                Log.d("DriverRepository", "  - return_start_location: '${request.return_start_location}'")
                
                val response = apiService.startReturn(tripId, legId, request)
                Log.d("DriverRepository", "Backend response code: ${response.code()}")
                
                if (response.isSuccessful) {
                    Log.d("DriverRepository", "✅ Return start successful - backend accepted the data")
                    Result.success(Unit)
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("DriverRepository", "❌ Return start failed: ${response.code()}")
                    Log.e("DriverRepository", "Error body: $errorBody")
                    Result.failure(Exception("Return start failed: ${response.code()} - $errorBody"))
                }
            } catch (e: Exception) {
                Log.e("DriverRepository", "Return start exception", e)
                Result.failure(e)
            }
        }
    }

    suspend fun arriveAtBase(tripId: Int, legId: Int, request: com.example.drivebroom.network.ReturnArrivalRequest): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("DriverRepository", "=== ARRIVING AT BASE ===")
                Log.d("DriverRepository", "Trip ID: $tripId, Leg ID: $legId")
                Log.d("DriverRepository", "Request data:")
                Log.d("DriverRepository", "  - odometer_end: ${request.odometer_end}")
                Log.d("DriverRepository", "  - fuel_end: ${request.fuel_end}")
                Log.d("DriverRepository", "  - return_arrival_time: '${request.return_arrival_time}'")
                Log.d("DriverRepository", "  - return_arrival_location: '${request.return_arrival_location}'")
                
                val response = apiService.arriveAtBase(tripId, legId, request)
                Log.d("DriverRepository", "Backend response code: ${response.code()}")
                
                if (response.isSuccessful) {
                    Log.d("DriverRepository", "✅ Return arrival successful - backend accepted the data")
                    Result.success(Unit)
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("DriverRepository", "❌ Return arrival failed: ${response.code()}")
                    Log.e("DriverRepository", "Error body: $errorBody")
                    Result.failure(Exception("Return arrival failed: ${response.code()} - $errorBody"))
                }
            } catch (e: Exception) {
                Log.e("DriverRepository", "Return arrival exception", e)
                Result.failure(e)
            }
        }
    }
} 