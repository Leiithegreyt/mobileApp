package com.example.drivebroom.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.*
import com.google.gson.JsonElement

interface ApiService {
    @POST("driver/login")
    suspend fun loginDriver(@Body loginRequest: LoginRequest): JsonElement

    @GET("me")
    suspend fun getDriverProfile(): DriverProfile

    @GET("trips")
    suspend fun getAssignedTrips(): AssignedTripsResponse

    @GET("trips/{id}")
    suspend fun getTripDetails(@Path("id") tripId: Int): com.google.gson.JsonElement

    @GET("driver/trips/completed")
    suspend fun getCompletedTrips(): CompletedTripsResponse

    @POST("logout")
    suspend fun logout(): LogoutResponse

    @POST("driver/fcm-token")
    suspend fun updateFcmToken(@Body request: FcmTokenRequest): retrofit2.Response<Unit>

    @POST("trips/{tripId}/departure")
    suspend fun logDeparture(
        @Path("tripId") tripId: Int,
        @Body body: DepartureBody
    ): retrofit2.Response<Unit>

    @POST("trips/{tripId}/arrival")
    suspend fun logArrival(
        @Path("tripId") tripId: Int,
        @Body body: ArrivalBody
    ): retrofit2.Response<Unit>

    @POST("trips/{tripId}/return")
    suspend fun logReturn(
        @Path("tripId") tripId: Int,
        @Body body: ReturnBody
    ): retrofit2.Response<Unit>

    // Unified API endpoints for leg execution (single + shared)
    @POST("trips/{tripId}/legs/{legId}/depart")
    suspend fun logLegDeparture(
        @Path("tripId") tripId: Int,
        @Path("legId") legId: Int,
        @Body body: LegDepartureRequest
    ): retrofit2.Response<Unit>

    @POST("trips/{tripId}/legs/{legId}/arrive")
    suspend fun logLegArrival(
        @Path("tripId") tripId: Int,
        @Path("legId") legId: Int,
        @Body body: LegArrivalRequest
    ): retrofit2.Response<Unit>

    @POST("trips/{tripId}/legs/{legId}/complete")
    suspend fun completeLeg(
        @Path("tripId") tripId: Int,
        @Path("legId") legId: Int,
        @Body body: LegCompletionRequest
    ): retrofit2.Response<Unit>

    @GET("trips/{tripId}/legs")
    suspend fun getTripLegs(@Path("tripId") tripId: Int): List<RawSharedTripLeg>

    // Finalize full trip (single or shared) via unified endpoint
    @POST("trips/{tripId}/submit")
    suspend fun submitSharedTrip(@Path("tripId") tripId: Int, @Body request: TripSubmissionRequest): retrofit2.Response<Unit>
}

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val access_token: String?,
    val user: User?
)

data class User(
    val id: Int,
    val name: String,
    val email: String
    // Add other fields as needed
)

data class DriverProfile(
    val id: Int,
    val name: String,
    val email: String,
    val phone: String
)

data class Trip(
    val id: Int,
    val destination: String?,
    val purpose: String?,
    val travel_date: String?,
    val travel_time: String?,
    val status: String?,
    val pickup_location: String?,
    @SerializedName("requested_by") val requestedBy: String?,
    val trip_type: String? = null, // "single" or "shared"
    val key: String? = null, // optional stable key like "shared_{id}"
    val is_shared_trip: Int? = null, // 1 or 0 (backend-provided)
    val shared_trip_id: Int? = null
)

data class TripDetails(
    val id: Int,
    val status: String,
    val travel_date: String,
    val passenger_email: String? = null,
    val date_of_request: String? = null, // Made nullable to handle API responses without this field
    val travel_time: String? = null, // Made nullable to handle API responses without this field
    val destination: String? = null, // Made nullable to handle API responses without this field
    val purpose: String? = null,
    val passengers: com.google.gson.JsonElement? = null, // Made nullable to handle API responses without this field
    val vehicle: Vehicle?,
    val trip_type: String? = "single", // "single" or "shared"
    val stops: List<TripStop>? = null, // Legacy stops
    val legs: List<SharedTripLeg>? = null, // New: legs from shared trips
    val current_leg: Int? = null // Current leg index for shared trips
)

data class TripDetailsResponse(
    val trip: TripDetails,
    val message: String? = null
)

data class CompletedTrip(
    @SerializedName("id") val id: Int,
    @SerializedName("trip_type") val tripType: String?, // "individual" or "shared"
    @SerializedName("driver_id") val driverId: Int?,
    @SerializedName("status") val status: String?,
    @SerializedName("destination") val destination: String?,
    @SerializedName("requested_by") val requestedBy: String?,
    @SerializedName("vehicle_info") val vehicleInfo: Vehicle?,
    @SerializedName("formatted_travel_date") val formattedTravelDate: String?,
    @SerializedName("formatted_created_at") val formattedCreatedAt: String?,
    @SerializedName("travel_date") val travelDate: String?,
    @SerializedName("travel_time") val travelTime: String?,
    @SerializedName("purpose") val purpose: String?,
    @SerializedName("passengers") val passengers: String?,
    // Shared trip specific fields
    @SerializedName("total_passengers") val totalPassengers: Int?,
    @SerializedName("stops") val stops: List<TripStop>?,
    @SerializedName("legs") val legs: List<SharedTripLeg>?
)

