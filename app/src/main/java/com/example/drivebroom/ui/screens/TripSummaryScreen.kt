package com.example.drivebroom.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.drivebroom.network.SharedTripLeg
import com.example.drivebroom.network.TripDetails
import com.example.drivebroom.viewmodel.TripDetailsViewModel
import java.time.format.DateTimeFormatter
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripSummaryScreen(
    tripDetails: TripDetails,
    completedLegs: List<SharedTripLeg>,
    totalDistance: Double,
    totalFuelUsed: Double,
    onBack: () -> Unit,
    onSubmitLogs: () -> Unit
) {
    // Format dates
    val formattedTravelDate = try {
        val zdt = java.time.ZonedDateTime.parse(tripDetails.travel_date)
        zdt.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
    } catch (e: Exception) {
        tripDetails.travel_date
    }

    val formattedTravelTime = try {
        val zdt = java.time.ZonedDateTime.parse(tripDetails.travel_time)
        zdt.format(DateTimeFormatter.ofPattern("h:mm a"))
    } catch (e: Exception) {
        tripDetails.travel_time
    }

    // Calculate statistics
    val totalStops = completedLegs.size
    val totalPassengers = completedLegs.sumOf { it.passengers.size }
    val completedStops = completedLegs.count { it.status == "completed" }
    val overallEfficiency = if (totalFuelUsed > 0.0) totalDistance / totalFuelUsed else 0.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip Summary") },
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
                // Trip Overview
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
                            text = "Trip #${tripDetails.id} - COMPLETED",
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
                // Summary Statistics
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Trip Summary",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Distance and Fuel
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SummaryItem(
                                icon = Icons.Default.Person,
                                label = "Total Distance",
                                value = "${String.format("%.2f", totalDistance)} km"
                            )
                            SummaryItem(
                                icon = Icons.Default.LocationOn,
                                label = "Total Fuel Used",
                                value = "${String.format("%.2f", totalFuelUsed)} L"
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SummaryItem(
                                icon = Icons.Default.LocationOn,
                                label = "Efficiency",
                                value = if (overallEfficiency > 0.0) "${String.format("%.2f", overallEfficiency)} km/L" else "—"
                            )
                            // Spacer cell to balance layout
                            SummaryItem(
                                icon = Icons.Default.LocationOn,
                                label = "",
                                value = ""
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Stops and Passengers
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SummaryItem(
                                icon = Icons.Default.CheckCircle,
                                label = "Stops Completed",
                                value = "$completedStops of $totalStops"
                            )
                            SummaryItem(
                                icon = Icons.Default.Person,
                                label = "Total Passengers",
                                value = totalPassengers.toString()
                            )
                        }
                    }
                }
            }

            item {
                // Completed Legs
                Text(
                    text = "Completed Legs",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            items(completedLegs) { leg ->
                CompletedLegCard(leg = leg)
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Submit Button
                Button(
                    onClick = onSubmitLogs,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Submit Full Trip")
                }
            }
        }
    }
}

@Composable
private fun SummaryItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(0.5f)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
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

@Composable
private fun CompletedLegCard(leg: SharedTripLeg) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Leg ${leg.leg_id} - ${leg.team_name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Completed",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Destination: ${leg.destination}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Text(
                text = "Passengers: ${leg.passengers.size}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            // Show odometer and fuel data if available
            if (leg.odometer_start != null || leg.odometer_end != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    leg.odometer_start?.let { start ->
                        Column {
                            Text(
                                text = "Odometer Start",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${String.format("%.1f", start)} km",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    leg.odometer_end?.let { end ->
                        Column {
                            Text(
                                text = "Odometer End",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${String.format("%.1f", end)} km",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                
                // Show fuel data if available
                if (leg.fuel_start != null || leg.fuel_end != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        leg.fuel_start?.let { start ->
                            Column {
                                Text(
                                    text = "Fuel Start",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${String.format("%.1f", start)} L",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        leg.fuel_end?.let { end ->
                            Column {
                                Text(
                                    text = "Fuel End",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${String.format("%.1f", end)} L",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
            
            // Show timing information
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
                                text = departureTime,
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
                                text = arrivalTime,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Show computed efficiency if data available
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
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
