package com.example.drivebroom.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.drivebroom.network.ApiService
import com.example.drivebroom.network.DriverProfile
import com.example.drivebroom.network.Trip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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
                val profile = apiService.getDriverProfile()
                val tripsResponse = apiService.getAssignedTrips()
                val trips = tripsResponse.trips
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