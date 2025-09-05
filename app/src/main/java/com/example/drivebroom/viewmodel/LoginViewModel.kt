package com.example.drivebroom.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.drivebroom.repository.DriverRepository
import com.example.drivebroom.utils.TokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging

class LoginViewModel(
    private val repository: DriverRepository,
    private val tokenManager: TokenManager
) : ViewModel() {
    private val _loginState = MutableStateFlow<LoginState>(LoginState.Initial)
    val loginState: StateFlow<LoginState> = _loginState

    init {
        // Check if user is already logged in
        tokenManager.getToken()?.let {
            _loginState.value = LoginState.Success(it)
        }
    }

    fun login(email: String, password: String) {
        Log.d("LoginViewModel", "Login button pressed with email: $email")
        if (!isValidEmail(email)) {
            Log.d("LoginViewModel", "Invalid email format")
            _loginState.value = LoginState.Error("Please enter a valid email address")
            return
        }

        if (password.length < 6) {
            Log.d("LoginViewModel", "Password too short")
            _loginState.value = LoginState.Error("Password must be at least 6 characters")
            return
        }

        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            Log.d("LoginViewModel", "Sending login request to repository")
            repository.loginDriver(email, password)
                .onSuccess { response ->
                    Log.d("LoginViewModel", "Login response: $response")
                    response.access_token?.let { token ->
                        tokenManager.saveToken(token)
                        _loginState.value = LoginState.Success(token)

                        // Get and send FCM token
                        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val fcmToken = task.result
                                Log.d("LoginViewModel", "FCM token retrieved: $fcmToken")
                                viewModelScope.launch {
                                    try {
                                        repository.updateFcmToken(fcmToken)
                                        Log.d("LoginViewModel", "FCM token updated successfully")
                                    } catch (e: Exception) {
                                        Log.e("LoginViewModel", "Failed to update FCM token", e)
                                        // Don't fail login if FCM token update fails
                                    }
                                }
                            } else {
                                Log.e("LoginViewModel", "Failed to get FCM token", task.exception)
                                // Don't fail login if FCM token retrieval fails
                            }
                        }
                    } ?: run {
                        Log.d("LoginViewModel", "No access_token in response")
                        _loginState.value = LoginState.Error("Invalid response from server")
                    }
                }
                .onFailure { error ->
                    Log.e("LoginViewModel", "Login failed", error)
                    _loginState.value = LoginState.Error(error.message ?: "Login failed")
                }
        }
    }

    fun logout() {
        tokenManager.clearToken()
        _loginState.value = LoginState.Initial
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}

sealed class LoginState {
    object Initial : LoginState()
    object Loading : LoginState()
    data class Success(val token: String) : LoginState()
    data class Error(val message: String) : LoginState()
} 