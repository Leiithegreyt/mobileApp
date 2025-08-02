package com.example.drivebroom.repository

import com.example.drivebroom.network.ApiService
import com.example.drivebroom.network.LoginRequest
import com.example.drivebroom.network.LoginResponse
import com.example.drivebroom.network.FcmTokenRequest
import com.example.drivebroom.network.DepartureBody
import com.example.drivebroom.network.ArrivalBody
import com.example.drivebroom.network.ReturnBody
import com.example.drivebroom.network.TripDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
                val response = apiService.getTripDetails(tripId) // No token param in ApiService, so call as is
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

    suspend fun getCompletedTrips(token: String): Result<List<com.example.drivebroom.network.TripDetails>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getCompletedTrips("Bearer $token")
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
} 