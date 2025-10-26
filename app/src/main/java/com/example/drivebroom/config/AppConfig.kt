package com.example.drivebroom.config

object AppConfig {
    // Development base URL(s)
    const val DEV_API_BASE_URL = "http://10.121.22.107:8000/api/" // Local/LAN backend
    // Production base URL
    const val PROD_API_BASE_URL = "https://tripsystem.online/api/"

    // Toggle manually if BuildConfig isn't available in this context
    // Set to true to use production base URL
    const val USE_PRODUCTION = false

    // Active base URL
    val API_BASE_URL: String
        get() = if (USE_PRODUCTION) PROD_API_BASE_URL else DEV_API_BASE_URL
} 
