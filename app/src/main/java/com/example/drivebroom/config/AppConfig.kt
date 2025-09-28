package com.example.drivebroom.config

object AppConfig {
    // For local development (if running on Android Emulator)
    //const val API_BASE_URL = "http://10.0.2.2:8000/api/"
    
    // For local development (if running on physical device)
    // Try different ports if 8000 doesn't work:
    //const val API_BASE_URL = "http://192.168.254.109:8000/api/"  // Default Laravel port
    //const val API_BASE_URL = "http://192.168.254.111:3000/api/"  // Node.js default
    //const val API_BASE_URL = "http://192.168.254.111:5000/api/"  // Flask default
    const val API_BASE_URL = "http://192.168.254.132:8000/api/"  // Alternative port
    
    // For production
   // const val API_BASE_URL = "https://tripsystem.online/api/"
} 
