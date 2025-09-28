package com.example.drivebroom.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.drivebroom.network.CompletedTrip
import com.example.drivebroom.network.SharedTripLeg
import com.example.drivebroom.network.TripStop
import com.example.drivebroom.ui.components.StatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedTripDetailsViewScreen(
    trip: CompletedTrip,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shared Trip Details") },
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
            // Trip Header
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
                        
                        Text(
                            text = "🔄 Shared Trip",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Text(
                            text = trip.destination ?: "Multiple Destinations",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            // Trip Statistics
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Trip Statistics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatisticItem(
                                label = "Total Passengers",
                                value = "${trip.totalPassengers ?: 0}",
                                icon = Icons.Default.Person
                            )
                            StatisticItem(
                                label = "Total Stops",
                                value = "${trip.stops?.size ?: 0}",
                                icon = Icons.Default.LocationOn
                            )
                            StatisticItem(
                                label = "Legs Executed",
                                value = "${trip.legs?.size ?: 0}",
                                icon = Icons.Default.Info
                            )
                        }
                    }
                }
            }
            
            // Trip Stops
            if (!trip.stops.isNullOrEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(4.dp)
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
                                StopItem(
                                    stop = stop,
                                    index = index + 1,
                                    isLast = index == trip.stops!!.size - 1
                                )
                            }
                        }
                    }
                }
            }
            
            // Leg Execution Details
            if (!trip.legs.isNullOrEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Leg Execution Details (${trip.legs!!.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            trip.legs!!.forEachIndexed { index, leg ->
                                LegItem(
                                    leg = leg,
                                    index = index + 1,
                                    isLast = index == trip.legs!!.size - 1
                                )
                            }
                        }
                    }
                }
            }
            
            // Trip Summary
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
                            text = "Trip Summary",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        // Calculate totals
                        val totalDistance = trip.legs?.sumOf { (it.odometer_end ?: 0.0) - (it.odometer_start ?: 0.0) } ?: 0.0
                        val totalFuelUsed = trip.legs?.sumOf { it.fuel_used ?: 0.0 } ?: 0.0
                        val totalFuelPurchased = trip.legs?.sumOf { it.fuel_purchased ?: 0.0 } ?: 0.0
                        
                        SummaryItem(
                            label = "Total Distance",
                            value = "${String.format("%.1f", totalDistance)} km"
                        )
                        SummaryItem(
                            label = "Total Fuel Used",
                            value = "${String.format("%.1f", totalFuelUsed)} L"
                        )
                        if (totalFuelPurchased > 0) {
                            SummaryItem(
                                label = "Total Fuel Purchased",
                                value = "${String.format("%.1f", totalFuelPurchased)} L"
                            )
                        }
                        SummaryItem(
                            label = "Trip Date",
                            value = trip.formattedTravelDate ?: trip.travelDate ?: "Unknown"
                        )
                        SummaryItem(
                            label = "Completed On",
                            value = trip.formattedCreatedAt ?: "Unknown"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StopItem(
    stop: TripStop,
    index: Int,
    isLast: Boolean
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Stop number
            Card(
                modifier = Modifier.size(32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = index.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Stop details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stop.destination,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${stop.passenger_count} passengers • ${stop.team_name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                stop.pickup_time?.let { time ->
                    Text(
                        text = "Pickup: $time",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        if (!isLast) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun LegItem(
    leg: SharedTripLeg,
    index: Int,
    isLast: Boolean
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leg number
            Card(
                modifier = Modifier.size(32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (leg.status) {
                        "completed" -> MaterialTheme.colorScheme.primary
                        "in_progress" -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = index.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = when (leg.status) {
                            "completed" -> MaterialTheme.colorScheme.onPrimary
                            "in_progress" -> MaterialTheme.colorScheme.onSecondary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Leg details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = leg.destination ?: "Unknown Destination",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Status: ${leg.status?.replaceFirstChar { it.uppercase() } ?: "Unknown"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Odometer information
                if (leg.odometer_start != null && leg.odometer_end != null) {
                    val distance = leg.odometer_end - leg.odometer_start
                    Text(
                        text = "Distance: ${String.format("%.1f", distance)} km",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Fuel information
                if (leg.fuel_used != null) {
                    Text(
                        text = "Fuel Used: ${String.format("%.1f", leg.fuel_used)} L",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Times
                leg.departure_time?.let { depTime ->
                    Text(
                        text = "Departure: $depTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                leg.arrival_time?.let { arrTime ->
                    Text(
                        text = "Arrival: $arrTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        if (!isLast) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun SummaryItem(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}