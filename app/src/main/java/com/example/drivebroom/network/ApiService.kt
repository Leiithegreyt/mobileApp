package com.example.drivebroom.network

import retrofit2.http.*

interface ApiService {
    @POST("driver/login")
    suspend fun loginDriver(@Body loginRequest: LoginRequest): LoginResponse

    @GET("me")
    suspend fun getDriverProfile(): DriverProfile

    @GET("driver/trips")
    suspend fun getAssignedTrips(): List<Trip>

    @GET("driver/trips/{id}")
    suspend fun getTripDetails(@Path("id") tripId: Int): TripDetails

    @POST("logout")
    suspend fun logout(): LogoutResponse

    @POST("driver/fcm-token")
    suspend fun updateFcmToken(@Body request: FcmTokenRequest): retrofit2.Response<Unit>
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
    val requested_by: String?
)

data class TripDetails(
    val id: Int,
    val pickup_location: String,
    val dropoff_location: String,
    val scheduled_time: String,
    val status: String,
    val passenger_name: String,
    val passenger_phone: String
)

data class LogoutResponse(
    val success: Boolean,
    val message: String
)

data class FcmTokenRequest(val fcm_token: String) 