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

@OptIn(ExperimentalMaterial3Api::class)

// Helper function to convert 24-hour format to 12-hour format for display
fun convertTo12Hour(time24Hour: String?): String {
    if (time24Hour == null) return ""
    return try {
        val formatter24 = DateTimeFormatter.ofPattern("HH:mm:ss")
        val time = LocalTime.parse(time24Hour, formatter24)
        time.format(DateTimeFormatter.ofPattern("hh:mm a"))
    } catch (e: Exception) {
        // If parsing fails, try without seconds
        try {
            val formatter24 = DateTimeFormatter.ofPattern("HH:mm")
            val time = LocalTime.parse(time24Hour, formatter24)
            time.format(DateTimeFormatter.ofPattern("hh:mm a"))
        } catch (e2: Exception) {
            // If all parsing fails, return original string
            time24Hour
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

    val formattedTravelTime = try {
        val zdt = ZonedDateTime.parse(tripDetails.travel_time)
        zdt.format(DateTimeFormatter.ofPattern("h:mm a"))
    } catch (e: Exception) {
        tripDetails.travel_time
    }

    // Calculate trip statistics
    val totalStops = sharedTripLegs.size
    val completedStops = sharedTripLegs.count { it.status == "completed" }
    val inProgressStops = sharedTripLegs.count { it.status == "in_progress" }
    val pendingStops = sharedTripLegs.count { it.status == "pending" }
    val totalPassengers = sharedTripLegs.sumOf { it.passengers.size }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shared Trip Details") },
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
                        Text(
                            text = "Purpose: ${tripDetails.purpose}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item {
                // Trip Statistics
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Trip Statistics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatItem("Total Stops", totalStops.toString())
                            StatItem("Completed", completedStops.toString())
                            StatItem("In Progress", inProgressStops.toString())
                            StatItem("Pending", pendingStops.toString())
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatItem("Total Passengers", totalPassengers.toString())
                        }
                    }
                }
            }

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
            
            Text(
                text = "Passengers: ${leg.passengers.size}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            if (leg.passengers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Passenger List:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                leg.passengers.forEach { passenger ->
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
            
            // Show timing information if available
            if (leg.departure_time != null || leg.arrival_time != null) {
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
                    leg.arrival_time?.let { arrivalTime ->
                        Column {
                            Text(
                                text = "Arrival",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = convertTo12Hour(arrivalTime),
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
