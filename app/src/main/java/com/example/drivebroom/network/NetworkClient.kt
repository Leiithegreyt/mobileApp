package com.example.drivebroom.network

import com.example.drivebroom.config.AppConfig
import com.example.drivebroom.utils.TokenManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import android.util.Log

class NetworkClient(private val tokenManager: TokenManager) {
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
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
            val response = chain.proceed(chain.request())
            val url = chain.request().url.toString()
            Log.d("NetworkClient", "API Call: $url")
            Log.d("NetworkClient", "Request headers: ${chain.request().headers}")
            Log.d("NetworkClient", "Request method: ${chain.request().method}")
            Log.d("NetworkClient", "Response code: ${response.code}")
            
            try {
                val responseBody = response.body
                val responseString = responseBody?.string()
                Log.d("NetworkClient", "Response for $url: $responseString")
                
                // Recreate the response body since we consumed it
                val newBody = responseString?.let { 
                    okhttp3.ResponseBody.create(responseBody.contentType(), it)
                }
                response.newBuilder().body(newBody).build()
            } catch (e: Exception) {
                Log.e("NetworkClient", "Error reading response for $url: ${e.message}")
                response
            }
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(AppConfig.API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)
} 