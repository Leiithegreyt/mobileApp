package com.example.drivebroom.network

import com.example.drivebroom.config.AppConfig
import com.example.drivebroom.utils.TokenManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import android.util.Log
import okio.Buffer
import okhttp3.Response
import okhttp3.Interceptor

class NetworkClient(private val tokenManager: TokenManager) {
    
    // Global 401 error handler
    private val authErrorHandler = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        
        if (response.code == 401) {
            Log.w("NetworkClient", "Received 401 Unauthorized - clearing token")
            tokenManager.clearToken()
            // The callback will be triggered by clearToken()
        }
        
        response
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .addInterceptor(authErrorHandler) // Add 401 handler first
        .addInterceptor { chain ->
            val token = tokenManager.getToken()
            Log.d("NetworkClient", "Using token: $token")
            val request = chain.request().newBuilder()
                .addHeader("Accept", "application/json")
                .apply {
                    token?.let {
                        addHeader("Authorization", "Bearer $it")
                    }
                }
                .build()
            chain.proceed(request)
        }
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()
            Log.d("NetworkClient", "Making API Call: $url")
            Log.d("NetworkClient", "Request headers: ${request.headers}")
            Log.d("NetworkClient", "Request method: ${request.method}")
            
            try {
                val response = chain.proceed(request)
                Log.d("NetworkClient", "Response code: ${response.code}")
                Log.d("NetworkClient", "Response headers: ${response.headers}")
                
                if (response.isSuccessful) {
                    Log.d("NetworkClient", "✅ SUCCESS: API call successful")
                } else {
                    Log.w("NetworkClient", "⚠️ WARNING: Response code ${response.code}")
                    val errorPreview = try {
                        response.peekBody(1024 * 1024).string()
                    } catch (e: Exception) {
                        "Unable to read error body: ${e.message}"
                    }
                    Log.e("NetworkClient", "Error body for $url: $errorPreview")
                }
                
                response
            } catch (e: java.net.ConnectException) {
                Log.e("NetworkClient", "❌ CONNECTION FAILED: Cannot connect to server at $url")
                Log.e("NetworkClient", "Check if server is running on the correct port")
                Log.e("NetworkClient", "Error: ${e.message}")
                throw e
            } catch (e: java.net.SocketTimeoutException) {
                Log.e("NetworkClient", "❌ TIMEOUT: Server at $url is not responding")
                Log.e("NetworkClient", "Check if server is running and accessible")
                Log.e("NetworkClient", "Error: ${e.message}")
                throw e
            } catch (e: Exception) {
                Log.e("NetworkClient", "❌ NETWORK ERROR: $url")
                Log.e("NetworkClient", "Error: ${e.message}", e)
                throw e
            }
        }
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(AppConfig.API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)
} 