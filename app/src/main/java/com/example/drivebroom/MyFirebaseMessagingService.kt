package com.example.drivebroom

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.example.drivebroom.R
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import com.example.drivebroom.repository.DriverRepository
import com.example.drivebroom.utils.TokenManager
import com.example.drivebroom.network.NetworkClient

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val tripId = remoteMessage.data["trip_id"]
        val titleFromData = remoteMessage.data["title"]
        val bodyFromData = remoteMessage.data["body"]
        val notif = remoteMessage.notification
        val title = notif?.title ?: titleFromData ?: "New Assignment"
        val body = notif?.body ?: bodyFromData ?: "You have a new trip assignment."
        sendNotification(title, body, tripId)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        try {
            Log.d("FCM", "New FCM token: $token")
            // Save locally
            val tokenManager = TokenManager(applicationContext)
            // Do not overwrite API token; store FCM token separately if supported
            // Attempt immediate backend update if user is logged in
            val networkClient = NetworkClient(tokenManager)
            val repo = DriverRepository(networkClient.apiService)
            // Fire and forget
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try { repo.updateFcmToken(token) } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e("FCM", "Failed to process new token", e)
        }
    }

    private fun sendNotification(title: String?, messageBody: String?, tripId: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            tripId?.let { putExtra("trip_id", it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "assignment_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Assignments", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title ?: "New Assignment")
            .setContentText(messageBody ?: "You have a new trip assignment.")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
        notificationManager.notify(0, notificationBuilder.build())
    }
} 