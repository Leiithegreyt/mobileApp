package com.example.drivebroom.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.drivebroom.repository.DriverRepository
import com.example.drivebroom.network.DepartureBody
import com.example.drivebroom.network.ArrivalBody
import com.example.drivebroom.network.ReturnBody
import com.example.drivebroom.network.TripDetails
import com.example.drivebroom.network.PassengerDetail
import com.example.drivebroom.network.CompletedTrip
import com.example.drivebroom.utils.TokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.util.Log

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
        val odometerStart: Double, // Odometer reading at departure
        val odometerEnd: Double? = null, // Odometer reading at arrival
        val timeDeparture: String, // 24-hour for backend
        val departure: String,
        val timeArrival: String? = null, // 24-hour for backend
        val arrival: String? = null,
        val timeDepartureDisplay: String? = null, // 12-hour for UI
        val timeArrivalDisplay: String? = null // 12-hour for UI
    )
    private val _itinerary = MutableStateFlow<List<ItineraryLeg>>(emptyList())
    val itinerary: StateFlow<List<ItineraryLeg>> = _itinerary

    val _completedTrips = MutableStateFlow<List<CompletedTrip>>(emptyList())
    val completedTrips: StateFlow<List<CompletedTrip>> = _completedTrips
    
    val _completedTripsMessage = MutableStateFlow<String>("")
    val completedTripsMessage: StateFlow<String> = _completedTripsMessage

    fun addDepartureLeg(odometer: Double, timeDeparture: String, departure: String, timeDepartureDisplay: String) {
        android.util.Log.d("TripDetailsViewModel", "=== VIEWMODEL DEPARTURE DEBUG ===")
        android.util.Log.d("TripDetailsViewModel", "Received odometer: $odometer")
        android.util.Log.d("TripDetailsViewModel", "Received timeDeparture: $timeDeparture")
        android.util.Log.d("TripDetailsViewModel", "Received departure: $departure")
        
        val newLeg = ItineraryLeg(odometerStart = odometer, timeDeparture = timeDeparture, departure = departure, timeDepartureDisplay = timeDepartureDisplay)
        android.util.Log.d("TripDetailsViewModel", "Created new leg: $newLeg")
        
        _itinerary.update { 
            val updatedList = it + newLeg
            android.util.Log.d("TripDetailsViewModel", "Updated itinerary list: $updatedList")
            updatedList
        }
    }

    fun addArrivalToLastLeg(odometerEnd: Double, timeArrival: String, arrival: String, timeArrivalDisplay: String) {
        _itinerary.update {
            if (it.isNotEmpty()) {
                it.dropLast(1) + it.last().copy(odometerEnd = odometerEnd, timeArrival = timeArrival, arrival = arrival, timeArrivalDisplay = timeArrivalDisplay)
            } else it
        }
    }

    fun clearItinerary() {
        _itinerary.value = emptyList()
    }

    fun loadTripDetails(tripId: Int) {
        viewModelScope.launch {
            val result = repository.getTripDetails(tripId)
            result.onSuccess { details ->
                _tripDetails.value = details
            }
            // Optionally handle error
        }
    }

    fun logDeparture(tripId: Int, odometerStart: Double, fuelBalanceStart: Double) {
        viewModelScope.launch {
            _actionState.value = TripActionState.Loading
            val rawToken = tokenManager.getToken()
            Log.d("TripDetailsViewModel", "Raw token from manager: $rawToken")
            val token = rawToken?.let { "Bearer $it" } ?: run {
                Log.e("TripDetailsViewModel", "No auth token available")
                _actionState.value = TripActionState.Error("No auth token")
                return@launch
            }
            Log.d("TripDetailsViewModel", "Attempting departure for trip $tripId with odometer: $odometerStart, fuel: $fuelBalanceStart")
            Log.d("TripDetailsViewModel", "Using token: $token")
            val result = repository.logDeparture(tripId, DepartureBody(odometerStart, fuelBalanceStart))
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
            val result = repository.logArrival(tripId, ArrivalBody(odometerArrival))
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
        fuelBalanceEnd: Double,
        passengerDetails: List<com.example.drivebroom.network.PassengerDetail>,
        itinerary: List<com.example.drivebroom.network.ItineraryLegDto>
    ) {
        viewModelScope.launch {
            _actionState.value = TripActionState.Loading
            val token = tokenManager.getToken()?.let { "Bearer $it" } ?: run {
                _actionState.value = TripActionState.Error("No auth token")
                return@launch
            }
            // Calculate fuel used: start + purchased - end
            val fuelUsed = fuelBalanceStart + fuelPurchased - fuelBalanceEnd
            
            // Calculate total distance travelled from itinerary
            val distanceTravelled = itinerary.sumOf { leg ->
                leg.odometer - leg.odometer_start
            }
            
            // Use the itinerary as-is from the UI (it already has correct odometer_start values)
            val fixedItinerary = itinerary
            
            // Get current time for return_time
            val currentTime = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
            
            val returnBody = ReturnBody(
                fuel_balance_start = fuelBalanceStart,
                fuel_purchased = fuelPurchased,
                fuel_used = fuelUsed,
                fuel_balance_end = fuelBalanceEnd,
                distance_travelled = distanceTravelled,
                passenger_details = passengerDetails,
                driver_signature = "", // Empty for now, can be implemented later
                return_time = currentTime,
                itinerary = fixedItinerary
            )
            
            Log.d("TripDetailsViewModel", "Sending return data:")
            Log.d("TripDetailsViewModel", "fuel_balance_start: $fuelBalanceStart")
            Log.d("TripDetailsViewModel", "fuel_purchased: $fuelPurchased")
            Log.d("TripDetailsViewModel", "fuel_used: $fuelUsed")
            Log.d("TripDetailsViewModel", "fuel_balance_end: $fuelBalanceEnd")
            Log.d("TripDetailsViewModel", "distance_travelled: $distanceTravelled")
            Log.d("TripDetailsViewModel", "passenger_details count: ${passengerDetails.size}")
            Log.d("TripDetailsViewModel", "driver_signature: ''")
            Log.d("TripDetailsViewModel", "return_time: $currentTime")
            Log.d("TripDetailsViewModel", "itinerary legs: ${fixedItinerary.size}")
            
            // Log each itinerary leg with fixed odometer values
            fixedItinerary.forEachIndexed { index, leg ->
                Log.d("TripDetailsViewModel", "Leg $index: start=${leg.odometer_start}, end=${leg.odometer}, distance=${leg.odometer - (leg.odometer_start ?: 0.0)}")
            }
            
            val result = repository.logReturn(tripId, returnBody)
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
        viewModelScope.launch {
            try {
                println("TripDetailsViewModel: Loading completed trips...")
                val result = repository.getCompletedTrips()
                result.onSuccess { trips ->
                    println("TripDetailsViewModel: Success - Got ${trips.size} trips")
                    _completedTrips.value = trips
                    _completedTripsMessage.value = if (trips.isNotEmpty()) {
                        "Found ${trips.size} completed trips"
                    } else {
                        "No completed trips found"
                    }
                }
                result.onFailure { e ->
                    println("TripDetailsViewModel: Error - ${e.message}")
                    _completedTripsMessage.value = "Error: ${e.message}"
                }
            } catch (e: Exception) {
                println("TripDetailsViewModel: Exception - ${e.message}")
                _completedTripsMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun testBackendConnection() {
        println("TripDetailsViewModel: Testing backend connection...")
        viewModelScope.launch {
            try {
                val rawToken = tokenManager.getToken()
                println("TripDetailsViewModel: Test - Raw token: $rawToken")
                
                // Test a simple API call first to see if authentication works
                println("TripDetailsViewModel: Testing driver profile API...")
                val profileResult = repository.getDriverProfile()
                profileResult.onSuccess { profile ->
                    println("TripDetailsViewModel: Profile API works - Driver ID: ${profile.id}")
                    
                    // Now test the completed trips API
                    println("TripDetailsViewModel: Testing completed trips API...")
                    val result = repository.getCompletedTrips()
                    result.onSuccess { trips ->
                        println("TripDetailsViewModel: Test SUCCESS - Got ${trips.size} trips")
                        if (trips.isNotEmpty()) {
                            trips.forEachIndexed { index, trip ->
                                println("TripDetailsViewModel: Test - Trip $index: ID=${trip.id}, Destination=${trip.destination}, Status=${trip.status}")
                            }
                        } else {
                            println("TripDetailsViewModel: Test - No trips found in response")
                            println("TripDetailsViewModel: This means either:")
                            println("TripDetailsViewModel: 1. No completed trips exist for this driver")
                            println("TripDetailsViewModel: 2. Backend query is not finding the trips")
                            println("TripDetailsViewModel: 3. Driver ID mismatch")
                        }
                    }
                    result.onFailure { e ->
                        println("TripDetailsViewModel: Test FAILED - ${e.message}")
                        e.printStackTrace()
                    }
                }
                profileResult.onFailure { e ->
                    println("TripDetailsViewModel: Test FAILED - Profile API: ${e.message}")
                    e.printStackTrace()
                }
            } catch (e: Exception) {
                println("TripDetailsViewModel: Test EXCEPTION - ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun testNetworkConnectivity() {
        println("TripDetailsViewModel: Testing network connectivity...")
        viewModelScope.launch {
            try {
                val rawToken = tokenManager.getToken()
                println("TripDetailsViewModel: Network test - Raw token: $rawToken")
                
                // Test the simplest API call possible
                println("TripDetailsViewModel: Testing /api/me endpoint...")
                val profileResult = repository.getDriverProfile()
                profileResult.onSuccess { profile ->
                    println("TripDetailsViewModel: SUCCESS - Network connectivity works!")
                    println("TripDetailsViewModel: Driver ID: ${profile.id}, Name: ${profile.name}")
                    _completedTripsMessage.value = "Network OK - Driver: ${profile.name}"
                    
                    // If network works, test the completed trips endpoint
                    println("TripDetailsViewModel: Now testing completed trips endpoint...")
                    val completedResult = repository.getCompletedTrips()
                    completedResult.onSuccess { trips ->
                        println("TripDetailsViewModel: SUCCESS - Completed trips API works!")
                        println("TripDetailsViewModel: Found ${trips.size} completed trips")
                        _completedTripsMessage.value = "Completed trips OK - Found ${trips.size} trips"
                    }
                    completedResult.onFailure { e ->
                        println("TripDetailsViewModel: FAILED - Completed trips API: ${e.message}")
                        _completedTripsMessage.value = "Completed trips Error: ${e.message}"
                    }
                }
                profileResult.onFailure { e ->
                    println("TripDetailsViewModel: FAILED - Network connectivity issue: ${e.message}")
                    _completedTripsMessage.value = "Network Error: ${e.message}"
                }
            } catch (e: Exception) {
                println("TripDetailsViewModel: EXCEPTION - Network test failed: ${e.message}")
                _completedTripsMessage.value = "Network Exception: ${e.message}"
            }
        }
    }

    fun resetActionState() {
        _actionState.value = TripActionState.Idle
    }
} 