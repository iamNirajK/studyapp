package com.example.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Study Reminder"
        val body = intent.getStringExtra("body") ?: "Time for your next session!"
        val notificationId = intent.getIntExtra("id", 1001)

        createNotificationChannel(context)

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val settings = com.example.core.settings.SettingsManager(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (settings.isSoundEnabled) {
            builder.setDefaults(NotificationCompat.DEFAULT_SOUND)
        } else {
            builder.setSound(null)
        }

        if (settings.isVibrationEnabled) {
            val defaults = if (settings.isSoundEnabled) {
                NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE
            } else {
                NotificationCompat.DEFAULT_VIBRATE
            }
            builder.setDefaults(defaults)
        } else {
            builder.setVibrate(null)
        }

        val notification = builder.build()

        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Permission not granted yet (Android 13+)
        }
    }

    companion object {
        const val CHANNEL_ID = "study_planner_notifications"

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "SK Study Planner Reminders"
                val descriptionText = "Notifications for schedules, class reminders, and breaks."
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
}
