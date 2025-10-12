package com.example.drivebroom.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.drivebroom.network.SharedTripLeg
import com.example.drivebroom.network.TripDetails
import com.example.drivebroom.viewmodel.TripDetailsViewModel
import com.example.drivebroom.viewmodel.TripActionState

@Composable
fun SharedTripFlowScreen(
    tripDetails: TripDetails,
    onBack: () -> Unit,
    viewModel: TripDetailsViewModel
) {
    val sharedTripLegs by viewModel.sharedTripLegs.collectAsState()
    val currentLegIndex by viewModel.currentLegIndex.collectAsState()
    val actionState by viewModel.actionState.collectAsState()
    val isLastLeg = viewModel.isLastLeg()
    
    // Debug leg data loading
    LaunchedEffect(sharedTripLegs) {
        android.util.Log.d("SharedTripFlowScreen", "=== SHARED TRIP LEGS UPDATED ===")
        android.util.Log.d("SharedTripFlowScreen", "Legs count: ${sharedTripLegs.size}")
        if (sharedTripLegs.isNotEmpty()) {
            android.util.Log.d("SharedTripFlowScreen", "Legs: ${sharedTripLegs.map { "ID=${it.leg_id}, status=${it.status}" }}")
        }
    }

    var showSummary by remember { mutableStateOf(false) }
    var isLoadingLeg by remember { mutableStateOf(false) } // Loading state for leg selection
    var selectedLeg by remember { mutableStateOf<SharedTripLeg?>(null) } // Track selected leg separately
    
    val coroutineScope = rememberCoroutineScope()
    
    // Helper functions for calculations
    fun calculateTotalDistance(legs: List<SharedTripLeg>): Double {
        return legs.sumOf { leg ->
            val start = leg.odometer_start ?: 0.0
            val end = leg.odometer_end ?: start
            end - start
        }
    }

    fun calculateTotalFuelUsed(legs: List<SharedTripLeg>): Double {
        return legs.sumOf { leg ->
            val start = leg.fuel_start ?: 0.0
            val end = leg.fuel_end ?: start
            val purchased = leg.fuel_purchased ?: 0.0
            start + purchased - end
        }
    }
    
       // Update selectedLeg when leg data changes (e.g., after departure/arrival/completion)
       LaunchedEffect(sharedTripLegs, currentLegIndex) {
           android.util.Log.d("SharedTripFlowScreen", "=== LEG DATA CHANGE DEBUG ===")
           android.util.Log.d("SharedTripFlowScreen", "sharedTripLegs size: ${sharedTripLegs.size}")
           android.util.Log.d("SharedTripFlowScreen", "currentLegIndex: $currentLegIndex")
           android.util.Log.d("SharedTripFlowScreen", "selectedLeg: ${selectedLeg?.leg_id}")
           
           if (sharedTripLegs.isNotEmpty()) {
               android.util.Log.d("SharedTripFlowScreen", "Legs data: ${sharedTripLegs.map { "ID=${it.leg_id}, status=${it.status}" }}")
           }
           
           // Don't auto-select - let user click "Begin First Leg" button
           // Removed auto-selection to prevent immediate popup of execution screen

           val currentSelectedLeg = selectedLeg
           if (currentSelectedLeg != null && sharedTripLegs.isNotEmpty()) {
               // Find the leg with matching ID in the updated legs list
               val matchingLeg = sharedTripLegs.find { it.leg_id == currentSelectedLeg.leg_id }
               if (matchingLeg != null) {
                   android.util.Log.d("SharedTripFlowScreen", "=== UPDATING SELECTED LEG ===")
                   android.util.Log.d("SharedTripFlowScreen", "Old status: ${currentSelectedLeg.status}")
                   android.util.Log.d("SharedTripFlowScreen", "New status: ${matchingLeg.status}")
                   selectedLeg = matchingLeg
                   
                   // Also update the currentLegIndex to match the selected leg
                   val newIndex = sharedTripLegs.indexOfFirst { it.leg_id == matchingLeg.leg_id }
                   if (newIndex >= 0 && newIndex != currentLegIndex) {
                       android.util.Log.d("SharedTripFlowScreen", "Updating currentLegIndex from $currentLegIndex to $newIndex to match selected leg")
                       viewModel.setCurrentLegIndex(newIndex)
                   }
                   
                   android.util.Log.d("SharedTripFlowScreen", "selectedLeg updated successfully")
               } else {
                   android.util.Log.d("SharedTripFlowScreen", "Selected leg ${currentSelectedLeg.leg_id} not found in updated legs list")
               }
           } else {
               android.util.Log.d("SharedTripFlowScreen", "Cannot update selectedLeg - selectedLeg: ${currentSelectedLeg?.leg_id}, sharedTripLegs.size: ${sharedTripLegs.size}")
           }
       }

    // Define onLegClick handler - this will be passed to SharedTripDetailsScreen
    val onLegClick: (Int) -> Unit = remember(sharedTripLegs) { { legId ->
        android.util.Log.d("SharedTripFlowScreen", "=== LEG CLICKED ===")
        android.util.Log.d("SharedTripFlowScreen", "Clicked leg ID: $legId")
        android.util.Log.d("SharedTripFlowScreen", "Available legs: ${sharedTripLegs.map { it.leg_id }}")
        val legIndex = sharedTripLegs.indexOfFirst { it.leg_id == legId }
        android.util.Log.d("SharedTripFlowScreen", "Found leg index: $legIndex")
        if (legIndex >= 0) {
            android.util.Log.d("SharedTripFlowScreen", "Setting current leg index to: $legIndex")
            // Show loading state
            isLoadingLeg = true
            viewModel.setCurrentLegIndex(legIndex)
            // Set the selected leg directly
            selectedLeg = sharedTripLegs[legIndex]
            // Add a small delay to show loading and make transition smoother
            coroutineScope.launch {
                delay(300) // 300ms delay
                isLoadingLeg = false
                // The execution screen will show as an overlay
            }
        } else {
            android.util.Log.e("SharedTripFlowScreen", "Leg ID $legId not found in legs list!")
        }
    } }

    when {
        isLoadingLeg -> {
            // Show loading screen while transitioning
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading leg details...",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
        showSummary -> {
            val isShared = (tripDetails.trip_type == "shared")
            TripSummaryScreen(
                tripDetails = tripDetails,
                completedLegs = sharedTripLegs.filter { it.status == "completed" },
                totalDistance = calculateTotalDistance(sharedTripLegs),
                totalFuelUsed = calculateTotalFuelUsed(sharedTripLegs),
                showSubmitButton = isShared,
                onBack = { showSummary = false },
                onSubmitLogs = {
                    if (isShared) {
                        // Submit full shared trip via ViewModel, then navigate back
                        viewModel.submitFullSharedTrip(tripDetails.id) {
                            onBack()
                        }
                    }
                }
            )
        }
        else -> {
            // Always show the leg selection screen, but overlay the execution screen when a leg is selected
            Box {
                SharedTripDetailsScreen(
                    tripDetails = tripDetails,
                    sharedTripLegs = sharedTripLegs,
                    onBack = onBack, // Use the original onBack to go back to trip list
                    onStartTrip = {
                        if (sharedTripLegs.isNotEmpty()) {
                            viewModel.setCurrentLegIndex(0)
                            selectedLeg = sharedTripLegs[0]
                        }
                    },
                    onLegClick = onLegClick
                )
                
                // Overlay the execution screen when a leg is selected
                selectedLeg?.let { currentSelectedLeg ->
                    android.util.Log.d("SharedTripFlowScreen", "=== RENDERING SHARED TRIP LEG EXECUTION SCREEN ===")
                    android.util.Log.d("SharedTripFlowScreen", "selectedLeg: ID=${currentSelectedLeg.leg_id}, status=${currentSelectedLeg.status}")
                    android.util.Log.d("SharedTripFlowScreen", "currentLegIndex: $currentLegIndex")
                    android.util.Log.d("SharedTripFlowScreen", "totalLegs: ${sharedTripLegs.size}")
                    
                    SharedTripLegExecutionScreen(
                        tripDetails = tripDetails,
                        currentLeg = currentSelectedLeg,
                        currentLegIndex = currentLegIndex,
                        totalLegs = sharedTripLegs.size,
                        sharedTripLegs = sharedTripLegs,
                        onBack = { 
                            // Reset action state before going back to prevent button disabled issue
                            viewModel.resetActionState()
                            onBack() 
                        }, // Back button goes to trip list
                        onLegList = { 
                            // Reset action state before going back to leg selection
                            viewModel.resetActionState()
                            selectedLeg = null // Clear selected leg to go back to leg selection
                        }, // Leg List button goes to leg selection
                        onRefresh = {
                            // Force refresh shared trip data and reset action state
                            android.util.Log.d("SharedTripFlowScreen", "=== REFRESH REQUESTED ===")
                            viewModel.resetActionState() // Reset action state to ensure buttons are enabled
                            viewModel.forceRefreshSharedTripData(tripDetails.id)
                        },
                        onLegDeparture = { legId, odometerStart, fuelStart, passengersConfirmed, depTime, depLoc, overrideReason ->
                            viewModel.logLegDeparture(
                                tripDetails.id,
                                legId,
                                odometerStart,
                                fuelStart,
                                passengersConfirmed,
                                departureTime = depTime,
                                departureLocation = depLoc,
                                manifestOverrideReason = overrideReason
                            )
                        },
                        onLegArrival = { legId, odometerEnd, fuelUsed, fuelEnd, passengersDropped, arrTime, arrLoc, fuelPurchased, notes ->
                            viewModel.logLegArrival(
                                tripDetails.id,
                                legId,
                                odometerEnd,
                                fuelUsed,
                                fuelEnd,
                                passengersDropped,
                                arrivalTime = arrTime,
                                arrivalLocation = arrLoc,
                                fuelPurchased = fuelPurchased,
                                notes = notes
                            )
                        },
                        onLegComplete = { legId, odometerEnd, fuelEnd, fuelPurchased, notes ->
                            viewModel.completeLeg(tripDetails.id, legId, odometerEnd, fuelEnd, fuelPurchased, notes)
                        },
                        onNextLeg = {
                            android.util.Log.d("SharedTripFlowScreen", "=== NEXT LEG CALLBACK ===")
                            android.util.Log.d("SharedTripFlowScreen", "Current leg index: $currentLegIndex")
                            android.util.Log.d("SharedTripFlowScreen", "Total legs: ${sharedTripLegs.size}")
                            
                            if (currentLegIndex < sharedTripLegs.size - 1) {
                                val nextIndex = currentLegIndex + 1
                                android.util.Log.d("SharedTripFlowScreen", "Moving to next leg index: $nextIndex")
                                viewModel.setCurrentLegIndex(nextIndex)
                                
                                // Update selected leg to the next one
                                if (nextIndex < sharedTripLegs.size) {
                                    selectedLeg = sharedTripLegs[nextIndex]
                                    android.util.Log.d("SharedTripFlowScreen", "Updated selectedLeg to: ${selectedLeg?.leg_id}")
                                }
                            } else {
                                android.util.Log.d("SharedTripFlowScreen", "⚠️ Already at last leg, cannot move to next")
                            }
                        },
                        onTripComplete = {
                            showSummary = true
                        },
                        actionState = actionState,
                        isLastLeg = isLastLeg
                    )
                }
            }
        }
    }
}
