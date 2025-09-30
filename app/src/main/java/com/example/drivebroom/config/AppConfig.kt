package com.example.drivebroom.config

object AppConfig {
    // Development base URL(s)
    const val DEV_API_BASE_URL = "http://192.168.254.109:8000/api/" // Local/LAN backend
    // Production base URL
    const val PROD_API_BASE_URL = "https://tripsystem.online/api/"

    // Active base URL (auto: debug → DEV, release → PROD)
    val API_BASE_URL: String
        get() = if (com.example.drivebroom.BuildConfig.DEBUG) DEV_API_BASE_URL else PROD_API_BASE_URL
} 
