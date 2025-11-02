package com.example.drivebroom.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
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
import com.example.drivebroom.ui.screens.SharedTripDetailsViewScreen
import com.example.drivebroom.utils.TripNotificationManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverHomeScreen(
    onLogout: () -> Unit,
    driverProfile: DriverProfile?,
    trips: List<Trip>,
    onTripClick: (Int) -> Unit,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    navigationViewModel: com.example.drivebroom.viewmodel.NavigationViewModel? = null
) {
    var showProfile by remember { mutableStateOf(false) }
    // Check if we need to show Next Schedule immediately when composable loads
    var showNextSchedule by remember { 
        mutableStateOf(navigationViewModel?.showNextScheduleRequested?.value == true) 
    }
    var showCompletedTrips by remember { mutableStateOf(false) }
    var selectedCompletedTrip by remember { mutableStateOf<com.example.drivebroom.network.CompletedTrip?>(null) }
    var showSharedTripDetails by remember { mutableStateOf<com.example.drivebroom.network.CompletedTrip?>(null) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showEditProfile by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    var isNavigatingToTripLogs by remember { mutableStateOf(false) }
    
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
    // Track which trip IDs we've already notified about to prevent duplicates
    var notifiedTripIds by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // Fire local notifications for newly assigned trips when the trips prop changes
    LaunchedEffect(trips) {
        val newTrips = trips.filter { it.status?.equals("approved", ignoreCase = true) == true && !notifiedTripIds.contains(it.id) }
        if (newTrips.isNotEmpty()) {
            val notifier = TripNotificationManager(context)
            newTrips.forEach { t ->
                notifier.showTripAssignedNotification(tripId = t.id, destination = t.destination ?: "New Trip")
            }
            notifiedTripIds = notifiedTripIds + newTrips.map { it.id }
        }
    }
    
    // Handle navigation to trip logs - use a simpler approach
    LaunchedEffect(navigationViewModel?.navigateToTripLogs?.value) {
        val shouldNavigate = navigationViewModel?.navigateToTripLogs?.value ?: false
        android.util.Log.d("DriverHomeScreen", "=== NAVIGATION CHECK: shouldNavigate = $shouldNavigate ===")
        if (shouldNavigate) {
            android.util.Log.d("DriverHomeScreen", "=== LOADING COMPLETED TRIPS AND SHOWING TRIP LOGS ===")
            isNavigatingToTripLogs = true
            viewModel.loadCompletedTrips()
            // Add a small delay to show the loading state
            kotlinx.coroutines.delay(500)
            showCompletedTrips = true
            isNavigatingToTripLogs = false
            navigationViewModel?.navigateToTripLogs?.value = false // Reset flag
            android.util.Log.d("DriverHomeScreen", "=== TRIP LOGS NAVIGATION COMPLETE ===")
        }
    }
    
    // Handle navigation back to Next Schedule - check immediately when composable loads
    LaunchedEffect(Unit) {
        val shouldShow = navigationViewModel?.showNextScheduleRequested?.value ?: false
        if (shouldShow) {
            android.util.Log.d("DriverHomeScreen", "=== SHOW NEXT SCHEDULE REQUESTED (IMMEDIATE CHECK) ===")
            android.util.Log.d("DriverHomeScreen", "Setting showNextSchedule = true")
            showNextSchedule = true
            navigationViewModel?.showNextScheduleRequested?.value = false // Reset flag
        }
    }
    
    // Also check immediately on first composition if flag is already set
    if (navigationViewModel?.showNextScheduleRequested?.value == true && !showNextSchedule) {
        LaunchedEffect(Unit) {
            android.util.Log.d("DriverHomeScreen", "=== SHOW NEXT SCHEDULE (SYNC CHECK) ===")
            showNextSchedule = true
            navigationViewModel?.showNextScheduleRequested?.value = false
        }
    }
    
    // Also poll for changes in case flag is set after initial load
    LaunchedEffect(navigationViewModel) {
        while (true) {
            val shouldShow = navigationViewModel?.showNextScheduleRequested?.value ?: false
            if (shouldShow) {
                android.util.Log.d("DriverHomeScreen", "=== SHOW NEXT SCHEDULE REQUESTED (POLLING) ===")
                android.util.Log.d("DriverHomeScreen", "Setting showNextSchedule = true")
                showNextSchedule = true
                navigationViewModel?.showNextScheduleRequested?.value = false // Reset flag
            }
            delay(100) // Check every 100ms
        }
    }
    
    // Show loading overlay when navigating to trip logs
    if (isNavigatingToTripLogs) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading Trip Logs...",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Please wait while we fetch your completed trips",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    
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
    }.sortedBy { trip ->
        // Sort by date (earliest first)
        trip.travel_date?.let { parseDateOrNull(it) } ?: Date(Long.MAX_VALUE)
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
    }.sortedBy { trip ->
        // Sort by date (earliest first)
        trip.travel_date?.let { parseDateOrNull(it) } ?: Date(Long.MAX_VALUE)
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

    // Check flag synchronously before rendering to avoid showing main screen first
    val shouldShowNextScheduleNow = navigationViewModel?.showNextScheduleRequested?.value == true || showNextSchedule
    if (shouldShowNextScheduleNow) {
        // Reset flag if it was set
        if (navigationViewModel?.showNextScheduleRequested?.value == true) {
            navigationViewModel?.showNextScheduleRequested?.value = false
        }
        if (!showNextSchedule) {
            // Set state for next recomposition
            showNextSchedule = true
        }
        android.util.Log.d("DriverHomeScreen", "Showing NextScheduleScreen with ${nextTrips.size} trips")
        NextScheduleScreen(
            driverProfile = driverProfile,
            nextTrips = nextTrips,
            onTripClick = { tripId ->
                // Set flag to track we came from Next Schedule - set it right before calling onTripClick
                android.util.Log.d("DriverHomeScreen", "=== TRIP CLICKED FROM NEXT SCHEDULE ===")
                android.util.Log.d("DriverHomeScreen", "Trip ID: $tripId")
                android.util.Log.d("DriverHomeScreen", "cameFromNextSchedule BEFORE setting: ${navigationViewModel?.cameFromNextSchedule?.value}")
                navigationViewModel?.cameFromNextSchedule?.value = true
                android.util.Log.d("DriverHomeScreen", "cameFromNextSchedule AFTER setting: ${navigationViewModel?.cameFromNextSchedule?.value}")
                // Call onTripClick after setting the flag
                onTripClick(tripId)
                // Verify flag is still set after onTripClick
                android.util.Log.d("DriverHomeScreen", "cameFromNextSchedule AFTER onTripClick: ${navigationViewModel?.cameFromNextSchedule?.value}")
            },
            onBack = { showNextSchedule = false }
        )
        return
    } else {
        android.util.Log.d("DriverHomeScreen", "Showing main screen with ${todaysTrips.size} today's trips")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val photoUrl = driverProfile?.profile_photo_url
                        var bmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                        LaunchedEffect(photoUrl) {
                            bmp = null
                            if (!photoUrl.isNullOrBlank()) {
                                try {
                                    val bytes = withContext(Dispatchers.IO) { URL(photoUrl).openStream().use { it.readBytes() } }
                                    bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                } catch (_: Exception) {}
                            }
                        }
                        if (bmp != null) {
                            Image(
                                bitmap = bmp!!.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(driverProfile?.name ?: "Driver")
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = { showProfile = true }) {
                        Icon(Icons.Default.Person, contentDescription = "Profile")
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
        } else if (showSharedTripDetails != null) {
            // Shared Trip Details View Screen
            SharedTripDetailsViewScreen(
                trip = showSharedTripDetails!!,
                onBack = { showSharedTripDetails = null }
            )
        } else if (selectedCompletedTrip != null) {
            // Completed Trip Details Screen with detailed legs
            CompletedTripDetailsWithLegs(
                trip = selectedCompletedTrip!!,
                onBack = { selectedCompletedTrip = null },
                onViewSharedTripDetails = { tripId ->
                    // Navigate to shared trip details
                    android.util.Log.d("DriverHomeScreen", "View shared trip details for trip ID: $tripId")
                    // Find the completed trip with this ID and show shared trip details
                    val sharedTrip = completedTrips.find { it.id == tripId }
                    if (sharedTrip != null) {
                        showSharedTripDetails = sharedTrip
                        selectedCompletedTrip = null // Hide the completed trip details
                    }
                },
                viewModel = viewModel
            )
        } else if (showCompletedTrips) {
            // Trip Log Screen
            TripLogScreen(
                completedTrips = completedTrips,
                onBack = { showCompletedTrips = false },
                onTripClick = { trip ->
                    selectedCompletedTrip = trip
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                item {
                    Column(modifier = Modifier.padding(bottom = 24.dp)) {
                    Text(
                            text = "Driver's Trip Ticket",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
                        Text("Trip Logs")
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

    if (showSettings) {
        SettingsScreen(
            onBack = { showSettings = false },
            onEditProfile = { showSettings = false; showEditProfile = true },
            onChangePassword = { showSettings = false; showChangePassword = true },
            onLogout = { showSettings = false; showLogoutConfirm = true }
        )
        return
    }

    if (showEditProfile) {
        EditProfileScreen(
            currentName = driverProfile?.name,
            currentPhone = driverProfile?.phone,
            onBack = { showEditProfile = false },
            onSaved = { showEditProfile = false }
        )
        return
    }

    if (showChangePassword) {
        ChangePasswordScreen(
            onBack = { showChangePassword = false },
            onChanged = { showChangePassword = false }
        )
        return
    }

    if (showProfile && driverProfile != null) {
        AlertDialog(
            onDismissRequest = { showProfile = false },
            title = { Text("Driver Profile") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val photoUrl = driverProfile.profile_photo_url
                    var bmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                    LaunchedEffect(photoUrl) {
                        bmp = null
                        if (!photoUrl.isNullOrBlank()) {
                            try {
                                val bytes = withContext(Dispatchers.IO) { java.net.URL(photoUrl).openStream().use { it.readBytes() } }
                                bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            } catch (_: Exception) {}
                        }
                    }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Name: ${driverProfile.name}")
                        Text("Email: ${driverProfile.email}")
                        Text("Phone: ${driverProfile.phone}")
                        Text("License: ${driverProfile.license_number ?: "-"}")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProfile = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Logout confirmation dialog
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Confirm Logout") },
            text = { Text("Are you sure you want to log out?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    onLogout()
                }) { Text("Logout") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel") }
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
                title = { Text("Next Schedule") }
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
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Back",
                        style = MaterialTheme.typography.titleMedium
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
    val formattedTravelTime = when {
        !trip.travel_time.isNullOrBlank() -> {
            val t = trip.travel_time!!
            if (t.startsWith("00:00")) "-" else formatTimeOnly(t)
        }
        !trip.travel_date.isNullOrBlank() && trip.travel_date!!.contains('T') -> {
            val dt = trip.travel_date!!
            if (dt.substringAfter('T').startsWith("00:00")) "-" else formatTimeOnly(dt)
        }
        else -> "-"
    }
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
                    LabelValue(label = "Requested By", value = trip.requestedBy ?: "-")
                    val isShared =
                        (trip.trip_type?.equals("shared", ignoreCase = true) == true) ||
                        (trip.key?.startsWith("shared_", ignoreCase = true) == true) ||
                        (trip.is_shared_trip == 1) ||
                        (trip.shared_trip_id != null)
                    
                    // Debug logging for trip type detection
                    android.util.Log.d("TripCard", "=== TRIP TYPE DETECTION DEBUG ===")
                    android.util.Log.d("TripCard", "Trip ID: ${trip.id}, Destination: ${trip.destination}")
                    android.util.Log.d("TripCard", "trip_type: '${trip.trip_type}'")
                    android.util.Log.d("TripCard", "key: '${trip.key}'")
                    android.util.Log.d("TripCard", "is_shared_trip: ${trip.is_shared_trip}")
                    android.util.Log.d("TripCard", "shared_trip_id: ${trip.shared_trip_id}")
                    android.util.Log.d("TripCard", "isShared result: $isShared")
                    
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
                if (parsed != null) {
                    android.util.Log.d("formatDateOnly", "Successfully parsed '$dateTimeStr' with pattern '$p' -> $parsed")
                    break
                }
            } catch (e: Exception) {
                android.util.Log.d("formatDateOnly", "Failed to parse '$dateTimeStr' with pattern '$p': ${e.message}")
            }
        }
        parsed
    } catch (e: Exception) {
        android.util.Log.e("formatDateOnly", "Error parsing '$dateTimeStr': ${e.message}")
        null 
    }
    val result = d?.let { outputFormat.format(it) } ?: dateTimeStr
    android.util.Log.d("formatDateOnly", "Input: '$dateTimeStr' -> Output: '$result'")
    return result
}

private fun formatTimeOnly(dateTimeStr: String): String {
    val outputFormat = SimpleDateFormat("h:mm a", Locale.getDefault()).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }
    val normalized = dateTimeStr.trim().let { if (it.endsWith('z')) it.dropLast(1) + 'Z' else it }
    val d = try {
        val patterns = listOf(
            // Full datetime variants
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd HH:mm:ss",
            // Time-only variants
            "HH:mm:ss.SSSSSSSS'Z'",
            "HH:mm:ss.SSSSSS'Z'",
            "HH:mm:ss.SSS'Z'",
            "HH:mm:ss'Z'",
            "HH:mm:ss.SSSSSSSS",
            "HH:mm:ss.SSSSSS",
            "HH:mm:ss.SSS",
            "HH:mm:ss"
        )
        var parsed: Date? = null
        for (p in patterns) {
            try {
                val fmt = SimpleDateFormat(p, Locale.getDefault())
                fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
                parsed = fmt.parse(normalized)
                if (parsed != null) break
            } catch (_: Exception) {}
        }
        parsed
    } catch (_: Exception) { null }
    return d?.let { outputFormat.format(it) } ?: dateTimeStr
}