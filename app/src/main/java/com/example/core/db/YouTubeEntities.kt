package com.example.core.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "youtube_channels")
data class YouTubeChannelEntity(
    @PrimaryKey val id: String, // Exact YouTube Channel ID starting with UC (e.g. "UCNsmL3gvYyL8nPA3w6QuZ6g")
    val name: String,
    val handle: String,
    val channelUrl: String,
    val avatarUrl: String = "",
    val notificationsEnabled: Boolean = true,
    val notify2HoursBefore: Boolean = true,
    val notify30MinutesBefore: Boolean = true,
    val notify5MinutesBefore: Boolean = true,
    val notifyWhenLive: Boolean = true,
    val filterClass12Only: Boolean = true,
    val lastSyncStatus: String = "IDLE", // "OK", "SYNCING", "ERROR", "NO_API_KEY", "NO_UPCOMING"
    val lastSyncMessage: String = "",
    val lastSyncTimestamp: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "youtube_upcoming_classes")
data class YouTubeUpcomingClassEntity(
    @PrimaryKey val videoId: String,
    val channelId: String, // Exact Channel ID of the video owner
    val channelName: String,
    val title: String,
    val subject: String,
    val classLevel: String = "Class 12",
    val thumbnailUrl: String = "",
    val videoUrl: String,
    val scheduledStartTimeMillis: Long,
    val isLive: Boolean = false,
    val liveStartTimeMillis: Long? = null,
    val actualEndTimeMillis: Long? = null,
    val liveBroadcastContent: String = "upcoming", // "upcoming", "live", "none"
    val notificationsSentFlags: Int = 0 // 1=2h, 2=30m, 4=5m, 8=live
)
