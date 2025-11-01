package com.example.drivebroom.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Info
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.drivebroom.network.CompletedTrip
import com.example.drivebroom.network.SharedTripLeg
import com.example.drivebroom.ui.components.StatusChip
import com.example.drivebroom.viewmodel.TripDetailsViewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompletedTripDetailsScreen(
    trip: CompletedTrip,
    onBack: () -> Unit,
    onViewSharedTripDetails: (Int) -> Unit = {},
    detailedLegs: List<SharedTripLeg>? = null // Add parameter for detailed legs
) {
    val isSharedTrip = trip.tripType == "shared"
    
    // Use detailed legs if available, otherwise fall back to trip.legs
    val legsToShow = detailedLegs ?: trip.legs
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Trip Header Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Trip #${trip.id}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            StatusChip(status = trip.status ?: "Unknown")
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Trip Type
                        val tripType = when (trip.tripType) {
                            "shared" -> "🔄 Shared Trip"
                            "individual" -> "🚗 Individual Trip"
                            else -> "🚗 Trip"
                        }
                        Text(
                            text = tripType,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            // Destination Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = "Destination",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Destination",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = trip.destination ?: "Not specified",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
            
            // Trip Information Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Trip Information",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        // Date and Time (12-hour format, friendly date)
                        val friendlyDate = when {
                            trip.formattedTravelDate != null -> trip.formattedTravelDate
                            !trip.travelDate.isNullOrBlank() -> {
                                try {
                                    ZonedDateTime.parse(trip.travelDate).format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                                } catch (_: Exception) {
                                    try {
                                        LocalDate.parse(trip.travelDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                                            .format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                                    } catch (_: Exception) { trip.travelDate }
                                }
                            }
                            else -> "Not specified"
                        }
                        TripInfoRow(
                            icon = Icons.Default.Info,
                            label = "Travel Date",
                            value = friendlyDate ?: "Not specified"
                        )

                        val friendlyTime = trip.travelTime?.let { t ->
                            try {
                                ZonedDateTime.parse(t).format(DateTimeFormatter.ofPattern("h:mm a"))
                            } catch (_: Exception) {
                                try {
                                    LocalTime.parse(t, DateTimeFormatter.ofPattern("HH:mm[:ss]"))
                                        .format(DateTimeFormatter.ofPattern("h:mm a"))
                                } catch (_: Exception) { t }
                            }
                        }
                        friendlyTime?.let { time ->
                            TripInfoRow(
                                icon = Icons.Default.Info,
                                label = "Travel Time",
                                value = time
                            )
                        }
                        
                        // Requested by
                        TripInfoRow(
                            icon = Icons.Default.Person,
                            label = "Requested by",
                            value = trip.requestedBy ?: "Not specified"
                        )
                        
                        // Purpose
                        trip.purpose?.let { purpose ->
                            TripInfoRow(
                                icon = Icons.Default.Person,
                                label = "Purpose",
                                value = purpose
                            )
                        }
                        
                        // Passengers
                        if (isSharedTrip && trip.totalPassengers != null) {
                            TripInfoRow(
                                icon = Icons.Default.Person,
                                label = "Total Passengers",
                                value = "${trip.totalPassengers} passengers"
                            )
                        } else {
                            val passengerNames = parsePassengersToNames(trip.passengers)
                            if (passengerNames.isNotEmpty()) {
                                TripInfoRow(
                                    icon = Icons.Default.Person,
                                    label = "Passengers",
                                    value = passengerNames.joinToString(", ")
                                )
                            }
                        }
                    }
                }
            }
            
            // Vehicle Information Card
            trip.vehicleInfo?.let { vehicle ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = "Vehicle",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Vehicle Information",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            TripInfoRow(
                                icon = Icons.Default.Info,
                                label = "Plate Number",
                                value = vehicle.plateNumber ?: "Not specified"
                            )
                            
                            TripInfoRow(
                                icon = Icons.Default.Info,
                                label = "Model",
                                value = vehicle.model ?: "Not specified"
                            )
                            
                            vehicle.type?.let { type ->
                                TripInfoRow(
                                    icon = Icons.Default.Info,
                                    label = "Type",
                                    value = type
                                )
                            }
                            
                            vehicle.capacity?.let { capacity ->
                                TripInfoRow(
                                    icon = Icons.Default.Info,
                                    label = "Capacity",
                                    value = "$capacity passengers"
                                )
                            }
                        }
                    }
                }
            }
            
            // Shared Trip Stops Information
            if (isSharedTrip && !trip.stops.isNullOrEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Trip Stops (${trip.stops!!.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            trip.stops!!.forEachIndexed { index, stop ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stop.destination,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "${stop.passenger_count} passengers - ${stop.team_name}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                                if (index < trip.stops!!.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Detailed Trip Legs Information
            if (!legsToShow.isNullOrEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Trip Execution Details",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            // Handle single trips differently - separate outbound and return legs
                            if (!isSharedTrip && legsToShow.size >= 2) {
                                // For single trips with multiple legs, separate outbound and return
                                val outboundLeg = legsToShow.find { !(it.return_to_base ?: false) }
                                val returnLeg = legsToShow.find { it.return_to_base ?: false }

                                // Display outbound leg as Leg 1
                                outboundLeg?.let { leg ->
                                    DetailedLegCard(
                                        leg = leg,
                                        legNumber = 1,
                                        isLast = true,
                                        returnLeg = returnLeg // Pass return leg data
                                    )
                                }
                            } else {
                                // For shared trips or single trips with single leg, use original logic
                                val sortedLegs = legsToShow.sortedBy { leg ->
                                    leg.departure_time ?: "00:00:00"
                                }

                                // Display detailed legs in the desired format with correct numbering
                                sortedLegs.forEachIndexed { index, leg ->
                                    DetailedLegCard(
                                        leg = leg,
                                        legNumber = index + 1,
                                        isLast = index == sortedLegs.size - 1
                                    )
                                    if (index < sortedLegs.size - 1) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Shared Trip Actions (for backward compatibility)
            if (isSharedTrip && legsToShow.isNullOrEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Trip Execution Details",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "View detailed leg execution information, fuel tracking, and trip summary.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            Button(
                                onClick = { onViewSharedTripDetails(trip.id) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("View Trip Legs & Summary")
                            }
                        }
                    }
                }
            }
            
            // Completion Information
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Completion Information",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = "Trip completed on ${trip.formattedCreatedAt ?: "Unknown date"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onBack,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Back")
                        }
                    }
                }
            }
        }
    }
}
private fun parsePassengersToNames(passengers: String?): List<String> {
    if (passengers.isNullOrBlank()) return emptyList()
    // Try: ["Alice","Bob"]
    try {
        val list = Json.decodeFromString<List<String>>(passengers)
        if (list.isNotEmpty()) return list
    } catch (_: Exception) {}
    // Try: [{"name":"Alice"},{"name":"Bob"}]
    try {
        val arr = Json.parseToJsonElement(passengers)
        if (arr is JsonArray) {
            val names = arr.mapNotNull { el ->
                if (el is JsonObject && el["name"] != null) el["name"]!!.jsonPrimitive.content else null
            }
            if (names.isNotEmpty()) return names
        }
    } catch (_: Exception) {}
    // Fallback: plain string
    return listOf(passengers)
}

