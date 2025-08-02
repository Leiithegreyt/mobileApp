package com.example.drivebroom.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.drivebroom.repository.DriverRepository
import com.example.drivebroom.network.DepartureBody
import com.example.drivebroom.network.ArrivalBody
import com.example.drivebroom.network.ReturnBody
import com.example.drivebroom.network.TripDetails
import com.example.drivebroom.network.PassengerDetail
import com.example.drivebroom.utils.TokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class TripActionState {
    object Idle : TripActionState()
    object Loading : TripActionState()
    object Success : TripActionState()
    data class Error(val message: String) : TripActionState()
}

class TripDetailsViewModel(
    private val repository: DriverRepository,
    private val tokenManager: TokenManager
) : ViewModel() {
    private val _actionState = MutableStateFlow<TripActionState>(TripActionState.Idle)
    val actionState: StateFlow<TripActionState> = _actionState

    private val _tripDetails = MutableStateFlow<TripDetails?>(null)
    val tripDetails: StateFlow<TripDetails?> = _tripDetails

    // Itinerary state for multi-stop trips (5 columns)
    data class ItineraryLeg(
        val odometer: Double,
        val timeDeparture: String, // 24-hour for backend
        val departure: String,
        val timeArrival: String? = null, // 24-hour for backend
        val arrival: String? = null,
        val timeDepartureDisplay: String? = null, // 12-hour for UI
        val timeArrivalDisplay: String? = null // 12-hour for UI
    )
    private val _itinerary = MutableStateFlow<List<ItineraryLeg>>(emptyList())
    val itinerary: StateFlow<List<ItineraryLeg>> = _itinerary

    private val _completedTrips = MutableStateFlow<List<TripDetails>>(emptyList())
    val completedTrips: StateFlow<List<TripDetails>> = _completedTrips

    fun addDepartureLeg(odometer: Double, timeDeparture: String, departure: String, timeDepartureDisplay: String) {
        _itinerary.update { it + ItineraryLeg(odometer, timeDeparture, departure, timeDepartureDisplay = timeDepartureDisplay) }
    }

    fun addArrivalToLastLeg(timeArrival: String, arrival: String, timeArrivalDisplay: String) {
        _itinerary.update {
            if (it.isNotEmpty()) {
                it.dropLast(1) + it.last().copy(timeArrival = timeArrival, arrival = arrival, timeArrivalDisplay = timeArrivalDisplay)
            } else it
        }
    }

    fun clearItinerary() {
        _itinerary.value = emptyList()
    }

    fun loadTripDetails(tripId: Int) {
        viewModelScope.launch {
            val token = tokenManager.getToken()?.let { "Bearer $it" } ?: return@launch
            val result = repository.getTripDetails(tripId, token)
            result.onSuccess { details ->
                _tripDetails.value = details
            }
            // Optionally handle error
        }
    }

    fun logDeparture(tripId: Int, odometerStart: Double, fuelBalanceStart: Double) {
        viewModelScope.launch {
            _actionState.value = TripActionState.Loading
            val token = tokenManager.getToken()?.let { "Bearer $it" } ?: run {
                _actionState.value = TripActionState.Error("No auth token")
                return@launch
            }
            val result = repository.logDeparture(tripId, DepartureBody(odometerStart, fuelBalanceStart), token)
            _actionState.value = result.fold(
                onSuccess = {
                    loadTripDetails(tripId)
                    TripActionState.Success
                },
                onFailure = { TripActionState.Error(it.message ?: "Departure failed") }
            )
        }
    }

    fun logArrival(tripId: Int, odometerArrival: Double) {
        viewModelScope.launch {
            _actionState.value = TripActionState.Loading
            val token = tokenManager.getToken()?.let { "Bearer $it" } ?: run {
                _actionState.value = TripActionState.Error("No auth token")
                return@launch
            }
            val result = repository.logArrival(tripId, ArrivalBody(odometerArrival), token)
            _actionState.value = result.fold(
                onSuccess = {
                    loadTripDetails(tripId)
                    TripActionState.Success
                },
                onFailure = { TripActionState.Error(it.message ?: "Arrival failed") }
            )
        }
    }

    fun logArrival(tripId: Int) {
        // Call the existing logArrival with a default value for odometerArrival
        logArrival(tripId, 0.0)
    }

    fun logReturn(
        tripId: Int,
        fuelBalanceStart: Double,
        fuelPurchased: Double,
        fuelUsed: Double,
        fuelBalanceEnd: Double,
        passengerDetails: List<PassengerDetail>,
        driverSignature: String,
        odometerArrival: Double,
        itinerary: List<com.example.drivebroom.network.ItineraryLegDto>
    ) {
        viewModelScope.launch {
            _actionState.value = TripActionState.Loading
            val token = tokenManager.getToken()?.let { "Bearer $it" } ?: run {
                _actionState.value = TripActionState.Error("No auth token")
                return@launch
            }
            val returnBody = ReturnBody(
                fuel_balance_start = fuelBalanceStart,
                fuel_purchased = fuelPurchased,
                fuel_used = fuelUsed,
                fuel_balance_end = fuelBalanceEnd,
                passenger_details = passengerDetails,
                driver_signature = driverSignature,
                odometer_arrival = odometerArrival,
                itinerary = itinerary
            )
            val result = repository.logReturn(tripId, returnBody, token)
            _actionState.value = result.fold(
                onSuccess = { TripActionState.Success },
                onFailure = { TripActionState.Error(it.message ?: "Return failed") }
            )
        }
    }

    fun restartTrip(tripId: Int) {
        viewModelScope.launch {
            _actionState.value = TripActionState.Loading
            val token = tokenManager.getToken()?.let { "Bearer $it" } ?: run {
                _actionState.value = TripActionState.Error("No auth token")
                return@launch
            }
            val result = repository.restartTrip(tripId, token)
            _actionState.value = result.fold(
                onSuccess = {
                    loadTripDetails(tripId)
                    TripActionState.Success
                },
                onFailure = { TripActionState.Error(it.message ?: "Restart failed") }
            )
        }
    }

    fun loadCompletedTrips() {
        println("Fetching completed trips...")
        viewModelScope.launch {
            val token = tokenManager.getToken()?.let { "Bearer $it" } ?: return@launch
            val result = repository.getCompletedTrips(token)
            result.onSuccess { trips ->
                println("Completed trips result: $trips")
                _completedTrips.value = trips
            }
            result.onFailure { e ->
                println("Error fetching completed trips: ${e.message}")
            }
        }
    }

    fun resetActionState() {
        _actionState.value = TripActionState.Idle
    }
} 