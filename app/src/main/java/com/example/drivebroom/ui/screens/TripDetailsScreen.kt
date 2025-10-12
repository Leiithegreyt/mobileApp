package com.example.drivebroom.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailsScreen(
    tripDetails: TripDetails,
    onBack: () -> Unit,
    onSharedTripClick: (TripDetails) -> Unit = {},
    viewModel: TripDetailsViewModel? = null
) {
    val context = LocalContext.current
    val tripDetailsViewModel: TripDetailsViewModel = viewModel ?: viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                val tokenManager = TokenManager(context)
                val networkClient = com.example.drivebroom.network.NetworkClient(tokenManager)
                val repo = DriverRepository(networkClient.apiService)
                @Suppress("UNCHECKED_CAST")
                return TripDetailsViewModel(repo, tokenManager, context) as T
            }
        }
    )
    val actionState by tripDetailsViewModel.actionState.collectAsState()
    val latestTripDetails by tripDetailsViewModel.tripDetails.collectAsState()
    val itinerary by tripDetailsViewModel.itinerary.collectAsState()
    val isSharedTrip by tripDetailsViewModel.isSharedTrip.collectAsState()
    val sharedTripLegs by tripDetailsViewModel.sharedTripLegs.collectAsState()
    val currentLegIndex by tripDetailsViewModel.currentLegIndex.collectAsState()

    // Use latestTripDetails if available, else fallback to initial tripDetails
    val trip = latestTripDetails ?: tripDetails

    // Check if this is a shared trip and route accordingly
    if (isSharedTrip) {
        SharedTripDetailsScreen(
            tripDetails = trip,
            sharedTripLegs = sharedTripLegs,
            onBack = onBack,
            onStartTrip = { onSharedTripClick(trip) },
            onLegClick = { legId ->
                // Navigate to leg execution screen
                onSharedTripClick(trip)
            }
        )
        return
    }

    // Itinerary state: list of legs (odometer, time, destination)
    data class ItineraryLeg(val odometer: Double, val time: String, val destination: String)
    var currentLegOdometer by remember { mutableStateOf("") }
    var currentLegFuelBalance by remember { mutableStateOf("") }
    var currentLegDestination by remember { mutableStateOf("Isatu Miagao Campus") }
    var showDepartureDialog by remember { mutableStateOf(false) }
    var showArrivalDialog by remember { mutableStateOf(false) }
    var showNextStopPrompt by remember { mutableStateOf(false) }
    var showReturnDialog by remember { mutableStateOf(false) }
    var fuelPurchasedInput by remember { mutableStateOf("") }
    var fuelBalanceEndInput by remember { mutableStateOf("") }
    var odometerArrivalInput by remember { mutableStateOf("") }
    var currentArrivalOdometer by remember { mutableStateOf("") }
    var lastFuelBalanceStart by remember { mutableStateOf<Double?>(null) }
    var tripStarted by remember { mutableStateOf(false) }
    var canArrive by remember { mutableStateOf(false) }
    var canReturn by remember { mutableStateOf(false) }
    var lastOdometerArrival by remember { mutableStateOf<Double?>(null) }

    // Passenger selections for single trip (visual parity with shared trip)
    var confirmedPassengerSet by remember { mutableStateOf<Set<String>>(emptySet()) }
    var droppedPassengerSet by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Clear form fields when trip changes (trip details are already loaded by MainActivity)
    LaunchedEffect(tripDetails.id) {
        // Clear all form fields when trip changes
        currentLegOdometer = ""
        currentLegFuelBalance = ""
        currentLegDestination = "Isatu Miagao Campus"
        fuelPurchasedInput = ""
        fuelBalanceEndInput = ""
        odometerArrivalInput = ""
        currentArrivalOdometer = ""
        lastFuelBalanceStart = null
        tripStarted = false
        canArrive = false
        canReturn = false
        lastOdometerArrival = null
        showDepartureDialog = false
        showArrivalDialog = false
        showNextStopPrompt = false
        showReturnDialog = false
    }

    // Calculate total distance travelled using proper odometer readings
    val departureOdometer = itinerary.firstOrNull()?.odometerStart
    val arrivalOdometer = itinerary.lastOrNull()?.odometerEnd
    val totalDistanceTravelled = if (departureOdometer != null && arrivalOdometer != null && arrivalOdometer >= departureOdometer) {
        arrivalOdometer - departureOdometer
    } else if (itinerary.size >= 2) {
        // Calculate total distance from all legs
        itinerary.sumOf { leg ->
            val start = leg.odometerStart
            val end = leg.odometerEnd ?: start
            end - start
        }
    } else 0.0

    // Format date (supports plain yyyy-MM-dd as well)
    val formattedTravelDate = try {
        val zdt = ZonedDateTime.parse(trip.travel_date)
        zdt.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
    } catch (_: Exception) {
        try {
            val ld = java.time.LocalDate.parse(trip.travel_date, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            ld.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
        } catch (_: Exception) {
        trip.travel_date
        }
    }

    val formattedRequestDate = try {
        trip.date_of_request?.let { dateStr ->
            val zdt = ZonedDateTime.parse(dateStr)
            zdt.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
        } ?: "N/A"
    } catch (e: Exception) {
        trip.date_of_request ?: "N/A"
    }

    val formattedTravelTime = trip.travel_time?.let { timeStr ->
        try {
            val zdt = ZonedDateTime.parse(timeStr)
            zdt.format(DateTimeFormatter.ofPattern("h:mm a"))
        } catch (_: Exception) {
            try {
                val lt = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"))
                lt.format(DateTimeFormatter.ofPattern("h:mm a"))
            } catch (_: Exception) {
                // Fallback to raw string if all parsing fails
                timeStr
            }
        }
    } ?: "N/A"

    // Parse passengers: supports ["Alice"], [{"name":"Alice"}], or stringified versions
    val passengersList: List<String> = try {
        val elem = trip.passengers
        android.util.Log.d("TripDetailsScreen", "=== PASSENGER PARSING DEBUG ===")
        android.util.Log.d("TripDetailsScreen", "Raw passengers element: $elem")
        android.util.Log.d("TripDetailsScreen", "Element is null: ${elem == null}")
        android.util.Log.d("TripDetailsScreen", "Element isJsonArray: ${elem?.isJsonArray}")
        android.util.Log.d("TripDetailsScreen", "Element isJsonPrimitive: ${elem?.isJsonPrimitive}")
        
        when {
            elem == null -> {
                android.util.Log.d("TripDetailsScreen", "Element is null, returning empty list")
                emptyList()
            }
            elem.isJsonArray -> {
                android.util.Log.d("TripDetailsScreen", "Parsing as JsonArray")
                val result = elem.asJsonArray.mapNotNull { jsonEl ->
                    if (jsonEl.isJsonPrimitive && jsonEl.asJsonPrimitive.isString) jsonEl.asString
                    else if (jsonEl.isJsonObject && jsonEl.asJsonObject.has("name")) jsonEl.asJsonObject.get("name").asString
                    else null
                }
                android.util.Log.d("TripDetailsScreen", "JsonArray result: $result")
                result
            }
            elem.isJsonPrimitive && elem.asJsonPrimitive.isString -> {
                val raw = elem.asString
                android.util.Log.d("TripDetailsScreen", "Parsing as JsonPrimitive string: $raw")
                var parsed: List<String> = emptyList()
                // Try list of objects with name (handle null values)
                try {
                    val jsonArray = Json.parseToJsonElement(raw)
                    if (jsonArray is JsonArray) {
                        val names = jsonArray.mapNotNull { jsonEl ->
                            if (jsonEl is JsonObject && jsonEl["name"] != null) {
                                val nameElement = jsonEl["name"]!!
                                if (nameElement is kotlinx.serialization.json.JsonPrimitive) {
                                    nameElement.content
                                } else null
                            } else null
                        }
                        if (names.isNotEmpty()) parsed = names
                        android.util.Log.d("TripDetailsScreen", "Parsed names from objects: $parsed")
                    }
                } catch (e: Exception) {
                    android.util.Log.d("TripDetailsScreen", "Failed to parse as objects: ${e.message}")
                }
                if (parsed.isEmpty()) {
                    try { 
                        parsed = Json.decodeFromString<List<String>>(raw)
                        android.util.Log.d("TripDetailsScreen", "Parsed as string list: $parsed")
                    } catch (e: Exception) { 
                        android.util.Log.d("TripDetailsScreen", "Failed to parse as string list: ${e.message}")
                        parsed = emptyList() 
                    }
                }
                parsed
            }
            else -> {
                android.util.Log.d("TripDetailsScreen", "Unknown element type, returning empty list")
                emptyList()
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("TripDetailsScreen", "Exception in passenger parsing: ${e.message}")
        emptyList()
    }
    
    android.util.Log.d("TripDetailsScreen", "Final parsed passengers: $passengersList")
    android.util.Log.d("TripDetailsScreen", "Passenger count: ${passengersList.size}")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Card(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Trip Info
                Text("Trip #${trip.id}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                trip.vehicle?.let { vehicle ->
                    Text("Vehicle: ${vehicle.plateNumber}", style = MaterialTheme.typography.bodyMedium)
                }
                Text("Destination: ${trip.destination ?: "N/A"}", style = MaterialTheme.typography.bodyMedium)
                Text("Purpose: ${trip.purpose ?: "-"}", style = MaterialTheme.typography.bodyMedium)
                Text("Date/Time: $formattedTravelDate, $formattedTravelTime", style = MaterialTheme.typography.bodyMedium)
                Text("Total Distance Travelled: ${String.format("%.2f", totalDistanceTravelled)} km", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Divider(Modifier.padding(vertical = 12.dp))

                // Passengers
                Text("Authorized Passenger(s): ${passengersList.size}", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.Start
                ) {
                    passengersList.forEach { passenger ->
                        AssistChip(
                            onClick = {},
                            label = { Text(passenger, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Divider()

                // Itinerary
                if (itinerary.isNotEmpty()) {
                    Text("Itinerary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        Text("Odometer Start", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("Odometer End", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("Time (Dep)", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("Departure", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("Time (Arr)", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("Arrival", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    }
                    Column {
                        itinerary.forEach { leg ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(leg.odometerStart.toString(), modifier = Modifier.weight(1f))
                                Text((leg.odometerEnd ?: leg.odometerStart).toString(), modifier = Modifier.weight(1f))
                                Text(leg.timeDepartureDisplay ?: "", modifier = Modifier.weight(1f))
                                Text(leg.departure, modifier = Modifier.weight(1f))
                                Text(leg.timeArrivalDisplay ?: "", modifier = Modifier.weight(1f))
                                Text(leg.arrival ?: "", modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Action Buttons
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val hasOpenLeg = itinerary.isNotEmpty() && itinerary.last().odometerEnd == null
                    Button(
                        onClick = { showDepartureDialog = true },
                        enabled = (!tripStarted || canArrive) && actionState !is TripActionState.Loading,
                        modifier = Modifier.weight(1f).defaultMinSize(minWidth = 110.dp)
                    ) { Text("Departure", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { showArrivalDialog = true },
                        enabled = hasOpenLeg,
                        modifier = Modifier.weight(1f).defaultMinSize(minWidth = 110.dp)
                    ) { Text("Arrival", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Spacer(modifier = Modifier.width(8.dp))
                    val canCompleteTrip = itinerary.isNotEmpty() && itinerary.last().odometerEnd != null && actionState !is TripActionState.Loading
                    OutlinedButton(
                        onClick = { showReturnDialog = true },
                        enabled = canCompleteTrip,
                        modifier = Modifier.weight(1f).defaultMinSize(minWidth = 110.dp)
                    ) { Text("Complete Trip", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }

                // Status/Loading/Error
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
                    // Let the success message be visible briefly; reset is done after dialogs close
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
                        label = { Text("Departure Location") },
                        placeholder = { Text("Isatu Miagao Campus") }
                    )
                    if (passengersList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Passengers (confirm)", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Column {
                            passengersList.forEach { name ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = confirmedPassengerSet.contains(name),
                                        onCheckedChange = { checked ->
                                            confirmedPassengerSet = if (checked) confirmedPassengerSet + name else confirmedPassengerSet - name
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(name, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val odo = currentLegOdometer.toDoubleOrNull()
                    val fuel = currentLegFuelBalance.toDoubleOrNull()
                    val dep = currentLegDestination
                    if (odo != null && fuel != null) {
                        // Unified leg departure for single trip with passenger list
                        val passengersConfirmed = if (confirmedPassengerSet.isNotEmpty()) confirmedPassengerSet.toList() else passengersList
                        val depTimeDisplay = LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"))
                        val depLocFinal = if (dep.isNotBlank()) dep else "Isatu Miagao Campus"
                        tripDetailsViewModel.logLegDeparture(
                            tripId = trip.id,
                            legId = trip.id,
                            odometerStart = odo,
                            fuelStart = fuel,
                            passengersConfirmed = passengersConfirmed,
                            departureTime = depTimeDisplay,
                            departureLocation = depLocFinal,
                            manifestOverrideReason = null
                        )
                        lastFuelBalanceStart = fuel
                        tripStarted = true
                        canArrive = true
                        val timeForBackend = LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                        val timeForDisplay = LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"))
                        
                        // Debug logging
                        android.util.Log.d("TripDetailsScreen", "=== DEPARTURE DEBUG ===")
                        android.util.Log.d("TripDetailsScreen", "Adding departure leg with odometer: $odo")
                        android.util.Log.d("TripDetailsScreen", "Time for backend: $timeForBackend")
                        android.util.Log.d("TripDetailsScreen", "Departure location: ${if (dep.isNotBlank()) dep else "Isatu Miagao Campus"}")
                        
                        tripDetailsViewModel.addDepartureLeg(odo, timeForBackend, depLocFinal, timeForDisplay)
                        // Note: Single-trip backend departure does not require passengers_confirmed; we store selection locally only
                        
                        // Log the itinerary state after adding
                        android.util.Log.d("TripDetailsScreen", "Itinerary after adding departure: ${tripDetailsViewModel.itinerary.value}")
                        showDepartureDialog = false
                        currentLegOdometer = ""
                        currentLegFuelBalance = ""
                        currentLegDestination = "Isatu Miagao Campus"
                        confirmedPassengerSet = emptySet()
                    } else {
                        // Basic feedback
                        android.widget.Toast.makeText(context, "Enter valid odometer and fuel.", android.widget.Toast.LENGTH_SHORT).show()
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
        // Auto-fill arrival location from trip destination
        val arrivalLocation = trip.destination ?: "N/A"
        AlertDialog(
            onDismissRequest = { showArrivalDialog = false },
            title = { Text("Arrival Details") },
            text = {
                Column {
                    OutlinedTextField(
                        value = arrivalLocation,
                        onValueChange = { }, // Read-only
                        label = { Text("Arrival Location") },
                        readOnly = true,
                        enabled = false
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = currentArrivalOdometer,
                        onValueChange = { currentArrivalOdometer = it },
                        label = { Text("Odometer Reading at Arrival") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Arriving at: $arrivalLocation",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    // Show warning if odometer hasn't increased
                    val lastDepartureOdometer = itinerary.lastOrNull()?.odometerStart
                    if (lastDepartureOdometer != null && currentArrivalOdometer.isNotBlank()) {
                        val arrivalOdometer = currentArrivalOdometer.toDoubleOrNull()
                        if (arrivalOdometer != null) {
                            if (arrivalOdometer <= lastDepartureOdometer) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "⚠️ Warning: Arrival odometer should be higher than departure odometer (${lastDepartureOdometer})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                val distance = arrivalOdometer - lastDepartureOdometer
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "✅ Distance: ${String.format("%.2f", distance)} km",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    if (passengersList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Passengers (dropped)", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Column {
                            passengersList.forEach { name ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = droppedPassengerSet.contains(name),
                                        onCheckedChange = { checked ->
                                            droppedPassengerSet = if (checked) droppedPassengerSet + name else droppedPassengerSet - name
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(name, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val odometerEnd = currentArrivalOdometer.toDoubleOrNull()
                    val lastDepartureOdometer = itinerary.lastOrNull()?.odometerStart
                    
                    if (arrivalLocation.isNotBlank() && odometerEnd != null) {
                        // Check if arrival odometer is higher than departure
                        if (lastDepartureOdometer != null && odometerEnd <= lastDepartureOdometer) {
                            android.widget.Toast.makeText(
                                context, 
                                "Arrival odometer (${odometerEnd}) must be higher than departure odometer (${lastDepartureOdometer})", 
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                            return@TextButton
                        }
                        
                        val timeForBackend = LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                        val timeForDisplay = LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"))
                        tripDetailsViewModel.addArrivalToLastLeg(odometerEnd, timeForBackend, arrivalLocation, timeForDisplay)
                        // Unified leg arrival for single trip with passenger list
                        val passengersDropped = if (droppedPassengerSet.isNotEmpty()) droppedPassengerSet.toList() else passengersList
                        val fuelEndForNow = lastFuelBalanceStart ?: 0.0
                        tripDetailsViewModel.logLegArrival(
                            tripId = trip.id,
                            legId = trip.id,
                            odometerEnd = odometerEnd,
                            fuelUsed = null,
                            fuelEnd = fuelEndForNow,
                            passengersDropped = passengersDropped,
                            arrivalTime = timeForDisplay,
                            arrivalLocation = arrivalLocation,
                            fuelPurchased = null,
                            notes = null
                        )
                        showArrivalDialog = false
                        showNextStopPrompt = true
                        canArrive = false
                        currentArrivalOdometer = "" // Clear the input
                        droppedPassengerSet = emptySet()
                    } else {
                        val msg = when {
                            arrivalLocation.isBlank() -> "No destination set for this trip."
                            odometerEnd == null -> "Enter valid odometer reading at arrival."
                            else -> "Invalid arrival details."
                        }
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Confirm Arrival") }
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
        // Calculate fuel used automatically from the difference
        val fuelPurchased = fuelPurchasedInput.toDoubleOrNull() ?: 0.0 // Default to 0 if no fuel purchased
        val fuelBalanceEnd = fuelBalanceEndInput.toDoubleOrNull() ?: 0.0
        val initialBalance = lastFuelBalanceStart ?: 0.0
        val calculatedFuelUsed = initialBalance + fuelPurchased - fuelBalanceEnd
        AlertDialog(
            onDismissRequest = { showReturnDialog = false },
            title = { Text("Trip Return Details") },
            text = {
                Column {
                    OutlinedTextField(
                        value = fuelPurchasedInput,
                        onValueChange = { fuelPurchasedInput = it },
                        label = { Text("Fuel Purchased (L)") },
                        placeholder = { Text("Enter 0 or leave empty if no fuel purchased") }
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
                        label = { Text("Final Odometer Reading") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Show calculated fuel used for reference
                    if (fuelBalanceEndInput.isNotBlank()) {
                        val fuelUsedText = if (fuelPurchasedInput.isNotBlank()) {
                            "Fuel Used: ${String.format("%.2f", calculatedFuelUsed)} L"
                        } else {
                            "Fuel Used: ${String.format("%.2f", calculatedFuelUsed)} L (No fuel purchased)"
                        }
                        Text(
                            text = fuelUsedText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
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
                    val fuelPurchased = fuelPurchasedInput.toDoubleOrNull() ?: 0.0 // Default to 0 if empty
                    val fuelBalanceEnd = fuelBalanceEndInput.toDoubleOrNull()
                    val odometerArrival = odometerArrivalInput.toDoubleOrNull()
                    if (
                        fuelBalanceEnd != null &&
                        odometerArrival != null &&
                        lastFuelBalanceStart != null
                    ) {
                        val passengerDetailsToSend = if (passengersList.isNotEmpty()) {
                            passengersList.map { name ->
                                com.example.drivebroom.network.                                PassengerDetail(
                                    name = name,
                                    destination = trip.destination ?: "N/A",
                                    signature = ""
                                )
                            }
                        } else {
                            listOf(
                                com.example.drivebroom.network.PassengerDetail(
                                    name = trip.passenger_email ?: "",
                                    destination = trip.destination ?: "N/A",
                                    signature = ""
                                )
                            )
                        }
                        // Create single leg with all three odometer readings
                        val firstLeg = itinerary.firstOrNull()
                        val arrivalLeg = itinerary.firstOrNull { leg -> leg.odometerEnd != null }
                        
                        // Debug logging
                        android.util.Log.d("TripDetailsScreen", "=== RETURN DEBUG ===")
                        android.util.Log.d("TripDetailsScreen", "Itinerary size: ${itinerary.size}")
                        android.util.Log.d("TripDetailsScreen", "First leg: $firstLeg")
                        android.util.Log.d("TripDetailsScreen", "Arrival leg: $arrivalLeg")
                        android.util.Log.d("TripDetailsScreen", "Odometer arrival input: $odometerArrival")
                        if (firstLeg != null) {
                            android.util.Log.d("TripDetailsScreen", "First leg odometerStart: ${firstLeg.odometerStart}")
                        }
                        
                        val finalItinerary = if (firstLeg != null && odometerArrival != null) {
                            // Single leg with start, arrival, and return odometer readings
                            val legDto = com.example.drivebroom.network.ItineraryLegDto(
                                odometer_start = firstLeg.odometerStart,     // Start odometer (departure from campus)
                                odometer = odometerArrival!!,               // Return odometer (back to campus)
                                odometer_arrival = arrivalLeg?.odometerEnd,  // Arrival odometer (at destination) - NEW FIELD
                                time_departure = firstLeg.timeDeparture,
                                departure = firstLeg.departure,             // "Isatu Miagao Campus"
                                time_arrival = arrivalLeg?.timeArrival ?: LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")),
                                arrival = arrivalLeg?.arrival ?: "Isatu Miagao Campus"
                            )
                            android.util.Log.d("TripDetailsScreen", "Created leg DTO: $legDto")
                            listOf(legDto)
                        } else {
                            android.util.Log.d("TripDetailsScreen", "Creating empty itinerary - firstLeg: $firstLeg, odometerArrival: $odometerArrival")
                            // Fallback if no departure leg recorded
                            emptyList()
                        }
                        tripDetailsViewModel.logReturn(
                            trip.id,
                            lastFuelBalanceStart!!,
                            fuelPurchased,
                            fuelBalanceEnd,
                            passengerDetailsToSend,
                            finalItinerary
                        )
                        showReturnDialog = false
                        fuelPurchasedInput = ""
                        fuelBalanceEndInput = ""
                        odometerArrivalInput = ""
                    } else {
                        val msg = when {
                            fuelBalanceEnd == null || odometerArrival == null || lastFuelBalanceStart == null -> "Enter fuel balance end and final odometer reading to complete return."
                            else -> "Invalid return details."
                        }
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Submit") }
            },
            dismissButton = {
                TextButton(onClick = { showReturnDialog = false }) { Text("Cancel") }
            }
        )
    }
} 