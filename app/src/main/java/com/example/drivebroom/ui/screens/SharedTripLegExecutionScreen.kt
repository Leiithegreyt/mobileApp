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
import java.time.LocalTime
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
    var showNextLegPrompt by remember { mutableStateOf(false) }
    var legCompleted by remember { mutableStateOf(false) }

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

    // Refresh leg data when screen becomes visible
    LaunchedEffect(Unit) {
        android.util.Log.d("SharedTripLegExecutionScreen", "=== SCREEN VISIBLE - REFRESHING LEG DATA ===")
        // This will trigger a refresh of the leg data when the screen is shown
        // The parent component should handle the refresh
    }
    
        // Auto-fill when screen becomes visible (in case leg data wasn't available initially)
        LaunchedEffect(sharedTripLegs, currentLegIndex) {
            android.util.Log.d("SharedTripLegExecutionScreen", "=== AUTO-FILL ON DATA CHANGE ===")
            android.util.Log.d("SharedTripLegExecutionScreen", "sharedTripLegs changed, size: ${sharedTripLegs.size}")
            android.util.Log.d("SharedTripLegExecutionScreen", "currentLegIndex: $currentLegIndex")
            
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
    var departureLocation by remember { 
        mutableStateOf(currentLeg?.departure_location?.takeIf { it.isNotBlank() } ?: "ISATU Miagao Campus") 
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
                elem.asJsonArray.mapNotNull { it.asString }
            }
            elem.isJsonPrimitive && elem.asJsonPrimitive.isString -> {
                android.util.Log.d("SharedTripLegExecutionScreen", "Parsing as string")
                val raw = elem.asString
                try { 
                    val parsed = Json.decodeFromString<List<String>>(raw)
                    android.util.Log.d("SharedTripLegExecutionScreen", "String parsed successfully: $parsed")
                    parsed
                } catch (e: Exception) { 
                    android.util.Log.e("SharedTripLegExecutionScreen", "String parsing failed: ${e.message}")
                    emptyList() 
                }
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
        departureLocation = currentLeg?.departure_location?.takeIf { it.isNotBlank() } ?: "ISATU Miagao Campus"
        
        android.util.Log.d("SharedTripLegExecutionScreen", "=== DEPARTURE LOCATION DEBUG ===")
        android.util.Log.d("SharedTripLegExecutionScreen", "currentLeg?.departure_location: '${currentLeg?.departure_location}'")
        android.util.Log.d("SharedTripLegExecutionScreen", "Set departureLocation to: '$departureLocation'")
        departureTime = LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"))
        manifestOverrideReason = ""
        odometerEnd = ""
        fuelUsed = ""
        arrivalLocation = currentLeg?.arrival_location ?: (currentLeg?.destination ?: "")
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
                        Text(
                            text = "Purpose: ${tripDetails.purpose ?: "Not specified"}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        // Debug information
                        LaunchedEffect(currentLeg, currentLegIndex) {
                            android.util.Log.d("SharedTripLegExecutionScreen", "=== LEG SELECTION DEBUG ===")
                            android.util.Log.d("SharedTripLegExecutionScreen", "currentLeg changed: ${currentLeg?.leg_id}")
                            android.util.Log.d("SharedTripLegExecutionScreen", "currentLegIndex: $currentLegIndex")
                            android.util.Log.d("SharedTripLegExecutionScreen", "totalLegs: $totalLegs")
                            android.util.Log.d("SharedTripLegExecutionScreen", "Current leg details: ID=${currentLeg?.leg_id}, destination=${currentLeg?.destination}, team=${currentLeg?.team_name}")
                        }
                        tripDetails.vehicle?.let { vehicle ->
                            Text(
                                text = "Vehicle: ${vehicle.plateNumber} (${vehicle.model})",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Text(
                            text = "Date: ${tripDetails.travel_date}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Time: ${tripDetails.travel_time}",
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
                        Text(
                            text = "Passengers: ${currentLeg?.passengers?.size ?: 0}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Status: ${currentLeg?.status?.replaceFirstChar { it.uppercaseChar() } ?: "Unknown"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = when (currentLeg?.status) {
                                "completed" -> MaterialTheme.colorScheme.primary
                                "in_progress" -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                        
                        // Show departure/arrival locations if available
                        currentLeg?.departure_location?.let { depLoc ->
                            Text(
                                text = "From: $depLoc",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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

            item {
                // Trip Passengers List (All passengers for the entire trip)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (tripPassengersList.isNotEmpty()) {
                            Text(
                                text = "Trip Passengers (${tripPassengersList.size}):",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            tripPassengersList.forEachIndexed { index, passenger ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
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
                        } else {
                    Text(
                                text = "Passengers: Not Available",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                            Text(
                                text = "The backend is sending empty passenger arrays. Please contact the administrator to populate passenger data for this shared trip.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

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
                    android.util.Log.d("SharedTripLegExecutionScreen", "currentLeg?.status: '${currentLeg?.status}'")
                    android.util.Log.d("SharedTripLegExecutionScreen", "actionState: $actionState")
                    android.util.Log.d("SharedTripLegExecutionScreen", "actionState is Loading: ${actionState is TripActionState.Loading}")
                    android.util.Log.d("SharedTripLegExecutionScreen", "Depart enabled: ${(currentLeg?.status == "pending" || currentLeg?.status == "approved") && actionState !is TripActionState.Loading}")
                    android.util.Log.d("SharedTripLegExecutionScreen", "Arrive enabled: ${currentLeg?.status == "in_progress" && actionState !is TripActionState.Loading}")
                    android.util.Log.d("SharedTripLegExecutionScreen", "Next Leg enabled: ${legCompleted && actionState !is TripActionState.Loading}")
                    android.util.Log.d("SharedTripLegExecutionScreen", "legCompleted state: $legCompleted")
                    
                    // Track status changes
                    if (currentLeg?.status == "completed") {
                        android.util.Log.d("SharedTripLegExecutionScreen", "⚠️ LEG STATUS CHANGED TO COMPLETED - This might be unexpected!")
                        android.util.Log.d("SharedTripLegExecutionScreen", "Leg ID: ${currentLeg?.leg_id}, Destination: ${currentLeg?.destination}")
                    }
                }
                
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = { showDepartureDialog = true },
                        enabled = (currentLeg?.status == "pending" || currentLeg?.status == "approved") && actionState !is TripActionState.Loading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Depart")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { showArrivalDialog = true },
                        enabled = currentLeg?.status == "in_progress" && actionState !is TripActionState.Loading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Arrive")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { 
                            onNextLeg()
                            legCompleted = false // Reset for next leg
                        },
                        enabled = legCompleted && actionState !is TripActionState.Loading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Next Leg")
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
                        label = { Text("Departure Location") },
                        placeholder = { Text("ISATU Miagao Campus") },
                        modifier = Modifier.fillMaxWidth()
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
                    android.util.Log.d("SharedTripLegExecutionScreen", "departureLocation from UI: '$departureLocation'")
                    android.util.Log.d("SharedTripLegExecutionScreen", "departureLocation isBlank: ${departureLocation.isBlank()}")
                    android.util.Log.d("SharedTripLegExecutionScreen", "Final depLoc being sent: '$depLoc'")
                    
                    if (odo != null && fuel != null && confirmedPassengers.isNotEmpty()) {
                        onLegDeparture(currentLeg?.leg_id ?: 0, odo, fuel, confirmedPassengers, depTime, depLoc, override)
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
                            val fuelStartValue = fuelStart.toDoubleOrNull() ?: 0.0
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
                        // Call arrival with completion data included
                        onLegArrival(currentLeg?.leg_id ?: 0, odoEnd, fuelUsedValue, fuelEndValue, confirmedPassengers, arrTime, arrLoc, fuelPurchasedValue, notes)
                        legCompleted = true // Mark leg as completed
                        showArrivalDialog = false
                    }
                }) {
                    Text("Complete Leg & Next")
                }
            },
            dismissButton = {
                TextButton(onClick = { showArrivalDialog = false }) {
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
}
