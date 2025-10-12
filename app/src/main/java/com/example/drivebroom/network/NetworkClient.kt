package com.example.drivebroom.network

import com.example.drivebroom.config.AppConfig
import com.example.drivebroom.utils.TokenManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import com.google.gson.GsonBuilder
import okhttp3.ResponseBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.TimeUnit
import android.util.Log
import okio.Buffer
import okhttp3.Response
import okhttp3.Interceptor
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSocketFactory

class NetworkClient(private val tokenManager: TokenManager) {
    
    // TEMPORARY: Trust all SSL certificates for production domain
    private fun getUnsafeOkHttpClient(): OkHttpClient.Builder {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        val sslSocketFactory: SSLSocketFactory = sslContext.socketFactory

        return OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
    }
    
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
    
    private val okHttpClient = getUnsafeOkHttpClient()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .addInterceptor(authErrorHandler) // Add 401 handler first
        // Sanitize any non-JSON preamble from server responses before parsing
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val body = response.body
            if (body == null) return@addInterceptor response

            return@addInterceptor try {
                val raw = body.string()
                val cleaned = sanitizeJson(raw)
                val mediaType = body.contentType()?.toString()?.toMediaTypeOrNull()
                val newBody: ResponseBody = cleaned.toResponseBody(mediaType)
                response.newBuilder().body(newBody).build()
            } catch (e: Exception) {
                response
            }
        }
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
        // Handle non-JSON or plain text gracefully
        .addConverterFactory(ScalarsConverterFactory.create())
        // Lenient JSON to tolerate minor formatting issues
        .addConverterFactory(
            GsonConverterFactory.create(
                GsonBuilder()
                    .setLenient()
                    .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    // Handle Trip objects with string IDs like "shared_2"
                    .registerTypeAdapter(Trip::class.java, TripDeserializer())
                    // Coerce passengers objects to string names for legs endpoint
                    .registerTypeAdapter(
                        java.lang.reflect.Type::class.java,
                        com.google.gson.JsonDeserializer { json, typeOfT, _ -> json }
                    )
                    .create()
            )
        )
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)

    private fun sanitizeJson(input: String): String {
        if (input.isEmpty()) return input
        // Remove UTF-8 BOM if present
        var s = if (input.isNotEmpty() && input[0] == '\uFEFF') input.substring(1) else input
        // Drop non-JSON preamble lines until we hit a line starting with { or [
        val lines = s.split("\n", "\r\n")
        val startIdx = lines.indexOfFirst { line ->
            val t = line.trimStart()
            t.startsWith("{") || t.startsWith("[")
        }
        if (startIdx >= 0) {
            return lines.drop(startIdx).joinToString("\n")
        }
        return s
    }
} 