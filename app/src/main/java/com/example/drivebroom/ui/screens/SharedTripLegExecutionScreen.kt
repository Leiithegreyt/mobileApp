package com.example.drivebroom.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.drivebroom.network.SharedTripLeg
import com.example.drivebroom.network.TripDetails
import com.example.drivebroom.viewmodel.TripDetailsViewModel
import com.example.drivebroom.viewmodel.TripActionState
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedTripLegExecutionScreen(
    tripDetails: TripDetails,
    currentLeg: SharedTripLeg?,
    currentLegIndex: Int,
    totalLegs: Int,
    sharedTripLegs: List<SharedTripLeg>,
    onBack: () -> Unit,
    onLegList: () -> Unit,
    onRefresh: () -> Unit,
    onLegDeparture: (Int, Double, Double, List<String>, String, String, String?) -> Unit,
    onLegArrival: (Int, Double, Double?, Double, List<String>, String, String, Double?, String?) -> Unit,
    onLegComplete: (Int, Double, Double, Double?, String?) -> Unit,
    onReturnStart: (Int, Double, Double, String, String) -> Unit,
    onReturnArrival: (Int, Double, Double, String, String, Double?, String?) -> Unit,
    onContinueToNext: (Int, Double, Double, Double?, String?) -> Unit,
    onNextLeg: () -> Unit,
    onTripComplete: () -> Unit,
    actionState: TripActionState,
    isLastLeg: Boolean
) {
    // Form state
    var odometerStart by remember { mutableStateOf("") }
    var fuelStart by remember { mutableStateOf("") }
    var showDepartureDialog by remember { mutableStateOf(false) }
    var showArrivalDialog by remember { mutableStateOf(false) }
    var showReturnStartDialog by remember { mutableStateOf(false) }
    var showReturnArrivalDialog by remember { mutableStateOf(false) }
    var showNextLegPrompt by remember { mutableStateOf(false) }
    var legCompleted by remember { mutableStateOf(false) }
    var showTripCompletedDialog by remember { mutableStateOf(false) }
    var showPreviousLegsDialog by remember { mutableStateOf(false) }

    // Debug when the composable receives new parameters
    LaunchedEffect(currentLeg?.leg_id, currentLegIndex) {
        android.util.Log.d("SharedTripLegExecutionScreen", "=== COMPOSABLE PARAMETERS DEBUG ===")
        android.util.Log.d("SharedTripLegExecutionScreen", "Received currentLeg: ID=${currentLeg?.leg_id}, destination=${currentLeg?.destination}, team=${currentLeg?.team_name}")
        android.util.Log.d("SharedTripLegExecutionScreen", "Received currentLegIndex: $currentLegIndex")
        android.util.Log.d("SharedTripLegExecutionScreen", "Received totalLegs: $totalLegs")
        android.util.Log.d("SharedTripLegExecutionScreen", "Current leg status: '${currentLeg?.status}'")
        
        // Reset leg completed state for new leg
        legCompleted = false
    }

    // Refresh leg data when screen becomes visible and reset action state
    LaunchedEffect(Unit) {
        android.util.Log.d("SharedTripLegExecutionScreen", "=== SCREEN VISIBLE - REFRESHING LEG DATA ===")
        // Trigger a refresh of the leg data when the screen is shown
        onRefresh()
    }
    
    var departureLocation by remember { 
        mutableStateOf("ISATU Miagao Campus") // Default to base for any leg clicked first
    }
    
        // Auto-fill when screen becomes visible (in case leg data wasn't available initially)
        LaunchedEffect(sharedTripLegs, currentLegIndex) {
            android.util.Log.d("SharedTripLegExecutionScreen", "=== AUTO-FILL ON DATA CHANGE ===")
            android.util.Log.d("SharedTripLegExecutionScreen", "sharedTripLegs changed, size: ${sharedTripLegs.size}")
            android.util.Log.d("SharedTripLegExecutionScreen", "currentLegIndex: $currentLegIndex")
            
            // Update departure location based on smart routing logic
            departureLocation = if (currentLegIndex == 0) {
                // First leg: always start from ISATU Miagao Campus
                "ISATU Miagao Campus"
            } else {
                // Check if any previous legs have been completed
                val hasCompletedPreviousLegs = sharedTripLegs.take(currentLegIndex).any { it.status in listOf("completed", "arrived") }
                
                if (hasCompletedPreviousLegs) {
                    // If previous legs are completed, use smart routing
                    val previousLeg = if (currentLegIndex - 1 < sharedTripLegs.size) {
                        sharedTripLegs[currentLegIndex - 1]
                    } else null
                    
                    val newLocation = if (previousLeg?.return_to_base == true) {
                        // Previous leg required return to base, so start from base
                        "ISATU Miagao Campus"
                    } else {
                        // Continue from previous leg's destination
                        previousLeg?.destination ?: "ISATU Miagao Campus"
                    }
                    android.util.Log.d("SharedTripLegExecutionScreen", "Updated departureLocation to: $newLocation (previous leg return_to_base: ${previousLeg?.return_to_base}, destination: ${previousLeg?.destination})")
                    newLocation
                } else {
                    // If no previous legs are completed, this is the first leg being executed
                    "ISATU Miagao Campus"
                }
            }
            
            if (sharedTripLegs.isNotEmpty() && currentLegIndex > 0) {
                val previousLeg = sharedTripLegs[currentLegIndex - 1]
                val previousOdometer = previousLeg.odometer_end?.toString() ?: ""
                val previousFuel = previousLeg.fuel_end?.toString() ?: ""
                
                android.util.Log.d("SharedTripLegExecutionScreen", "Previous leg found: ID=${previousLeg.leg_id}")
                android.util.Log.d("SharedTripLegExecutionScreen", "Previous leg status: ${previousLeg.status}")
                android.util.Log.d("SharedTripLegExecutionScreen", "Previous odometer_end: ${previousLeg.odometer_end}")
                android.util.Log.d("SharedTripLegExecutionScreen", "Previous fuel_end: ${previousLeg.fuel_end}")
                android.util.Log.d("SharedTripLegExecutionScreen", "Previous odometer string: '$previousOdometer'")
                android.util.Log.d("SharedTripLegExecutionScreen", "Previous fuel string: '$previousFuel'")
                
                if (previousOdometer.isNotEmpty()) {
                    odometerStart = previousOdometer
                    android.util.Log.d("SharedTripLegExecutionScreen", "Updated odometerStart to: $odometerStart")
                } else {
                    android.util.Log.d("SharedTripLegExecutionScreen", "No previous odometer data to auto-fill")
                }
                if (previousFuel.isNotEmpty()) {
                    fuelStart = previousFuel
                    android.util.Log.d("SharedTripLegExecutionScreen", "Updated fuelStart to: $fuelStart")
                } else {
                    android.util.Log.d("SharedTripLegExecutionScreen", "No previous fuel data to auto-fill")
                }
            } else {
                android.util.Log.d("SharedTripLegExecutionScreen", "Cannot auto-fill - sharedTripLegs: ${sharedTripLegs.size}, currentLegIndex: $currentLegIndex")
            }
        }
    var departureTime by remember { mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"))) }
    var manifestOverrideReason by remember { mutableStateOf("") }
    var odometerEnd by remember { mutableStateOf("") }
    var fuelUsed by remember { mutableStateOf("") }
    var arrivalLocation by remember { mutableStateOf(currentLeg?.arrival_location ?: (currentLeg?.destination ?: "")) }
    var arrivalTime by remember { mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"))) }
    var fuelEnd by remember { mutableStateOf("") }
    var fuelPurchased by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    // Parse passengers from JsonElement (same logic as TripDetailsScreen)
    val tripPassengersList: List<String> = try {
        val elem = tripDetails.passengers
        android.util.Log.d("SharedTripLegExecutionScreen", "=== PASSENGER PARSING DEBUG ===")
        android.util.Log.d("SharedTripLegExecutionScreen", "Raw passengers element: $elem")
        android.util.Log.d("SharedTripLegExecutionScreen", "Element is null: ${elem == null}")
        if (elem != null) {
            android.util.Log.d("SharedTripLegExecutionScreen", "Element type: ${elem.javaClass.simpleName}")
            android.util.Log.d("SharedTripLegExecutionScreen", "Is JsonArray: ${elem.isJsonArray}")
            android.util.Log.d("SharedTripLegExecutionScreen", "Is JsonPrimitive: ${elem.isJsonPrimitive}")
            if (elem.isJsonPrimitive) {
                android.util.Log.d("SharedTripLegExecutionScreen", "Primitive value: ${elem.asString}")
            }
        }
        
        val result = when {
            elem == null -> {
                android.util.Log.d("SharedTripLegExecutionScreen", "Element is null, returning empty list")
                emptyList()
            }
            elem.isJsonArray -> {
                android.util.Log.d("SharedTripLegExecutionScreen", "Parsing as JsonArray")
                // Support both ["Alice","Bob"] and [{"name":"Alice"},{"name":"Bob"}]
                elem.asJsonArray.mapNotNull { jsonEl ->
                    if (jsonEl.isJsonPrimitive && jsonEl.asJsonPrimitive.isString) jsonEl.asString
                    else if (jsonEl.isJsonObject && jsonEl.asJsonObject.has("name")) jsonEl.asJsonObject.get("name").asString
                    else null
                }
            }
            elem.isJsonPrimitive && elem.asJsonPrimitive.isString -> {
                android.util.Log.d("SharedTripLegExecutionScreen", "Parsing as string")
                val raw = elem.asString
                var parsedList: List<String> = emptyList()
                // Try to parse as list of {name}
                try {
                    val objects = Json.decodeFromString<List<Map<String, String>>>(raw)
                    val names = objects.mapNotNull { it["name"] }
                    if (names.isNotEmpty()) parsedList = names
                } catch (_: Exception) {}
                // Fallback: parse as list of strings if still empty
                if (parsedList.isEmpty()) {
                    try {
                        parsedList = Json.decodeFromString<List<String>>(raw)
                    } catch (_: Exception) {
                        parsedList = emptyList()
                    }
                }
                parsedList
            }
            else -> {
                android.util.Log.d("SharedTripLegExecutionScreen", "Unknown element type, returning empty list")
                emptyList()
            }
        }
        android.util.Log.d("SharedTripLegExecutionScreen", "Final parsed passengers: $result")
        result
    } catch (e: Exception) { 
        android.util.Log.e("SharedTripLegExecutionScreen", "Passenger parsing exception: ${e.message}")
        emptyList() 
    }

    var confirmedPassengers by remember { mutableStateOf(currentLeg?.passengers ?: emptyList()) }

    // Reset form when leg changes
    LaunchedEffect(currentLeg?.leg_id) {
        // Auto-fill odometer start with previous leg's odometer end
        val previousLegOdometerEnd = if (currentLegIndex > 0) {
            // Get the previous leg's odometer end
            val previousLeg = if (currentLegIndex - 1 < sharedTripLegs.size) {
                sharedTripLegs[currentLegIndex - 1]
            } else null
            previousLeg?.odometer_end?.toString() ?: ""
        } else {
            // For the first leg, use current leg's odometer start if available
            currentLeg?.odometer_start?.toString() ?: ""
        }
        
        // Auto-fill fuel start with previous leg's fuel end
        val previousLegFuelEnd = if (currentLegIndex > 0) {
            // Get the previous leg's fuel end
            val previousLeg = if (currentLegIndex - 1 < sharedTripLegs.size) {
                sharedTripLegs[currentLegIndex - 1]
            } else null
            previousLeg?.fuel_end?.toString() ?: ""
        } else {
            // For the first leg, use current leg's fuel start if available
            currentLeg?.fuel_start?.toString() ?: ""
        }
        
        odometerStart = previousLegOdometerEnd
        fuelStart = previousLegFuelEnd
        
        // Chain departure locations: check return_to_base flag for smart routing
        departureLocation = if (currentLegIndex == 0) {
            // First leg: always start from ISATU Miagao Campus
            "ISATU Miagao Campus"
        } else {
            // Check if any previous legs have been completed
            val hasCompletedPreviousLegs = sharedTripLegs.take(currentLegIndex).any { it.status in listOf("completed", "arrived") }
            
            if (hasCompletedPreviousLegs) {
                // If previous legs are completed, use smart routing
                val previousLeg = if (currentLegIndex - 1 < sharedTripLegs.size) {
                    sharedTripLegs[currentLegIndex - 1]
                } else null
                
                if (previousLeg?.return_to_base == true) {
                    // Previous leg required return to base, so start from base
                    "ISATU Miagao Campus"
                } else {
                    // Continue from previous leg's destination
                    previousLeg?.destination ?: "ISATU Miagao Campus"
                }
            } else {
                // If no previous legs are completed, this is the first leg being executed
                "ISATU Miagao Campus"
            }
        }
        
        android.util.Log.d("SharedTripLegExecutionScreen", "=== DEPARTURE LOCATION DEBUG ===")
        android.util.Log.d("SharedTripLegExecutionScreen", "currentLegIndex: $currentLegIndex")
        android.util.Log.d("SharedTripLegExecutionScreen", "Previous leg destination: ${if (currentLegIndex > 0 && currentLegIndex - 1 < sharedTripLegs.size) sharedTripLegs[currentLegIndex - 1].destination else "N/A"}")
        android.util.Log.d("SharedTripLegExecutionScreen", "Set departureLocation to: '$departureLocation'")
        departureTime = LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"))
        manifestOverrideReason = ""
        odometerEnd = ""
        fuelUsed = ""
        // Set arrival location based on return_to_base flag
        arrivalLocation = if (currentLeg?.return_to_base == true) {
            "ISATU Miagao Campus"
        } else {
            currentLeg?.arrival_location ?: (currentLeg?.destination ?: "")
        }
        arrivalTime = ""
        fuelEnd = ""
        fuelPurchased = ""
        notes = ""
        confirmedPassengers = currentLeg?.passengers ?: emptyList()
        
        android.util.Log.d("SharedTripLegExecutionScreen", "=== AUTO-FILL DEBUG ===")
        android.util.Log.d("SharedTripLegExecutionScreen", "Current leg index: $currentLegIndex")
        android.util.Log.d("SharedTripLegExecutionScreen", "Total legs available: ${sharedTripLegs.size}")
        android.util.Log.d("SharedTripLegExecutionScreen", "All legs data: ${sharedTripLegs.map { "ID=${it.leg_id}, odometer_end=${it.odometer_end}, fuel_end=${it.fuel_end}" }}")
        android.util.Log.d("SharedTripLegExecutionScreen", "Previous leg odometer end: $previousLegOdometerEnd")
        android.util.Log.d("SharedTripLegExecutionScreen", "Previous leg fuel end: $previousLegFuelEnd")
        android.util.Log.d("SharedTripLegExecutionScreen", "Auto-filled odometer start: $odometerStart")
        android.util.Log.d("SharedTripLegExecutionScreen", "Auto-filled fuel start: $fuelStart")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("Leg ${currentLegIndex + 1} of $totalLegs") 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Back to previous legs button
                    IconButton(
                        onClick = {
                            android.util.Log.d("SharedTripLegExecutionScreen", "=== PREVIOUS LEGS BUTTON CLICKED ===")
                            showPreviousLegsDialog = true
                        }
                    ) {
                        Icon(Icons.Default.Person, contentDescription = "View Previous Legs")
                    }
                    
                    // Refresh button
                    IconButton(
                        onClick = {
                            // Force refresh shared trip data
                            android.util.Log.d("SharedTripLegExecutionScreen", "=== REFRESH BUTTON CLICKED ===")
                            onRefresh()
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Data")
                    }
                    TextButton(
                        onClick = onLegList,
                        content = {
                            Text("Leg List")
                        }
                    )
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            item {
                // Trip Overview Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Trip #${tripDetails.id}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // Debug information
                        LaunchedEffect(currentLeg, currentLegIndex) {
                            android.util.Log.d("SharedTripLegExecutionScreen", "=== LEG SELECTION DEBUG ===")
                            android.util.Log.d("SharedTripLegExecutionScreen", "currentLeg changed: ${currentLeg?.leg_id}")
                            android.util.Log.d("SharedTripLegExecutionScreen", "currentLegIndex: $currentLegIndex")
                            android.util.Log.d("SharedTripLegExecutionScreen", "totalLegs: $totalLegs")
                            android.util.Log.d("SharedTripLegExecutionScreen", "Current leg details: ID=${currentLeg?.leg_id}, destination=${currentLeg?.destination}, team=${currentLeg?.team_name}")
                        }
                        tripDetails.vehicle?.let { vehicle ->
                            Column {
                                if (!vehicle.plateNumber.isNullOrBlank() || !vehicle.model.isNullOrBlank()) {
                                    Text(
                                        text = "Vehicle: ${vehicle.plateNumber ?: "N/A"}${if (!vehicle.model.isNullOrBlank()) " (${vehicle.model})" else ""}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                if (!vehicle.type.isNullOrBlank()) {
                                    Text(
                                        text = "Type: ${vehicle.type}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                vehicle.capacity?.let { cap ->
                                    Text(
                                        text = "Capacity: $cap passengers",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        // Format date/time to human-friendly forms
                        val formattedDate = try {
                            ZonedDateTime.parse(tripDetails.travel_date).format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                        } catch (_: Exception) {
                            try {
                                LocalDate.parse(tripDetails.travel_date, DateTimeFormatter.ofPattern("yyyy-MM-dd")).format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                            } catch (_: Exception) {
                                tripDetails.travel_date
                            }
                        }
                        val formattedTime = tripDetails.travel_time?.let { timeStr ->
                            try {
                                ZonedDateTime.parse(timeStr).format(DateTimeFormatter.ofPattern("h:mm a"))
                            } catch (_: Exception) {
                                try {
                                    LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm[:ss]"))
                                        .format(DateTimeFormatter.ofPattern("h:mm a"))
                                } catch (_: Exception) {
                                    timeStr
                                }
                            }
                        } ?: "N/A"
                        Text(
                            text = "Date: $formattedDate",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Time: $formattedTime",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item {
                // Current Leg Info Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Leg ${currentLegIndex + 1} of $totalLegs",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Team: ${currentLeg?.team_name ?: "Unknown Team"}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Destination: ${currentLeg?.destination ?: "Unknown"}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        currentLeg?.purpose?.let { purpose ->
                            Text(
                                text = "Purpose: $purpose",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val legPassengerCount = when {
                            currentLeg?.passengers?.isNotEmpty() == true -> currentLeg.passengers.size
                            else -> tripPassengersList.size
                        }
                        Text(
                            text = "Passengers: $legPassengerCount",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        // Vehicle info (repeat here for clarity)
                        tripDetails.vehicle?.let { vehicle ->
                            Column {
                                if (!vehicle.plateNumber.isNullOrBlank() || !vehicle.model.isNullOrBlank()) {
                                    Text(
                                        text = "Vehicle: ${vehicle.plateNumber ?: "N/A"}${if (!vehicle.model.isNullOrBlank()) " (${vehicle.model})" else ""}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                if (!vehicle.type.isNullOrBlank()) {
                                    Text(
                                        text = "Type: ${vehicle.type}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                vehicle.capacity?.let { cap ->
                                    Text(
                                        text = "Capacity: $cap passengers",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Status: ${currentLeg?.status?.replaceFirstChar { it.uppercaseChar() } ?: "Unknown"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = when (currentLeg?.status) {
                                "completed" -> MaterialTheme.colorScheme.primary
                                "on_route" -> MaterialTheme.colorScheme.secondary
                                "arrived" -> MaterialTheme.colorScheme.tertiary
                                "returning" -> MaterialTheme.colorScheme.error
                                "pending" -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                        
                        // Return to base indicator
                        if (currentLeg?.return_to_base == true) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Return to Base",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Return to Base Required",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        
                        // Show arrival location if available
                        currentLeg?.arrival_location?.let { arrLoc ->
                            Text(
                                text = "To: $arrLoc",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Overall trip passengers card moved to SharedTripDetailsScreen

            item {
                // Leg-specific Passengers List (if different from trip passengers)
                if (currentLeg?.passengers?.isNotEmpty() == true && currentLeg.passengers != tripPassengersList) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Passengers for this leg (${currentLeg.passengers.size}):",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            currentLeg.passengers.forEachIndexed { index, passenger ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                        text = "${index + 1}. $passenger",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    }
                }
            }

            item {
                // Debug information for button states
                LaunchedEffect(currentLeg?.status, actionState) {
                    android.util.Log.d("SharedTripLegExecutionScreen", "=== BUTTON STATE DEBUG ===")
                    android.util.Log.d("SharedTripLegExecutionScreen", "currentLeg: $currentLeg")
                    android.util.Log.d("SharedTripLegExecutionScreen", "currentLeg?.status: '${currentLeg?.status}'")
                    android.util.Log.d("SharedTripLegExecutionScreen", "currentLeg?.leg_id: ${currentLeg?.leg_id}")
                    android.util.Log.d("SharedTripLegExecutionScreen", "actionState: $actionState")
                    android.util.Log.d("SharedTripLegExecutionScreen", "actionState is Loading: ${actionState is TripActionState.Loading}")
                    android.util.Log.d("SharedTripLegExecutionScreen", "legCompleted state: $legCompleted")
                    android.util.Log.d("SharedTripLegExecutionScreen", "isLastLeg: $isLastLeg")
                    android.util.Log.d("SharedTripLegExecutionScreen", "totalLegs: $totalLegs")
                    android.util.Log.d("SharedTripLegExecutionScreen", "currentLegIndex: $currentLegIndex")
                    
                    // Check what button should be shown
                    android.util.Log.d("SharedTripLegExecutionScreen", "=== BUTTON STATUS DEBUG ===")
                    android.util.Log.d("SharedTripLegExecutionScreen", "Current leg ID: ${currentLeg?.leg_id}")
                    android.util.Log.d("SharedTripLegExecutionScreen", "Current leg status: '${currentLeg?.status}'")
                    android.util.Log.d("SharedTripLegExecutionScreen", "Current leg index: $currentLegIndex")
                    android.util.Log.d("SharedTripLegExecutionScreen", "Total legs: $totalLegs")
                    
                    when (currentLeg?.status) {
                        "pending", "approved" -> android.util.Log.d("SharedTripLegExecutionScreen", "✅ Should show DEPART button")
                        "on_route" -> android.util.Log.d("SharedTripLegExecutionScreen", "✅ Should show ARRIVED button")
                        "arrived" -> {
                            android.util.Log.d("SharedTripLegExecutionScreen", "✅ Should show RETURN TO BASE / CONTINUE TO NEXT buttons")
                            android.util.Log.d("SharedTripLegExecutionScreen", "Return to base flag: ${currentLeg?.return_to_base}")
                        }
                        "returning" -> android.util.Log.d("SharedTripLegExecutionScreen", "✅ Should show ARRIVED AT BASE button")
                        "completed" -> android.util.Log.d("SharedTripLegExecutionScreen", "✅ Should show NEXT LEG / COMPLETE TRIP button")
                        else -> android.util.Log.d("SharedTripLegExecutionScreen", "❌ UNKNOWN STATUS: '${currentLeg?.status}' - Will show default text")
                    }
                }
                
                
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // New status-based button logic
                    when (currentLeg?.status) {
                        "pending", "approved" -> {
                            Button(
                                onClick = { showDepartureDialog = true },
                                enabled = actionState !is TripActionState.Loading,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Depart")
                            }
                        }
                        "on_route" -> {
                            OutlinedButton(
                                onClick = { 
                                    android.util.Log.d("SharedTripLegExecutionScreen", "=== ARRIVE BUTTON CLICKED ===")
                                    android.util.Log.d("SharedTripLegExecutionScreen", "Current leg status: '${currentLeg?.status}'")
                                    showArrivalDialog = true 
                                },
                                enabled = actionState !is TripActionState.Loading,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Arrived")
                            }
                        }
                        "arrived" -> {
                            // Show appropriate options based on whether this is the last leg
                            android.util.Log.d("SharedTripLegExecutionScreen", "=== ARRIVED STATUS BUTTON LOGIC ===")
                            android.util.Log.d("SharedTripLegExecutionScreen", "Current leg ID: ${currentLeg?.leg_id}")
                            android.util.Log.d("SharedTripLegExecutionScreen", "Current leg index: $currentLegIndex")
                            android.util.Log.d("SharedTripLegExecutionScreen", "Total legs: $totalLegs")
                            android.util.Log.d("SharedTripLegExecutionScreen", "isLastLeg: $isLastLeg")
                            
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Button(
                                    onClick = { 
                                        android.util.Log.d("SharedTripLegExecutionScreen", "=== RETURN TO BASE BUTTON CLICKED ===")
                                        // Auto-fill with arrival values for return start
                                        odometerStart = currentLeg?.odometer_end?.toString() ?: ""
                                        fuelStart = currentLeg?.fuel_end?.toString() ?: ""
                                        android.util.Log.d("SharedTripLegExecutionScreen", "Auto-filled return start: odo=${odometerStart}, fuel=${fuelStart}")
                                        showReturnStartDialog = true
                                    },
                                    enabled = actionState !is TripActionState.Loading,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Return to Base")
                                }
                                
                                // Only show "Continue to Next" if this is NOT the last leg
                                // Use the ViewModel's isLastLeg() function which considers completion status
                                val shouldShowContinueToNext = !isLastLeg
                                
                                android.util.Log.d("SharedTripLegExecutionScreen", "=== CONTINUE TO NEXT BUTTON LOGIC ===")
                                android.util.Log.d("SharedTripLegExecutionScreen", "currentLegIndex: $currentLegIndex")
                                android.util.Log.d("SharedTripLegExecutionScreen", "totalLegs: $totalLegs")
                                android.util.Log.d("SharedTripLegExecutionScreen", "shouldShowContinueToNext: $shouldShowContinueToNext")
                                android.util.Log.d("SharedTripLegExecutionScreen", "isLastLeg: $isLastLeg")
                                
                                if (shouldShowContinueToNext) {
                                    android.util.Log.d("SharedTripLegExecutionScreen", "Showing Continue to Next button - NOT the last leg")
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = { 
                                            android.util.Log.d("SharedTripLegExecutionScreen", "=== CONTINUE TO NEXT BUTTON CLICKED ===")
                                            // Use the actual leg data for completion
                                            val odometerEnd = currentLeg.odometer_end ?: 0.0
                                            val fuelEnd = currentLeg.fuel_end ?: 0.0
                                            android.util.Log.d("SharedTripLegExecutionScreen", "Using leg data - odometer end: $odometerEnd, fuel end: $fuelEnd")
                                            onContinueToNext(currentLeg.leg_id, odometerEnd, fuelEnd, null, null)
                                        },
                                        enabled = actionState !is TripActionState.Loading,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Continue to Next")
                                    }
                                } else {
                                    android.util.Log.d("SharedTripLegExecutionScreen", "NOT showing Continue to Next button - this IS the last leg")
                                }
                            }
                        }
                        "returning" -> {
                            OutlinedButton(
                                onClick = { 
                                    android.util.Log.d("SharedTripLegExecutionScreen", "=== ARRIVED AT BASE BUTTON CLICKED ===")
                                    // Auto-fill with return start values (these should be the same as arrival values)
                                    odometerEnd = currentLeg?.odometer_end?.toString() ?: ""
                                    fuelEnd = currentLeg?.fuel_end?.toString() ?: ""
                                    
                                    // Auto-calculate fuel used for the ORIGINAL LEG (departure to arrival)
                                    // This should be the fuel consumed during the actual trip, not the return journey
                                    val legFuelStart = currentLeg?.fuel_start ?: 0.0
                                    val legFuelEnd = currentLeg?.fuel_end ?: 0.0
                                    val legFuelPurchased = currentLeg?.fuel_purchased ?: 0.0
                                    val calculatedFuelUsed = legFuelStart + legFuelPurchased - legFuelEnd
                                    fuelUsed = maxOf(0.0, calculatedFuelUsed).toString()
                                    
                                    android.util.Log.d("SharedTripLegExecutionScreen", "=== FUEL CALCULATION DEBUG ===")
                                    android.util.Log.d("SharedTripLegExecutionScreen", "currentLeg: $currentLeg")
                                    android.util.Log.d("SharedTripLegExecutionScreen", "legFuelStart (departure): $legFuelStart")
                                    android.util.Log.d("SharedTripLegExecutionScreen", "legFuelEnd (arrival): $legFuelEnd")
                                    android.util.Log.d("SharedTripLegExecutionScreen", "legFuelPurchased: $legFuelPurchased")
                                    android.util.Log.d("SharedTripLegExecutionScreen", "Auto-filled return arrival: odo=${odometerEnd}, fuel=${fuelEnd}")
                                    android.util.Log.d("SharedTripLegExecutionScreen", "Auto-calculated fuel used for LEG: $legFuelStart + $legFuelPurchased - $legFuelEnd = $calculatedFuelUsed")
                                    android.util.Log.d("SharedTripLegExecutionScreen", "Final fuelUsed string: '$fuelUsed'")
                                    showReturnArrivalDialog = true
                                },
                                enabled = actionState !is TripActionState.Loading,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Arrived at Base")
                            }
                        }
                        "completed" -> {
                            // Calculate if there are other incomplete legs at the time of rendering
                            val otherIncompleteLegs = sharedTripLegs.filter { 
                                it.leg_id != currentLeg?.leg_id && it.status != "completed" 
                            }
                            val hasOtherIncompleteLegs = otherIncompleteLegs.isNotEmpty()
                            
                            android.util.Log.d("SharedTripLegExecutionScreen", "=== COMPLETED LEG BUTTON LOGIC ===")
                            android.util.Log.d("SharedTripLegExecutionScreen", "Current leg ID: ${currentLeg?.leg_id}")
                            android.util.Log.d("SharedTripLegExecutionScreen", "Other incomplete legs: ${otherIncompleteLegs.map { "ID=${it.leg_id}, status=${it.status}" }}")
                            android.util.Log.d("SharedTripLegExecutionScreen", "Has other incomplete legs: $hasOtherIncompleteLegs")
                            
                            if (hasOtherIncompleteLegs) {
                                OutlinedButton(
                                    onClick = { 
                                        android.util.Log.d("SharedTripLegExecutionScreen", "=== NEXT LEG BUTTON CLICKED ===")
                                        onNextLeg()
                                    },
                                    enabled = actionState !is TripActionState.Loading,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Next Leg")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { 
                                        android.util.Log.d("SharedTripLegExecutionScreen", "=== COMPLETE TRIP BUTTON CLICKED ===")
                                        showTripCompletedDialog = true
                                    },
                                    enabled = actionState !is TripActionState.Loading,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Complete Trip")
                                }
                            }
                        }
                        else -> {
                            // Default case - show a generic button for unknown status
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Status: ${currentLeg?.status ?: "Unknown"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Button(
                                    onClick = { 
                                        android.util.Log.d("SharedTripLegExecutionScreen", "=== FALLBACK BUTTON CLICKED ===")
                                        android.util.Log.d("SharedTripLegExecutionScreen", "Current status: '${currentLeg?.status}'")
                                        // Try to start the leg as pending or approved
                                        if (currentLeg?.status == null || currentLeg?.status == "unknown" || currentLeg?.status == "approved") {
                                            showDepartureDialog = true
                                        }
                                    },
                                    enabled = actionState !is TripActionState.Loading,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Start Leg")
                                }
                            }
                        }
                    }
                }
            }

            item {
                // Status/Loading/Error
                if (actionState is TripActionState.Loading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                if (actionState is TripActionState.Error) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = (actionState as TripActionState.Error).message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                if (actionState is TripActionState.Success) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = "Action successful!",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }

    // Departure Dialog
    if (showDepartureDialog) {
        AlertDialog(
            onDismissRequest = { showDepartureDialog = false },
            title = { Text("Departure Details") },
            text = {
                Column {
                    OutlinedTextField(
                        value = odometerStart,
                        onValueChange = { odometerStart = it },
                        label = { Text("Odometer Start") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = fuelStart,
                        onValueChange = { fuelStart = it },
                        label = { Text("Fuel Start (L)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = departureLocation,
                        onValueChange = { departureLocation = it },
                        label = { Text("Departure Location (Auto-filled)") },
                        placeholder = { Text("ISATU Miagao Campus") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        enabled = false
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = departureTime,
                        onValueChange = { departureTime = it },
                        label = { Text("Departure Time (hh:mm a)") },
                        readOnly = true,
                        enabled = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Confirm passengers for this leg:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    currentLeg?.passengers?.forEach { passenger ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = confirmedPassengers.contains(passenger),
                                onCheckedChange = { checked ->
                                    confirmedPassengers = if (checked) {
                                        confirmedPassengers + passenger
                                    } else {
                                        confirmedPassengers - passenger
                                    }
                                }
                            )
                            Text(
                                text = passenger,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manifestOverrideReason,
                        onValueChange = { manifestOverrideReason = it },
                        label = { Text("Manifest Override Reason (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val odo = odometerStart.toDoubleOrNull()
                    val fuel = fuelStart.toDoubleOrNull()
                    val depTime = departureTime // Always use the current time since it's auto-generated
                    val depLoc = if (departureLocation.isBlank()) "ISATU Miagao Campus" else departureLocation
                    val override = manifestOverrideReason.takeIf { it.isNotBlank() }
                    
                    android.util.Log.d("SharedTripLegExecutionScreen", "=== DEPARTURE CONFIRMATION DEBUG ===")
                    android.util.Log.d("SharedTripLegExecutionScreen", "Current leg index: $currentLegIndex")
                    android.util.Log.d("SharedTripLegExecutionScreen", "Total legs: ${sharedTripLegs.size}")
                    android.util.Log.d("SharedTripLegExecutionScreen", "Previous leg data: ${if (currentLegIndex > 0 && currentLegIndex - 1 < sharedTripLegs.size) "ID=${sharedTripLegs[currentLegIndex - 1].leg_id}, destination=${sharedTripLegs[currentLegIndex - 1].destination}" else "N/A"}")
                    android.util.Log.d("SharedTripLegExecutionScreen", "departureLocation from UI: '$departureLocation'")
                    android.util.Log.d("SharedTripLegExecutionScreen", "departureLocation isBlank: ${departureLocation.isBlank()}")
                    android.util.Log.d("SharedTripLegExecutionScreen", "Final depLoc being sent: '$depLoc'")
                    
                    if (odo != null && fuel != null) {
                        val passengersForBackend = if (confirmedPassengers.isNotEmpty()) confirmedPassengers else (currentLeg?.passengers ?: tripPassengersList)
                        onLegDeparture(currentLeg?.leg_id ?: 0, odo, fuel, passengersForBackend, depTime, depLoc, override)
                        showDepartureDialog = false
                    }
                }) {
                    Text("Confirm Departure")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDepartureDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Arrival Dialog
    if (showArrivalDialog) {
        // Ensure defaults when dialog opens
        LaunchedEffect(Unit) {
            if (arrivalLocation.isBlank()) {
                arrivalLocation = currentLeg?.arrival_location ?: (currentLeg?.destination ?: "")
            }
            if (arrivalTime.isBlank()) {
                arrivalTime = LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"))
            }
            if (fuelStart.isBlank() && currentLeg?.fuel_start != null) {
                fuelStart = currentLeg.fuel_start.toString()
            }
            val startVal = fuelStart.toDoubleOrNull()
            val endVal = fuelEnd.toDoubleOrNull()
            if (startVal != null && endVal != null) {
                val calc = startVal - endVal
                fuelUsed = if (calc >= 0) calc.toString() else "0"
            }
        }
        AlertDialog(
            onDismissRequest = { showArrivalDialog = false },
            title = { Text("Arrival Details") },
            text = {
                Column {
                    OutlinedTextField(
                        value = odometerEnd,
                        onValueChange = { odometerEnd = it },
                        label = { Text("Odometer End") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = fuelUsed,
                        onValueChange = { fuelUsed = it },
                        label = { Text("Fuel Used (L) - Auto-calculated") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        enabled = false
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = fuelEnd,
                        onValueChange = { 
                            fuelEnd = it
                            // Auto-calculate fuel used when fuel end changes
                            val fuelStartValue = fuelStart.toDoubleOrNull() ?: currentLeg?.fuel_start ?: 0.0
                            val fuelEndValue = it.toDoubleOrNull() ?: 0.0
                            val calculatedFuelUsed = fuelStartValue - fuelEndValue
                            
                            android.util.Log.d("SharedTripLegExecutionScreen", "=== FUEL CALCULATION DEBUG ===")
                            android.util.Log.d("SharedTripLegExecutionScreen", "fuelStart: '$fuelStart' -> $fuelStartValue")
                            android.util.Log.d("SharedTripLegExecutionScreen", "fuelEnd: '$it' -> $fuelEndValue")
                            android.util.Log.d("SharedTripLegExecutionScreen", "calculatedFuelUsed: $calculatedFuelUsed")
                            
                            fuelUsed = if (calculatedFuelUsed >= 0) calculatedFuelUsed.toString() else "0"
                            android.util.Log.d("SharedTripLegExecutionScreen", "fuelUsed set to: '${fuelUsed}'")
                        },
                        label = { Text("Fuel End (L)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = arrivalLocation,
                        onValueChange = { arrivalLocation = it },
                        label = { Text("Arrival Location") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = arrivalTime,
                        onValueChange = { arrivalTime = it },
                        label = { Text("Arrival Time (hh:mm a) - Auto-filled") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        enabled = false
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = fuelPurchased,
                        onValueChange = { fuelPurchased = it },
                        label = { Text("Fuel Purchased (L)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Confirm passengers dropped off:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    currentLeg?.passengers?.forEach { passenger ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = confirmedPassengers.contains(passenger),
                                onCheckedChange = { checked ->
                                    confirmedPassengers = if (checked) {
                                        confirmedPassengers + passenger
                                    } else {
                                        confirmedPassengers - passenger
                                    }
                                }
                            )
                            Text(
                                text = passenger,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val odoEnd = odometerEnd.toDoubleOrNull()
                    val fuelUsedValue = fuelUsed.toDoubleOrNull()
                    val fuelEndValue = fuelEnd.toDoubleOrNull()
                    val fuelPurchasedValue = fuelPurchased.toDoubleOrNull()
                    val arrTime = if (arrivalTime.isBlank()) {
                        LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"))
                    } else arrivalTime
                    val arrLoc = if (arrivalLocation.isBlank()) (currentLeg?.destination ?: "") else arrivalLocation
                    if (odoEnd != null && fuelEndValue != null) {
                        // Call arrival only - let user choose next action
                        val legId = currentLeg?.leg_id ?: 0
                        onLegArrival(legId, odoEnd, fuelUsedValue, fuelEndValue, confirmedPassengers, arrTime, arrLoc, fuelPurchasedValue, notes)
                        showArrivalDialog = false
                    }
                }) {
                    Text("Arrive")
                }
            },
            dismissButton = {
                TextButton(onClick = { showArrivalDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Return Start Dialog
    if (showReturnStartDialog) {
        AlertDialog(
            onDismissRequest = { showReturnStartDialog = false },
            title = { Text("Return to Base") },
            text = {
                Column {
                    Text("Starting return journey to base.")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = odometerStart,
                        onValueChange = { odometerStart = it },
                        label = { Text("Odometer Start") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = fuelStart,
                        onValueChange = { fuelStart = it },
                        label = { Text("Fuel Level Start") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Auto-filled with arrival values: Odo=${currentLeg?.odometer_end ?: "N/A"}, Fuel=${currentLeg?.fuel_end ?: "N/A"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val odo = odometerStart.toDoubleOrNull()
                    val fuel = fuelStart.toDoubleOrNull()
                    if (odo != null && fuel != null) {
                        val returnTime = LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"))
                        onReturnStart(currentLeg?.leg_id ?: 0, odo, fuel, returnTime, "ISATU Miagao Campus")
                        showReturnStartDialog = false
                    }
                }) {
                    Text("Start Return")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReturnStartDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Return Arrival Dialog
    if (showReturnArrivalDialog) {
        AlertDialog(
            onDismissRequest = { showReturnArrivalDialog = false },
            title = { Text("Arrived at Base") },
            text = {
                Column {
                    Text("You have arrived at the base.")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = odometerEnd,
                        onValueChange = { odometerEnd = it },
                        label = { Text("Odometer End") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = fuelEnd,
                        onValueChange = { fuelEnd = it },
                        label = { Text("Fuel Level End") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = fuelUsed,
                        onValueChange = { fuelUsed = it },
                        label = { Text("Fuel Used (Auto-calculated)") },
                        enabled = false, // Make it read-only since it's auto-calculated
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Auto-filled with arrival values: Odo=${currentLeg?.odometer_end ?: "N/A"}, Fuel=${currentLeg?.fuel_end ?: "N/A"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Fuel used auto-calculated for this leg: ${currentLeg?.fuel_start ?: 0} + ${currentLeg?.fuel_purchased ?: 0} - ${currentLeg?.fuel_end ?: 0} = ${fuelUsed}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val odo = odometerEnd.toDoubleOrNull()
                    val fuel = fuelEnd.toDoubleOrNull()
                    if (odo != null && fuel != null) {
                        val arrivalTime = LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"))
                        val fuelUsedValue = fuelUsed.toDoubleOrNull()
                        onReturnArrival(currentLeg?.leg_id ?: 0, odo, fuel, arrivalTime, "ISATU Miagao Campus", fuelUsedValue, notes.takeIf { it.isNotBlank() })
                        showReturnArrivalDialog = false
                    }
                }) {
                    Text("Complete Return")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReturnArrivalDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Next Leg Prompt
    if (showNextLegPrompt) {
        AlertDialog(
            onDismissRequest = { showNextLegPrompt = false },
            title = { Text("Leg Completed") },
            text = { 
                Text(
                    if (isLastLeg) {
                        "This was the last leg. Ready to return to base?"
                    } else {
                        "Proceed to next stop?"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showNextLegPrompt = false
                    if (isLastLeg) {
                        onTripComplete()
                    } else {
                        onNextLeg()
                    }
                }) {
                    Text(if (isLastLeg) "Return to Base" else "Next Stop")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNextLegPrompt = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Trip Completed Congratulations Dialog
    if (showTripCompletedDialog) {
        AlertDialog(
            onDismissRequest = { showTripCompletedDialog = false },
            title = { 
                Text(
                    text = "🎉 Congratulations!",
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = { 
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Trip Successfully Completed!",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        text = "All legs have been completed successfully. Proceeding to trip summary...",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTripCompletedDialog = false
                        onTripComplete()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("View Summary")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTripCompletedDialog = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Previous Legs Dialog
    if (showPreviousLegsDialog) {
        AlertDialog(
            onDismissRequest = { showPreviousLegsDialog = false },
            title = { Text("Previous Legs") },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(sharedTripLegs) { leg ->
                        val isCurrentLeg = leg.leg_id == currentLeg?.leg_id
                        val legIndex = sharedTripLegs.indexOf(leg)
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrentLeg) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else 
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Leg ${legIndex + 1}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (isCurrentLeg) {
                                        Text(
                                            text = "CURRENT",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                
                                Text(
                                    text = leg.destination,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                
                                if (leg.team_name != null) {
                                    Text(
                                        text = "Team: ${leg.team_name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                Text(
                                    text = "Status: ${leg.status.uppercase()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when (leg.status) {
                                        "completed" -> MaterialTheme.colorScheme.primary
                                        "on_route" -> MaterialTheme.colorScheme.secondary
                                        "arrived" -> MaterialTheme.colorScheme.tertiary
                                        "returning" -> MaterialTheme.colorScheme.error
                                        "pending" -> MaterialTheme.colorScheme.onSurfaceVariant
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    fontWeight = FontWeight.Medium
                                )
                                
                                // Show leg data if available
                                if (leg.odometer_start != null && leg.odometer_end != null) {
                                    Text(
                                        text = "Distance: ${leg.odometer_end - leg.odometer_start} km",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                if (leg.fuel_used != null) {
                                    Text(
                                        text = "Fuel Used: ${leg.fuel_used} L",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                if (leg.return_to_base == true) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle, 
                                            contentDescription = "Return to Base", 
                                            tint = MaterialTheme.colorScheme.primary, 
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Return to Base Required", 
                                            style = MaterialTheme.typography.bodySmall, 
                                            color = MaterialTheme.colorScheme.primary, 
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showPreviousLegsDialog = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Close")
                }
            }
        )
    }
}
