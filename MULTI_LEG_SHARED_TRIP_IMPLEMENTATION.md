# Multi-Leg Shared Trip Implementation

## Overview

This implementation extends the DriveBroom driver app to support multi-leg shared trips with comprehensive odometer tracking, fuel logging, passenger details, and per-stop execution. The system maintains backward compatibility with existing single-trip functionality.

## 🏗️ Architecture

### Data Models

#### Extended TripDetails
```kotlin
data class TripDetails(
    val id: Int,
    val status: String,
    val travel_date: String,
    val passenger_email: String,
    val date_of_request: String,
    val travel_time: String,
    val destination: String,
    val purpose: String,
    val passengers: String,
    val vehicle: Vehicle?,
    val trip_type: String? = "single", // "single" or "shared"
    val stops: List<TripStop>? = null, // For shared trips
    val current_leg: Int? = null // Current leg index for shared trips
)
```

#### New Shared Trip Models
```kotlin
data class TripStop(
    val id: Int,
    val stop_order: Int,
    val team_name: String,
    val destination: String,
    val passenger_count: Int,
    val passengers: List<String>,
    val pickup_time: String?,
    val dropoff_time: String?,
    val status: String // "pending", "in_progress", "completed"
)

data class SharedTripLeg(
    val leg_id: Int,
    val stop_id: Int,
    val team_name: String,
    val destination: String,
    val passengers: List<String>,
    val odometer_start: Double?,
    val odometer_end: Double?,
    val fuel_start: Double?,
    val fuel_end: Double?,
    val fuel_purchased: Double?,
    val departure_time: String?,
    val arrival_time: String?,
    val status: String // "pending", "in_progress", "completed"
)
```

### API Endpoints

#### New Shared Trip Endpoints
```kotlin
@POST("shared-trips/{tripId}/legs/{legId}/depart")
suspend fun logLegDeparture(
    @Path("tripId") tripId: Int,
    @Path("legId") legId: Int,
    @Body body: LegDepartureRequest
): retrofit2.Response<Unit>

@POST("shared-trips/{tripId}/legs/{legId}/arrive")
suspend fun logLegArrival(
    @Path("tripId") tripId: Int,
    @Path("legId") legId: Int,
    @Body body: LegArrivalRequest
): retrofit2.Response<Unit>

@POST("shared-trips/{tripId}/legs/{legId}/complete")
suspend fun completeLeg(
    @Path("tripId") tripId: Int,
    @Path("legId") legId: Int,
    @Body body: LegCompletionRequest
): retrofit2.Response<Unit>

@GET("shared-trips/{tripId}/legs")
suspend fun getSharedTripLegs(@Path("tripId") tripId: Int): List<SharedTripLeg>
```

## 🎯 User Interface Flow

### 1. Dashboard Screen
- **View Today's Trips**: Shows both single and shared trips
- **View Upcoming Trips**: Displays future scheduled trips
- **View Completed Trips**: Historical trip records
- **Profile & Settings**: Driver information and preferences

### 2. Trip List Screen
Each trip card displays:
- Trip Date & Time
- Vehicle & Driver Info
- Status: Pending, In Progress, Completed
- Trip Type: Single or Shared
- Button: Start Trip

### 3. Trip Details Screen
**For Single Trips**: Existing functionality maintained
**For Shared Trips**: New shared trip overview showing:
- Vehicle Info
- Assigned Teams / Stops
- Capacity Summary
- Passenger Overview
- Button: Begin First Leg

### 4. Leg Execution Screen (Per Stop)
**Top Section**:
- Current Leg: Stop 1 of 3
- Team Name
- Destination
- Passenger Count or List

**Action Buttons**:
- **Depart** → Opens modal:
  - Odometer Start
  - Fuel in Tank
  - Timestamp
  - Confirm Passengers
- **Arrive** → Opens modal:
  - Odometer End
  - Fuel Used
  - Timestamp
  - Confirm Drop-off
- **Complete Leg** → Finalizes leg and moves to next stop

### 5. Next Stop Prompt
After completing a leg:
- "Proceed to next stop?"
- Buttons: Yes → Load next leg / No → Return to Dashboard

### 6. Final Leg: Return to Base
- Depart from Last Stop
- Arrive at Base → Log final odometer, fuel, passenger count
- Complete Trip

### 7. Trip Summary Screen
Displays:
- Total Distance Traveled
- Total Fuel Used
- All Stops Completed
- Passenger Logs
- Button: Submit Final Logs

## 🔧 Implementation Details

### ViewModel Updates

