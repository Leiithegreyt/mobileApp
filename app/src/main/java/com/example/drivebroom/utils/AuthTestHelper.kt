package com.example.drivebroom.utils

import android.util.Log

/**
 * Helper class to test authentication flow and 401 error handling
 */
class AuthTestHelper(private val tokenManager: TokenManager) {
    
    fun testTokenValidation(): Boolean {
        val token = tokenManager.getToken()
        val isValid = tokenManager.isTokenValid()
        
        Log.d("AuthTestHelper", "Token exists: ${token != null}")
        Log.d("AuthTestHelper", "Token is valid: $isValid")
        Log.d("AuthTestHelper", "Token preview: ${token?.take(10)}...")
        
        return isValid
    }
    
    fun simulateTokenExpiration() {
        Log.d("AuthTestHelper", "Simulating token expiration by clearing token")
        tokenManager.clearToken()
    }
    
    fun logAuthState() {
        Log.d("AuthTestHelper", "=== AUTH STATE ===")
        Log.d("AuthTestHelper", "Has token: ${tokenManager.getToken() != null}")
        Log.d("AuthTestHelper", "Token valid: ${tokenManager.isTokenValid()}")
        Log.d("AuthTestHelper", "=================")
    }
}
