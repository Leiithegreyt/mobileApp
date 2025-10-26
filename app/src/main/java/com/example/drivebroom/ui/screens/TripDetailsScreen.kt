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
    onNavigateToTripLogs: () -> Unit = {},
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
    val singleTripStatus by tripDetailsViewModel.singleTripStatus.collectAsState()

    // Use latestTripDetails if available, else fallback to initial tripDetails
    val trip = latestTripDetails ?: tripDetails

    // Check if this is a shared trip and route accordingly
    if (isSharedTrip) {
        SharedTripDetailsScreen(
            tripDetails = trip,
            sharedTripLegs = sharedTripLegs,
            onBack = onBack,
            onStartTrip = {
                // Set current leg to the first actionable leg before navigating
                val defaultIndex = sharedTripLegs.indexOfFirst { it.status in listOf("pending", "approved", "in_progress") }
                val targetIndex = if (defaultIndex >= 0) defaultIndex else 0
                tripDetailsViewModel.setCurrentLegIndex(targetIndex)
                onSharedTripClick(trip)
            },
            onLegClick = { legId ->
                // Set the current leg index based on clicked leg, then navigate
                val legIndex = sharedTripLegs.indexOfFirst { it.leg_id == legId }
                if (legIndex >= 0) {
                    tripDetailsViewModel.setCurrentLegIndex(legIndex)
                }
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
    var showReturnDialog by remember { mutableStateOf(false) }
    var showReturnStartDialog by remember { mutableStateOf(false) }
    var showReturnArrivalDialog by remember { mutableStateOf(false) }
    var fuelPurchasedInput by remember { mutableStateOf("") }
    var fuelBalanceEndInput by remember { mutableStateOf("") }
    var odometerArrivalInput by remember { mutableStateOf("") }
    var currentArrivalOdometer by remember { mutableStateOf("") }
    var lastFuelBalanceStart by remember { mutableStateOf<Double?>(null) }
    
    // Store fuel purchased and fuel end values for trip completion
    var storedFuelPurchased by remember { mutableStateOf(0.0) }
    var storedFuelBalanceEnd by remember { mutableStateOf(0.0) }
    var tripStarted by remember { mutableStateOf(false) }
    var canArrive by remember { mutableStateOf(false) }
    var canReturn by remember { mutableStateOf(false) }
    var departurePosted by remember { mutableStateOf(false) }
    var returnStartPosted by remember { mutableStateOf(false) }
    var lastOdometerArrival by remember { mutableStateOf<Double?>(null) }

    // Passenger selections for single trip (visual parity with shared trip)
    var confirmedPassengerSet by remember { mutableStateOf<Set<String>>(emptySet()) }
    var droppedPassengerSet by remember { mutableStateOf<Set<String>>(emptySet()) }
    var notes by remember { mutableStateOf("") }

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
        notes = ""
        // DON'T reset lastFuelBalanceStart - it should persist across trip reloads
        // lastFuelBalanceStart = null
        storedFuelPurchased = 0.0 // Clear stored fuel purchased
        storedFuelBalanceEnd = 0.0 // Clear stored fuel balance end
        tripStarted = false
        canArrive = false
        canReturn = false
        departurePosted = false
        returnStartPosted = false // Reset return start flag
        lastOdometerArrival = null
        showDepartureDialog = false
        showArrivalDialog = false
        showReturnDialog = false
        
        // If trip is already returning, mark the flag as posted to prevent duplicate calls
        if (singleTripStatus == "returning") {
            returnStartPosted = true
            android.util.Log.d("TripDetailsScreen", "Trip is already returning - marking flag as posted")
        }
        
        // Try to retrieve fuel start from itinerary if available
        if (lastFuelBalanceStart == null && itinerary.isNotEmpty()) {
            val firstLeg = itinerary.firstOrNull()
            if (firstLeg?.fuelStart != null) {
                lastFuelBalanceStart = firstLeg.fuelStart
                android.util.Log.d("TripDetailsScreen", "Retrieved fuel start from itinerary: $lastFuelBalanceStart")
            }
        }
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
                
                // Check if trip has operational data
                val hasOperationalData = lastFuelBalanceStart != null || itinerary.any { it.odometerStart != null } || totalDistanceTravelled > 0
                
                if (hasOperationalData) {
                    Text("Total Distance Travelled: ${String.format("%.2f", totalDistanceTravelled)} km", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                
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
                if (itinerary.isNotEmpty() && hasOperationalData) {
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
                } else if (singleTripStatus == "completed" && !hasOperationalData) {
                    // Show message for completed trips without operational data
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "No Operational Data Available",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "This trip was completed but no operational data was recorded. The driver may not have executed the trip properly.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Action Buttons - Status-based like shared trips
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Debug logging for status
                    android.util.Log.d("TripDetailsScreen", "=== STATUS DEBUG ===")
                    android.util.Log.d("TripDetailsScreen", "Current singleTripStatus: '$singleTripStatus'")
                    android.util.Log.d("TripDetailsScreen", "Trip ID: ${trip.id}")
                    android.util.Log.d("TripDetailsScreen", "Trip destination: ${trip.destination}")
                    android.util.Log.d("TripDetailsScreen", "Itinerary count: ${itinerary.size}")
                    android.util.Log.d("TripDetailsScreen", "Has arrival data: ${itinerary.any { it.odometerEnd != null }}")
                    android.util.Log.d("TripDetailsScreen", "lastFuelBalanceStart: $lastFuelBalanceStart")
                    android.util.Log.d("TripDetailsScreen", "Action state: $actionState")
                    android.util.Log.d("TripDetailsScreen", "Trip status from backend: ${trip.status}")
                    
                    // Status-based button logic for single trips (EXACT copy from shared trip last leg flow)
                    // Flow: Depart → Arrive → Return to Base → Arrived at Base → Complete
                    when (singleTripStatus) {
                        "pending", "approved" -> {
                            // Step 1: Depart
                            Button(
                                onClick = { 
                                    android.util.Log.d("TripDetailsScreen", "🚀 DEPARTURE BUTTON CLICKED!")
                                    android.util.Log.d("TripDetailsScreen", "Trip ID: ${trip.id}")
                                    android.util.Log.d("TripDetailsScreen", "Trip Status: ${trip.status}")
                                    android.util.Log.d("TripDetailsScreen", "Action State: $actionState")
                                    showDepartureDialog = true 
                                },
                                enabled = actionState !is TripActionState.Loading,
                                modifier = Modifier.weight(1f).defaultMinSize(minWidth = 110.dp)
                            ) { 
                                Text("Depart", maxLines = 1, overflow = TextOverflow.Ellipsis) 
                            }
                        }
                        "on_route", "in_progress" -> {
                            // Step 2: Arrive
                            OutlinedButton(
                                onClick = { 
                                    android.util.Log.d("TripDetailsScreen", "🚀 ARRIVAL BUTTON CLICKED!")
                                    android.util.Log.d("TripDetailsScreen", "Trip ID: ${trip.id}")
                                    android.util.Log.d("TripDetailsScreen", "Trip Status: ${trip.status}")
                                    android.util.Log.d("TripDetailsScreen", "Departure Posted: $departurePosted")
                                    showArrivalDialog = true 
                                },
                                enabled = actionState !is TripActionState.Loading && departurePosted,
                                modifier = Modifier.weight(1f).defaultMinSize(minWidth = 110.dp)
                            ) { 
                                Text("Arrived", maxLines = 1, overflow = TextOverflow.Ellipsis) 
                            }
                        }
                        "arrived" -> {
                            // Step 3: Return to Base (EXACT copy from shared trip last leg)
                            OutlinedButton(
                                onClick = { showReturnStartDialog = true },
                                enabled = actionState !is TripActionState.Loading,
                                modifier = Modifier.weight(1f).defaultMinSize(minWidth = 110.dp)
                            ) { 
                                Text("Return to Base", maxLines = 1, overflow = TextOverflow.Ellipsis) 
                            }
                        }
                        "returning" -> {
                            // Step 4: Arrived at Base
                            OutlinedButton(
                                onClick = { showReturnArrivalDialog = true },
                                enabled = actionState !is TripActionState.Loading,
                                modifier = Modifier.weight(1f).defaultMinSize(minWidth = 110.dp)
                            ) { 
                                Text("Arrived at Base", maxLines = 1, overflow = TextOverflow.Ellipsis) 
                            }
                        }
                        "completed" -> {
                            // Check if trip has been executed (has fuel data) or just completed without execution
                            val hasExecutedTrip = lastFuelBalanceStart != null || itinerary.any { it.odometerStart != null }
                            val hasArrivalData = itinerary.any { it.odometerEnd != null }
                            
                            android.util.Log.d("TripDetailsScreen", "=== COMPLETED STATUS DEBUG ===")
                            android.util.Log.d("TripDetailsScreen", "hasExecutedTrip: $hasExecutedTrip")
                            android.util.Log.d("TripDetailsScreen", "hasArrivalData: $hasArrivalData")
                            android.util.Log.d("TripDetailsScreen", "hasOperationalData: $hasOperationalData")
                            android.util.Log.d("TripDetailsScreen", "itinerary: $itinerary")
                            
                            if (!hasOperationalData) {
                                // Trip is completed but never executed - show Start Trip button
                                Button(
                                    onClick = { showDepartureDialog = true },
                                    enabled = actionState !is TripActionState.Loading,
                                    modifier = Modifier.weight(1f).defaultMinSize(minWidth = 110.dp)
                                ) { 
                                    Text("Start Trip", maxLines = 1, overflow = TextOverflow.Ellipsis) 
                                }
                            } else if (hasExecutedTrip && !hasArrivalData) {
                                // Trip was started but not arrived - show Return to Base button
                                OutlinedButton(
                                    onClick = { showReturnStartDialog = true },
                                    enabled = actionState !is TripActionState.Loading,
                                    modifier = Modifier.weight(1f).defaultMinSize(minWidth = 110.dp)
                                ) { 
                                    Text("Return to Base", maxLines = 1, overflow = TextOverflow.Ellipsis) 
                                }
                            } else if (hasExecutedTrip && hasArrivalData) {
                                // Trip was executed and arrived - show Complete Trip button
                                OutlinedButton(
                                    onClick = { showReturnDialog = true },
                                    enabled = actionState !is TripActionState.Loading,
                                    modifier = Modifier.weight(1f).defaultMinSize(minWidth = 110.dp)
                                ) { 
                                    Text("Complete Trip", maxLines = 1, overflow = TextOverflow.Ellipsis) 
                                }
                            } else {
                                // Fallback - show Start Trip button
                                Button(
                                    onClick = { showDepartureDialog = true },
                                    enabled = actionState !is TripActionState.Loading,
                                    modifier = Modifier.weight(1f).defaultMinSize(minWidth = 110.dp)
                                ) { 
                                    Text("Start Trip", maxLines = 1, overflow = TextOverflow.Ellipsis) 
                                }
                            }
                        }
                        else -> {
                            // Fallback for unknown status - show Depart button
                            Button(
                                onClick = { showDepartureDialog = true },
                                enabled = actionState !is TripActionState.Loading,
                                modifier = Modifier.weight(1f).defaultMinSize(minWidth = 110.dp)
                            ) { 
                                Text("Start Trip", maxLines = 1, overflow = TextOverflow.Ellipsis) 
                            }
                        }
                    }
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
                        label = { Text("Odometer Start") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = currentLegFuelBalance,
                        onValueChange = { currentLegFuelBalance = it },
                        label = { Text("Fuel Start (L)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = currentLegDestination,
                        onValueChange = { currentLegDestination = it },
                        label = { Text("Departure Location (Auto-filled)") },
                        placeholder = { Text("ISATU Miagao Campus") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        enabled = false
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a")),
                        onValueChange = { },
                        label = { Text("Departure Time (hh:mm a)") },
                        readOnly = true,
                        enabled = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (passengersList.isNotEmpty()) {
                        Text(
                            text = "Passengers (${passengersList.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                            passengersList.forEach { name ->
                                Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
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
            },
            confirmButton = {
                TextButton(onClick = {
                    val odo = currentLegOdometer.toDoubleOrNull()
                    val fuel = currentLegFuelBalance.toDoubleOrNull()
                    val dep = currentLegDestination
                    if (odo != null && fuel != null) {
                        android.util.Log.i("TripDetailsScreen", "UI_INPUT Departure: odometer_start=$odo, fuel_start=$fuel, departure_location='${if (dep.isNotBlank()) dep else "Isatu Miagao Campus"}', time=${LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))}")
                        // Use regular departure for single trips (not leg departure)
                        tripDetailsViewModel.logDeparture(
                            tripId = trip.id,
                            odometerStart = odo,
                            fuelBalanceStart = fuel,
                            passengersConfirmed = passengersList
                        )
                        // Store fuel start for arrival calculation
                        lastFuelBalanceStart = fuel
                        tripStarted = true
                        departurePosted = true
                        canArrive = true
                        
                        android.util.Log.d("TripDetailsScreen", "=== DEPARTURE CONFIRMATION DEBUG ===")
                        android.util.Log.d("TripDetailsScreen", "Stored lastFuelBalanceStart: $lastFuelBalanceStart")
                        android.util.Log.d("TripDetailsScreen", "Fuel value: $fuel")
                        val timeForBackend = LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                        val timeForDisplay = LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"))
                        
                        // Debug logging
                        android.util.Log.d("TripDetailsScreen", "=== DEPARTURE DEBUG ===")
                        android.util.Log.d("TripDetailsScreen", "Adding departure leg with odometer: $odo")
                        android.util.Log.d("TripDetailsScreen", "Time for backend: $timeForBackend")
                        android.util.Log.d("TripDetailsScreen", "Departure location: ${if (dep.isNotBlank()) dep else "Isatu Miagao Campus"}")
                        
                        val depLocFinal = if (dep.isNotBlank()) dep else "Isatu Miagao Campus"
                        tripDetailsViewModel.addDepartureLeg(odo, timeForBackend, depLocFinal, timeForDisplay)
                        // Note: Single-trip backend departure does not require passengers_confirmed; we store selection locally only
                        
                        // Store fuel start in itinerary for later retrieval
                        tripDetailsViewModel.addFuelStartToLastLeg(fuel)
                        
                        // Log the itinerary state after adding
                        android.util.Log.d("TripDetailsScreen", "Itinerary after adding departure: ${tripDetailsViewModel.itinerary.value}")
                        showDepartureDialog = false
                        currentLegOdometer = ""
                        currentLegFuelBalance = ""
                        currentLegDestination = "Isatu Miagao Campus"
                        confirmedPassengerSet = emptySet()
                        
                        // Do not refresh here; the backend state is reflected in lists/navigation
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
                        value = currentArrivalOdometer,
                        onValueChange = { 
                            currentArrivalOdometer = it
                            android.util.Log.d("TripDetailsScreen", "=== ODOMETER INPUT CHANGED ===")
                            android.util.Log.d("TripDetailsScreen", "currentArrivalOdometer: '$currentArrivalOdometer'")
                            android.util.Log.d("TripDetailsScreen", "currentArrivalOdometer length: ${currentArrivalOdometer.length}")
                            android.util.Log.d("TripDetailsScreen", "currentArrivalOdometer toDoubleOrNull: ${currentArrivalOdometer.toDoubleOrNull()}")
                        },
                        label = { Text("Odometer End - Enter your actual odometer reading") },
                        placeholder = { Text("e.g., 12510") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Calculate fuel used reactively (FIXED - proper fuel start retrieval)
                    val fuelUsed = remember(fuelBalanceEndInput, fuelPurchasedInput, lastFuelBalanceStart) {
                        // Get fuel start from departure (stored in lastFuelBalanceStart)
                        val fuelStart = lastFuelBalanceStart ?: 0.0
                        val fuelEnd = fuelBalanceEndInput.toDoubleOrNull() ?: 0.0
                        val fuelPurchased = fuelPurchasedInput.toDoubleOrNull() ?: 0.0
                        val calculatedFuelUsed = fuelStart + fuelPurchased - fuelEnd
                        
                        android.util.Log.d("TripDetailsScreen", "=== FUEL CALCULATION DEBUG (FIXED) ===")
                        android.util.Log.d("TripDetailsScreen", "lastFuelBalanceStart: $lastFuelBalanceStart")
                        android.util.Log.d("TripDetailsScreen", "fuelStart: $fuelStart")
                        android.util.Log.d("TripDetailsScreen", "fuelEnd: $fuelEnd")
                        android.util.Log.d("TripDetailsScreen", "fuelPurchased: $fuelPurchased")
                        android.util.Log.d("TripDetailsScreen", "calculatedFuelUsed: $calculatedFuelUsed")
                        
                        // Ensure fuel used is not negative
                        val finalFuelUsed = maxOf(0.0, calculatedFuelUsed)
                        android.util.Log.d("TripDetailsScreen", "finalFuelUsed: $finalFuelUsed")
                        
                        // Show warning if fuel start is 0
                        if (fuelStart == 0.0) {
                            android.util.Log.w("TripDetailsScreen", "⚠️ Fuel start is 0 - trip may not have been executed properly")
                            android.util.Log.w("TripDetailsScreen", "⚠️ User needs to complete departure first to set fuel start value")
                        }
                        
                        String.format("%.2f", finalFuelUsed)
                    }
                    
                    OutlinedTextField(
                        value = fuelBalanceEndInput,
                        onValueChange = { 
                            fuelBalanceEndInput = it
                            android.util.Log.d("TripDetailsScreen", "=== FUEL INPUT CHANGED ===")
                            android.util.Log.d("TripDetailsScreen", "fuelBalanceEndInput: '$fuelBalanceEndInput'")
                            android.util.Log.d("TripDetailsScreen", "fuelBalanceEndInput length: ${fuelBalanceEndInput.length}")
                            android.util.Log.d("TripDetailsScreen", "fuelBalanceEndInput isEmpty: ${fuelBalanceEndInput.isEmpty()}")
                            android.util.Log.d("TripDetailsScreen", "fuelBalanceEndInput toDoubleOrNull: ${fuelBalanceEndInput.toDoubleOrNull()}")
                        },
                        label = { Text("Fuel Balance End (L) - Enter your actual fuel reading") },
                        placeholder = { Text("e.g., 54") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = fuelPurchasedInput,
                        onValueChange = { fuelPurchasedInput = it },
                        label = { Text("Fuel Purchased (L)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = fuelUsed,
                        onValueChange = { },
                        label = { Text("Fuel Used (L) - Auto-calculated") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        enabled = false
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = arrivalLocation,
                        onValueChange = { },
                        label = { Text("Arrival Location") },
                        readOnly = true,
                        enabled = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a")),
                        onValueChange = { },
                        label = { Text("Arrival Time (hh:mm a) - Auto-filled") },
                        readOnly = true,
                        enabled = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (Optional)") },
                        placeholder = { Text("Enter any additional notes or comments") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (passengersList.isNotEmpty()) {
                        Text(
                            text = "Passengers (${passengersList.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                            passengersList.forEach { name ->
                                Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
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
                        
                        // Calculate fuel used
                        android.util.Log.d("TripDetailsScreen", "=== BEFORE FUEL CALCULATION ===")
                        android.util.Log.d("TripDetailsScreen", "fuelBalanceEndInput raw: '$fuelBalanceEndInput'")
                        android.util.Log.d("TripDetailsScreen", "fuelPurchasedInput raw: '$fuelPurchasedInput'")
                        android.util.Log.d("TripDetailsScreen", "lastFuelBalanceStart: $lastFuelBalanceStart")
                        
                        val fuelEndForNow = fuelBalanceEndInput.toDoubleOrNull() ?: 0.0
                        val fuelPurchasedValue = fuelPurchasedInput.toDoubleOrNull() ?: 0.0
                        val fuelUsed = (lastFuelBalanceStart ?: 0.0) + fuelPurchasedValue - fuelEndForNow
                        
                        android.util.Log.d("TripDetailsScreen", "=== AFTER FUEL CALCULATION ===")
                        android.util.Log.d("TripDetailsScreen", "fuelEndForNow: $fuelEndForNow")
                        android.util.Log.d("TripDetailsScreen", "fuelPurchasedValue: $fuelPurchasedValue")
                        android.util.Log.d("TripDetailsScreen", "fuelUsed: $fuelUsed")
                        
                        // Call the arrival API for single trips
                        android.util.Log.i("TripDetailsScreen", "UI_INPUT Arrival: odometer_end=$odometerEnd, fuel_end=$fuelEndForNow, arrival_location='${arrivalLocation}', fuel_purchased=$fuelPurchasedValue, fuel_used=${maxOf(0.0, fuelUsed)}")
                        tripDetailsViewModel.logArrival(
                            tripId = trip.id,
                            odometerEnd = odometerEnd,
                            fuelEnd = fuelEndForNow,
                            arrivalLocation = arrivalLocation,
                            fuelUsed = maxOf(0.0, fuelUsed),
                            notes = "Arrived at destination"
                        )
                        
                        // Also update local itinerary for display
                        tripDetailsViewModel.addArrivalToLastLeg(odometerEnd, timeForBackend, arrivalLocation, timeForDisplay)
                        
                        // Store values for trip completion
                        storedFuelPurchased = fuelPurchasedValue
                        storedFuelBalanceEnd = fuelEndForNow
                        
                        android.util.Log.d("TripDetailsScreen", "=== STORING FUEL VALUES ===")
                        android.util.Log.d("TripDetailsScreen", "fuelEndForNow: $fuelEndForNow")
                        android.util.Log.d("TripDetailsScreen", "storedFuelBalanceEnd set to: $storedFuelBalanceEnd")
                        
                        // Debug logging
                        android.util.Log.d("TripDetailsScreen", "=== ARRIVAL CONFIRMATION DEBUG ===")
                        android.util.Log.d("TripDetailsScreen", "fuelBalanceEndInput: '$fuelBalanceEndInput'")
                        android.util.Log.d("TripDetailsScreen", "fuelPurchasedInput: '$fuelPurchasedInput'")
                        android.util.Log.d("TripDetailsScreen", "fuelEndForNow: $fuelEndForNow")
                        android.util.Log.d("TripDetailsScreen", "fuelPurchasedValue: $fuelPurchasedValue")
                        android.util.Log.d("TripDetailsScreen", "lastFuelBalanceStart: $lastFuelBalanceStart")
                        android.util.Log.d("TripDetailsScreen", "storedFuelPurchased: $storedFuelPurchased")
                        android.util.Log.d("TripDetailsScreen", "storedFuelBalanceEnd: $storedFuelBalanceEnd")
                        android.util.Log.d("TripDetailsScreen", "odometerEnd: $odometerEnd")
                        android.util.Log.d("TripDetailsScreen", "arrivalLocation: '$arrivalLocation'")
                        android.util.Log.d("TripDetailsScreen", "=== ARRIVAL CONFIRMATION DEBUG ===")
                        android.util.Log.d("TripDetailsScreen", "fuelBalanceEndInput: '$fuelBalanceEndInput'")
                        android.util.Log.d("TripDetailsScreen", "fuelPurchasedInput: '$fuelPurchasedInput'")
                        android.util.Log.d("TripDetailsScreen", "fuelEndForNow: $fuelEndForNow")
                        android.util.Log.d("TripDetailsScreen", "fuelPurchasedValue: $fuelPurchasedValue")
                        android.util.Log.d("TripDetailsScreen", "lastFuelBalanceStart: $lastFuelBalanceStart")
                        
                        showArrivalDialog = false
                        canArrive = false
                        canReturn = true // Enable the Complete Trip button
                        currentArrivalOdometer = "" // Clear the input
                        
                        android.util.Log.d("TripDetailsScreen", "=== CLEARING INPUTS AFTER ARRIVAL ===")
                        android.util.Log.d("TripDetailsScreen", "fuelBalanceEndInput before clear: '$fuelBalanceEndInput'")
                        fuelBalanceEndInput = "" // Clear the fuel balance end input
                        android.util.Log.d("TripDetailsScreen", "fuelBalanceEndInput after clear: '$fuelBalanceEndInput'")
                        
                        fuelPurchasedInput = "" // Clear the fuel purchased input
                        notes = "" // Clear the notes field
                        droppedPassengerSet = emptySet()
                        
                        // Do not refresh here; rely on local state and navigation
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


    // For return dialog, collect signatures for each passenger (now removed, just show details)

    if (showReturnDialog) {
        AlertDialog(
            onDismissRequest = { showReturnDialog = false },
            title = { Text("Complete Trip") },
            text = {
                Column {
                    Text(
                        text = "Trip completed successfully!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Trip Summary:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Passengers: ${passengersList.size}")
                    Text("Destination: ${trip.destination ?: "N/A"}")
                    Text("Total Distance: ${String.format("%.2f", totalDistanceTravelled)} km")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // No need to call complete - Return Arrive already completed the trip
                    // Backend is idempotent, but no-op avoids unnecessary 422 in logs
                    showReturnDialog = false
                    onNavigateToTripLogs()
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showReturnDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Return Start Dialog (for "Return to Base" button)
    if (showReturnStartDialog) {
        // Auto-fill with arrival values (copy from shared trip logic)
        LaunchedEffect(showReturnStartDialog) {
            val arrivalLeg = itinerary.firstOrNull { leg -> leg.odometerEnd != null }
            val arrivalOdometer = arrivalLeg?.odometerEnd?.toString() ?: ""
            val arrivalFuel = storedFuelBalanceEnd?.toString() ?: ""
            
            android.util.Log.d("TripDetailsScreen", "=== AUTO-FILL RETURN START ===")
            android.util.Log.d("TripDetailsScreen", "Arrival leg: $arrivalLeg")
            android.util.Log.d("TripDetailsScreen", "Arrival odometer: $arrivalOdometer")
            android.util.Log.d("TripDetailsScreen", "Arrival fuel: $arrivalFuel")
            
            if (arrivalOdometer.isNotEmpty()) {
                currentLegOdometer = arrivalOdometer
                android.util.Log.d("TripDetailsScreen", "Auto-filled odometer: $currentLegOdometer")
            }
            if (arrivalFuel.isNotEmpty()) {
                currentLegFuelBalance = arrivalFuel
                android.util.Log.d("TripDetailsScreen", "Auto-filled fuel: $currentLegFuelBalance")
            }
        }
        
        AlertDialog(
            onDismissRequest = { showReturnStartDialog = false },
            title = { Text("Return to Base") },
            text = {
                Column {
                    Text("Starting return journey to base.")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = currentLegOdometer,
                        onValueChange = { currentLegOdometer = it },
                        label = { Text("Odometer Start") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = currentLegFuelBalance,
                        onValueChange = { currentLegFuelBalance = it },
                        label = { Text("Fuel Level Start") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Auto-filled with arrival values: Odo=${itinerary.lastOrNull()?.odometerEnd ?: "N/A"}, Fuel=${storedFuelBalanceEnd ?: "N/A"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    android.util.Log.d("TripDetailsScreen", "=== START RETURN BUTTON CLICKED ===")
                    val odo = currentLegOdometer.toDoubleOrNull()
                    val fuel = currentLegFuelBalance.toDoubleOrNull()
                    android.util.Log.d("TripDetailsScreen", "Odometer: $odo, Fuel: $fuel")
                    
                    if (odo != null && fuel != null) {
                        if (returnStartPosted) {
                            android.util.Log.d("TripDetailsScreen", "Return start already posted - preventing duplicate call")
                            android.widget.Toast.makeText(context, "Return to base already started", android.widget.Toast.LENGTH_SHORT).show()
                            showReturnStartDialog = false
                            return@TextButton
                        }
                        
                        android.util.Log.d("TripDetailsScreen", "Calling startReturn...")
                        android.util.Log.i("TripDetailsScreen", "UI_INPUT ReturnStart: odometer_start=$odo, fuel_start=$fuel")
                        // Start return journey
                        tripDetailsViewModel.startReturn(trip.id, odo, fuel)
                        returnStartPosted = true
                        showReturnStartDialog = false
                        // Don't automatically show arrival dialog - let user click "Arrived at Base" button
                        
                        // Show success message
                        android.widget.Toast.makeText(context, "Return journey started successfully! Click 'Arrived at Base' when you reach base.", android.widget.Toast.LENGTH_LONG).show()
                        android.util.Log.d("TripDetailsScreen", "Return started - waiting for user to click 'Arrived at Base'")
                        
                        // Don't refresh immediately - the backend might not support return start
                        // The local status update should be sufficient
                        android.util.Log.d("TripDetailsScreen", "=== RETURN START COMPLETED ===")
                        android.util.Log.d("TripDetailsScreen", "Status after return start: '$singleTripStatus'")
                        android.util.Log.d("TripDetailsScreen", "Skipping refresh to preserve local status update")
                    } else {
                        android.util.Log.d("TripDetailsScreen", "Invalid input - showing toast")
                        android.widget.Toast.makeText(context, "Enter valid odometer and fuel.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Start Return") }
            },
            dismissButton = {
                TextButton(onClick = { showReturnStartDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Return Arrival Dialog (for "Arrived at Base" button)
    if (showReturnArrivalDialog) {
        // Auto-fill with return start values (copy from shared trip logic)
        LaunchedEffect(showReturnArrivalDialog) {
            // Auto-fill with the values from the return start dialog
            val returnStartOdometer = currentLegOdometer
            val returnStartFuel = currentLegFuelBalance
            
            android.util.Log.d("TripDetailsScreen", "=== AUTO-FILL RETURN ARRIVAL ===")
            android.util.Log.d("TripDetailsScreen", "Return start odometer: $returnStartOdometer")
            android.util.Log.d("TripDetailsScreen", "Return start fuel: $returnStartFuel")
            
            // Don't auto-fill - let user input their actual values
            android.util.Log.d("TripDetailsScreen", "=== RETURN ARRIVAL DIALOG ===")
            android.util.Log.d("TripDetailsScreen", "Calculated return start odometer: $returnStartOdometer")
            android.util.Log.d("TripDetailsScreen", "Calculated return start fuel: $returnStartFuel")
            android.util.Log.d("TripDetailsScreen", "User should input their actual values")
        }
        
        AlertDialog(
            onDismissRequest = { showReturnArrivalDialog = false },
            title = { Text("Arrived at Base") },
            text = {
                Column {
                    Text("You have arrived back at base.")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = currentArrivalOdometer,
                        onValueChange = { currentArrivalOdometer = it },
                        label = { Text("Final Odometer") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = fuelBalanceEndInput,
                        onValueChange = { fuelBalanceEndInput = it },
                        label = { Text("Final Fuel Level") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (Optional)") },
                        placeholder = { Text("Enter any additional notes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Auto-filled with return start values: Odo=${currentLegOdometer}, Fuel=${currentLegFuelBalance}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val odo = currentArrivalOdometer.toDoubleOrNull()
                    val fuel = fuelBalanceEndInput.toDoubleOrNull()
                    if (odo != null && fuel != null) {
                        // Use the user's entered return start fuel to compute fuel used (no purchases on return)
                        val returnStartFuel = currentLegFuelBalance.toDoubleOrNull() ?: 0.0
                        val fuelUsed = kotlin.math.max(0.0, returnStartFuel - fuel)

                        // Call the correct single-trip return-arrive endpoint with user's inputs
                        android.util.Log.i("TripDetailsScreen", "UI_INPUT ReturnArrive: odometer_end=$odo, fuel_end=$fuel, fuel_used=$fuelUsed, notes='${notes.ifBlank { null }}'")
                        tripDetailsViewModel.logReturnArrival(
                            tripId = trip.id,
                            odometerEnd = odo,
                            fuelEnd = fuel,
                            returnArrivalLocation = "ISATU Miagao Campus",
                            fuelUsed = fuelUsed,
                            notes = notes.ifBlank { null }
                        )

                        // Close and navigate
                        showReturnArrivalDialog = false
                        onNavigateToTripLogs()
                    } else {
                        android.widget.Toast.makeText(context, "Enter valid odometer and fuel.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Complete Trip") }
            },
            dismissButton = {
                TextButton(onClick = { showReturnArrivalDialog = false }) { Text("Cancel") }
            }
        )
    }
} 