#### TripDetailsViewModel Extensions
```kotlin
// Shared trip state
private val _sharedTripLegs = MutableStateFlow<List<SharedTripLeg>>(emptyList())
val sharedTripLegs: StateFlow<List<SharedTripLeg>> = _sharedTripLegs

private val _currentLegIndex = MutableStateFlow(0)
val currentLegIndex: StateFlow<Int> = _currentLegIndex

private val _isSharedTrip = MutableStateFlow(false)
val isSharedTrip: StateFlow<Boolean> = _isSharedTrip

// New methods for shared trip leg execution
fun logLegDeparture(tripId: Int, legId: Int, odometerStart: Double, fuelStart: Double, passengersConfirmed: List<String>)
fun logLegArrival(tripId: Int, legId: Int, odometerEnd: Double, fuelUsed: Double?, passengersDropped: List<String>)
fun completeLeg(tripId: Int, legId: Int, odometerEnd: Double, fuelEnd: Double, fuelPurchased: Double?, notes: String?)
```

### Repository Extensions

#### DriverRepository New Methods
```kotlin
suspend fun getSharedTripLegs(tripId: Int): List<SharedTripLeg>
suspend fun logLegDeparture(tripId: Int, legId: Int, request: LegDepartureRequest): Result<Unit>
suspend fun logLegArrival(tripId: Int, legId: Int, request: LegArrivalRequest): Result<Unit>
suspend fun completeLeg(tripId: Int, legId: Int, request: LegCompletionRequest): Result<Unit>
```

### UI Components

#### New Screens
1. **SharedTripDetailsScreen**: Overview of all stops and trip statistics
2. **SharedTripLegExecutionScreen**: Per-leg execution with departure/arrival/completion
3. **TripSummaryScreen**: Final trip summary with all completed legs
4. **SharedTripFlowScreen**: Main coordinator for shared trip navigation

#### Navigation Flow
```
TripDetailsScreen (detects trip_type)
    ↓
SharedTripDetailsScreen (overview)
    ↓
SharedTripLegExecutionScreen (per leg)
    ↓
TripSummaryScreen (final summary)
```

## 🔔 Notifications & Feedback

### Push Notifications
- "New Trip Assigned"
- "Reminder: Trip starts in 30 mins"
- "Leg 2 completed successfully"
- "Fuel usage exceeds expected range — please review"

### UI Feedback
- Toast messages for successful actions
- Loading states during API calls
- Error handling with user-friendly messages
- Progress indicators for multi-step processes

## 🧠 Optional Enhancements

### Current Implementation
- ✅ Progress bar: Stop 2 of 4
- ✅ Real-time validation for fuel and odometer inputs
- ✅ Offline mode with sync-on-connect (via existing repository pattern)
- ✅ Backward compatibility with single trips

### Future Enhancements
- Map view per leg
- Admin override for skipped stops or reroutes
- Advanced fuel efficiency tracking
- Passenger signature capture
- Real-time GPS tracking

## 🔄 Backward Compatibility

The implementation maintains full backward compatibility:
- Existing single-trip flow unchanged
- New shared trip functionality only activated when `trip_type == "shared"`
- All existing API endpoints continue to work
- No breaking changes to existing data models

## 📱 Usage Example

### Starting a Shared Trip
1. Driver sees shared trip in dashboard
2. Clicks on trip → SharedTripDetailsScreen opens
3. Views all stops and passenger details
4. Clicks "Begin First Leg" → SharedTripLegExecutionScreen opens
5. Executes each leg: Depart → Arrive → Complete
6. After final leg → TripSummaryScreen opens
7. Submits final logs

### Single Trip (Unchanged)
1. Driver sees single trip in dashboard
2. Clicks on trip → TripDetailsScreen opens (existing flow)
3. Executes single trip as before

## 🚀 Getting Started

### Backend Requirements
1. Implement the new API endpoints for shared trip leg execution
2. Update TripDetails model to include `trip_type`, `stops`, and `current_leg` fields
3. Create database tables for `trip_stops` and `shared_trip_legs`
4. Update trip creation logic to support shared trips

### Frontend Integration
1. The Android app is ready to use with the new shared trip functionality
2. No additional configuration required
3. Shared trips will automatically be detected and routed to the new flow

## 📊 Data Flow

### Leg Execution Flow
```
1. Load Trip Details → Check trip_type
2. If shared → Load Shared Trip Legs
3. Show Shared Trip Details Screen
4. User clicks "Begin First Leg"
5. Show Leg Execution Screen for current leg
6. User executes: Depart → Arrive → Complete
7. Move to next leg or show summary
8. Submit final logs
```

### API Call Sequence
```
GET /driver/trips/{id} → Check trip_type
GET /shared-trips/{id}/legs → Load legs
POST /shared-trips/{id}/legs/{legId}/depart → Log departure
POST /shared-trips/{id}/legs/{legId}/arrive → Log arrival
POST /shared-trips/{id}/legs/{legId}/complete → Complete leg
```

This implementation provides a comprehensive, user-friendly solution for multi-leg shared trips while maintaining the simplicity and reliability of the existing single-trip system.
