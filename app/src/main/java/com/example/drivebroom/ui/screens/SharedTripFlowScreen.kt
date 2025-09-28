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
    
       // Update selectedLeg when leg data changes (e.g., after departure/arrival/completion)
       LaunchedEffect(sharedTripLegs, currentLegIndex) {
           android.util.Log.d("SharedTripFlowScreen", "=== LEG DATA CHANGE DEBUG ===")
           android.util.Log.d("SharedTripFlowScreen", "sharedTripLegs size: ${sharedTripLegs.size}")
           android.util.Log.d("SharedTripFlowScreen", "currentLegIndex: $currentLegIndex")
           android.util.Log.d("SharedTripFlowScreen", "selectedLeg: ${selectedLeg?.leg_id}")
           
           if (sharedTripLegs.isNotEmpty()) {
               android.util.Log.d("SharedTripFlowScreen", "Legs data: ${sharedTripLegs.map { "ID=${it.leg_id}, status=${it.status}" }}")
           }
           
           if (selectedLeg != null && currentLegIndex < sharedTripLegs.size) {
               val updatedLeg = sharedTripLegs[currentLegIndex]
               if (updatedLeg.leg_id == selectedLeg?.leg_id) {
                   android.util.Log.d("SharedTripFlowScreen", "=== UPDATING SELECTED LEG ===")
                   android.util.Log.d("SharedTripFlowScreen", "Old status: ${selectedLeg?.status}")
                   android.util.Log.d("SharedTripFlowScreen", "New status: ${updatedLeg.status}")
                   selectedLeg = updatedLeg
                   android.util.Log.d("SharedTripFlowScreen", "selectedLeg updated successfully")
               } else {
                   android.util.Log.d("SharedTripFlowScreen", "Leg ID mismatch - selectedLeg: ${selectedLeg?.leg_id}, updatedLeg: ${updatedLeg.leg_id}")
               }
           } else {
               android.util.Log.d("SharedTripFlowScreen", "Cannot update selectedLeg - selectedLeg: ${selectedLeg?.leg_id}, currentLegIndex: $currentLegIndex, sharedTripLegs.size: ${sharedTripLegs.size}")
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
                TripSummaryScreen(
                tripDetails = tripDetails,
                completedLegs = sharedTripLegs.filter { it.status == "completed" },
                totalDistance = calculateTotalDistance(sharedTripLegs),
                totalFuelUsed = calculateTotalFuelUsed(sharedTripLegs),
                onBack = { showSummary = false },
                onSubmitLogs = {
                    // Submit full trip via ViewModel, then navigate back
                    viewModel.submitFullSharedTrip(tripDetails.id) {
                        onBack()
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
                        onBack = onBack, // Back button goes to trip list
                        onLegList = { 
                            selectedLeg = null // Clear selected leg to go back to leg selection
                        }, // Leg List button goes to leg selection
                        onRefresh = {
                            // Force refresh shared trip data
                            android.util.Log.d("SharedTripFlowScreen", "=== REFRESH REQUESTED ===")
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
                            // Move to next leg (handled by ViewModel)
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

private fun calculateTotalDistance(legs: List<SharedTripLeg>): Double {
    return legs.sumOf { leg ->
        val start = leg.odometer_start ?: 0.0
        val end = leg.odometer_end ?: start
        end - start
    }
}

private fun calculateTotalFuelUsed(legs: List<SharedTripLeg>): Double {
    return legs.sumOf { leg ->
        val start = leg.fuel_start ?: 0.0
        val end = leg.fuel_end ?: start
        val purchased = leg.fuel_purchased ?: 0.0
        start + purchased - end
    }
}
