package com.example.drivebroom.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.drivebroom.network.DriverProfile
import com.example.drivebroom.network.Trip
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import com.example.drivebroom.viewmodel.TripDetailsViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverHomeScreen(
    onLogout: () -> Unit,
    driverProfile: DriverProfile?,
    trips: List<Trip>,
    onTripClick: (Int) -> Unit,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    var showProfile by remember { mutableStateOf(false) }
    var showNextSchedule by remember { mutableStateOf(false) }
    var showCompletedTrips by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val viewModel: TripDetailsViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                val tokenManager = com.example.drivebroom.utils.TokenManager(context)
                val networkClient = com.example.drivebroom.network.NetworkClient(tokenManager)
                val repo = com.example.drivebroom.repository.DriverRepository(networkClient.apiService)
                @Suppress("UNCHECKED_CAST")
                return com.example.drivebroom.viewmodel.TripDetailsViewModel(repo, tokenManager) as T
            }
        }
    )
    val completedTrips by viewModel.completedTrips.collectAsState()

    // Date logic for filtering
    val today = remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.time
    }
    val millisInDay = 24 * 60 * 60 * 1000
    val todaysTrips = trips.filter { trip ->
        trip.travel_date?.let {
            try {
                val tripDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).parse(it)
                tripDate != null && !tripDate.before(today) && tripDate.before(Date(today.time + millisInDay))
            } catch (e: Exception) {
                false
            }
        } ?: false
    }
    val nextTrips = trips.filter { trip ->
        trip.travel_date?.let {
            try {
                val tripDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).parse(it)
                tripDate != null && tripDate.after(Date(today.time + millisInDay - 1))
            } catch (e: Exception) {
                false
            }
        } ?: false
    }

    if (showNextSchedule) {
        NextScheduleScreen(
            driverProfile = driverProfile,
            nextTrips = nextTrips,
            onTripClick = onTripClick,
            onBack = { showNextSchedule = false }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Driver Dashboard") },
                actions = {
                    IconButton(onClick = { showProfile = true }) {
                        Icon(Icons.Default.Person, contentDescription = "Profile")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (showCompletedTrips) {
            // Completed Trips Screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                Text("Completed Trips", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                println("Completed trips: $completedTrips") // Debug log
                if (completedTrips.isEmpty()) {
                    Text("No completed trips found.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    completedTrips.forEach { trip ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Trip #${trip.id}", fontWeight = FontWeight.Bold)
                                Text("Destination: ${trip.destination}")
                                Text("Date: ${trip.travel_date}")
                                Text("Status: ${trip.status}")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { showCompletedTrips = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("Back to Dashboard")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                item {
                    Text(
                        text = "Welcome, ${driverProfile?.name ?: "Driver"}",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(bottom = 32.dp)
                    )
                }
                // Add Completed Trips Button
                item {
                    Button(
                        onClick = {
                            viewModel.loadCompletedTrips()
                            showCompletedTrips = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Text("View Completed Trips")
                    }
                }
                item {
                    Button(
                        onClick = onRefresh,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Text("Refresh")
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Today's Schedule",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Button(
                            onClick = { showNextSchedule = true },
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text("Next Schedule")
                        }
                    }
                }
                if (todaysTrips.isEmpty()) {
                    item {
                        Text("No trips scheduled for today.", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    items(todaysTrips) { trip ->
                        TripCard(
                            trip = trip,
                            onClick = { onTripClick(trip.id) }
                        )
                    }
                }
            }
        }
    }

    if (showProfile && driverProfile != null) {
        AlertDialog(
            onDismissRequest = { showProfile = false },
            title = { Text("Driver Profile") },
            text = {
                Column {
                    Text("Name: ${driverProfile.name}")
                    Text("Email: ${driverProfile.email}")
                    Text("Phone: ${driverProfile.phone}")
                }
            },
            confirmButton = {
                TextButton(onClick = { showProfile = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NextScheduleScreen(
    driverProfile: DriverProfile?,
    nextTrips: List<Trip>,
    onTripClick: (Int) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Next Schedule") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Back")
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
                Text(
                    text = "Upcoming Trips",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            if (nextTrips.isEmpty()) {
                item {
                    Text("No upcoming trips.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                items(nextTrips) { trip ->
                    TripCard(
                        trip = trip,
                        onClick = { onTripClick(trip.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripCard(
    trip: Trip,
    onClick: () -> Unit
) {
    // Format travel_date and travel_time
    val formattedTravelDate = trip.travel_date?.let { formatDateOnly(it) } ?: "-"
    val formattedTravelTime = trip.travel_time?.let { formatTimeOnly(it) } ?: "-"

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(8.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Status bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatusChip(status = trip.status ?: "Unknown")
                Text(
                    text = "Trip #${trip.id}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Main details
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    LabelValue(label = "Date", value = formattedTravelDate)
                    LabelValue(label = "Time", value = formattedTravelTime)
                    LabelValue(label = "Destination", value = trip.destination ?: "-")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    LabelValue(label = "Purpose", value = trip.purpose ?: "-")
                    LabelValue(label = "Requested By", value = trip.requested_by ?: "-")
                }
            }
        }
    }
}

@Composable
fun StatusChip(status: String) {
    val (color, text) = when (status.lowercase()) {
        "approved" -> Color(0xFF4CAF50) to "Approved" // Green
        "pending" -> MaterialTheme.colorScheme.tertiary to "Pending"
        "in_progress" -> MaterialTheme.colorScheme.primary to "In Progress"
        "completed" -> MaterialTheme.colorScheme.secondary to "Completed"
        "cancelled" -> MaterialTheme.colorScheme.error to "Cancelled"
        else -> MaterialTheme.colorScheme.surfaceVariant to status
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
fun LabelValue(label: String, value: String) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    Text(
        value,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

private fun formatDateOnly(dateTimeStr: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val date = inputFormat.parse(dateTimeStr)
        date?.let { outputFormat.format(it) } ?: dateTimeStr
    } catch (e: Exception) {
        dateTimeStr
    }
}

private fun formatTimeOnly(dateTimeStr: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        val outputFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val date = inputFormat.parse(dateTimeStr)
        date?.let { outputFormat.format(it) } ?: dateTimeStr
    } catch (e: Exception) {
        dateTimeStr
    }
} 