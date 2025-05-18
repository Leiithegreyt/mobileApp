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

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val tripId = remoteMessage.data["trip_id"]
        remoteMessage.notification?.let {
            sendNotification(it.title, it.body, tripId)
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