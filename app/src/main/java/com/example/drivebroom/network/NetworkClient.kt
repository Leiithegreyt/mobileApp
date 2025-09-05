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
            val request = chain.request()
            val url = request.url.toString()
            Log.d("NetworkClient", "Making API Call: $url")
            Log.d("NetworkClient", "Request headers: ${request.headers}")
            Log.d("NetworkClient", "Request method: ${request.method}")
            Log.d("NetworkClient", "Request body: ${request.body}")
            if (request.body != null) {
                val buffer = Buffer()
                request.body!!.writeTo(buffer)
                Log.d("NetworkClient", "Request body content: ${buffer.readUtf8()}")
            }
            
            try {
                val response = chain.proceed(request)
                Log.d("NetworkClient", "Response code: ${response.code}")
                Log.d("NetworkClient", "Response headers: ${response.headers}")
                
                val responseBody = response.body
                val responseString = responseBody?.string()
                Log.d("NetworkClient", "Response body for $url: $responseString")
                
                // Recreate the response body since we consumed it
                val newBody = responseString?.let { 
                    okhttp3.ResponseBody.create(responseBody.contentType(), it)
                }
                response.newBuilder().body(newBody).build()
            } catch (e: Exception) {
                Log.e("NetworkClient", "Network error for $url: ${e.message}", e)
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