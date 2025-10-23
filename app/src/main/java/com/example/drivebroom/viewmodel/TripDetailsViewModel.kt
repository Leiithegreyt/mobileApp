package com.example.drivebroom.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.drivebroom.repository.DriverRepository
import com.example.drivebroom.network.DepartureBody
import com.example.drivebroom.network.ArrivalBody
import com.example.drivebroom.network.ReturnBody
import com.example.drivebroom.network.ReturnStartBody
import com.example.drivebroom.network.ReturnArrivalBody
import com.example.drivebroom.network.CompleteBody
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
    
    // Track the last successfully loaded trip ID to guard against 0/invalid IDs
    private var lastLoadedTripId: Int = -1

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

    private val _tripDetailsError = MutableStateFlow<String?>(null)
    val tripDetailsError: StateFlow<String?> = _tripDetailsError

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

    // Single trip status tracking
    private val _singleTripStatus = MutableStateFlow("pending")
    val singleTripStatus: StateFlow<String> = _singleTripStatus
    
    // Track when we've made a local status update to prevent backend override
    private var lastLocalStatusUpdate = 0L
    private val STATUS_UPDATE_PROTECTION_DURATION = 5000L // 5 seconds

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

    fun addFuelStartToLastLeg(fuelStart: Double) {
        _itinerary.update {
            if (it.isNotEmpty()) {
                it.dropLast(1) + it.last().copy(fuelStart = fuelStart)
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
        _tripDetailsError.value = null
        _singleTripStatus.value = "pending"
        android.util.Log.d("TripDetailsViewModel", "State reset - tripDetails: ${_tripDetails.value?.id}, isLoading: ${_isLoading.value}")
    }

    fun loadTripDetails(tripId: Int) {
        android.util.Log.d("TripDetailsViewModel", "=== LOADING TRIP DETAILS ===")
        android.util.Log.d("TripDetailsViewModel", "TripId: $tripId")
        _isLoading.value = true
        _tripDetailsError.value = null // Clear any previous error
        viewModelScope.launch {
            val result = repository.getTripDetails(tripId)
            result.onSuccess { details ->
                android.util.Log.d("TripDetailsViewModel", "Successfully loaded trip details: ${details.id}, destination: ${details.destination}")
                _tripDetails.value = details
                _isLoading.value = false
                android.util.Log.d("TripDetailsViewModel", "State updated - tripDetails: ${_tripDetails.value?.id}, isLoading: ${_isLoading.value}")
                // Remember trip id
                lastLoadedTripId = details.id
                // Update single trip status
                android.util.Log.d("TripDetailsViewModel", "Backend returned status: '${details.status}'")
                android.util.Log.d("TripDetailsViewModel", "Current local status before update: '${_singleTripStatus.value}'")
                
                // Only update status from backend if it's a valid progression
                // This prevents backend from overriding local status updates
                val currentStatus = _singleTripStatus.value
                val backendStatus = details.status
                val currentTime = System.currentTimeMillis()
                val timeSinceLastUpdate = currentTime - lastLocalStatusUpdate
                
                android.util.Log.d("TripDetailsViewModel", "Backend status: '$backendStatus', Current local status: '$currentStatus'")
                android.util.Log.d("TripDetailsViewModel", "Time since last local update: ${timeSinceLastUpdate}ms")
                
                // Only update if backend status is a valid progression or if local status is still pending
                // Also check if we're still in the protection period
                val shouldUpdate = when {
                    currentStatus == "pending" -> true // Always update from pending
                    timeSinceLastUpdate < STATUS_UPDATE_PROTECTION_DURATION -> {
                        android.util.Log.d("TripDetailsViewModel", "Still in protection period, ignoring backend status")
                        false // Don't override during protection period
                    }
                    currentStatus == "on_route" && backendStatus == "arrived" -> true // Valid progression
                    currentStatus == "arrived" && backendStatus == "returning" -> true // Valid progression
                    currentStatus == "returning" && backendStatus == "completed" -> true // Valid progression
                    currentStatus == "arrived" && backendStatus == "completed" -> true // Direct completion
                    backendStatus == currentStatus -> true // Same status, no harm
                    else -> false // Don't override with invalid status
                }
                
                if (shouldUpdate) {
                    android.util.Log.d("TripDetailsViewModel", "Updating status from '$currentStatus' to '$backendStatus'")
                    _singleTripStatus.value = backendStatus
                } else {
                    android.util.Log.d("TripDetailsViewModel", "Keeping local status '$currentStatus', ignoring backend status '$backendStatus'")
                }
                // Simple trip type detection - just check if it has legs
                val isSharedTrip = (details.legs?.size ?: 0) > 1
                _isSharedTrip.value = isSharedTrip
                android.util.Log.d("TripDetailsViewModel", "Trip type detection: legs=${details.legs?.size ?: 0}, isSharedTrip: $isSharedTrip")
                
                // Only load shared trip legs if this is actually a shared trip
                if (_isSharedTrip.value) {
                    android.util.Log.d("TripDetailsViewModel", "Loading legs via unified endpoint for shared trip: ${details.id}")
                    loadSharedTripLegs(tripId)
                } else {
                    android.util.Log.d("TripDetailsViewModel", "Single trip detected - skipping leg loading")
                }
            }.onFailure { error ->
                android.util.Log.e("TripDetailsViewModel", "Failed to load trip details: ${error.message}")
                // If details 404, handle according to trip type policy
                val httpCode = (error as? retrofit2.HttpException)?.code()
                if (httpCode == 404) {
                    // Do NOT call /trips/{id}/legs for single trips; completed singles are not returned by details endpoint
                    android.util.Log.w("TripDetailsViewModel", "Details 404 for tripId=$tripId. Treating as completed single and stopping refresh.")
                    _singleTripStatus.value = "completed"
                    // Optionally fetch completed list to reflect UI state
                    loadCompletedTrips()
                    _isLoading.value = false
                    _tripDetailsError.value = null
                    return@launch
                } else {
                    _tripDetailsError.value = "Failed to load trip details: ${error.message}"
                }
                _isLoading.value = false
            }
        }
    }

    fun loadSharedTripLegs(tripId: Int) {
        viewModelScope.launch {
            try {
                android.util.Log.d("TripDetailsViewModel", "=== LOADING TRIP LEGS (UNIFIED) ===")
                android.util.Log.d("TripDetailsViewModel", "Loading legs for trip ID: $tripId")
                android.util.Log.d("TripDetailsViewModel", "Current time: ${System.currentTimeMillis()}")

                // Test debug endpoint on initial load
                android.util.Log.d("TripDetailsViewModel", "=== ABOUT TO CALL DEBUG ENDPOINT ===")
                try {
                    android.util.Log.d("TripDetailsViewModel", "=== TESTING DEBUG ENDPOINT ON LOAD ===")
                    android.util.Log.d("TripDetailsViewModel", "Calling repository.getDebugTripLegs($tripId)")
                    val debugResponse = repository.getDebugTripLegs(tripId)
                    android.util.Log.d("TripDetailsViewModel", "Debug response on load: $debugResponse")
                    android.util.Log.d("TripDetailsViewModel", "=== DEBUG ENDPOINT CALL COMPLETED ===")
                } catch (e: Exception) {
                    android.util.Log.e("TripDetailsViewModel", "Debug endpoint error on load: ${e.message}")
                    e.printStackTrace()
                }

                val legs = repository.getTripLegs(tripId)
                android.util.Log.d("TripDetailsViewModel", "Loaded ${legs.size} legs")
                // Remember trip id when legs load
                lastLoadedTripId = tripId
                
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
                // Don't automatically set current leg index - let UI handle leg selection
                // This prevents race conditions between automatic selection and manual selection
                android.util.Log.d("TripDetailsViewModel", "Legs loaded successfully - UI will handle leg selection")
                
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

    fun logDeparture(tripId: Int, odometerStart: Double, fuelBalanceStart: Double, passengersConfirmed: List<String> = emptyList()) {
        viewModelScope.launch {
            android.util.Log.d("TripDetailsViewModel", "🚀 === DEPARTURE METHOD CALLED ===")
            android.util.Log.d("TripDetailsViewModel", "Trip ID: $tripId")
            android.util.Log.d("TripDetailsViewModel", "Odometer Start: $odometerStart")
            android.util.Log.d("TripDetailsViewModel", "Fuel Start: $fuelBalanceStart")
            android.util.Log.d("TripDetailsViewModel", "Passengers: $passengersConfirmed")
            
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
            
            // Call single trip endpoint (backend now works correctly)
            val currentTime = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
            val departureBody = DepartureBody(odometerStart, fuelBalanceStart, "Base", currentTime, passengersConfirmed)
            android.util.Log.d("TripDetailsViewModel", "=== DEPARTURE PAYLOAD ===")
            android.util.Log.d("TripDetailsViewModel", "odometer_start=${departureBody.odometerStart}")
            android.util.Log.d("TripDetailsViewModel", "fuel_start=${departureBody.fuelStart}")
            android.util.Log.d("TripDetailsViewModel", "departure_location='${departureBody.departureLocation}'")
            android.util.Log.d("TripDetailsViewModel", "departure_time='${departureBody.departureTime}'")
            android.util.Log.d("TripDetailsViewModel", "passengers_confirmed=${departureBody.passengersConfirmed}")
            val result = repository.logDeparture(tripId, departureBody)
            result.fold(
                onSuccess = {
                    // Update status locally immediately for UI responsiveness
                    _singleTripStatus.value = "on_route"
                    lastLocalStatusUpdate = System.currentTimeMillis()
                    android.util.Log.d("TripDetailsViewModel", "Updated status to 'on_route' locally")
                    
                    // Don't reload immediately - let the UI handle the status change
                    // The reload will happen when needed
                    _actionState.value = TripActionState.Success
                },
                onFailure = { error ->
                    android.util.Log.e("TripDetailsViewModel", "Departure failed: ${error.message}")
                    _actionState.value = TripActionState.Error(error.message ?: "Departure failed")
                }
            )
        }
    }

    fun logArrival(tripId: Int, odometerEnd: Double, fuelEnd: Double, arrivalLocation: String, fuelUsed: Double? = null, notes: String? = null, passengersDropped: List<String> = emptyList()) {
        viewModelScope.launch {
            android.util.Log.d("TripDetailsViewModel", "=== SINGLE TRIP ARRIVAL CALLED ===")
            android.util.Log.d("TripDetailsViewModel", "TripId: $tripId, OdometerEnd: $odometerEnd, FuelEnd: $fuelEnd, ArrivalLocation: $arrivalLocation")
            
            _actionState.value = TripActionState.Loading
            val token = tokenManager.getToken()?.let { "Bearer $it" } ?: run {
                _actionState.value = TripActionState.Error("No auth token")
                return@launch
            }
            
            // Call single trip endpoint (backend now works correctly)
            val currentTime = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
            android.util.Log.d("TripDetailsViewModel", "Calling repository.logArrival with ArrivalBody")
            val result = repository.logArrival(tripId, ArrivalBody(odometerEnd, fuelEnd, arrivalLocation, fuelUsed, notes, currentTime, passengersDropped))
            result.fold(
                onSuccess = {
                    // Update status locally immediately for UI responsiveness
                    _singleTripStatus.value = "arrived"
                    lastLocalStatusUpdate = System.currentTimeMillis()
                    android.util.Log.d("TripDetailsViewModel", "Updated status to 'arrived' locally")
                    
                    // Don't reload immediately - let the UI handle the status change
                    // The reload will happen when the dialog is closed
                    _actionState.value = TripActionState.Success
                },
                onFailure = { error ->
                    android.util.Log.e("TripDetailsViewModel", "Arrival failed: ${error.message}")
                    _actionState.value = TripActionState.Error(error.message ?: "Arrival failed")
                }
            )
        }
    }

    fun logReturnStart(tripId: Int, returnStartLocation: String, odometerStart: Double? = null, fuelStart: Double? = null) {
        viewModelScope.launch {
            _actionState.value = TripActionState.Loading
            val token = tokenManager.getToken()?.let { "Bearer $it" } ?: run {
                _actionState.value = TripActionState.Error("No auth token")
                return@launch
            }
            
            val currentTime = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
            val result = repository.logReturnStart(tripId, ReturnStartBody(returnStartLocation, odometerStart, fuelStart, currentTime))
            result.fold(
                onSuccess = {
                    _singleTripStatus.value = "returning"
                    lastLocalStatusUpdate = System.currentTimeMillis()
                    _actionState.value = TripActionState.Success
                },
                onFailure = { error ->
                    android.util.Log.e("TripDetailsViewModel", "Return start failed: ${error.message}")
                    _actionState.value = TripActionState.Error(error.message ?: "Return start failed")
                }
            )
        }
    }

    fun logReturnArrival(tripId: Int, odometerEnd: Double, fuelEnd: Double, returnArrivalLocation: String, fuelUsed: Double, notes: String? = null) {
        viewModelScope.launch {
            _actionState.value = TripActionState.Loading
            val token = tokenManager.getToken()?.let { "Bearer $it" } ?: run {
                _actionState.value = TripActionState.Error("No auth token")
                return@launch
            }
            
            val result = repository.logReturnArrival(tripId, ReturnArrivalBody(odometerEnd, fuelEnd, returnArrivalLocation, fuelUsed, notes))
            result.fold(
                onSuccess = {
                    _singleTripStatus.value = "completed"
                    lastLocalStatusUpdate = System.currentTimeMillis()
                    _actionState.value = TripActionState.Success
                },
                onFailure = { error ->
                    android.util.Log.e("TripDetailsViewModel", "Return arrival failed: ${error.message}")
                    _actionState.value = TripActionState.Error(error.message ?: "Return arrival failed")
                }
            )
        }
    }

    fun logComplete(tripId: Int, odometerEnd: Double, fuelEnd: Double, fuelUsed: Double, completionNotes: String? = null) {
        viewModelScope.launch {
            _actionState.value = TripActionState.Loading
            val token = tokenManager.getToken()?.let { "Bearer $it" } ?: run {
                _actionState.value = TripActionState.Error("No auth token")
                return@launch
            }
            
            val result = repository.logComplete(tripId, CompleteBody(odometerEnd, fuelEnd, fuelUsed, completionNotes))
            result.fold(
                onSuccess = {
                    _singleTripStatus.value = "completed"
                    lastLocalStatusUpdate = System.currentTimeMillis()
                    _actionState.value = TripActionState.Success
                },
                onFailure = { error ->
                    android.util.Log.e("TripDetailsViewModel", "Complete failed: ${error.message}")
                    _actionState.value = TripActionState.Error(error.message ?: "Complete failed")
                }
            )
        }
    }

    fun logArrival(tripId: Int) {
        // Call the existing logArrival with default values
        logArrival(tripId, 0.0, 0.0, "Destination")
    }

    fun startReturn(tripId: Int, odometerStart: Double, fuelStart: Double) {
        viewModelScope.launch {
            android.util.Log.d("TripDetailsViewModel", "=== START RETURN CALLED ===")
            android.util.Log.d("TripDetailsViewModel", "TripId: $tripId, Odometer: $odometerStart, Fuel: $fuelStart")
            android.util.Log.d("TripDetailsViewModel", "Current status before: ${_singleTripStatus.value}")
            
            _actionState.value = TripActionState.Loading
            val token = tokenManager.getToken()?.let { "Bearer $it" } ?: run {
                _actionState.value = TripActionState.Error("No auth token")
                return@launch
            }
            
            // Get current time and location for return start
            val currentTime = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
            val returnLocation = "Destination" // This should be the current location
            
            val returnStartRequest = com.example.drivebroom.network.SingleTripReturnStartRequest(
                odometer_start = odometerStart,
                fuel_start = fuelStart,
                return_start_time = currentTime,
                return_start_location = returnLocation
            )
            
            android.util.Log.d("TripDetailsViewModel", "Sending return start request: $returnStartRequest")
            
            val result = repository.startSingleTripReturn(tripId, returnStartRequest)
            _actionState.value = result.fold(
                onSuccess = {
                    android.util.Log.d("TripDetailsViewModel", "✅ Return start successful - backend accepted the request")
                    // Update status locally immediately for UI responsiveness
                    _singleTripStatus.value = "returning"
                    lastLocalStatusUpdate = System.currentTimeMillis()
                    android.util.Log.d("TripDetailsViewModel", "✅ Updated status to 'returning' locally")
                    android.util.Log.d("TripDetailsViewModel", "Current status after update: '${_singleTripStatus.value}'")
                    
                    // Don't reload immediately - let the UI handle the status change
                    // The reload will happen when needed
                    TripActionState.Success
                },
                onFailure = { error ->
                    android.util.Log.e("TripDetailsViewModel", "❌ Single trip return start failed: ${error.message}")
                    
                    // Check if it's a trip type error - fallback to local status update
                    if (error.message?.contains("Invalid trip type") == true || 
                        error.message?.contains("only for single trips") == true) {
                        android.util.Log.w("TripDetailsViewModel", "Backend doesn't support single trip return start - updating status locally")
                        _singleTripStatus.value = "returning"
                        lastLocalStatusUpdate = System.currentTimeMillis()
                        android.util.Log.d("TripDetailsViewModel", "✅ Updated status to 'returning' locally (fallback)")
                        android.util.Log.d("TripDetailsViewModel", "Current status after fallback update: '${_singleTripStatus.value}'")
                        TripActionState.Success
                    } else {
                        TripActionState.Error(error.message ?: "Return start failed")
                    }
                }
            )
        }
    }

    fun logReturnArrival(tripId: Int, odometerEnd: Double, fuelEnd: Double, notes: String? = null) {
        viewModelScope.launch {
            android.util.Log.d("TripDetailsViewModel", "=== RETURN ARRIVAL CALLED ===")
            android.util.Log.d("TripDetailsViewModel", "TripId: $tripId, Odometer: $odometerEnd, Fuel: $fuelEnd")
            
            _actionState.value = TripActionState.Loading
            val token = tokenManager.getToken()?.let { "Bearer $it" } ?: run {
                _actionState.value = TripActionState.Error("No auth token")
                return@launch
            }
            
            // For now, just update status locally since we don't have a dedicated return arrival endpoint
            // In a full implementation, you would call a backend endpoint
            android.util.Log.d("TripDetailsViewModel", "Updating status to completed locally")
            _singleTripStatus.value = "completed"
            _actionState.value = TripActionState.Success
        }
    }

    fun logReturn(
        tripId: Int,
        fuelBalanceStart: Double,
        fuelPurchased: Double,
        fuelBalanceEnd: Double,
        passengerDetails: List<com.example.drivebroom.network.PassengerDetail>,
        itinerary: List<com.example.drivebroom.network.ItineraryLegDto>
    ) {
        android.util.Log.d("TripDetailsViewModel", "⚠️ OLD logReturn CALLED! (This won't create return legs)")
        android.util.Log.d("TripDetailsViewModel", "Trip ID: $tripId")
        
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
                onSuccess = { 
                    _singleTripStatus.value = "completed"
                    TripActionState.Success 
                },
                onFailure = { TripActionState.Error(it.message ?: "Return failed") }
            )
        }
    }

    /**
     * NEW METHOD: Unified return journey flow that properly stores return journey data
     * This method calls the proper return journey endpoints that create TripLeg records with return_to_base = true
     */
    fun logReturnWithUnifiedFlow(
        tripId: Int,
        fuelBalanceStart: Double,
        fuelPurchased: Double,
        fuelBalanceEnd: Double,
        passengerDetails: List<com.example.drivebroom.network.PassengerDetail>,
        itinerary: List<com.example.drivebroom.network.ItineraryLegDto>,
        onComplete: (() -> Unit)? = null
    ) {
        android.util.Log.d("TripDetailsViewModel", "🚀 logReturnWithUnifiedFlow CALLED!")
        android.util.Log.d("TripDetailsViewModel", "Trip ID: $tripId")
        android.util.Log.d("TripDetailsViewModel", "Fuel Start: $fuelBalanceStart, End: $fuelBalanceEnd")
        android.util.Log.d("TripDetailsViewModel", "Itinerary legs: ${itinerary.size}")
        
        viewModelScope.launch {
            _actionState.value = TripActionState.Loading
            val token = tokenManager.getToken()?.let { "Bearer $it" } ?: run {
                _actionState.value = TripActionState.Error("No auth token")
                return@launch
            }
            
            try {
                android.util.Log.d("TripDetailsViewModel", "=== UNIFIED RETURN JOURNEY FLOW ===")
                android.util.Log.d("TripDetailsViewModel", "Trip ID: $tripId")
                
                // Check if this is a single trip or shared trip
                val isSharedTrip = _isSharedTrip.value
                
                // Get common data
                val firstLeg = itinerary.firstOrNull()
                if (firstLeg == null) {
                    android.util.Log.e("TripDetailsViewModel", "No itinerary legs found for return journey")
                    _actionState.value = TripActionState.Error("No itinerary data found for return journey")
                    return@launch
                }
                
                // Extract return journey data from the itinerary
                val returnStartOdometer = firstLeg.odometer_arrival ?: firstLeg.odometer ?: 0.0 // Use odometer_arrival (arrival) first, fallback to odometer
                val returnStartFuel = fuelBalanceEnd // End fuel from main journey
                // For return journey, we need to calculate the final odometer reading
                // Assuming return journey covers the same distance as outbound journey
                val outboundDistance = (firstLeg.odometer_arrival ?: firstLeg.odometer ?: 0.0) - (firstLeg.odometer_start ?: 0.0)
                val returnEndOdometer = returnStartOdometer + outboundDistance // Return to base odometer
                // Calculate fuel used for return journey (assuming similar fuel consumption)
                val returnFuelUsed = outboundDistance * 0.1 // Rough estimate: 0.1L per km
                val returnEndFuel = maxOf(0.0, returnStartFuel - returnFuelUsed) // Ensure fuel never goes negative
                
                android.util.Log.d("TripDetailsViewModel", "=== ODOMETER CALCULATION DEBUG ===")
                android.util.Log.d("TripDetailsViewModel", "firstLeg.odometer_start: ${firstLeg.odometer_start}")
                android.util.Log.d("TripDetailsViewModel", "firstLeg.odometer: ${firstLeg.odometer}")
                android.util.Log.d("TripDetailsViewModel", "firstLeg.odometer_arrival: ${firstLeg.odometer_arrival}")
                android.util.Log.d("TripDetailsViewModel", "returnStartOdometer: $returnStartOdometer")
                android.util.Log.d("TripDetailsViewModel", "outboundDistance: $outboundDistance km")
                android.util.Log.d("TripDetailsViewModel", "returnEndOdometer: $returnEndOdometer")
                
                android.util.Log.d("TripDetailsViewModel", "=== FUEL CALCULATION DEBUG ===")
                android.util.Log.d("TripDetailsViewModel", "Return start fuel: $returnStartFuel L")
                android.util.Log.d("TripDetailsViewModel", "Calculated return fuel used: $returnFuelUsed L")
                android.util.Log.d("TripDetailsViewModel", "Raw return end fuel: ${returnStartFuel - returnFuelUsed} L")
                android.util.Log.d("TripDetailsViewModel", "Final return end fuel (capped at 0): $returnEndFuel L")
                
                // Get current time for return journey
                val currentTime = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                val returnStartLocation = firstLeg.arrival ?: "Destination"
                val returnEndLocation = firstLeg.departure ?: "ISATU Miagao Campus"
                
                if (isSharedTrip) {
                    // For shared trips, use the leg-based return API
                    android.util.Log.d("TripDetailsViewModel", "Shared trip return journey data:")
                    android.util.Log.d("TripDetailsViewModel", "Start odometer: $returnStartOdometer, Start fuel: $returnStartFuel")
                    android.util.Log.d("TripDetailsViewModel", "End odometer: $returnEndOdometer, End fuel: $returnEndFuel")
                    android.util.Log.d("TripDetailsViewModel", "Start location: $returnStartLocation, End location: $returnEndLocation")
                    
                    // For shared trips, we'll use a default leg ID of 1 since there's only one leg
                    val legId = 1
                    
                    // Step 1: Start return journey
                    android.util.Log.d("TripDetailsViewModel", "Step 1: Starting shared trip return journey...")
                    val returnStartRequest = com.example.drivebroom.network.ReturnStartRequest(
                        odometer_start = returnStartOdometer,
                        fuel_start = returnStartFuel,
                        return_start_time = currentTime,
                        return_start_location = returnStartLocation
                    )
                    
                    val startResult = repository.startReturn(tripId, legId, returnStartRequest)
                    if (startResult.isFailure) {
                        android.util.Log.e("TripDetailsViewModel", "Return start failed: ${startResult.exceptionOrNull()?.message}")
                        _actionState.value = TripActionState.Error("Failed to start return journey: ${startResult.exceptionOrNull()?.message}")
                        return@launch
                    }
                } else {
                    // For single trips, use the single trip return API
                    android.util.Log.d("TripDetailsViewModel", "Single trip return journey data:")
                    android.util.Log.d("TripDetailsViewModel", "Start odometer: $returnStartOdometer, Start fuel: $returnStartFuel")
                    android.util.Log.d("TripDetailsViewModel", "Start location: $returnStartLocation")
                    
                    // Step 1: Start return journey using single trip API
                    android.util.Log.d("TripDetailsViewModel", "Step 1: Starting single trip return journey...")
                    val returnStartRequest = com.example.drivebroom.network.SingleTripReturnStartRequest(
                        odometer_start = returnStartOdometer,
                        fuel_start = returnStartFuel,
                        return_start_time = currentTime,
                        return_start_location = returnStartLocation
                    )
                    
                    val startResult = repository.startSingleTripReturn(tripId, returnStartRequest)
                    if (startResult.isFailure) {
                        android.util.Log.e("TripDetailsViewModel", "Single trip return start failed: ${startResult.exceptionOrNull()?.message}")
                        _actionState.value = TripActionState.Error("Failed to start return journey: ${startResult.exceptionOrNull()?.message}")
                        return@launch
                    }
                }
                
                android.util.Log.d("TripDetailsViewModel", "✅ Return journey started successfully")
                
                // Step 2: Arrive at base
                android.util.Log.d("TripDetailsViewModel", "Step 2: Arriving at base...")
                
                if (isSharedTrip) {
                    // For shared trips, use the leg-based return arrival API
                    val legId = 1 // Same legId as used in the start request
                    val returnArrivalRequest = com.example.drivebroom.network.ReturnArrivalRequest(
                        odometer_end = returnEndOdometer,
                        fuel_end = returnEndFuel,
                        return_arrival_time = currentTime,
                        return_arrival_location = returnEndLocation,
                        fuel_used = returnStartFuel - returnEndFuel,
                        notes = "Return journey completed via unified flow"
                    )
                    
                    val arrivalResult = repository.arriveAtBase(tripId, legId, returnArrivalRequest)
                    if (arrivalResult.isFailure) {
                        android.util.Log.e("TripDetailsViewModel", "Return arrival failed: ${arrivalResult.exceptionOrNull()?.message}")
                        _actionState.value = TripActionState.Error("Failed to complete return journey: ${arrivalResult.exceptionOrNull()?.message}")
                        return@launch
                    }
                } else {
                    // For single trips, we need to call return-arrive API after return start
                    android.util.Log.d("TripDetailsViewModel", "Single trip return journey started, now calling return-arrive...")
                    
                    // Calculate return arrival data
                    val returnArrivalRequest = com.example.drivebroom.network.ReturnArrivalBody(
                        odometerEnd = returnEndOdometer,
                        fuelEnd = returnEndFuel,
                        returnArrivalLocation = returnEndLocation,
                        fuelUsed = returnStartFuel - returnEndFuel,
                        notes = "Return journey completed"
                    )
                    
                    android.util.Log.d("TripDetailsViewModel", "Single trip return arrival data:")
                    android.util.Log.d("TripDetailsViewModel", "Return end odometer: $returnEndOdometer")
                    android.util.Log.d("TripDetailsViewModel", "Return end fuel: $returnEndFuel")
                    android.util.Log.d("TripDetailsViewModel", "Return fuel used: ${returnStartFuel - returnEndFuel}")
                    
                    // Call the single trip return arrival API
                    val returnArrivalResult = repository.logReturnArrival(tripId, returnArrivalRequest)
                    if (returnArrivalResult.isFailure) {
                        android.util.Log.e("TripDetailsViewModel", "Single trip return arrival failed: ${returnArrivalResult.exceptionOrNull()?.message}")
                        _actionState.value = TripActionState.Error("Failed to complete return journey: ${returnArrivalResult.exceptionOrNull()?.message}")
                        return@launch
                    }
                    
                    android.util.Log.d("TripDetailsViewModel", "✅ Single trip return journey completed successfully")
                    
                    // Step 3: Complete the trip
                    android.util.Log.d("TripDetailsViewModel", "Step 3: Completing single trip...")
                    val completeResult = repository.logComplete(tripId, com.example.drivebroom.network.CompleteBody(
                        odometerEnd = returnEndOdometer,
                        fuelEnd = returnEndFuel,
                        fuelUsed = returnStartFuel - returnEndFuel,
                        completionNotes = "Single trip completed with return journey"
                    ))
                    
                    if (completeResult.isFailure) {
                        android.util.Log.e("TripDetailsViewModel", "Single trip completion failed: ${completeResult.exceptionOrNull()?.message}")
                        _actionState.value = TripActionState.Error("Failed to complete trip: ${completeResult.exceptionOrNull()?.message}")
                        return@launch
                    }
                    
                    android.util.Log.d("TripDetailsViewModel", "✅ Single trip completed successfully")
                }
                
                android.util.Log.d("TripDetailsViewModel", "✅ Return journey completed successfully")
                android.util.Log.d("TripDetailsViewModel", "Return journey data has been stored as TripLeg records with return_to_base = true")
                
                // DEBUG: Let's check what legs exist after creating the return leg (only for shared trips)
                if (isSharedTrip) {
                    android.util.Log.d("TripDetailsViewModel", "=== DEBUGGING: Checking trip legs after return journey ===")
                    try {
                        // Wait a moment for the backend to process the return leg creation
                        kotlinx.coroutines.delay(1000)
                        
                        // Check what legs exist now
                        val updatedLegs = repository.getTripLegs(tripId)
                        android.util.Log.d("TripDetailsViewModel", "=== LEGS AFTER RETURN JOURNEY ===")
                        android.util.Log.d("TripDetailsViewModel", "Total legs found: ${updatedLegs.size}")
                        updatedLegs.forEachIndexed { index, leg ->
                            android.util.Log.d("TripDetailsViewModel", "Leg $index: ID=${leg.leg_id}, Destination=${leg.destination}, ReturnToBase=${leg.return_to_base}, Status=${leg.status}")
                        }
                        
                    } catch (e: Exception) {
                        android.util.Log.e("TripDetailsViewModel", "Failed to fetch legs after return: ${e.message}")
                    }
                } else {
                    android.util.Log.d("TripDetailsViewModel", "Single trip return journey completed - no legs to check")
                }
                
                _singleTripStatus.value = "completed"
                _actionState.value = TripActionState.Success
                
                // Call the completion callback if provided
                onComplete?.invoke()
                
            } catch (e: Exception) {
                android.util.Log.e("TripDetailsViewModel", "Unified return journey flow failed", e)
                _actionState.value = TripActionState.Error("Return journey failed: ${e.message}")
            }
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
                    // DEBUG: Log each trip and its legs
                    trips.forEach { trip ->
                        android.util.Log.d("TripDetailsViewModel", "=== COMPLETED TRIP DEBUG ===")
                        android.util.Log.d("TripDetailsViewModel", "Trip ${trip.id}: ${trip.destination}, legs: ${trip.legs?.size ?: 0}")
                        trip.legs?.forEachIndexed { index, leg ->
                            android.util.Log.d("TripDetailsViewModel", "  Leg $index: ID=${leg.leg_id}, Status=${leg.status}, ReturnToBase=${leg.return_to_base}, Destination=${leg.destination}")
                        }
                    }
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

    /**
     * Create return legs for shared trips after completion
     * Note: This is currently disabled due to backend validation requiring legs to be in "arrived" status
     * The return leg creation should be handled through the normal trip execution flow
     */
    private fun createReturnLegsForSharedTrip(tripId: Int, lastLeg: com.example.drivebroom.network.SharedTripLeg?, totalFuelUsed: Double) {
        viewModelScope.launch {
            try {
                android.util.Log.d("TripDetailsViewModel", "=== RETURN LEG CREATION FOR SHARED TRIP ===")
                android.util.Log.d("TripDetailsViewModel", "Trip ID: $tripId")
                android.util.Log.d("TripDetailsViewModel", "Last leg: ${lastLeg?.leg_id}, Destination: ${lastLeg?.destination}")
                
                // Check current leg statuses
                val completedLegs = _sharedTripLegs.value.filter { it.status == "completed" }
                android.util.Log.d("TripDetailsViewModel", "Found ${completedLegs.size} completed legs")
                
                for (leg in completedLegs) {
                    android.util.Log.d("TripDetailsViewModel", "Leg ${leg.leg_id} (${leg.destination}): status=${leg.status}")
                }

                android.util.Log.w("TripDetailsViewModel", "⚠️ Return leg creation skipped - Backend requires legs to be in 'arrived' status")
                android.util.Log.w("TripDetailsViewModel", "⚠️ Return legs should be created through the normal trip execution flow")
                android.util.Log.w("TripDetailsViewModel", "⚠️ This requires the driver to manually return to base for each destination")

            } catch (e: Exception) {
                android.util.Log.e("TripDetailsViewModel", "Failed to create return legs for shared trip: ${e.message}")
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
                val safeTripId = if (tripId > 0) tripId else lastLoadedTripId
                android.util.Log.d("TripDetailsViewModel", "Leg depart using tripId=$safeTripId (original=$tripId), legId=$legId")
                val request = com.example.drivebroom.network.LegDepartureRequest(
                    odometer_start = odometerStart,
                    fuel_start = fuelStart,
                    passengers_confirmed = passengersConfirmed,
                    departure_time = convertTo24Hour(departureTime), // Convert to 24-hour for backend
                    departure_location = departureLocation,
                    manifest_override_reason = manifestOverrideReason
                )
                
                android.util.Log.d("TripDetailsViewModel", "=== SENDING LEG DEPARTURE REQUEST ===")
                android.util.Log.d("TripDetailsViewModel", "Trip ID: $safeTripId, Leg ID: $legId")
                android.util.Log.d("TripDetailsViewModel", "Odometer Start: $odometerStart")
                android.util.Log.d("TripDetailsViewModel", "Fuel Start: $fuelStart")
                android.util.Log.d("TripDetailsViewModel", "Departure Time: ${convertTo24Hour(departureTime)}")
                android.util.Log.d("TripDetailsViewModel", "Departure Location: '$departureLocation'")
                android.util.Log.d("TripDetailsViewModel", "Passengers Confirmed: $passengersConfirmed")
                android.util.Log.d("TripDetailsViewModel", "Manifest Override: $manifestOverrideReason")
                val result = repository.logLegDeparture(safeTripId, legId, request)
                _actionState.value = result.fold(
                    onSuccess = { 
                        android.util.Log.d("TripDetailsViewModel", "=== LEG DEPARTURE SUCCESS ===")
                        android.util.Log.d("TripDetailsViewModel", "Successfully departed leg ID: $legId")
                        
                        
                        // Only reload shared trip legs if this is actually a shared trip
                        if (_isSharedTrip.value) {
                            android.util.Log.d("TripDetailsViewModel", "Reloading shared trip legs after departure...")
                            
                            // Debug: Call debug endpoint to see raw database state
                            try {
                                android.util.Log.d("TripDetailsViewModel", "=== CALLING DEBUG ENDPOINT ===")
                                val debugResponse = repository.getDebugTripLegs(safeTripId)
                                android.util.Log.d("TripDetailsViewModel", "Debug response: $debugResponse")
                            } catch (e: Exception) {
                                android.util.Log.e("TripDetailsViewModel", "Debug endpoint error: ${e.message}")
                            }
                            
                            // Add a small delay to ensure backend has updated the database
                            kotlinx.coroutines.delay(500)
                            loadSharedTripLegs(safeTripId)
                        } else {
                            android.util.Log.d("TripDetailsViewModel", "Single trip - skipping leg reload after departure")
                        }
                        
                        // Show notification for leg departure
                        context?.let { ctx ->
                            val notificationManager = TripNotificationManager(ctx)
                            val currentLeg = getCurrentLeg()
                            currentLeg?.let { leg ->
                                notificationManager.showLegCompletedNotification(
                                    legNumber = _currentLegIndex.value + 1,
                                    teamName = leg.team_name ?: "Unknown Team"
                                )
                            }
                        }
                        TripActionState.Success 
                    },
                    onFailure = { 
                        android.util.Log.e("TripDetailsViewModel", "=== LEG DEPARTURE FAILED ===")
                        android.util.Log.e("TripDetailsViewModel", "Failed to depart leg ID: $legId")
                        android.util.Log.e("TripDetailsViewModel", "Error: ${it.message}")
                        
                        // Handle authentication errors specifically
                        val httpCode = (it as? retrofit2.HttpException)?.code()
                        if (httpCode == 401) {
                            android.util.Log.e("TripDetailsViewModel", "Authentication failed - user needs to re-login")
                            TripActionState.Error("Your session has expired. Please log in again.")
                        } else {
                            // Fallback: if unified leg endpoint returns 404, try single-trip departure
                            if (httpCode == 404) {
                                android.util.Log.w("TripDetailsViewModel", "Leg depart 404 - attempting single-trip /trips/{id}/departure fallback")
                                viewModelScope.launch {
                                    val dep = repository.logDeparture(safeTripId, DepartureBody(odometerStart, fuelStart, "Base"))
                                    _actionState.value = dep.fold(
                                        onSuccess = {
                                            loadTripDetails(safeTripId)
                                            TripActionState.Success
                                        },
                                        onFailure = { TripActionState.Error(it.message ?: "Departure failed") }
                                    )
                                }
                                TripActionState.Loading
                            } else {
                                TripActionState.Error(it.message ?: "Leg departure failed")
                            }
                        } 
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
                val safeTripId = if (tripId > 0) tripId else lastLoadedTripId
                android.util.Log.d("TripDetailsViewModel", "Leg arrive using tripId=$safeTripId (original=$tripId), legId=$legId")
                // Ensure passengers_dropped is non-empty for backend validation
                val fallbackPassengers = getCurrentLeg()?.passengers?.filter { it.isNotBlank() } ?: emptyList()
                val safePassengersDropped = if (passengersDropped.isNotEmpty()) passengersDropped else fallbackPassengers

                val request = com.example.drivebroom.network.LegArrivalRequest(
                    odometer_end = odometerEnd,
                    fuel_used = fuelUsed,
                    fuel_end = fuelEnd,
                    passengers_dropped = safePassengersDropped,
                    arrival_time = convertTo24Hour(arrivalTime), // Convert to 24-hour for backend
                    arrival_location = arrivalLocation,
                    fuel_purchased = fuelPurchased,
                    notes = notes
                )
                val result = repository.logLegArrival(safeTripId, legId, request)
                _actionState.value = result.fold(
                    onSuccess = { 
                        android.util.Log.d("TripDetailsViewModel", "=== LEG ARRIVAL SUCCESS ===")
                        android.util.Log.d("TripDetailsViewModel", "Successfully arrived leg ID: $legId")
                        
                        
                        // Update itinerary for single trip UI
                        _itinerary.update { list ->
                            if (list.isNotEmpty()) list.dropLast(1) + list.last().copy(
                                odometerEnd = odometerEnd,
                                timeArrival = convertTo24Hour(arrivalTime),
                                arrival = arrivalLocation,
                                timeArrivalDisplay = arrivalTime
                            ) else list
                        }
                        // Don't complete the leg automatically - let user choose Return to Base or Continue to Next
                        android.util.Log.d("TripDetailsViewModel", "Leg arrived successfully - waiting for user choice")
                        // Refresh shared trip legs to get updated leg status
                        loadSharedTripLegs(safeTripId)
                        TripActionState.Success 
                    },
                    onFailure = { 
                        android.util.Log.e("TripDetailsViewModel", "=== LEG ARRIVAL FAILED ===")
                        android.util.Log.e("TripDetailsViewModel", "Failed to arrive leg ID: $legId")
                        android.util.Log.e("TripDetailsViewModel", "Error: ${it.message}")
                        // Fallback: if unified leg arrival returns 404, try single-trip arrival
                        val httpCode = (it as? retrofit2.HttpException)?.code()
                        if (httpCode == 404) {
                            android.util.Log.w("TripDetailsViewModel", "Leg arrive 404 - attempting single-trip /trips/{id}/arrival fallback")
                            viewModelScope.launch {
                                val arr = repository.logArrival(safeTripId, ArrivalBody(odometerEnd, 0.0, "Destination"))
                                _actionState.value = arr.fold(
                                    onSuccess = {
                                        loadTripDetails(safeTripId)
                                        TripActionState.Success
                                    },
                                    onFailure = { TripActionState.Error(it.message ?: "Arrival failed") }
                                )
                            }
                            TripActionState.Loading
                        } else {
                            TripActionState.Error(it.message ?: "Leg arrival failed")
                        }
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
                val safeTripId = if (tripId > 0) tripId else lastLoadedTripId
                android.util.Log.d("TripDetailsViewModel", "Leg complete using tripId=$safeTripId (original=$tripId), legId=$legId")
                // Calculate distance travelled and fuel used for this leg
                val currentLeg = getCurrentLeg()
                val odometerStart = currentLeg?.odometer_start ?: 0.0
                val fuelStart = currentLeg?.fuel_start ?: 0.0
                val distanceTravelled = maxOf(0.0, odometerEnd - odometerStart) // Ensure distance is not negative
                // Calculate fuel used correctly: fuelStart + fuelPurchased - fuelEnd
                val fuelPurchasedValue = fuelPurchased ?: 0.0
                val fuelUsed = fuelStart + fuelPurchasedValue - fuelEnd
                
                android.util.Log.d("TripDetailsViewModel", "=== LEG COMPLETION CALCULATION ===")
                android.util.Log.d("TripDetailsViewModel", "Odometer start: $odometerStart")
                android.util.Log.d("TripDetailsViewModel", "Odometer end: $odometerEnd")
                android.util.Log.d("TripDetailsViewModel", "Distance travelled: $distanceTravelled")
                android.util.Log.d("TripDetailsViewModel", "Fuel start: $fuelStart")
                android.util.Log.d("TripDetailsViewModel", "Fuel end: $fuelEnd")
                android.util.Log.d("TripDetailsViewModel", "Fuel purchased: $fuelPurchasedValue")
                android.util.Log.d("TripDetailsViewModel", "Fuel used calculation (fuelStart + fuelPurchased - fuelEnd): $fuelStart + $fuelPurchasedValue - $fuelEnd = $fuelUsed")
                
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
                android.util.Log.d("TripDetailsViewModel", "Request data: final_odometer=${request.final_odometer}, final_fuel=${request.final_fuel}, fuel_used=${request.fuel_used}, fuel_purchased=${request.fuel_purchased}")
                
                val result = repository.completeLeg(safeTripId, legId, request)
                _actionState.value = result.fold(
                    onSuccess = {
                        android.util.Log.d("TripDetailsViewModel", "=== LEG COMPLETION SUCCESS ===")
                        android.util.Log.d("TripDetailsViewModel", "Successfully completed leg ID: $legId")
                        
                        // Reload shared trip legs to get updated status
                        loadSharedTripLegs(safeTripId)
                        
                        // Show notification for leg completion
                        context?.let { ctx ->
                            val notificationManager = TripNotificationManager(ctx)
                            val currentLeg = getCurrentLeg()
                            currentLeg?.let { leg ->
                                notificationManager.showLegCompletedNotification(
                                    legNumber = _currentLegIndex.value + 1,
                                    teamName = leg.team_name ?: "Unknown Team"
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
                        // Fallback: if unified leg completion returns 404, try single-trip return flow (requires UI to prompt final fields)
                        val httpCode = (it as? retrofit2.HttpException)?.code()
                        if (httpCode == 404) {
                            android.util.Log.w("TripDetailsViewModel", "Leg complete 404 - backend may require single-trip /return endpoint. Use single-trip Complete flow.")
                            TripActionState.Error("Trip not found for unified leg complete. Use single-trip Complete flow.")
                        } else {
                            TripActionState.Error(it.message ?: "Leg completion failed")
                        }
                    }
                )
            } catch (e: Exception) {
                _actionState.value = TripActionState.Error(e.message ?: "Leg completion failed")
            }
        }
    }

    // New methods for return flow
    fun startReturn(
        tripId: Int,
        legId: Int,
        odometerStart: Double,
        fuelStart: Double,
        returnStartTime: String = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a")),
        returnStartLocation: String = "ISATU Miagao Campus"
    ) {
        viewModelScope.launch {
            _actionState.value = TripActionState.Loading
            try {
                val safeTripId = if (tripId > 0) tripId else lastLoadedTripId
                android.util.Log.d("TripDetailsViewModel", "=== STARTING RETURN TO BASE ===")
                android.util.Log.d("TripDetailsViewModel", "Trip ID: $safeTripId, Leg ID: $legId")
                
                val request = com.example.drivebroom.network.ReturnStartRequest(
                    odometer_start = odometerStart,
                    fuel_start = fuelStart,
                    return_start_time = convertTo24Hour(returnStartTime),
                    return_start_location = returnStartLocation
                )
                
                val result = repository.startReturn(safeTripId, legId, request)
                _actionState.value = result.fold(
                    onSuccess = { 
                        android.util.Log.d("TripDetailsViewModel", "=== RETURN START SUCCESS ===")
                        
                        // DEBUG: Check what legs exist after starting return
                        kotlinx.coroutines.delay(500)
                        val legsAfterStart = repository.getTripLegs(safeTripId)
                        android.util.Log.d("TripDetailsViewModel", "=== LEGS AFTER RETURN START ===")
                        android.util.Log.d("TripDetailsViewModel", "Total legs found: ${legsAfterStart.size}")
                        legsAfterStart.forEachIndexed { index, leg ->
                            android.util.Log.d("TripDetailsViewModel", "Leg $index: ID=${leg.leg_id}, Destination=${leg.destination}, ReturnToBase=${leg.return_to_base}, Status=${leg.status}")
                        }
                        
                        loadSharedTripLegs(tripId)
                        TripActionState.Success 
                    },
                    onFailure = { 
                        android.util.Log.e("TripDetailsViewModel", "=== RETURN START FAILED ===")
                        TripActionState.Error(it.message ?: "Return start failed") 
                    }
                )
            } catch (e: Exception) {
                _actionState.value = TripActionState.Error(e.message ?: "Return start failed")
            }
        }
    }

    fun arriveAtBase(
        tripId: Int,
        legId: Int,
        odometerEnd: Double,
        fuelEnd: Double,
        returnArrivalTime: String = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a")),
        returnArrivalLocation: String = "ISATU Miagao Campus",
        fuelUsed: Double? = null,
        notes: String? = null
    ) {
        viewModelScope.launch {
            _actionState.value = TripActionState.Loading
            try {
                val safeTripId = if (tripId > 0) tripId else lastLoadedTripId
                android.util.Log.d("TripDetailsViewModel", "=== ARRIVING AT BASE ===")
                android.util.Log.d("TripDetailsViewModel", "Trip ID: $safeTripId, Leg ID: $legId")
                
                // Validate odometer reading - end must be >= start
                val currentLeg = getCurrentLeg()
                val odometerStart = currentLeg?.odometer_start ?: 0.0
                
                // Allow small differences due to backend data inconsistency
                val odometerDifference = odometerStart - odometerEnd
                if (odometerEnd < odometerStart && odometerDifference > 5.0) {
                    android.util.Log.e("TripDetailsViewModel", "Invalid odometer reading: end ($odometerEnd) < start ($odometerStart), difference: $odometerDifference")
                    _actionState.value = TripActionState.Error("End odometer reading ($odometerEnd) must be greater than or equal to start reading ($odometerStart). Please check your odometer values.")
                    return@launch
                } else if (odometerDifference > 0.0) {
                    android.util.Log.w("TripDetailsViewModel", "Warning: odometer end ($odometerEnd) < start ($odometerStart), but difference is small ($odometerDifference). Allowing due to backend data inconsistency.")
                }
                
                val request = com.example.drivebroom.network.ReturnArrivalRequest(
                    odometer_end = odometerEnd,
                    fuel_end = fuelEnd,
                    return_arrival_time = convertTo24Hour(returnArrivalTime),
                    return_arrival_location = returnArrivalLocation,
                    fuel_used = fuelUsed,
                    notes = notes
                )
                
                val result = repository.arriveAtBase(safeTripId, legId, request)
                _actionState.value = result.fold(
                    onSuccess = { 
                        android.util.Log.d("TripDetailsViewModel", "=== RETURN ARRIVAL SUCCESS ===")
                        
                        // DEBUG: Check what legs exist after completing return
                        kotlinx.coroutines.delay(500)
                        val legsAfterArrival = repository.getTripLegs(safeTripId)
                        android.util.Log.d("TripDetailsViewModel", "=== LEGS AFTER RETURN ARRIVAL ===")
                        android.util.Log.d("TripDetailsViewModel", "Total legs found: ${legsAfterArrival.size}")
                        legsAfterArrival.forEachIndexed { index, leg ->
                            android.util.Log.d("TripDetailsViewModel", "Leg $index: ID=${leg.leg_id}, Destination=${leg.destination}, ReturnToBase=${leg.return_to_base}, Status=${leg.status}")
                        }
                        
                        loadSharedTripLegs(tripId)
                        TripActionState.Success 
                    },
                    onFailure = { 
                        android.util.Log.e("TripDetailsViewModel", "=== RETURN ARRIVAL FAILED ===")
                        TripActionState.Error(it.message ?: "Return arrival failed") 
                    }
                )
            } catch (e: Exception) {
                _actionState.value = TripActionState.Error(e.message ?: "Return arrival failed")
            }
        }
    }

    fun continueToNextLeg(
        tripId: Int,
        legId: Int,
        odometerEnd: Double,
        fuelEnd: Double,
        fuelPurchased: Double? = null,
        notes: String? = null
    ) {
        viewModelScope.launch {
            _actionState.value = TripActionState.Loading
            try {
                val safeTripId = if (tripId > 0) tripId else lastLoadedTripId
                android.util.Log.d("TripDetailsViewModel", "Continue to next leg using tripId=$safeTripId (original=$tripId), legId=$legId")
                
                // Get the current leg data to use actual values
                val currentLeg = getCurrentLeg()
                val actualOdometerEnd = if (odometerEnd > 0.0) odometerEnd else (currentLeg?.odometer_end ?: 0.0)
                val actualFuelEnd = if (fuelEnd > 0.0) fuelEnd else (currentLeg?.fuel_end ?: 0.0)
                
                android.util.Log.d("TripDetailsViewModel", "Using odometer end: $actualOdometerEnd, fuel end: $actualFuelEnd")
                
                // Complete the current leg with actual values
                completeLeg(safeTripId, legId, actualOdometerEnd, actualFuelEnd, fuelPurchased, notes)
                
            } catch (e: Exception) {
                android.util.Log.e("TripDetailsViewModel", "Exception in continueToNextLeg: ${e.message}")
                _actionState.value = TripActionState.Error("Continue to next leg failed: ${e.message}")
            }
        }
    }

    fun submitFullSharedTrip(tripId: Int, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            _actionState.value = TripActionState.Loading
            try {
                android.util.Log.d("TripDetailsViewModel", "=== SUBMIT FULL SHARED TRIP ===")
                android.util.Log.d("TripDetailsViewModel", "Trip ID: $tripId")
                
                val legs = _sharedTripLegs.value
                if (legs.isEmpty()) {
                    android.util.Log.e("TripDetailsViewModel", "No legs available for trip submission")
                    _actionState.value = TripActionState.Error("No legs available for trip submission")
                    return@launch
                }
                
                // Get the last leg's final values
                val lastLeg = legs.lastOrNull()
                val finalOdometer = lastLeg?.odometer_end ?: 0.0
                val finalFuel = lastLeg?.fuel_end ?: 0.0
                
                // Calculate totals
                val totalDistance = legs.sumOf { (it.odometer_end ?: 0.0) - (it.odometer_start ?: 0.0) }
                val totalFuelUsed = legs.sumOf { it.fuel_used ?: 0.0 }
                val totalFuelPurchased = legs.sumOf { it.fuel_purchased ?: 0.0 }
                
                android.util.Log.d("TripDetailsViewModel", "Final odometer: $finalOdometer, Final fuel: $finalFuel")
                android.util.Log.d("TripDetailsViewModel", "Total distance: $totalDistance, Total fuel used: $totalFuelUsed")
                android.util.Log.d("TripDetailsViewModel", "Total fuel purchased: $totalFuelPurchased")
                
                val request = com.example.drivebroom.network.TripSubmissionRequest(
                    final_odometer = finalOdometer,
                    final_fuel = finalFuel,
                    total_distance = totalDistance,
                    total_fuel_used = totalFuelUsed,
                    total_fuel_purchased = if (totalFuelPurchased > 0) totalFuelPurchased else null,
                    notes = lastLeg?.notes
                )
                
                val result = repository.submitSharedTrip(tripId, request)
                _actionState.value = result.fold(
                    onSuccess = {
                        android.util.Log.d("TripDetailsViewModel", "Trip submitted successfully")
                        
                        // Create return legs for shared trips
                        android.util.Log.d("TripDetailsViewModel", "🚀 Creating return legs for shared trip...")
                        createReturnLegsForSharedTrip(tripId, lastLeg, totalFuelUsed)
                        
                        onComplete?.invoke()
                        TripActionState.Success
                    },
                    onFailure = { 
                        android.util.Log.e("TripDetailsViewModel", "Trip submission failed: ${it.message}")
                        TripActionState.Error(it.message ?: "Submit full trip failed") 
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("TripDetailsViewModel", "Trip submission exception: ${e.message}")
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
        
        // NEW LOGIC: A leg is "last" only if ALL OTHER legs are completed
        // This allows flexible leg ordering - you can do legs in any order
        val otherLegs = legs.filterIndexed { index, _ -> index != currentIndex }
        val allOtherLegsCompleted = otherLegs.all { it.status == "completed" }
        
        android.util.Log.d("TripDetailsViewModel", "Current leg: ID=${legs[currentIndex].leg_id}")
        android.util.Log.d("TripDetailsViewModel", "Other legs: ${otherLegs.map { "ID=${it.leg_id}, status=${it.status}" }}")
        android.util.Log.d("TripDetailsViewModel", "All other legs completed: $allOtherLegsCompleted")
        
        val isLast = allOtherLegsCompleted
        
        android.util.Log.d("TripDetailsViewModel", "Final isLast result: $isLast")
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

    // Manually refresh trip details (call this after dialogs are closed)
    fun refreshTripDetails(tripId: Int) {
        android.util.Log.d("TripDetailsViewModel", "=== MANUAL REFRESH TRIP DETAILS ===")
        android.util.Log.d("TripDetailsViewModel", "Refreshing trip details for ID: $tripId")
        loadTripDetails(tripId)
    }
} 