@Composable
private fun TripInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DetailedLegCard(
    leg: SharedTripLeg,
    legNumber: Int,
    isLast: Boolean,
    returnLeg: SharedTripLeg? = null
    ) {
        Column(
        modifier = Modifier.fillMaxWidth()
        ) {
            // Leg Header
                Text(
            text = "🟢 Leg $legNumber - ${leg.destination}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        // Main Leg Details - Two Column Table Layout
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            leg.odometer_start?.let { odometer ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = "Odometer Start",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${String.format("%.0f", odometer)} km",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
                    leg.fuel_start?.let { fuel ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = "Fuel Level Start",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${String.format("%.1f", fuel)} L",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            leg.departure_time?.let { time ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = "Departure Time",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatTime(time),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            leg.departure_location?.let { location ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = "Departure Location",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = location,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
                    leg.odometer_end?.let { odometer ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = "Odometer Arrival",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${String.format("%.0f", odometer)} km",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
                    leg.fuel_end?.let { fuel ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = "Fuel Level",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${String.format("%.1f", fuel)} L",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            leg.arrival_time?.let { time ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = "Arrival Time",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatTime(time),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            leg.arrival_location?.let { location ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = "Arrival Location",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = location,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            if (!leg.passengers.isNullOrEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = "Passengers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = leg.passengers.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        
        // Return Journey Section (if returnLeg is provided)
        returnLeg?.let { returnData ->
            Spacer(modifier = Modifier.height(16.dp))

            // Return to Base Header
            Text(
                text = "🔄 Return to Base - ${returnData.arrival_location ?: returnData.destination}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Return Journey Details - Two Column Table Layout
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                    returnData.return_journey?.return_odometer_start?.let { odometer ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "Odometer Start",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${String.format("%.0f", odometer.toDoubleOrNull() ?: 0.0)} km",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                    returnData.return_journey?.return_fuel_start?.let { fuel ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "Fuel Level Start",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${String.format("%.1f", fuel.toDoubleOrNull() ?: 0.0)} L",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                returnData.return_journey?.return_start_time?.let { time ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "Departure Time",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = formatTime(time),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                returnData.return_journey?.return_start_location?.let { location ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "Departure Location",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = location,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                returnData.return_journey?.return_odometer_end?.let { odometer ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "Odometer End",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${String.format("%.0f", odometer.toDoubleOrNull() ?: 0.0)} km",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                returnData.return_journey?.return_fuel_end?.let { fuel ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "Fuel Level End",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${String.format("%.1f", fuel.toDoubleOrNull() ?: 0.0)} L",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                returnData.return_journey?.return_arrival_time?.let { time ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "Arrival Time",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = formatTime(time),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                returnData.return_journey?.return_arrival_location?.let { location ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "Final Location",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = location,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = "Remarks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Return to base completed",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Fallback: Original return_journey logic for backward compatibility
        leg.return_journey?.let { returnJourney ->
            Spacer(modifier = Modifier.height(16.dp))

            // Return to Base Header
            Text(
                text = "🔄 Return to Base - ${returnJourney.return_arrival_location ?: "ISATU Miagao Campus"}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Return Journey Details - Two Column Table Layout
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                    returnJourney.return_odometer_start?.let { odometer ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "Odometer Start",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${String.format("%.0f", odometer.toDoubleOrNull() ?: 0.0)} km",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                    returnJourney.return_fuel_start?.let { fuel ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "Fuel Level Start",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${String.format("%.1f", fuel.toDoubleOrNull() ?: 0.0)} L",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                returnJourney.return_start_time?.let { time ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "Departure Time",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = formatTime(time),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                returnJourney.return_start_location?.let { location ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "Departure Location",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = location,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                returnJourney.return_odometer_end?.let { odometer ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "Odometer End",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${String.format("%.0f", odometer.toDoubleOrNull() ?: 0.0)} km",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                returnJourney.return_fuel_end?.let { fuel ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "Fuel Level End",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${String.format("%.1f", fuel.toDoubleOrNull() ?: 0.0)} L",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                returnJourney.return_arrival_time?.let { time ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "Arrival Time",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = formatTime(time),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                returnJourney.return_arrival_location?.let { location ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "Final Location",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = location,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = "Remarks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Return to base completed",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun LegSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(1.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
            content()
        }
    }
}

@Composable
private fun LegInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun CompletedTripDetailsWithLegs(
    trip: CompletedTrip,
    onBack: () -> Unit,
    onViewSharedTripDetails: (Int) -> Unit = {},
    viewModel: TripDetailsViewModel
) {
    val detailedLegs by viewModel.sharedTripLegs.collectAsState()
    
    // Load detailed legs when screen is shown
    LaunchedEffect(trip.id) {
        android.util.Log.d("CompletedTripDetailsScreen", "Loading detailed legs for trip ${trip.id}")
        viewModel.loadSharedTripLegs(trip.id)
    }
    
    CompletedTripDetailsScreen(
        trip = trip,
        onBack = onBack,
        onViewSharedTripDetails = onViewSharedTripDetails,
        detailedLegs = detailedLegs
    )
}

private fun formatTime(timeString: String): String {
    return try {
        // Try to parse as 24-hour time first
        LocalTime.parse(timeString, DateTimeFormatter.ofPattern("HH:mm:ss"))
            .format(DateTimeFormatter.ofPattern("h:mm a"))
    } catch (_: Exception) {
        try {
            // Try to parse as 24-hour time without seconds
            LocalTime.parse(timeString, DateTimeFormatter.ofPattern("HH:mm"))
                .format(DateTimeFormatter.ofPattern("h:mm a"))
        } catch (_: Exception) {
            // Return as is if parsing fails
            timeString
        }
    }
}
