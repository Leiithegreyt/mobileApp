package com.example.drivebroom.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.drivebroom.network.SharedTripLeg
import com.example.drivebroom.network.TripDetails
import com.example.drivebroom.network.Vehicle
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import com.example.drivebroom.ui.components.StatusChip
import java.time.ZonedDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)

// Helper function to convert 24-hour format to 12-hour format for display
fun convertTo12Hour(time24Hour: String?): String {
    if (time24Hour == null || time24Hour.isBlank()) return "N/A"
    
    // Clean the input - remove any extra whitespace
    val cleanTime = time24Hour.trim()
    
    return try {
        // Try HH:mm format first (most common from API)
        val formatter24 = DateTimeFormatter.ofPattern("HH:mm")
        val time = LocalTime.parse(cleanTime, formatter24)
        time.format(DateTimeFormatter.ofPattern("h:mm a"))
    } catch (e: Exception) {
        // If HH:mm fails, try HH:mm:ss format
        try {
            val formatter24 = DateTimeFormatter.ofPattern("HH:mm:ss")
            val time = LocalTime.parse(cleanTime, formatter24)
            time.format(DateTimeFormatter.ofPattern("h:mm a"))
        } catch (e2: Exception) {
            // If all parsing fails, return original string
            cleanTime
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedTripDetailsScreen(
    tripDetails: TripDetails,
    sharedTripLegs: List<SharedTripLeg>,
    onBack: () -> Unit,
    onStartTrip: () -> Unit,
    onLegClick: (Int) -> Unit
) {
    // Format dates
    val formattedTravelDate = try {
        val zdt = ZonedDateTime.parse(tripDetails.travel_date)
        zdt.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
    } catch (e: Exception) {
        tripDetails.travel_date
    }

    val formattedTravelTime = run {
        // Prefer trip-level travel_time; fallback to first leg scheduled_pickup_time; else show "-"
        val rawTime = tripDetails.travel_time ?: sharedTripLegs.firstOrNull()?.departure_time
        if (rawTime.isNullOrBlank()) "-" else {
            try {
                val zdt = ZonedDateTime.parse(rawTime)
                zdt.format(DateTimeFormatter.ofPattern("h:mm a"))
            } catch (_: Exception) {
                // Fallback: try without timezone or seconds
                try {
                    val lt = java.time.LocalTime.parse(rawTime.substringAfterLast('T').substring(0,5))
                    lt.format(DateTimeFormatter.ofPattern("h:mm a"))
                } catch (_: Exception) {
                    "-"
                }
            }
        }
    }

    // Calculate trip statistics
    val totalStops = sharedTripLegs.size
    val completedStops = sharedTripLegs.count { it.status == "completed" }
    val inProgressStops = sharedTripLegs.count { it.status == "in_progress" }
    val pendingStops = sharedTripLegs.count { it.status == "pending" }
    val totalPassengers = sharedTripLegs.sumOf { leg -> leg.passengers?.size ?: 0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val titleText = if (tripDetails.trip_type?.equals("shared", ignoreCase = true) == true) {
                        "Shared Trip Details"
                    } else {
                        "Trip Details"
                    }
                    Text(titleText)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
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
                            text = "Date/Time: $formattedTravelDate, $formattedTravelTime",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        tripDetails.vehicle?.let { vehicle ->
                            Text(
                                text = "Vehicle: ${vehicle.plateNumber} (${vehicle.model})",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // Trip Statistics removed per request

            item {
                // Start Trip Button
                Button(
                    onClick = onStartTrip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Begin First Leg")
                }
            }

            item {
                Text(
                    text = "Trip Stops",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                // Overall Trip Passengers (moved from leg execution screen)
                // Build from tripDetails.passengers or by concatenating leg passengers as a fallback
                val passengersList: List<String> = run {
                    val fromTrip: List<String> = try {
                        val elem = tripDetails.passengers
                        when {
                            elem == null -> emptyList()
                            elem.isJsonArray -> {
                                elem.asJsonArray.mapNotNull { jsonEl ->
                                    if (jsonEl.isJsonPrimitive && jsonEl.asJsonPrimitive.isString) jsonEl.asString
                                    else if (jsonEl.isJsonObject && jsonEl.asJsonObject.has("name")) jsonEl.asJsonObject.get("name").asString
                                    else null
                                }
                            }
                            elem.isJsonPrimitive && elem.asJsonPrimitive.isString -> {
                                val raw = elem.asString
                                var parsed: List<String> = emptyList()
                                try {
                                    val arr = kotlinx.serialization.json.Json.parseToJsonElement(raw)
                                    if (arr is kotlinx.serialization.json.JsonArray) {
                                        val names = arr.mapNotNull { el ->
                                            (el as? kotlinx.serialization.json.JsonObject)?.get("name")?.let { nameEl ->
                                                if (nameEl is kotlinx.serialization.json.JsonPrimitive) nameEl.content else null
                                            }
                                        }
                                        if (names.isNotEmpty()) parsed = names
                                    }
                                } catch (_: Exception) {}
                                if (parsed.isEmpty()) {
                                    try { parsed = kotlinx.serialization.json.Json.decodeFromString<List<String>>(raw) } catch (_: Exception) { parsed = emptyList() }
                                }
                                parsed
                            }
                            else -> emptyList()
                        }
                    } catch (_: Exception) { emptyList() }
                    if (fromTrip.isNotEmpty()) fromTrip else sharedTripLegs.flatMap { it.passengers ?: emptyList() }.distinct()
                }

                if (passengersList.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Trip Passengers (${passengersList.size}):",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            passengersList.forEachIndexed { index, passenger ->
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
                        }
                    }
                }
            }

            items(sharedTripLegs) { leg ->
                LegCard(
                    leg = leg,
                    onClick = { 
                        // Call the onLegClick handler directly
                        onLegClick(leg.leg_id)
                    }
                )
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegCard(
    leg: SharedTripLeg,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (leg.status) {
                "completed" -> MaterialTheme.colorScheme.primaryContainer
                "in_progress" -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Leg ${leg.leg_id}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                StatusChip(status = leg.status)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Team: ${leg.team_name}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = "Destination: ${leg.destination}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            // Return to base indicator
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
            
            leg.purpose?.let { purpose ->
                Text(
                    text = "Purpose: $purpose",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            val safePassengers = leg.passengers ?: emptyList()
            Text(
                text = "Passengers: ${safePassengers.size}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            if (safePassengers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Passenger List:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                safePassengers.forEach { passenger ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = passenger,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            
            // Show timing information: only departure as requested
            if (leg.departure_time != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    leg.departure_time?.let { departureTime ->
                        Column {
                            Text(
                                text = "Departure",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = convertTo12Hour(departureTime),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Show simple efficiency if data available
            val distance = if (leg.odometer_start != null && leg.odometer_end != null && leg.odometer_end > leg.odometer_start) {
                leg.odometer_end - leg.odometer_start
            } else 0.0
            val fuelUsed = if (leg.fuel_start != null && leg.fuel_end != null) {
                val purchased = leg.fuel_purchased ?: 0.0
                leg.fuel_start + purchased - leg.fuel_end
            } else 0.0
            if (distance > 0 && fuelUsed > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Efficiency: ${String.format("%.2f", distance / fuelUsed)} km/L",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            // Add Select Leg button
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Select This Leg")
            }
        }
    }
}
