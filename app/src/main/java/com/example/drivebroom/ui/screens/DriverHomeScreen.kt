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
import com.example.drivebroom.ui.components.StatusChip

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
    val completedTripsMessage by viewModel.completedTripsMessage.collectAsState()

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
    
    // Debug today's date calculation
    LaunchedEffect(Unit) {
        android.util.Log.d("DriverHomeScreen", "Today's date: $today")
        android.util.Log.d("DriverHomeScreen", "Tomorrow's date: ${Date(today.time + millisInDay)}")
        android.util.Log.d("DriverHomeScreen", "Day after tomorrow: ${Date(today.time + millisInDay - 1)}")
    }
    // In the LazyColumn for today's trips and next trips, filter out completed trips
    fun parseDateOrNull(dateStr: String): Date? {
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        for (p in patterns) {
            try {
                val fmt = SimpleDateFormat(p, Locale.getDefault())
                fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val d = fmt.parse(dateStr)
                if (d != null) return d
            } catch (_: Exception) {}
        }
        return null
    }

    val todaysTrips = trips.filter { trip ->
        if (trip.status?.lowercase() == "completed") return@filter false
        val d = trip.travel_date?.let { parseDateOrNull(it) }
        if (d == null) return@filter true // If unknown format, do not hide it
        !d.before(today) && d.before(Date(today.time + millisInDay))
    }

    val nextTrips = trips.filter { trip ->
        if (trip.status?.lowercase() == "completed") {
            android.util.Log.d("DriverHomeScreen", "Filtering out completed trip: id=${trip.id}, destination=${trip.destination}")
            return@filter false
        }
        val d = trip.travel_date?.let { parseDateOrNull(it) }
        if (d == null) {
            android.util.Log.d("DriverHomeScreen", "Trip with unknown date format: id=${trip.id}, destination=${trip.destination}, date=${trip.travel_date}")
            return@filter true // Show items with unknown date too
        }
        val isAfterToday = d.after(Date(today.time + millisInDay - 1))
        android.util.Log.d("DriverHomeScreen", "Trip date check: id=${trip.id}, destination=${trip.destination}, date=${trip.travel_date}, parsed=${d}, isAfterToday=${isAfterToday}")
        isAfterToday
    }

    // Debug logging for trip filtering
    LaunchedEffect(trips) {
        android.util.Log.d("DriverHomeScreen", "=== TRIP FILTERING DEBUG ===")
        android.util.Log.d("DriverHomeScreen", "Total trips received: ${trips.size}")
        trips.forEachIndexed { index, trip ->
            android.util.Log.d("DriverHomeScreen", "Trip $index: id=${trip.id}, destination=${trip.destination}, date=${trip.travel_date}, status=${trip.status}, type=${trip.trip_type}")
        }
        android.util.Log.d("DriverHomeScreen", "Today's trips: ${todaysTrips.size}")
        android.util.Log.d("DriverHomeScreen", "Next trips: ${nextTrips.size}")
        todaysTrips.forEachIndexed { index, trip ->
            android.util.Log.d("DriverHomeScreen", "Today's trip $index: id=${trip.id}, destination=${trip.destination}")
        }
        nextTrips.forEachIndexed { index, trip ->
            android.util.Log.d("DriverHomeScreen", "Next trip $index: id=${trip.id}, destination=${trip.destination}")
        }
    }

    if (showNextSchedule) {
        android.util.Log.d("DriverHomeScreen", "Showing NextScheduleScreen with ${nextTrips.size} trips")
        NextScheduleScreen(
            driverProfile = driverProfile,
            nextTrips = nextTrips,
            onTripClick = onTripClick,
            onBack = { showNextSchedule = false }
        )
        return
    } else {
        android.util.Log.d("DriverHomeScreen", "Showing main screen with ${todaysTrips.size} today's trips")
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
            // Trip Log Screen
            TripLogScreen(
                completedTrips = completedTrips,
                onBack = { showCompletedTrips = false }
            )
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ExitToApp,
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
                        onClick = { 
                            android.util.Log.d("TripCard", "Clicked trip: id=${trip.id}, destination=${trip.destination}, type=${trip.trip_type}")
                            onTripClick(trip.id) 
                        }
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
    // Debug logging for each TripCard
    LaunchedEffect(trip.id) {
        android.util.Log.d("TripCard", "Rendering TripCard for trip id=${trip.id}, destination=${trip.destination}, type=${trip.trip_type}")
    }
    
    // Format travel_date and travel_time
    val formattedTravelDate = trip.travel_date?.let { formatDateOnly(it) } ?: "-"
    val formattedTravelTime = trip.travel_time?.let { formatTimeOnly(it) } ?: "-"
    val isCompleted = trip.status?.lowercase() == "completed"
    Card(
        onClick = onClick,
        enabled = !isCompleted,
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
                    LabelValue(label = "Requested By", value = trip.requestedBy ?: "-")
                    val isShared =
                        (trip.trip_type?.equals("shared", ignoreCase = true) == true) ||
                        (trip.key?.startsWith("shared_", ignoreCase = true) == true) ||
                        (trip.is_shared_trip == 1) ||
                        (trip.shared_trip_id != null)
                    if (isShared) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Shared") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                }
            }
        }
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
        val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val d = try {
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        var parsed: Date? = null
        for (p in patterns) {
            try {
                val fmt = SimpleDateFormat(p, Locale.getDefault())
                fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
                parsed = fmt.parse(dateTimeStr)
                if (parsed != null) break
            } catch (_: Exception) {}
        }
        parsed
    } catch (_: Exception) { null }
    return d?.let { outputFormat.format(it) } ?: dateTimeStr
}

private fun formatTimeOnly(dateTimeStr: String): String {
        val outputFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val d = try {
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        var parsed: Date? = null
        for (p in patterns) {
            try {
                val fmt = SimpleDateFormat(p, Locale.getDefault())
                fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
                parsed = fmt.parse(dateTimeStr)
                if (parsed != null) break
            } catch (_: Exception) {}
        }
        parsed
    } catch (_: Exception) { null }
    return d?.let { outputFormat.format(it) } ?: dateTimeStr
} 