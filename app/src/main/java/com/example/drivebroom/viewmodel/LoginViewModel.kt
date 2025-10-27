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
import java.net.SocketTimeoutException
import java.net.ConnectException
import java.net.UnknownHostException
import java.io.InterruptedIOException
import com.example.drivebroom.network.DriverProfile
import com.example.drivebroom.network.PendingApprovalException
import com.example.drivebroom.network.InactiveAccountException
import com.example.drivebroom.network.NotDriverException
import com.example.drivebroom.network.InvalidCredentialsException

class LoginViewModel(
    private val repository: DriverRepository,
    private val tokenManager: TokenManager
) : ViewModel() {
    private val _loginState = MutableStateFlow<LoginState>(LoginState.Initial)
    val loginState: StateFlow<LoginState> = _loginState

    init {
        // Check if user is already logged in with valid token
        val token = tokenManager.getToken()
        if (!token.isNullOrBlank() && tokenManager.isTokenValid()) {
            Log.d("LoginViewModel", "Valid token found, user is logged in")
            _loginState.value = LoginState.Success(token)
        } else {
            Log.d("LoginViewModel", "No valid token found, user needs to login")
            _loginState.value = LoginState.Initial
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
            // Try to fetch FCM token up front (non-blocking), but don't fail if unavailable
            var cachedFcm: String? = null
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    cachedFcm = task.result
                }
            }
            try {
				val result = repository.loginDriver(email, password, cachedFcm)
				result
					.onSuccess { response ->
						Log.d("LoginViewModel", "Login response: $response")
						response.access_token?.let { token ->
							tokenManager.saveToken(token)
							// Bootstrap session with /api/me
							routeByMe()
							// Send FCM token if not already
							if (cachedFcm == null) {
								FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
									if (task.isSuccessful) {
										val fcmToken = task.result
										viewModelScope.launch { runCatching { repository.updateFcmToken(fcmToken) } }
									}
								}
							} else {
								cachedFcm?.let { token ->
									viewModelScope.launch { runCatching { repository.updateFcmToken(token) } }
								}
							}
						} ?: run {
							Log.d("LoginViewModel", "No access_token in response")
							_loginState.value = LoginState.Error("Invalid response from server")
						}
					}
					.onFailure { err ->
						throw err
					}
            } catch (error: Exception) {
                Log.e("LoginViewModel", "Login failed", error)
                when (error) {
                    is PendingApprovalException -> {
                        val me = DriverProfile(0, "", "", "", role = "driver", approval_status = "pending", status = null)
                        _loginState.value = LoginState.PendingApproval(me)
                    }
                    is InterruptedIOException -> _loginState.value = LoginState.Error("Connection timed out. Please try again.")
                    is SocketTimeoutException -> _loginState.value = LoginState.Error("Connection timed out. Please try again.")
                    is ConnectException, is UnknownHostException -> _loginState.value = LoginState.Error("Cannot reach server. Check your internet or server status.")
                    is InactiveAccountException -> _loginState.value = LoginState.Error("Your account is inactive. Contact admin.")
                    is NotDriverException -> _loginState.value = LoginState.Error("Please use a driver account to log in.")
                    is InvalidCredentialsException -> _loginState.value = LoginState.Error("Invalid email or password.")
                    else -> _loginState.value = LoginState.Error(error.message ?: "Login failed. Please try again.")
                }
            } finally {
                // If still in loading, reset to Initial to clear spinner unless Success/Pending/Declined already set
                if (_loginState.value is LoginState.Loading) {
                    _loginState.value = LoginState.Initial
                }
            }
        }
    }

    fun logout() {
        Log.d("LoginViewModel", "Logging out user")
        tokenManager.clearToken()
        _loginState.value = LoginState.Initial
    }
    
    fun handleAuthFailure() {
        Log.d("LoginViewModel", "Handling authentication failure - forcing logout")
        tokenManager.clearToken(triggerCallback = false)
        _loginState.value = LoginState.Initial
    }
    
    fun clearError() {
        if (_loginState.value is LoginState.Error) {
            _loginState.value = LoginState.Initial
        }
    }

    // Session bootstrap: GET /api/me and route by role/approval_status
    fun routeByMe() {
        viewModelScope.launch {
            try {
                // Only attempt if we have a valid token
                if (!tokenManager.isTokenValid()) return@launch
                val me = repository.apiService.getDriverProfile()
                val role = me.role ?: "driver"
                val approval = me.approval_status ?: "approved"
                if (role != "driver") {
                    _loginState.value = LoginState.Error("Not a driver account")
                    return@launch
                }
                when (approval.lowercase()) {
                    "approved" -> _loginState.value = LoginState.Success(tokenManager.getToken().orEmpty())
                    "pending" -> _loginState.value = LoginState.PendingApproval(me)
                    "declined" -> _loginState.value = LoginState.Declined(me)
                    else -> _loginState.value = LoginState.Success(tokenManager.getToken().orEmpty())
                }
            } catch (e: Exception) {
                // Keep current state on failure
            }
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}

sealed class LoginState {
    object Initial : LoginState()
    object Loading : LoginState()
    data class Success(val token: String) : LoginState()
    data class PendingApproval(val me: DriverProfile) : LoginState()
    data class Declined(val me: DriverProfile) : LoginState()
    data class Error(val message: String) : LoginState()
} 