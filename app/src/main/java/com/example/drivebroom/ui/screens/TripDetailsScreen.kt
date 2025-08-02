package com.example.drivebroom.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import com.example.drivebroom.network.TripDetails
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.drivebroom.viewmodel.TripDetailsViewModel
import com.example.drivebroom.viewmodel.TripActionState
import com.example.drivebroom.repository.DriverRepository
import com.example.drivebroom.utils.TokenManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import java.time.LocalTime
import androidx.compose.foundation.clickable

@Composable
fun TripDetailsScreen(
    tripDetails: TripDetails,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: TripDetailsViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                val tokenManager = TokenManager(context)
                val networkClient = com.example.drivebroom.network.NetworkClient(tokenManager)
                val repo = DriverRepository(networkClient.apiService)
                @Suppress("UNCHECKED_CAST")
                return TripDetailsViewModel(repo, tokenManager) as T
            }
        }
    )
    val actionState by viewModel.actionState.collectAsState()
    val latestTripDetails by viewModel.tripDetails.collectAsState()
    val itinerary by viewModel.itinerary.collectAsState()
    var showCompletedTrips by remember { mutableStateOf(false) }
    val completedTrips by viewModel.completedTrips.collectAsState()

    // On first composition, load trip details
    LaunchedEffect(tripDetails.id) {
        viewModel.loadTripDetails(tripDetails.id)
    }

    // Use latestTripDetails if available, else fallback to initial tripDetails
    val trip = latestTripDetails ?: tripDetails

    // Itinerary state: list of legs (odometer, time, destination)
    data class ItineraryLeg(val odometer: Double, val time: String, val destination: String)
    var currentLegOdometer by remember { mutableStateOf("") }
    var currentLegFuelBalance by remember { mutableStateOf("") }
    var currentLegDestination by remember { mutableStateOf("") }
    var showDepartureDialog by remember { mutableStateOf(false) }
    var showArrivalDialog by remember { mutableStateOf(false) }
    var showNextStopPrompt by remember { mutableStateOf(false) }
    var showReturnDialog by remember { mutableStateOf(false) }
    var fuelPurchasedInput by remember { mutableStateOf("") }
    var fuelUsedInput by remember { mutableStateOf("") }
    var fuelBalanceEndInput by remember { mutableStateOf("") }
    var odometerArrivalInput by remember { mutableStateOf("") }
    var lastFuelBalanceStart by remember { mutableStateOf<Double?>(null) }
    var tripStarted by remember { mutableStateOf(false) }
    var canArrive by remember { mutableStateOf(false) }
    var canReturn by remember { mutableStateOf(false) }

    // Format dates
    val formattedTravelDate = try {
        val zdt = ZonedDateTime.parse(trip.travel_date)
        zdt.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
    } catch (e: Exception) {
        trip.travel_date
    }

    val formattedRequestDate = try {
        val zdt = ZonedDateTime.parse(trip.date_of_request)
        zdt.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
    } catch (e: Exception) {
        trip.date_of_request
    }

    val formattedTravelTime = try {
        val zdt = ZonedDateTime.parse(trip.travel_time)
        zdt.format(DateTimeFormatter.ofPattern("h:mm a"))
    } catch (e: Exception) {
        trip.travel_time
    }

    // Parse passengers JSON string
    val passengersList = try {
        Json.decodeFromString<List<String>>(trip.passengers)
    } catch (e: Exception) {
        emptyList()
    }

    Card(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Trip Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Trip #: ${trip.id}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            trip.vehicle?.let { vehicle ->
                Text("Vehicle: ${vehicle.plate_number}", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Destination: ${trip.destination}", style = MaterialTheme.typography.bodyMedium)
            Text("Date/Time: $formattedTravelDate, $formattedTravelTime", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Authorized Passenger(s):", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            passengersList.forEach { passenger ->
                Text(passenger, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Show itinerary table (5 columns)
            if (itinerary.isNotEmpty()) {
                Text("Itinerary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    Text("Odometer", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("Time (Dep)", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("Departure", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("Time (Arr)", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("Arrival", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                }
                Column {
                    itinerary.forEachIndexed { idx, leg ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(leg.odometer.toString(), modifier = Modifier.weight(1f))
                            Text(leg.timeDepartureDisplay ?: "", modifier = Modifier.weight(1f))
                            Text(leg.departure, modifier = Modifier.weight(1f))
                            Text(leg.timeArrivalDisplay ?: "", modifier = Modifier.weight(1f))
                            Text(leg.arrival ?: "", modifier = Modifier.weight(1f))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Button logic
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(
                    onClick = { showDepartureDialog = true },
                    enabled = !tripStarted || canArrive,
                    modifier = Modifier.weight(1f)
                ) { Text("Departure") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { showArrivalDialog = true },
                    enabled = tripStarted && canArrive,
                    modifier = Modifier.weight(1f)
                ) { Text("Arrival") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { showReturnDialog = true },
                    enabled = canReturn,
                    modifier = Modifier.weight(1f)
                ) { Text("Returned") }
            }

            if (actionState is TripActionState.Loading) {
                Spacer(modifier = Modifier.height(8.dp))
                CircularProgressIndicator()
            }
            if (actionState is TripActionState.Error) {
                Spacer(modifier = Modifier.height(8.dp))
                Text((actionState as TripActionState.Error).message, color = MaterialTheme.colorScheme.error)
            }
            if (actionState is TripActionState.Success) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Action successful!", color = MaterialTheme.colorScheme.primary)
                // Optionally refresh trip details here
                viewModel.resetActionState()
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Back")
            }

            // After the main action button, add a restart button for testing (not shown if completed)
            if (trip.status.lowercase() != "completed") {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.restartTrip(trip.id) },
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .align(Alignment.CenterHorizontally),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Text("Restart (Test)", style = MaterialTheme.typography.labelLarge)
                }
            }

            // Add a button to view completed trips
            Button(
                onClick = {
                    viewModel.loadCompletedTrips()
                    showCompletedTrips = true
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("View Completed Trips") }
            Spacer(modifier = Modifier.height(8.dp))
            // Show completed trips list if requested
            if (showCompletedTrips) {
                Text("Completed Trips", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                completedTrips.forEach { trip ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { /* TODO: Show trip details/edit screen */ },
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Trip #${trip.id}", fontWeight = FontWeight.Bold)
                            Text("Destination: ${trip.destination}")
                            Text("Date: ${trip.travel_date}")
                            Text("Status: ${trip.status}")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { showCompletedTrips = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("Back to Active Trips")
                }
            }
        }
    }

    // Departure dialog
    if (showDepartureDialog) {
        AlertDialog(
            onDismissRequest = { showDepartureDialog = false },
            title = { Text("Departure Details") },
            text = {
                Column {
                    OutlinedTextField(
                        value = currentLegOdometer,
                        onValueChange = { currentLegOdometer = it },
                        label = { Text("Odometer Start") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = currentLegFuelBalance,
                        onValueChange = { currentLegFuelBalance = it },
                        label = { Text("Fuel Balance Start (L)") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = currentLegDestination,
                        onValueChange = { currentLegDestination = it },
                        label = { Text("Departure Location") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val odo = currentLegOdometer.toDoubleOrNull()
                    val fuel = currentLegFuelBalance.toDoubleOrNull()
                    val dep = currentLegDestination
                    if (odo != null && fuel != null && dep.isNotBlank()) {
                        viewModel.logDeparture(trip.id, odo, fuel)
                        lastFuelBalanceStart = fuel
                        tripStarted = true
                        canArrive = true
                        val timeForBackend = LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                        val timeForDisplay = LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"))
                        viewModel.addDepartureLeg(odo, timeForBackend, dep, timeForDisplay)
                        showDepartureDialog = false
                        currentLegOdometer = ""
                        currentLegFuelBalance = ""
                        currentLegDestination = ""
                    }
                }) { Text("Submit") }
            },
            dismissButton = {
                TextButton(onClick = { showDepartureDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Arrival dialog
    if (showArrivalDialog) {
        var arrivalLocation by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showArrivalDialog = false },
            title = { Text("Arrival Details") },
            text = {
                Column {
                    OutlinedTextField(
                        value = arrivalLocation,
                        onValueChange = { arrivalLocation = it },
                        label = { Text("Arrival Location") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val arr = arrivalLocation
                    if (arr.isNotBlank()) {
                        val timeForBackend = LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                        val timeForDisplay = LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"))
                        viewModel.addArrivalToLastLeg(timeForBackend, arr, timeForDisplay)
                        showArrivalDialog = false
                        showNextStopPrompt = true
                        arrivalLocation = ""
                        canArrive = false
                    }
                }) { Text("Submit") }
            },
            dismissButton = {
                TextButton(onClick = { showArrivalDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Next stop prompt
    if (showNextStopPrompt) {
        AlertDialog(
            onDismissRequest = { showNextStopPrompt = false },
            title = { Text("Another Stop?") },
            text = { Text("Do you have another stop?") },
            confirmButton = {
                TextButton(onClick = {
                    canArrive = false
                    showNextStopPrompt = false
                    showDepartureDialog = true
                }) { Text("Yes, add another leg") }
            },
            dismissButton = {
                TextButton(onClick = {
                    canReturn = true
                    showNextStopPrompt = false
                }) { Text("No, trip is done") }
            }
        )
    }

    // For return dialog, collect signatures for each passenger (now removed, just show details)

    if (showReturnDialog) {
        AlertDialog(
            onDismissRequest = { showReturnDialog = false },
            title = { Text("Trip Return Details") },
            text = {
                Column {
                    OutlinedTextField(
                        value = fuelPurchasedInput,
                        onValueChange = { fuelPurchasedInput = it },
                        label = { Text("Fuel Purchased (L)") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = fuelUsedInput,
                        onValueChange = { fuelUsedInput = it },
                        label = { Text("Fuel Used (L)") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = fuelBalanceEndInput,
                        onValueChange = { fuelBalanceEndInput = it },
                        label = { Text("Fuel Balance End (L)") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = odometerArrivalInput,
                        onValueChange = { odometerArrivalInput = it },
                        label = { Text("Odometer Arrival") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Passenger Details", fontWeight = FontWeight.Bold)
                    Column {
                        passengersList.forEachIndexed { idx, passengerName ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                elevation = CardDefaults.cardElevation(1.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(passengerName, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    Text("All passengers: ${passengersList.size}")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val fuelPurchased = fuelPurchasedInput.toDoubleOrNull()
                    val fuelUsed = fuelUsedInput.toDoubleOrNull()
                    val fuelBalanceEnd = fuelBalanceEndInput.toDoubleOrNull()
                    val odometerArrival = odometerArrivalInput.toDoubleOrNull()
                    if (
                        fuelPurchased != null &&
                        fuelUsed != null &&
                        fuelBalanceEnd != null &&
                        odometerArrival != null &&
                        passengersList.isNotEmpty() &&
                        lastFuelBalanceStart != null
                    ) {
                        val passengerDetailsToSend = passengersList.map { name ->
                            com.example.drivebroom.network.PassengerDetail(
                                name = name,
                                destination = "",
                                signature = ""
                            )
                        }
                        val itineraryDto = itinerary.map {
                            com.example.drivebroom.network.ItineraryLegDto(
                                odometer = it.odometer,
                                time_departure = it.timeDeparture,
                                departure = it.departure,
                                time_arrival = it.timeArrival,
                                arrival = it.arrival
                            )
                        }
                        viewModel.logReturn(
                            trip.id,
                            lastFuelBalanceStart!!,
                            fuelPurchased,
                            fuelUsed,
                            fuelBalanceEnd,
                            passengerDetailsToSend,
                            "",
                            odometerArrival,
                            itineraryDto
                        )
                        showReturnDialog = false
                        fuelPurchasedInput = ""
                        fuelUsedInput = ""
                        fuelBalanceEndInput = ""
                        odometerArrivalInput = ""
                    }
                }) { Text("Submit") }
            },
            dismissButton = {
                TextButton(onClick = { showReturnDialog = false }) { Text("Cancel") }
            }
        )
    }
} 