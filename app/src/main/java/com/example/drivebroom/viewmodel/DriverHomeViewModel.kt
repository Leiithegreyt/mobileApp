package com.example.drivebroom.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.drivebroom.network.ApiService
import com.example.drivebroom.network.DriverProfile
import com.example.drivebroom.network.Trip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.util.Log

class DriverHomeViewModel(private val apiService: ApiService) : ViewModel() {
    private val _uiState = MutableStateFlow<DriverHomeUiState>(DriverHomeUiState.Loading)
    val uiState: StateFlow<DriverHomeUiState> = _uiState

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = DriverHomeUiState.Loading
            try {
                // Fetch full driver profile details (includes phone, license_number, profile_photo_url)
                val profile = apiService.getDriverProfileDetails()
                val tripsResponse = apiService.getAssignedTrips()
                val trips = tripsResponse.trips
                
                // Detailed logging for debugging
                Log.d("DriverHomeViewModel", "=== API RESPONSE DEBUG ===")
                Log.d("DriverHomeViewModel", "API Response message: ${tripsResponse.message}")
                Log.d("DriverHomeViewModel", "API Response count: ${tripsResponse.count}")
                Log.d("DriverHomeViewModel", "Trips list size: ${trips.size}")
                
                trips.forEachIndexed { index, trip ->
                    Log.d("DriverHomeViewModel", "Trip $index: id=${trip.id}, destination=${trip.destination}, date=${trip.travel_date}, status=${trip.status}, type=${trip.trip_type}")
                    // Additional debugging for date and trip type
                    Log.d("DriverHomeViewModel", "  - Raw travel_date: '${trip.travel_date}'")
                    Log.d("DriverHomeViewModel", "  - trip_type: '${trip.trip_type}'")
                    Log.d("DriverHomeViewModel", "  - is_shared_trip: ${trip.is_shared_trip}")
                    Log.d("DriverHomeViewModel", "  - shared_trip_id: ${trip.shared_trip_id}")
                    Log.d("DriverHomeViewModel", "  - key: '${trip.key}'")
                }
                
                // Log counts of shared vs single for quick diagnostics
                val sharedCount = trips.count { it.trip_type == "shared" }
                val singleCount = trips.size - sharedCount
                Log.d("DriverHomeViewModel", "Assigned trips: total=${trips.size}, shared=${sharedCount}, single=${singleCount}")
                _uiState.value = DriverHomeUiState.Success(profile, trips)
            } catch (e: Exception) {
                _uiState.value = DriverHomeUiState.Error(e.message ?: "Failed to load data")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                apiService.logout()
            } catch (e: Exception) {
                // Handle logout error if needed
            }
        }
    }
}

sealed class DriverHomeUiState {
    object Loading : DriverHomeUiState()
    data class Success(
        val profile: DriverProfile,
        val trips: List<Trip>
    ) : DriverHomeUiState()
    data class Error(val message: String) : DriverHomeUiState()
} 