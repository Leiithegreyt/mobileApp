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
import com.example.drivebroom.utils.TripNotificationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.util.Log
import android.content.Context

sealed class TripActionState {
    object Idle : TripActionState()
    object Loading : TripActionState()
    object Success : TripActionState()
    data class Error(val message: String) : TripActionState()
}

class TripDetailsViewModel(
    private val repository: DriverRepository,
    private val tokenManager: TokenManager,
    private val context: Context? = null
) : ViewModel() {
    
    // Helper function to convert 12-hour format to 24-hour format for backend
    private fun convertTo24Hour(time12Hour: String): String {
        return try {
            val formatter12 = java.time.format.DateTimeFormatter.ofPattern("hh:mm a")
            val time = java.time.LocalTime.parse(time12Hour, formatter12)
            time.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
        } catch (e: Exception) {
            // If parsing fails, return current time in 24-hour format
            java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
        }
    }
    
    // Helper function to convert 24-hour format to 12-hour format for display
    private fun convertTo12Hour(time24Hour: String): String {
        return try {
            val formatter24 = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
            val time = java.time.LocalTime.parse(time24Hour, formatter24)
            time.format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"))
        } catch (e: Exception) {
            // If parsing fails, return current time in 12-hour format
            java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"))
        }
    }
    private val _actionState = MutableStateFlow<TripActionState>(TripActionState.Idle)
    val actionState: StateFlow<TripActionState> = _actionState

    private val _tripDetails = MutableStateFlow<TripDetails?>(null)
    val tripDetails: StateFlow<TripDetails?> = _tripDetails

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // Itinerary state for multi-stop trips (5 columns)
    data class ItineraryLeg(
        val odometerStart: Double, // Odometer reading at departure
        val odometerEnd: Double? = null, // Odometer reading at arrival
        val timeDeparture: String, // 24-hour for backend
        val departure: String,
        val timeArrival: String? = null, // 24-hour for backend
        val arrival: String? = null,
        val timeDepartureDisplay: String? = null, // 12-hour for UI
        val timeArrivalDisplay: String? = null, // 12-hour for UI
        // New fields for shared trips
        val legId: Int? = null,
        val teamName: String? = null,
        val passengers: List<String> = emptyList(),
        val fuelStart: Double? = null,
        val fuelEnd: Double? = null,
        val fuelPurchased: Double? = null
    )
    private val _itinerary = MutableStateFlow<List<ItineraryLeg>>(emptyList())
    val itinerary: StateFlow<List<ItineraryLeg>> = _itinerary

    val _completedTrips = MutableStateFlow<List<CompletedTrip>>(emptyList())
    val completedTrips: StateFlow<List<CompletedTrip>> = _completedTrips
    
    val _completedTripsMessage = MutableStateFlow<String>("")
    val completedTripsMessage: StateFlow<String> = _completedTripsMessage

    // Shared trip state
    private val _sharedTripLegs = MutableStateFlow<List<com.example.drivebroom.network.SharedTripLeg>>(emptyList())
    val sharedTripLegs: StateFlow<List<com.example.drivebroom.network.SharedTripLeg>> = _sharedTripLegs

    private val _currentLegIndex = MutableStateFlow(0)
    val currentLegIndex: StateFlow<Int> = _currentLegIndex

    private val _isSharedTrip = MutableStateFlow(false)
    val isSharedTrip: StateFlow<Boolean> = _isSharedTrip

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

    fun resetState() {
        android.util.Log.d("TripDetailsViewModel", "=== RESETTING STATE ===")
        _tripDetails.value = null
        _isSharedTrip.value = false
        _sharedTripLegs.value = emptyList()
        _currentLegIndex.value = 0
        _itinerary.value = emptyList()
        _actionState.value = TripActionState.Idle
        _isLoading.value = false
        android.util.Log.d("TripDetailsViewModel", "State reset - tripDetails: ${_tripDetails.value?.id}, isLoading: ${_isLoading.value}")
    }

    fun loadTripDetails(tripId: Int) {
        android.util.Log.d("TripDetailsViewModel", "=== LOADING TRIP DETAILS ===")
        android.util.Log.d("TripDetailsViewModel", "TripId: $tripId")
        _isLoading.value = true
        viewModelScope.launch {
            val result = repository.getTripDetails(tripId)
            result.onSuccess { details ->
                android.util.Log.d("TripDetailsViewModel", "Successfully loaded trip details: ${details.id}, destination: ${details.destination}")
                _tripDetails.value = details
                _isLoading.value = false
                android.util.Log.d("TripDetailsViewModel", "State updated - tripDetails: ${_tripDetails.value?.id}, isLoading: ${_isLoading.value}")
                // Check if this is a shared trip
                _isSharedTrip.value = details.trip_type == "shared"
                if (details.trip_type == "shared") {
                    android.util.Log.d("TripDetailsViewModel", "This is a shared trip - using dedicated shared trip legs endpoint")
                    // For shared trips, always use the dedicated shared trip legs endpoint
                    // This ensures we get complete leg data with end values for auto-fill
                    loadSharedTripLegs(tripId)
                } else {
                    android.util.Log.d("TripDetailsViewModel", "This is a regular trip")
                }
            }.onFailure { error ->
                android.util.Log.e("TripDetailsViewModel", "Failed to load trip details: ${error.message}")
                _isLoading.value = false
            }
        }
    }

    fun loadSharedTripLegs(tripId: Int) {
        viewModelScope.launch {
            try {
                android.util.Log.d("TripDetailsViewModel", "=== LOADING SHARED TRIP LEGS ===")
                android.util.Log.d("TripDetailsViewModel", "Loading legs for trip ID: $tripId")
                
                val legs = repository.getSharedTripLegs(tripId)
                android.util.Log.d("TripDetailsViewModel", "Loaded ${legs.size} legs")
                
                // Enhanced debug logging for each leg's data
                legs.forEachIndexed { index, leg ->
                    android.util.Log.d("TripDetailsViewModel", "=== LEG $index DETAILS ===")
                    android.util.Log.d("TripDetailsViewModel", "ID: ${leg.leg_id}")
                    android.util.Log.d("TripDetailsViewModel", "Status: ${leg.status}")
                    android.util.Log.d("TripDetailsViewModel", "Odometer Start: ${leg.odometer_start}")
                    android.util.Log.d("TripDetailsViewModel", "Odometer End: ${leg.odometer_end}")
                    android.util.Log.d("TripDetailsViewModel", "Fuel Start: ${leg.fuel_start}")
                    android.util.Log.d("TripDetailsViewModel", "Fuel End: ${leg.fuel_end}")
                    android.util.Log.d("TripDetailsViewModel", "Fuel Used: ${leg.fuel_used}")
                    android.util.Log.d("TripDetailsViewModel", "Fuel Purchased: ${leg.fuel_purchased}")
                    android.util.Log.d("TripDetailsViewModel", "Notes: ${leg.notes}")
                    android.util.Log.d("TripDetailsViewModel", "Departure Time: ${leg.departure_time}")
                    android.util.Log.d("TripDetailsViewModel", "Arrival Time: ${leg.arrival_time}")
                    android.util.Log.d("TripDetailsViewModel", "Departure Location: ${leg.departure_location}")
                    android.util.Log.d("TripDetailsViewModel", "Arrival Location: ${leg.arrival_location}")
                }
                
                _sharedTripLegs.value = legs
                // Set current leg to first pending leg
                val firstPendingIndex = legs.indexOfFirst { it.status == "pending" }
                if (firstPendingIndex >= 0) {
                    _currentLegIndex.value = firstPendingIndex
                    android.util.Log.d("TripDetailsViewModel", "Set current leg index to: $firstPendingIndex")
                } else {
                    android.util.Log.d("TripDetailsViewModel", "No pending legs found")
                }
                
                // Debug: Check if the current leg status was updated
                val currentLeg = getCurrentLeg()
                android.util.Log.d("TripDetailsViewModel", "=== AFTER LEG RELOAD ===")
                android.util.Log.d("TripDetailsViewModel", "Current leg: ${currentLeg?.leg_id}, status: ${currentLeg?.status}")
                android.util.Log.d("TripDetailsViewModel", "Current leg index: ${_currentLegIndex.value}")
                android.util.Log.d("TripDetailsViewModel", "Total legs: ${legs.size}")
            } catch (e: Exception) {
                android.util.Log.e("TripDetailsViewModel", "Error loading shared trip legs: ${e.message}")
                Log.e("TripDetailsViewModel", "Error loading shared trip legs: ${e.message}")
            }
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

    // Shared trip leg execution methods
    fun logLegDeparture(
        tripId: Int,
        legId: Int,
        odometerStart: Double,
        fuelStart: Double,
        passengersConfirmed: List<String>,
        departureTime: String = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a")),
        departureLocation: String = "",
        manifestOverrideReason: String? = null
    ) {
        viewModelScope.launch {
            _actionState.value = TripActionState.Loading
            try {
                val request = com.example.drivebroom.network.LegDepartureRequest(
                    odometer_start = odometerStart,
                    fuel_start = fuelStart,
                    passengers_confirmed = passengersConfirmed,
                    departure_time = convertTo24Hour(departureTime), // Convert to 24-hour for backend
                    departure_location = departureLocation,
                    manifest_override_reason = manifestOverrideReason
                )
                val result = repository.logLegDeparture(tripId, legId, request)
                _actionState.value = result.fold(
                    onSuccess = { 
                        android.util.Log.d("TripDetailsViewModel", "=== LEG DEPARTURE SUCCESS ===")
                        android.util.Log.d("TripDetailsViewModel", "Successfully departed leg ID: $legId")
                        
                        // Reload shared trip legs to get updated status
                        android.util.Log.d("TripDetailsViewModel", "Reloading shared trip legs after departure...")
                        loadSharedTripLegs(tripId)
                        
                        // Show notification for leg departure
                        context?.let { ctx ->
                            val notificationManager = TripNotificationManager(ctx)
                            val currentLeg = getCurrentLeg()
                            currentLeg?.let { leg ->
                                notificationManager.showLegCompletedNotification(
                                    legNumber = _currentLegIndex.value + 1,
                                    teamName = leg.team_name
                                )
                            }
                        }
                        TripActionState.Success 
                    },
                    onFailure = { 
                        android.util.Log.e("TripDetailsViewModel", "=== LEG DEPARTURE FAILED ===")
                        android.util.Log.e("TripDetailsViewModel", "Failed to depart leg ID: $legId")
                        android.util.Log.e("TripDetailsViewModel", "Error: ${it.message}")
                        TripActionState.Error(it.message ?: "Leg departure failed") 
                    }
                )
            } catch (e: Exception) {
                _actionState.value = TripActionState.Error(e.message ?: "Leg departure failed")
            }
        }
    }

    fun logLegArrival(
        tripId: Int,
        legId: Int,
        odometerEnd: Double,
        fuelUsed: Double?,
        fuelEnd: Double,
        passengersDropped: List<String>,
        arrivalTime: String = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a")),
        arrivalLocation: String = "",
        fuelPurchased: Double? = null,
        notes: String? = null
    ) {
        viewModelScope.launch {
            _actionState.value = TripActionState.Loading
            try {
                val request = com.example.drivebroom.network.LegArrivalRequest(
                    odometer_end = odometerEnd,
                    fuel_used = fuelUsed,
                    fuel_end = fuelEnd,
                    passengers_dropped = passengersDropped,
                    arrival_time = convertTo24Hour(arrivalTime), // Convert to 24-hour for backend
                    arrival_location = arrivalLocation,
                    fuel_purchased = fuelPurchased,
                    notes = notes
                )
                val result = repository.logLegArrival(tripId, legId, request)
                _actionState.value = result.fold(
                    onSuccess = { 
                        android.util.Log.d("TripDetailsViewModel", "=== LEG ARRIVAL SUCCESS ===")
                        android.util.Log.d("TripDetailsViewModel", "Successfully arrived leg ID: $legId")
                        
                        // Reload shared trip legs to get updated status
                        android.util.Log.d("TripDetailsViewModel", "Reloading shared trip legs after arrival...")
                        loadSharedTripLegs(tripId)
                        TripActionState.Success 
                    },
                    onFailure = { 
                        android.util.Log.e("TripDetailsViewModel", "=== LEG ARRIVAL FAILED ===")
                        android.util.Log.e("TripDetailsViewModel", "Failed to arrive leg ID: $legId")
                        android.util.Log.e("TripDetailsViewModel", "Error: ${it.message}")
                        TripActionState.Error(it.message ?: "Leg arrival failed") 
                    }
                )
            } catch (e: Exception) {
                _actionState.value = TripActionState.Error(e.message ?: "Leg arrival failed")
            }
        }
    }

    fun completeLeg(tripId: Int, legId: Int, odometerEnd: Double, fuelEnd: Double, fuelPurchased: Double?, notes: String?) {
        viewModelScope.launch {
            _actionState.value = TripActionState.Loading
            try {
                // Calculate distance travelled and fuel used for this leg
                val currentLeg = getCurrentLeg()
                val odometerStart = currentLeg?.odometer_start ?: 0.0
                val fuelStart = currentLeg?.fuel_start ?: 0.0
                val distanceTravelled = odometerEnd - odometerStart
                // Fuel used should be positive (consumed) or zero (no change)
                // If fuelEnd > fuelStart, it means fuel was added, so fuel used = 0
                val fuelUsed = maxOf(0.0, fuelStart - fuelEnd)
                
                android.util.Log.d("TripDetailsViewModel", "=== LEG COMPLETION CALCULATION ===")
                android.util.Log.d("TripDetailsViewModel", "Odometer start: $odometerStart")
                android.util.Log.d("TripDetailsViewModel", "Odometer end: $odometerEnd")
                android.util.Log.d("TripDetailsViewModel", "Distance travelled: $distanceTravelled")
                android.util.Log.d("TripDetailsViewModel", "Fuel start: $fuelStart")
                android.util.Log.d("TripDetailsViewModel", "Fuel end: $fuelEnd")
                android.util.Log.d("TripDetailsViewModel", "Raw fuel calculation (fuelStart - fuelEnd): ${fuelStart - fuelEnd}")
                android.util.Log.d("TripDetailsViewModel", "Fuel used (maxOf 0): $fuelUsed")
                android.util.Log.d("TripDetailsViewModel", "Fuel added during trip: ${if (fuelEnd > fuelStart) fuelEnd - fuelStart else 0.0}")
                
                val request = com.example.drivebroom.network.LegCompletionRequest(
                    final_odometer = odometerEnd,
                    final_fuel = fuelEnd,
                    distance_travelled = distanceTravelled,
                    fuel_used = fuelUsed,
                    fuel_purchased = fuelPurchased,
                    notes = notes
                )
                android.util.Log.d("TripDetailsViewModel", "=== LEG COMPLETION DEBUG ===")
                android.util.Log.d("TripDetailsViewModel", "Completing leg ID: $legId")
                android.util.Log.d("TripDetailsViewModel", "Request data: final_odometer=${request.final_odometer}, final_fuel=${request.final_fuel}")
                
                val result = repository.completeLeg(tripId, legId, request)
                _actionState.value = result.fold(
                    onSuccess = {
                        android.util.Log.d("TripDetailsViewModel", "=== LEG COMPLETION SUCCESS ===")
                        android.util.Log.d("TripDetailsViewModel", "Successfully completed leg ID: $legId")
                        
                        // Reload shared trip legs to get updated status
                        loadSharedTripLegs(tripId)
                        
                        // Show notification for leg completion
                        context?.let { ctx ->
                            val notificationManager = TripNotificationManager(ctx)
                            val currentLeg = getCurrentLeg()
                            currentLeg?.let { leg ->
                                notificationManager.showLegCompletedNotification(
                                    legNumber = _currentLegIndex.value + 1,
                                    teamName = leg.team_name
                                )
                            }
                        }
                        // Move to next leg
                        moveToNextLeg()
                        TripActionState.Success
                    },
                    onFailure = { 
                        android.util.Log.e("TripDetailsViewModel", "=== LEG COMPLETION FAILED ===")
                        android.util.Log.e("TripDetailsViewModel", "Failed to complete leg ID: $legId")
                        android.util.Log.e("TripDetailsViewModel", "Error: ${it.message}")
                        TripActionState.Error(it.message ?: "Leg completion failed") 
                    }
                )
            } catch (e: Exception) {
                _actionState.value = TripActionState.Error(e.message ?: "Leg completion failed")
            }
        }
    }

    fun submitFullSharedTrip(tripId: Int, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            _actionState.value = TripActionState.Loading
            try {
                val result = repository.submitSharedTrip(tripId)
                _actionState.value = result.fold(
                    onSuccess = {
                        onComplete?.invoke()
                        TripActionState.Success
                    },
                    onFailure = { TripActionState.Error(it.message ?: "Submit full trip failed") }
                )
            } catch (e: Exception) {
                _actionState.value = TripActionState.Error(e.message ?: "Submit full trip failed")
            }
        }
    }

    private fun moveToNextLeg() {
        val currentIndex = _currentLegIndex.value
        val legs = _sharedTripLegs.value
        val nextPendingIndex = legs.indexOfFirst { it.status == "pending" }
        if (nextPendingIndex >= 0) {
            _currentLegIndex.value = nextPendingIndex
        }
    }

    fun getCurrentLeg(): com.example.drivebroom.network.SharedTripLeg? {
        val currentIndex = _currentLegIndex.value
        val legs = _sharedTripLegs.value
        return if (currentIndex < legs.size) legs[currentIndex] else null
    }

    fun isLastLeg(): Boolean {
        val currentIndex = _currentLegIndex.value
        val legs = _sharedTripLegs.value
        
        android.util.Log.d("TripDetailsViewModel", "=== IS LAST LEG DEBUG ===")
        android.util.Log.d("TripDetailsViewModel", "Current index: $currentIndex")
        android.util.Log.d("TripDetailsViewModel", "Legs size: ${legs.size}")
        android.util.Log.d("TripDetailsViewModel", "Legs: ${legs.map { "ID=${it.leg_id}, status=${it.status}" }}")
        
        // If no legs are loaded, we can't determine if it's the last leg
        if (legs.isEmpty()) {
            android.util.Log.d("TripDetailsViewModel", "No legs loaded, returning false for isLastLeg")
            return false
        }
        
        val isLast = currentIndex >= legs.size - 1
        android.util.Log.d("TripDetailsViewModel", "Is last leg: $isLast")
        return isLast
    }

    fun setCurrentLegIndex(index: Int) {
        android.util.Log.d("TripDetailsViewModel", "=== SET CURRENT LEG INDEX ===")
        android.util.Log.d("TripDetailsViewModel", "Setting current leg index to: $index")
        android.util.Log.d("TripDetailsViewModel", "Previous currentLegIndex: ${_currentLegIndex.value}")
        _currentLegIndex.value = index
        android.util.Log.d("TripDetailsViewModel", "New currentLegIndex: ${_currentLegIndex.value}")
    }

    // Force refresh shared trip data (clear cache and reload)
    fun forceRefreshSharedTripData(tripId: Int) {
        android.util.Log.d("TripDetailsViewModel", "=== FORCE REFRESH SHARED TRIP DATA ===")
        android.util.Log.d("TripDetailsViewModel", "Clearing cache and reloading data for trip ID: $tripId")
        android.util.Log.d("TripDetailsViewModel", "Using dedicated shared trip legs endpoint: /api/shared-trips/$tripId/legs")
        
        // Clear current data
        _sharedTripLegs.value = emptyList()
        _currentLegIndex.value = 0
        
        // Reload fresh data using the correct endpoint
        loadSharedTripLegs(tripId)
    }
} 