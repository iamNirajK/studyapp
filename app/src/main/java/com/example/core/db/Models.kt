package com.example.core.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_schedules")
data class DailyScheduleEntity(
    @PrimaryKey val date: String, // format: yyyy-MM-dd
    val dayOfWeek: String,
    val studyHours: Double,
    val revisionHours: Double,
    val breakHours: Double,
    val completion: Int, // percentage 0-100
    val focusSubject: String,
    val productivityScore: Int,
    val motivation: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val telegramSent: Boolean = false,
    val telegramError: String? = null
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val taskId: String, // format: date_startTime
    val date: String, // maps to DailyScheduleEntity.date
    val title: String,
    val subject: String, // Physics, Chemistry, Maths, English, Hindi, General, Custom
    val type: String, // Live Class, Revision, Self Study, Mock Test, PYQ, Notes, Break, Meal, Buffer, Sleep, Water Reminder, Custom
    val priority: String, // High, Medium, Low, Critical
    val startTime: String, // format: HH:mm (24-hour)
    val endTime: String, // format: HH:mm (24-hour)
    val durationMinutes: Int,
    val status: String, // Pending, In Progress, Completed, Skipped
    val fixed: Boolean,
    val notes: String = ""
)

@Entity(tableName = "scheduled_notifications")
data class ScheduledNotificationEntity(
    @PrimaryKey val id: Int,
    val date: String, // format: yyyy-MM-dd
    val taskTitle: String,
    val notificationTimeMillis: Long,
    val title: String,
    val body: String,
    val type: String // e.g. "Session Start", "Reminder 10m", "Reminder 30m", "Break End", "Wake-up", "Water", "Motivation", "EOD"
)

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val messageId: String,
    val sessionId: String, // maps to ChatSessionEntity.id
    val role: String, // "user" or "model"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val localImagePath: String? = null,
    val docText: String? = null
)

