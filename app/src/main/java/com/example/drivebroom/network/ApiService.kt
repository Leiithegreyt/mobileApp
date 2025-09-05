package com.example.drivebroom.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.*

interface ApiService {
    @POST("driver/login")
    suspend fun loginDriver(@Body loginRequest: LoginRequest): LoginResponse

    @GET("me")
    suspend fun getDriverProfile(): DriverProfile

    @GET("driver/trips")
    suspend fun getAssignedTrips(): AssignedTripsResponse

    @GET("driver/trips/{id}")
    suspend fun getTripDetails(@Path("id") tripId: Int): TripDetails

    @GET("driver/trips/completed")
    suspend fun getCompletedTrips(@Header("Authorization") token: String): CompletedTripsResponse

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
    @SerializedName("requested_by") val requestedBy: String?
)

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
    val vehicle: Vehicle?
)

data class CompletedTrip(
    @SerializedName("id") val id: Int,
    @SerializedName("driver_id") val driverId: Int,
    @SerializedName("status") val status: String?,
    @SerializedName("destination") val destination: String?,
    @SerializedName("requested_by") val requestedBy: String?,
    @SerializedName("vehicle_info") val vehicleInfo: Vehicle?,
    @SerializedName("formatted_travel_date") val formattedTravelDate: String?,
    @SerializedName("formatted_created_at") val formattedCreatedAt: String?,
    @SerializedName("travel_date") val travelDate: String?,
    @SerializedName("travel_time") val travelTime: String?,
    @SerializedName("purpose") val purpose: String?,
    @SerializedName("passengers") val passengers: String?
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
    val odometer: Double,
    val time_departure: String,
    val departure: String,
    val time_arrival: String?,
    val arrival: String?
)
data class ReturnBody(
    val fuel_balance_start: Double,
    val fuel_purchased: Double,
    val fuel_used: Double,
    val fuel_balance_end: Double,
    val passenger_details: List<PassengerDetail>,
    val driver_signature: String,
    val odometer_arrival: Double,
    val itinerary: List<ItineraryLegDto>
)
data class PassengerDetail(
    val name: String,
    val destination: String,
    val signature: String
)

data class CompletedTripsResponse(
    @SerializedName("trips") val trips: List<CompletedTrip>,
    @SerializedName("count") val count: Int?,
    @SerializedName("message") val message: String? = null
)

data class AssignedTripsResponse(
    val trips: List<Trip>,
    val message: String,
    val count: Int
) 