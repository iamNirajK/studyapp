package com.example.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.core.db.AppDatabase
import com.example.core.db.ScheduledNotificationEntity
import com.example.core.db.TaskEntity
import com.example.core.settings.SettingsManager
import com.example.core.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object StudyNotificationManager {

    private const val TAG = "StudyNotifManager"

    fun scheduleAlarmsForTasks(
        context: Context,
        tasks: List<TaskEntity>,
        motivation: String = "Success is the sum of small efforts, repeated day in and day out. Let's make today count!"
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val settings = SettingsManager(context)
        val database = AppDatabase.getDatabase(context)
        val dao = database.scheduleDao()

        // Offload all processing and database operations to Dispatchers.IO
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val date = if (tasks.isNotEmpty()) {
                    tasks.first().date
                } else {
                    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                }

                Log.d(TAG, "Starting to schedule alarms for date: $date with ${tasks.size} tasks.")

                // 1. Cancel previously scheduled notifications using the metadata stored in Room DB
                val oldNotifs = dao.getScheduledNotificationsSync(date)
                Log.d(TAG, "Found ${oldNotifs.size} previous alarms to cancel for date: $date")
                oldNotifs.forEach { notif ->
                    val intent = Intent(context, NotificationReceiver::class.java)
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        notif.id,
                        intent,
                        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                    )
                    if (pendingIntent != null) {
                        alarmManager.cancel(pendingIntent)
                        pendingIntent.cancel()
                        Log.d(TAG, "Cancelled alarm ID: ${notif.id} ('${notif.title}')")
                    }
                }

                // Delete the old scheduled notification metadata records
                dao.deleteScheduledNotificationsByDate(date)

                // If notifications are globally disabled by the user, we stop scheduling new alarms
                if (!settings.isNotificationsEnabled) {
                    Log.d(TAG, "Local notifications are disabled in user settings. Skipping reschedule.")
                    return@launch
                }

                val newNotifications = mutableListOf<ScheduledNotificationEntity>()

                // Helper to validate, build, and schedule a single alarm
                fun planNotification(
                    timeString: String,
                    offsetMinutes: Int = 0,
                    typeKey: String,
                    taskTitle: String,
                    title: String,
                    body: String
                ) {
                    val calendar = getCalendarForTimeString(date, timeString) ?: return
                    if (offsetMinutes != 0) {
                        calendar.add(Calendar.MINUTE, offsetMinutes)
                    }

                    val timeMillis = calendar.timeInMillis

                    // Verify that the scheduled alarm time is in the future
                    if (timeMillis <= System.currentTimeMillis()) {
                        return
                    }

                    // Enforce Quiet Hours
                    if (isWithinQuietHours(timeMillis, context)) {
                        Log.d(TAG, "Notification at $timeString skipped because it falls within Quiet Hours.")
                        return
                    }

                    // Create unique deterministic ID based on date, time, offset, and type
                    val uniqueKey = "${date}_${timeString}_${offsetMinutes}_${typeKey}"
                    val id = uniqueKey.hashCode()

                    val entity = ScheduledNotificationEntity(
                        id = id,
                        date = date,
                        taskTitle = taskTitle,
                        notificationTimeMillis = timeMillis,
                        title = title,
                        body = body,
                        type = typeKey
                    )

                    newNotifications.add(entity)

                    // Schedule alarm with the receiver
                    val intent = Intent(context, NotificationReceiver::class.java).apply {
                        putExtra("title", title)
                        putExtra("body", body)
                        putExtra("id", id)
                    }

                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        id,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
                            } else {
                                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
                            }
                        } else {
                            alarmManager.setExact(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
                        }
                        Log.d(TAG, "Successfully set alarm ID: $id at $timeString (offset $offsetMinutes mins) for '$title'")
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Exact alarm permission denied. Falling back to inexact alarm: ${e.message}")
                        alarmManager.set(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
                    }
                }

                // 2. Schedule Wake-up Reminder
                // Search for "Sleep" tasks to determine waking time, or default to 06:00 AM
                val sleepTask = tasks.find { it.type.lowercase(Locale.US) == "sleep" }
                val wakeupTime = sleepTask?.endTime ?: "06:00"
                planNotification(
                    timeString = wakeupTime,
                    typeKey = "wakeup",
                    taskTitle = "Wake-up",
                    title = "🌅 Wake Up & Shine",
                    body = "Start your day with purpose! Get ready for your study sessions."
                )

                // 3. Schedule Morning Motivation Reminder
                if (settings.isDailyMotivationEnabled) {
                    planNotification(
                        timeString = "07:00",
                        typeKey = "motivation",
                        taskTitle = "Morning Motivation",
                        title = "✨ Morning Motivation",
                        body = if (motivation.isNotBlank()) motivation else "Success is the sum of small efforts, repeated day in and day out. Let's make today count!"
                    )
                }

                // 4. Schedule Task-Specific Alarms
                tasks.forEach { task ->
                    val typeLower = task.type.lowercase(Locale.US)
                    if (typeLower == "sleep") return@forEach

                    val start12 = TimeUtils.formatTo12Hour(task.startTime)

                    when (typeLower) {
                        "break", "meal", "buffer" -> {
                            // Schedule Break End notification
                            planNotification(
                                timeString = task.endTime,
                                typeKey = "breakend",
                                taskTitle = task.title,
                                title = "⏰ Break Finished",
                                body = "Your break is over. Let's continue studying."
                            )
                        }
                        "live class" -> {
                            // Live classes require 15-min precursor, 10-min precursor, and exact start triggers
                            planNotification(
                                timeString = task.startTime,
                                offsetMinutes = -15,
                                typeKey = "live_15m",
                                taskTitle = task.title,
                                title = "🎥 Live Class Starting Soon",
                                body = "${task.subject} Live Class starts at $start12. Join on time."
                            )
                            planNotification(
                                timeString = task.startTime,
                                offsetMinutes = -10,
                                typeKey = "live_10m",
                                taskTitle = task.title,
                                title = "🎥 Live Class Starting Soon",
                                body = "${task.subject} Live Class starts at $start12. Join on time."
                            )
                            planNotification(
                                timeString = task.startTime,
                                typeKey = "live_start",
                                taskTitle = task.title,
                                title = "🎥 Live Class Started",
                                body = "${task.subject} Live Class has started. Join now!"
                            )
                        }
                        "water reminder" -> {
                            // Dedicated hydration engine scheduled separately below
                        }
                        else -> {
                            // Study session, Revision, Mock Test, Notes, PYQs, Self Study, etc.
                            // Trigger timings: 30 minutes before (optional), 10 minutes before, 5 minutes before (optional), At the exact start time
                            
                            // 30 minutes before (optional checkbox check)
                            if (settings.is30MinReminderEnabled) {
                                planNotification(
                                    timeString = task.startTime,
                                    offsetMinutes = -30,
                                    typeKey = "study_30m",
                                    taskTitle = task.title,
                                    title = "📚 Upcoming Study Session",
                                    body = "${task.title} starts at $start12."
                                )
                            }

                            // 10 minutes before (mandatory)
                            planNotification(
                                timeString = task.startTime,
                                offsetMinutes = -10,
                                typeKey = "study_10m",
                                taskTitle = task.title,
                                title = "📚 Upcoming Study Session",
                                body = "${task.title} starts at $start12. Get ready."
                            )

                            // 5 minutes before (optional checkbox check)
                            if (settings.is5MinReminderEnabled) {
                                planNotification(
                                    timeString = task.startTime,
                                    offsetMinutes = -5,
                                    typeKey = "study_5m",
                                    taskTitle = task.title,
                                    title = "📚 Upcoming Study Session",
                                    body = "${task.title} starts at $start12."
                                )
                            }

                            // Exact start time
                            planNotification(
                                timeString = task.startTime,
                                typeKey = "study_start",
                                taskTitle = task.title,
                                title = "🚀 Time to Study",
                                body = "${task.title} has started. Stay focused and complete today's goal."
                            )
                        }
                    }
                }

                // 5. Schedule Hydration Reminders (every 2 hours between 8 AM and 10 PM if enabled)
                if (settings.isWaterRemindersEnabled) {
                    val startHour = 8
                    val endHour = 22
                    for (hour in startHour..endHour step 2) {
                        val targetTimeStr = String.format(Locale.US, "%02d:00", hour)
                        // Make sure hydration prompts do not overlap with high-importance Live Classes or Mock Tests
                        val overlapsWithImportantTask = tasks.any { task ->
                            val isImportant = task.type.lowercase(Locale.US) in listOf("live class", "mock test")
                            isImportant && isTimeWithinRange(targetTimeStr, task.startTime, task.endTime)
                        }

                        if (!overlapsWithImportantTask) {
                            planNotification(
                                timeString = targetTimeStr,
                                typeKey = "water",
                                taskTitle = "Water Reminder",
                                title = "Hydration Reminder 💧",
                                body = "Time to drink some water and stay focused!"
                            )
                        }
                    }
                }

                // 6. Schedule End of Day Summary (At 10:00 PM / 22:00)
                planNotification(
                    timeString = "22:00",
                    typeKey = "eod",
                    taskTitle = "Daily Summary",
                    title = "🎯 Daily Summary",
                    body = "Review today's progress and prepare for tomorrow."
                )

                // Save all planned scheduled notification metadata to database
                if (newNotifications.isNotEmpty()) {
                    dao.insertScheduledNotifications(newNotifications)
                    Log.d(TAG, "Saved ${newNotifications.size} scheduled notification metadata to DB.")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Exception in scheduleAlarmsForTasks: ${e.message}", e)
            }
        }
    }

    private fun isWithinQuietHours(timeMillis: Long, context: Context): Boolean {
        val settings = SettingsManager(context)
        if (!settings.isQuietHoursEnabled) return false

        val calendar = Calendar.getInstance().apply { this.timeInMillis = timeMillis }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val timeMinutes = hour * 60 + minute

        val startParts = settings.quietHoursStart.split(":")
        val startHour = startParts.getOrNull(0)?.toIntOrNull() ?: 22
        val startMin = startParts.getOrNull(1)?.toIntOrNull() ?: 0
        val startMinutes = startHour * 60 + startMin

        val endParts = settings.quietHoursEnd.split(":")
        val endHour = endParts.getOrNull(0)?.toIntOrNull() ?: 7
        val endMin = endParts.getOrNull(1)?.toIntOrNull() ?: 0
        val endMinutes = endHour * 60 + endMin

        return if (startMinutes < endMinutes) {
            timeMinutes in startMinutes..endMinutes
        } else {
            // Quiet hours span across midnight (e.g., 22:00 to 07:00)
            timeMinutes >= startMinutes || timeMinutes <= endMinutes
        }
    }

    private fun isTimeWithinRange(time: String, start: String, end: String): Boolean {
        return time >= start && time <= end
    }

    private fun getCalendarForTimeString(dateString: String, timeString: String): Calendar? {
        val dateParts = dateString.split("-")
        if (dateParts.size != 3) return null
        val year = dateParts[0].toIntOrNull() ?: return null
        val month = dateParts[1].toIntOrNull()?.minus(1) ?: return null
        val day = dateParts[2].toIntOrNull() ?: return null

        val parts = timeString.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null

        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
}
