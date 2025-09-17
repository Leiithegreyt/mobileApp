package com.example.drivebroom.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.util.Log

class TokenManager(context: Context) {
    
    // Callback for authentication failures
    private var onAuthFailureCallback: (() -> Unit)? = null
    
    fun setOnAuthFailureCallback(callback: () -> Unit) {
        onAuthFailureCallback = callback
    }
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveToken(token: String) {
        Log.d("TokenManager", "Saving token: $token")
        sharedPreferences.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? {
        val token = sharedPreferences.getString(KEY_TOKEN, null)
        Log.d("TokenManager", "Getting token: $token")
        return token
    }

    fun clearToken() {
        Log.d("TokenManager", "Clearing token")
        sharedPreferences.edit().remove(KEY_TOKEN).apply()
        // Trigger auth failure callback if set
        onAuthFailureCallback?.invoke()
    }
    
    fun isTokenValid(): Boolean {
        val token = getToken()
        return !token.isNullOrBlank()
    }

    companion object {
        private const val KEY_TOKEN = "auth_token"
    }
} 