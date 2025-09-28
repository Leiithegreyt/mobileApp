package com.example.drivebroom.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.drivebroom.MainActivity
import com.example.drivebroom.R

class TripNotificationManager(private val context: Context) {
    
    companion object {
        const val CHANNEL_ID = "trip_notifications"
        const val NOTIFICATION_ID_DEPARTURE = 1001
        const val NOTIFICATION_ID_ARRIVAL = 1002
        const val NOTIFICATION_ID_COMPLETION = 1003
        const val NOTIFICATION_ID_REMINDER = 1004
        const val NOTIFICATION_ID_FUEL_WARNING = 1005
    }
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Trip Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for trip updates and reminders"
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    fun showTripAssignedNotification(tripId: Int, destination: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("trip_id", tripId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use your app icon
            .setContentTitle("New Trip Assigned")
            .setContentText("Trip to $destination is ready")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        with(NotificationManagerCompat.from(context)) {
            notify(tripId, notification)
        }
    }
    
    fun showTripReminderNotification(tripId: Int, destination: String, minutesUntilStart: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("trip_id", tripId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Trip Reminder")
            .setContentText("Trip to $destination starts in $minutesUntilStart minutes")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        with(NotificationManagerCompat.from(context)) {
            notify(NOTIFICATION_ID_REMINDER, notification)
        }
    }
    
    fun showLegCompletedNotification(legNumber: Int, teamName: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Leg Completed")
            .setContentText("Leg $legNumber for $teamName completed successfully")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        
        with(NotificationManagerCompat.from(context)) {
            notify(NOTIFICATION_ID_COMPLETION + legNumber, notification)
        }
    }
    
    fun showFuelWarningNotification(expectedFuel: Double, actualFuel: Double) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Fuel Usage Alert")
            .setContentText("Fuel usage (${String.format("%.1f", actualFuel)}L) exceeds expected range (${String.format("%.1f", expectedFuel)}L)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        with(NotificationManagerCompat.from(context)) {
            notify(NOTIFICATION_ID_FUEL_WARNING, notification)
        }
    }
    
    fun showTripCompletedNotification(tripId: Int, totalDistance: Double, totalFuel: Double) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("trip_id", tripId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Trip Completed")
            .setContentText("Trip completed: ${String.format("%.1f", totalDistance)}km, ${String.format("%.1f", totalFuel)}L fuel used")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        with(NotificationManagerCompat.from(context)) {
            notify(tripId, notification)
        }
    }
    
    fun cancelNotification(notificationId: Int) {
        with(NotificationManagerCompat.from(context)) {
            cancel(notificationId)
        }
    }
    
    fun cancelAllNotifications() {
        with(NotificationManagerCompat.from(context)) {
            cancelAll()
        }
    }
}
