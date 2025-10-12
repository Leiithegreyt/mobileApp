package com.example.drivebroom.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.drivebroom.network.CompletedTrip
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.example.drivebroom.ui.components.StatusChip
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripLogScreen(
    completedTrips: List<CompletedTrip>,
    onBack: () -> Unit,
    onTripClick: (CompletedTrip) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var showFilterDialog by remember { mutableStateOf(false) }
    var selectedTripType by remember { mutableStateOf("All") }
    
    // Filter trips based on search query and trip type
    val filteredTrips = remember(completedTrips, searchQuery, selectedTripType) {
        completedTrips.filter { trip ->
            val matchesSearch = searchQuery.isEmpty() || 
                trip.destination?.contains(searchQuery, ignoreCase = true) == true ||
                trip.requestedBy?.contains(searchQuery, ignoreCase = true) == true ||
                trip.id.toString().contains(searchQuery)
            
            val matchesType = when (selectedTripType) {
                "All" -> true
                "Shared" -> trip.tripType == "shared"
                "Individual" -> trip.tripType == "individual"
                else -> true
            }
            
            matchesSearch && matchesType
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip Logs") },
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
                .padding(16.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Back",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            item {
                Column {
                    Text(
                        text = "Trip Logs (${filteredTrips.size})",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // Trip statistics
                    val sharedCount = completedTrips.count { it.tripType == "shared" }
                    val individualCount = completedTrips.count { it.tripType == "individual" }
                    val completedCount = completedTrips.count { it.status?.equals("completed", ignoreCase = true) == true }
                    val cancelledCount = completedTrips.count { 
                        val s = it.status?.lowercase()
                        s == "cancelled" || s == "canceled"
                    }
                    
                    if (completedTrips.isNotEmpty()) {
                        Column(modifier = Modifier.padding(bottom = 16.dp)) {
                            Text(
                                text = "📊 ${sharedCount} shared • ${individualCount} individual",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "✅ Completed: ${completedCount}   ❌ Cancelled: ${cancelledCount}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            // Search and Filter Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search trips...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    
                    OutlinedButton(
                        onClick = { showFilterDialog = true }
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Filter")
                    }
                }
            }
            
            // Filter chips
            if (selectedTripType != "All") {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            onClick = { selectedTripType = "All" },
                            label = { Text("All") },
                            selected = selectedTripType == "All"
                        )
                        FilterChip(
                            onClick = { selectedTripType = "Shared" },
                            label = { Text("Shared") },
                            selected = selectedTripType == "Shared"
                        )
                        FilterChip(
                            onClick = { selectedTripType = "Individual" },
                            label = { Text("Individual") },
                            selected = selectedTripType == "Individual"
                        )
                    }
                }
            }
            
            if (filteredTrips.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🚛",
                                style = MaterialTheme.typography.displayMedium
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (completedTrips.isEmpty()) "No completed trips yet" else "No trips match your search",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (completedTrips.isEmpty()) "Your completed trips will appear here" else "Try adjusting your search or filter",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredTrips) { trip ->
                    CompletedTripCard(
                        trip = trip,
                        onClick = { onTripClick(trip) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
        
        // Filter Dialog
        if (showFilterDialog) {
            AlertDialog(
                onDismissRequest = { showFilterDialog = false },
                title = { Text("Filter Trips") },
                text = {
                    Column {
                        Text(
                            text = "Trip Type",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                onClick = { selectedTripType = "All" },
                                label = { Text("All") },
                                selected = selectedTripType == "All",
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                onClick = { selectedTripType = "Shared" },
                                label = { Text("Shared") },
                                selected = selectedTripType == "Shared",
                                modifier = Modifier.weight(1f)
                            )
                        FilterChip(
                            onClick = { selectedTripType = "Individual" },
                            label = { Text("Individual") },
                            selected = selectedTripType == "Individual",
                            modifier = Modifier.weight(1f)
                        )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showFilterDialog = false }) {
                        Text("Apply")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        selectedTripType = "All"
                        showFilterDialog = false 
                    }) {
                        Text("Clear")
                    }
                }
            )
        }
    }
}

@Composable
fun CompletedTripCard(
    trip: CompletedTrip,
    onClick: () -> Unit = {}
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Trip ID, Type, and Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Trip #${trip.id}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    // Use trip type from API response
                    val tripType = when (trip.tripType) {
                        "shared" -> "🔄 Shared Trip"
                        "individual" -> "🚗 Individual Trip"
                        else -> "🚗 Trip"
                    }
                    Text(
                        text = tripType,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
                StatusChip(status = trip.status ?: "Unknown")
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Destination
            Text(
                text = "📍 ${trip.destination}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Date
            Text(
                text = "📅 Date: ${trip.formattedTravelDate}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Requested by
            Text(
                text = "Requested by: ${trip.requestedBy}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Vehicle info if available
            trip.vehicleInfo?.let { vehicle ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "🚗 Vehicle: ${vehicle.plateNumber} (${vehicle.model})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Purpose if available
            trip.purpose?.let { purpose ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "📝 Purpose: $purpose",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Passengers if available
            trip.passengers?.let { passengersRaw ->
                val names = parsePassengersToNames(passengersRaw)
                if (names.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "👥 Passengers: ${names.joinToString(", ")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // View Details indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tap to view details →",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun parsePassengersToNames(passengers: String?): List<String> {
    if (passengers.isNullOrBlank()) return emptyList()
    // Try array of strings: ["Alice","Bob"]
    try {
        val list = Json.decodeFromString<List<String>>(passengers)
        if (list.isNotEmpty()) return list
    } catch (_: Exception) {}
    // Try array of objects with name: [{"name":"Alice"},{"name":"Bob"}]
    try {
        val arr = Json.parseToJsonElement(passengers)
        if (arr is JsonArray) {
            val names = arr.mapNotNull { el ->
                (el as? JsonObject)?.get("name")?.jsonPrimitive?.content
            }
            if (names.isNotEmpty()) return names
        }
    } catch (_: Exception) {}
    // Fallback: plain string
    return listOf(passengers)
}

 