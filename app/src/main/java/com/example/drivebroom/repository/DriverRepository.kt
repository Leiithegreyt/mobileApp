package com.example.drivebroom.repository

import com.example.drivebroom.network.ApiService
import com.example.drivebroom.network.LoginRequest
import com.example.drivebroom.network.LoginResponse
import com.example.drivebroom.network.FcmTokenRequest
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
} 