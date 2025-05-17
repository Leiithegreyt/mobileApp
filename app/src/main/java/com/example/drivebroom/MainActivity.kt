package com.example.drivebroom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.drivebroom.network.NetworkClient
import com.example.drivebroom.repository.DriverRepository
import com.example.drivebroom.ui.screens.DriverHomeScreen
import com.example.drivebroom.ui.screens.LoginScreen
import com.example.drivebroom.ui.theme.DriveBroomTheme
import com.example.drivebroom.utils.TokenManager
import com.example.drivebroom.viewmodel.DriverHomeUiState
import com.example.drivebroom.viewmodel.DriverHomeViewModel
import com.example.drivebroom.viewmodel.LoginState
import com.example.drivebroom.viewmodel.LoginViewModel

class MainActivity : ComponentActivity() {
    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenManager = TokenManager(this)
        
        enableEdgeToEdge()
        setContent {
            DriveBroomTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(tokenManager)
                }
            }
        }
    }
}

@Composable
fun MainScreen(tokenManager: TokenManager) {
    val repository = DriverRepository(NetworkClient(tokenManager).apiService)
    val loginViewModel: LoginViewModel = viewModel(
        factory = LoginViewModelFactory(repository, tokenManager)
    )
    val loginState by loginViewModel.loginState.collectAsState()

    when (loginState) {
        is LoginState.Success -> {
            val driverHomeViewModel: DriverHomeViewModel = viewModel(
                factory = DriverHomeViewModelFactory(NetworkClient(tokenManager).apiService)
            )
            val driverHomeState by driverHomeViewModel.uiState.collectAsState()

            when (driverHomeState) {
                is DriverHomeUiState.Loading -> {
                    DriverHomeScreen(
                        onLogout = { 
                            driverHomeViewModel.logout()
                            loginViewModel.logout()
                        },
                        driverProfile = null,
                        trips = emptyList(),
                        onTripClick = { /* TODO: Handle trip click */ },
                        isLoading = true
                    )
                }
                is DriverHomeUiState.Success -> {
                    val state = driverHomeState as DriverHomeUiState.Success
                    DriverHomeScreen(
                        onLogout = { 
                            driverHomeViewModel.logout()
                            loginViewModel.logout()
                        },
                        driverProfile = state.profile,
                        trips = state.trips,
                        onTripClick = { /* TODO: Handle trip click */ },
                        isLoading = false
                    )
                }
                is DriverHomeUiState.Error -> {
                    val errorState = driverHomeState as DriverHomeUiState.Error
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Text(
                            text = "Error: ${errorState.message}",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        else -> {
            LoginScreen(
                onLoginSuccess = { email, password ->
                    loginViewModel.login(email, password)
                }
            )
        }
    }
}

class LoginViewModelFactory(
    private val repository: DriverRepository,
    private val tokenManager: TokenManager
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LoginViewModel(repository, tokenManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class DriverHomeViewModelFactory(
    private val apiService: com.example.drivebroom.network.ApiService
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DriverHomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DriverHomeViewModel(apiService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}