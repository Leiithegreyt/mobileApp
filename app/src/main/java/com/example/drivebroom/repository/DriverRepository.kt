package com.example.drivebroom.repository

import com.example.drivebroom.network.ApiService
import com.example.drivebroom.network.LoginRequest
import com.example.drivebroom.network.LoginResponse
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
} 