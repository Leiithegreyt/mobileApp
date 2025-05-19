package com.example.drivebroom.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import com.example.drivebroom.network.TripDetails

@Composable
fun TripDetailsScreen(tripDetails: TripDetails, onBack: () -> Unit) {
    // Format dates
    val formattedTravelDate = try {
        val zdt = ZonedDateTime.parse(tripDetails.travel_date)
        zdt.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
    } catch (e: Exception) {
        tripDetails.travel_date
    }

    val formattedRequestDate = try {
        val zdt = ZonedDateTime.parse(tripDetails.date_of_request)
        zdt.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
    } catch (e: Exception) {
        tripDetails.date_of_request
    }

    val formattedTravelTime = try {
        val zdt = ZonedDateTime.parse(tripDetails.travel_time)
        zdt.format(DateTimeFormatter.ofPattern("h:mm a"))
    } catch (e: Exception) {
        tripDetails.travel_time
    }

    // Parse passengers JSON string
    val passengersList = try {
        Json.decodeFromString<List<String>>(tripDetails.passengers)
    } catch (e: Exception) {
        emptyList()
    }

    Card(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Trip #${tripDetails.id}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Status: ${tripDetails.status}", style = MaterialTheme.typography.bodyMedium)
            Text("Travel Date: $formattedTravelDate", style = MaterialTheme.typography.bodyMedium)
            Text("Travel Time: $formattedTravelTime", style = MaterialTheme.typography.bodyMedium)
            Text("Destination: ${tripDetails.destination}", style = MaterialTheme.typography.bodyMedium)
            Text("Purpose: ${tripDetails.purpose}", style = MaterialTheme.typography.bodyMedium)
            Text("Request Date: $formattedRequestDate", style = MaterialTheme.typography.bodyMedium)
            Text("Passenger Email: ${tripDetails.passenger_email}", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Passengers:", style = MaterialTheme.typography.bodyMedium)
            passengersList.forEach { passenger ->
                Text(passenger, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Vehicle Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            tripDetails.vehicle?.let { vehicle ->
                Text("Plate Number: ${vehicle.plate_number}", style = MaterialTheme.typography.bodyMedium)
                Text("Model: ${vehicle.model}", style = MaterialTheme.typography.bodyMedium)
                Text("Type: ${vehicle.type}", style = MaterialTheme.typography.bodyMedium)
                Text("Capacity: ${vehicle.capacity}", style = MaterialTheme.typography.bodyMedium)
                Text("Status: ${vehicle.status}", style = MaterialTheme.typography.bodyMedium)
                Text("Last Maintenance: ${vehicle.last_maintenance_date}", style = MaterialTheme.typography.bodyMedium)
                Text("Next Maintenance: ${vehicle.next_maintenance_date}", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Back")
            }
        }
    }
} 