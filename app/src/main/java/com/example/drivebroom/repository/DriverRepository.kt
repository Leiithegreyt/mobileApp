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
                val json = apiService.getTripDetails(tripId)
                Log.d("DriverRepository", "Raw trip details JSON: $json")
                
                val gson = Gson()
                val tripDetails: TripDetails = if (json.isJsonObject) {
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
                
                Log.d("DriverRepository", "Parsed trip details - ID: ${tripDetails.id}, Purpose: ${tripDetails.purpose}, Passengers: ${tripDetails.passengers}")
                Log.d("DriverRepository", "Passengers type: ${tripDetails.passengers?.javaClass?.simpleName}")
                Log.d("DriverRepository", "Passengers isJsonArray: ${tripDetails.passengers?.isJsonArray}")
                Log.d("DriverRepository", "Passengers isJsonPrimitive: ${tripDetails.passengers?.isJsonPrimitive}")
                if (tripDetails.passengers?.isJsonPrimitive == true) {
                    Log.d("DriverRepository", "Passengers as string: ${tripDetails.passengers?.asString}")
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

    // Shared trip leg execution methods
    suspend fun getSharedTripLegs(tripId: Int): List<SharedTripLeg> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("DriverRepository", "=== GET SHARED TRIP LEGS ===")
                Log.d("DriverRepository", "Getting shared trip legs for trip $tripId")
                val response = apiService.getSharedTripLegs(tripId)
                Log.d("DriverRepository", "Raw API response: $response")
                Log.d("DriverRepository", "Response size: ${response.size}")
                
                // Log each leg's raw data
                response.forEachIndexed { index, leg ->
                    Log.d("DriverRepository", "=== RAW LEG $index ===")
                    Log.d("DriverRepository", "ID: ${leg.leg_id}")
                    Log.d("DriverRepository", "Status: ${leg.status}")
                    Log.d("DriverRepository", "Odometer End: ${leg.odometer_end}")
                    Log.d("DriverRepository", "Fuel End: ${leg.fuel_end}")
                    Log.d("DriverRepository", "Fuel Used: ${leg.fuel_used}")
                    Log.d("DriverRepository", "Fuel Purchased: ${leg.fuel_purchased}")
                    Log.d("DriverRepository", "Notes: ${leg.notes}")
                }
                
                response
            } catch (e: Exception) {
                Log.e("DriverRepository", "Error getting shared trip legs: ${e.message}")
                e.printStackTrace()
                emptyList()
            }
        }
    }

    suspend fun logLegDeparture(tripId: Int, legId: Int, request: LegDepartureRequest): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("DriverRepository", "Logging leg departure for trip $tripId, leg $legId")
                val response = apiService.logLegDeparture(tripId, legId, request)
                if (response.isSuccessful) {
                    Log.d("DriverRepository", "Leg departure successful")
                    Result.success(Unit)
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("DriverRepository", "Leg departure failed: ${response.code()} - $errorBody")
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

    suspend fun submitSharedTrip(tripId: Int): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.submitSharedTrip(tripId)
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    val errorBody = response.errorBody()?.string()
                    Result.failure(Exception("Submit shared trip failed: ${response.code()} - $errorBody"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
} 