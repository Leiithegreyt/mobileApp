package com.example.drivebroom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.drivebroom.network.NetworkClient
import com.example.drivebroom.repository.DriverRepository
import com.example.drivebroom.ui.screens.DriverHomeScreen
import com.example.drivebroom.ui.screens.LoginScreen
import com.example.drivebroom.ui.screens.TripDetailsScreen
import com.example.drivebroom.ui.screens.SharedTripFlowScreen
import com.example.drivebroom.network.TripDetails
import com.example.drivebroom.ui.theme.DriveBroomTheme
import com.example.drivebroom.utils.TokenManager
import com.example.drivebroom.viewmodel.DriverHomeUiState
import com.example.drivebroom.viewmodel.DriverHomeViewModel
import com.example.drivebroom.viewmodel.LoginState
import com.example.drivebroom.viewmodel.LoginViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import kotlinx.coroutines.launch
import com.example.drivebroom.viewmodel.NavigationViewModel
import com.google.firebase.FirebaseApp
import android.util.Log

class MainActivity : ComponentActivity() {
    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        
        tokenManager = TokenManager(this)
        
        // Set up authentication failure callback
        tokenManager.setOnAuthFailureCallback {
            Log.d("MainActivity", "Authentication failed - redirecting to login")
            // The UI will automatically handle this through the login state
        }
        
        val tripIdFromIntent = intent.getStringExtra("trip_id")?.toIntOrNull()
        enableEdgeToEdge()
        setContent {
            DriveBroomTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(tokenManager, tripIdFromIntent)
                }
            }
        }
    }
}

@Composable
fun MainScreen(tokenManager: TokenManager, tripIdFromIntent: Int? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val navigationViewModel: NavigationViewModel = viewModel()
    // Set pendingTripId if tripIdFromIntent is not null
    LaunchedEffect(tripIdFromIntent) {
        if (tripIdFromIntent != null) {
            navigationViewModel.pendingTripId.value = tripIdFromIntent
        }
    }
    val loginViewModel: LoginViewModel = viewModel(
        factory = LoginViewModelFactory(DriverRepository(NetworkClient(tokenManager).apiService), tokenManager)
    )
    val loginState by loginViewModel.loginState.collectAsState()

    // Use a unique ViewModelStore for each login session
    val sessionStore = remember(loginState) { ViewModelStore() }
    val sessionOwner = remember(sessionStore) {
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore
                get() = sessionStore
        }
    }

    // Trip details navigation state
    val selectedTripId = remember { mutableStateOf<Int?>(null) }
    val tripDetailsState = remember { mutableStateOf<TripDetails?>(null) }
    val tripDetailsLoading = remember { mutableStateOf(false) }
    val tripDetailsError = remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // After login, if pendingTripId is set, navigate to trip details
    LaunchedEffect(loginState) {
        if (loginState is LoginState.Success && navigationViewModel.pendingTripId.value != null) {
            selectedTripId.value = navigationViewModel.pendingTripId.value
            navigationViewModel.pendingTripId.value = null
        }
    }

    when (loginState) {
        is LoginState.Success -> {
            val token = (loginState as LoginState.Success).token
            val networkClient = remember(token) { NetworkClient(tokenManager) }
            val repository = remember(token) { DriverRepository(networkClient.apiService) }
            val driverHomeViewModel = remember(sessionOwner) {
                androidx.lifecycle.ViewModelProvider(
                    sessionOwner,
                    DriverHomeViewModelFactory(repository.apiService)
                ).get(DriverHomeViewModel::class.java)
            }
            val driverHomeState by driverHomeViewModel.uiState.collectAsState()

            if (selectedTripId.value == null) {
                when (driverHomeState) {
                    is DriverHomeUiState.Loading -> {
                        DriverHomeScreen(
                            onLogout = { 
                                driverHomeViewModel.logout()
                                loginViewModel.logout()
                            },
                            driverProfile = null,
                            trips = emptyList(),
                            onTripClick = { tripId ->
                                selectedTripId.value = tripId
                                tripDetailsLoading.value = false
                                tripDetailsError.value = null
                                // No need to load trip details here - the ViewModel will handle it
                            },
                            isLoading = true,
                            onRefresh = { driverHomeViewModel.loadData() }
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
                            onTripClick = { tripId ->
                                selectedTripId.value = tripId
                                tripDetailsLoading.value = false
                                tripDetailsError.value = null
                                // No need to load trip details here - the ViewModel will handle it
                            },
                            isLoading = false,
                            onRefresh = { driverHomeViewModel.loadData() }
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
            } else {
                when {
                    tripDetailsLoading.value -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.CircularProgressIndicator()
                        }
                    }
                    tripDetailsError.value != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.Text(
                                text = "Error: ${tripDetailsError.value}",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    selectedTripId.value != null -> {
                        // Create ViewModel for both shared and regular trips
                        val tripDetailsViewModel: com.example.drivebroom.viewmodel.TripDetailsViewModel = viewModel(
                            key = "trip_details_${selectedTripId.value}",
                            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                    val tokenManager = TokenManager(context)
                                    val networkClient = NetworkClient(tokenManager)
                                    val repo = DriverRepository(networkClient.apiService)
                                    @Suppress("UNCHECKED_CAST")
                                    return com.example.drivebroom.viewmodel.TripDetailsViewModel(repo, tokenManager, context) as T
                                }
                            }
                        )
                        
                        // Load trip details into the ViewModel
                        LaunchedEffect(selectedTripId.value) {
                            selectedTripId.value?.let { tripId ->
                                android.util.Log.d("MainActivity", "Loading trip details for tripId: $tripId")
                                // Do not reset state here to preserve in-progress itinerary when navigating back
                                tripDetailsViewModel.loadTripDetails(tripId)
                            }
                        }
                        
                        // Use ViewModel's state instead of MainActivity's state
                        val viewModelTripDetails by tripDetailsViewModel.tripDetails.collectAsState()
                        val isLoading by tripDetailsViewModel.isLoading.collectAsState()
                        
                        // Store in local variable to enable smart cast
                        val currentTripDetails = viewModelTripDetails
                        
                        // Debug logging
                        android.util.Log.d("MainActivity", "=== UI STATE DEBUG ===")
                        android.util.Log.d("MainActivity", "selectedTripId: ${selectedTripId.value}")
                        android.util.Log.d("MainActivity", "isLoading: $isLoading")
                        android.util.Log.d("MainActivity", "currentTripDetails: ${currentTripDetails?.id}, destination: ${currentTripDetails?.destination}")
                        
                        when {
                            isLoading -> {
                                // Show loading while ViewModel loads the trip details
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                            currentTripDetails != null -> {
                                // Unified leg flow for both single and shared trips
                                SharedTripFlowScreen(
                                    tripDetails = currentTripDetails,
                                    onBack = {
                                        selectedTripId.value = null
                                        tripDetailsError.value = null
                                    },
                                    viewModel = tripDetailsViewModel
                                )
                            }
                            else -> {
                                // Show error or empty state
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.material3.Text("Failed to load trip details")
                                }
                            }
                        }
                    }
                }
            }
        }
        else -> {
            LoginScreen(
                loginState = loginState,
                onLoginSuccess = { email, password ->
                    loginViewModel.login(email, password)
                },
                onClearError = {
                    loginViewModel.clearError()
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