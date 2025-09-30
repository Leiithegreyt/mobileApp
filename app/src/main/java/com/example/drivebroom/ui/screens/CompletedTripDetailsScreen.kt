package com.example.drivebroom.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.drivebroom.network.CompletedTrip
import com.example.drivebroom.ui.components.StatusChip
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
    onViewSharedTripDetails: (Int) -> Unit = {}
) {
    val isSharedTrip = trip.tripType == "shared"
    
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
                        
                        // Date and Time
                        TripInfoRow(
                            icon = Icons.Default.Info,
                            label = "Travel Date",
                            value = trip.formattedTravelDate ?: trip.travelDate ?: "Not specified"
                        )
                        
                        trip.travelTime?.let { time ->
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
            
            // Shared Trip Actions
            if (isSharedTrip) {
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
