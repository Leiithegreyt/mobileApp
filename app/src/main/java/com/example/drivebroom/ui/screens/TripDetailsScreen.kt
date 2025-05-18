package com.example.drivebroom.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.drivebroom.network.TripDetails

@Composable
fun TripDetailsScreen(tripDetails: TripDetails, onBack: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Trip #${tripDetails.id}", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text("From: ${tripDetails.pickup_location}")
        Text("To: ${tripDetails.dropoff_location}")
        Text("Scheduled: ${tripDetails.scheduled_time}")
        Text("Status: ${tripDetails.status}")
        Text("Passenger: ${tripDetails.passenger_name}")
        Text("Phone: ${tripDetails.passenger_phone}")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack) { Text("Back") }
    }
} 