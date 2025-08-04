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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import com.google.gson.JsonParser
import android.util.Log

class DriverRepository(val apiService: ApiService) {
    suspend fun loginDriver(email: String, password: String): Result<LoginResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.loginDriver(LoginRequest(email, password))
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

    suspend fun logDeparture(tripId: Int, body: DepartureBody, token: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.logDeparture(tripId, body, token)
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception(response.errorBody()?.string() ?: "Departure failed"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun logArrival(tripId: Int, body: ArrivalBody, token: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.logArrival(tripId, body, token)
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception(response.errorBody()?.string() ?: "Arrival failed"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun logReturn(tripId: Int, body: ReturnBody, token: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.logReturn(tripId, body, token)
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception(response.errorBody()?.string() ?: "Return failed"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getTripDetails(tripId: Int, token: String): Result<TripDetails> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getTripDetails(tripId)
                Result.success(response)
            } catch (e: Exception) {
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

    suspend fun getDriverProfile(token: String): Result<DriverProfile> {
        return withContext(Dispatchers.IO) {
            try {
                println("Repository: getDriverProfile() called")
                val response = apiService.getDriverProfile()
                println("Repository: Driver profile - ID: ${response.id}, Name: ${response.name}")
                Result.success(response)
            } catch (e: Exception) {
                println("Repository: Error in getDriverProfile: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun getCompletedTrips(token: String): Result<List<CompletedTrip>> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("Repository", "getCompletedTrips() called")
                Log.d("Repository", "Token being used: $token")

                val response = apiService.getCompletedTrips(token)
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
} 