data class Vehicle(
    @SerializedName("id") val id: Int,
    @SerializedName("plate_number") val plateNumber: String?,
    @SerializedName("model") val model: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("capacity") val capacity: Int?,
    @SerializedName("status") val status: String?,
    @SerializedName("last_maintenance_date") val lastMaintenanceDate: String?,
    @SerializedName("next_maintenance_date") val nextMaintenanceDate: String?
)

data class LogoutResponse(
    val success: Boolean,
    val message: String
)

data class FcmTokenRequest(val fcm_token: String)

// Trip action payloads

data class DepartureBody(
    @SerializedName("odometer_start") val odometerStart: Double, 
    @SerializedName("fuel_balance_start") val fuelBalanceStart: Double
)
data class ArrivalBody(val odometer_arrival: Double)
data class ItineraryLegDto(
    val odometer_start: Double, // Required - odometer reading at departure
    val odometer: Double,
    val odometer_arrival: Double? = null, // NEW: Odometer reading at destination arrival
    val time_departure: String,
    val departure: String,
    val time_arrival: String,
    val arrival: String
)
data class ReturnBody(
    val fuel_balance_start: Double,
    val fuel_purchased: Double,
    val fuel_used: Double,
    val fuel_balance_end: Double,
    val distance_travelled: Double? = null, // Optional - can be calculated from itinerary
    val passenger_details: List<PassengerDetail>,
    val driver_signature: String? = null, // Optional - can be empty for now
    val return_time: String? = null, // Optional - can be set to current time
    val itinerary: List<ItineraryLegDto>
)
data class PassengerDetail(
    val name: String,
    val destination: String? = null, // Optional
    val signature: String? = null // Optional
)

data class CompletedTripsResponse(
    @SerializedName("trips") val trips: List<CompletedTrip>,
    @SerializedName("count") val count: Int?,
    @SerializedName("individual_count") val individualCount: Int?,
    @SerializedName("shared_count") val sharedCount: Int?,
    @SerializedName("message") val message: String? = null
)

data class AssignedTripsResponse(
    val trips: List<Trip>,
    val message: String,
    val count: Int
)

// New data classes for multi-leg shared trips
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
    val purpose: String? = null, // Purpose for this specific leg
    val passengers: List<String>?,
    val odometer_start: Double?,
    val odometer_end: Double?,
    val fuel_start: Double?,
    val fuel_end: Double?,
    val fuel_used: Double?,
    val fuel_purchased: Double?,
    val notes: String?,
    val departure_time: String?,
    val arrival_time: String?,
    val departure_location: String? = null,
    val arrival_location: String? = null,
    val status: String // "pending", "in_progress", "completed"
)

// Raw DTO to handle backend returning passengers as objects
data class RawSharedTripLeg(
    val leg_id: Int,
    val stop_id: Int,
    val team_name: String,
    val destination: String,
    val purpose: String? = null,
    val passengers: com.google.gson.JsonElement?,
    val odometer_start: Double?,
    val odometer_end: Double?,
    val fuel_start: Double?,
    val fuel_end: Double?,
    val fuel_used: Double?,
    val fuel_purchased: Double?,
    val notes: String?,
    val departure_time: String?,
    val arrival_time: String?,
    val departure_location: String? = null,
    val arrival_location: String? = null,
    val status: String
)

// API endpoints for shared trip leg execution
data class LegDepartureRequest(
    val odometer_start: Double,
    val fuel_start: Double,
    val passengers_confirmed: List<String>,
    val departure_time: String,
    val departure_location: String,
    val manifest_override_reason: String? = null
)

data class LegArrivalRequest(
    val odometer_end: Double,
    val fuel_used: Double?,
    val fuel_end: Double,
    val passengers_dropped: List<String>,
    val arrival_time: String,
    val arrival_location: String,
    val fuel_purchased: Double? = null,
    val notes: String? = null
)

data class LegCompletionRequest(
    @SerializedName("final_odometer") val final_odometer: Double,
    @SerializedName("final_fuel") val final_fuel: Double,
    @SerializedName("distance_travelled") val distance_travelled: Double,
    @SerializedName("fuel_used") val fuel_used: Double,
    @SerializedName("fuel_purchased") val fuel_purchased: Double?,
    @SerializedName("notes") val notes: String?
)

data class TripSubmissionRequest(
    @SerializedName("final_odometer") val final_odometer: Double,
    @SerializedName("final_fuel") val final_fuel: Double,
    @SerializedName("total_distance") val total_distance: Double,
    @SerializedName("total_fuel_used") val total_fuel_used: Double,
    @SerializedName("total_fuel_purchased") val total_fuel_purchased: Double?,
    @SerializedName("notes") val notes: String?
) 