package com.example.core.youtube

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.core.db.YouTubeChannelEntity
import com.example.core.db.YouTubeDao
import com.example.core.db.YouTubeUpcomingClassEntity
import com.example.core.notifications.NotificationReceiver
import com.example.core.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class YouTubeTrackerManager(
    private val context: Context,
    private val dao: YouTubeDao,
    private val settingsManager: SettingsManager? = null
) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    companion object {
        private const val TAG = "YouTubeTrackerManager"

        // Authoritative verified YouTube channel IDs (starts with UC)
        const val CHANNEL_1_ID = "UCNsmL3gvYyL8nPA3w6QuZ6g"
        const val CHANNEL_1_NAME = "Disha Hindi & English Classes"
        const val CHANNEL_1_HANDLE = "@dishahindienglish"
        const val CHANNEL_1_URL = "https://www.youtube.com/@dishahindienglish"

        const val CHANNEL_2_ID = "UCvaUdm8gLsPUD1PD8N8qr8w"
        const val CHANNEL_2_NAME = "Disha Science Classes"
        const val CHANNEL_2_HANDLE = "@DishaScienceClasses"
        const val CHANNEL_2_URL = "https://www.youtube.com/@DishaScienceClasses"
    }

    /**
     * Initializes default tracked channels without creating ANY fake/mock video data.
     * Cleans up legacy mock channels and classes from previous versions.
     */
    suspend fun initializeDefaultChannelsIfNeeded() = withContext(Dispatchers.IO) {
        try {
            // 1. Purge legacy mock classes and invalid mock channel records
            dao.deleteLegacyMockClasses()
            dao.deleteChannel("UC_dishahindienglish")
            dao.deleteChannel("UC_DishaScienceClasses")
            dao.deleteClassesForChannel("UC_dishahindienglish")
            dao.deleteClassesForChannel("UC_DishaScienceClasses")

            // 2. Ensure official channels exist with exact permanent channel IDs
            val existingChannels = dao.getAllChannelsSync().associateBy { it.id }

            if (!existingChannels.containsKey(CHANNEL_1_ID)) {
                val ch1 = YouTubeChannelEntity(
                    id = CHANNEL_1_ID,
                    name = CHANNEL_1_NAME,
                    handle = CHANNEL_1_HANDLE,
                    channelUrl = CHANNEL_1_URL,
                    avatarUrl = "https://images.unsplash.com/photo-1546410531-bb4caa6b424d?w=200&auto=format&fit=crop&q=80",
                    notificationsEnabled = true,
                    notify2HoursBefore = true,
                    notify30MinutesBefore = true,
                    notify5MinutesBefore = true,
                    notifyWhenLive = true,
                    filterClass12Only = true,
                    lastSyncStatus = "IDLE",
                    lastSyncMessage = "Ready to sync with YouTube"
                )
                dao.insertChannel(ch1)
            }

            if (!existingChannels.containsKey(CHANNEL_2_ID)) {
                val ch2 = YouTubeChannelEntity(
                    id = CHANNEL_2_ID,
                    name = CHANNEL_2_NAME,
                    handle = CHANNEL_2_HANDLE,
                    channelUrl = CHANNEL_2_URL,
                    avatarUrl = "https://images.unsplash.com/photo-1532094349884-543bc11b234d?w=200&auto=format&fit=crop&q=80",
                    notificationsEnabled = true,
                    notify2HoursBefore = true,
                    notify30MinutesBefore = true,
                    notify5MinutesBefore = true,
                    notifyWhenLive = true,
                    filterClass12Only = true,
                    lastSyncStatus = "IDLE",
                    lastSyncMessage = "Ready to sync with YouTube"
                )
                dao.insertChannel(ch2)
            }

            // 3. Trigger initial sync to fetch real YouTube broadcasts (zero fake data!)
            syncAllChannels()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing default YouTube channels: ${e.message}", e)
        }
    }

    fun getAllChannels(): Flow<List<YouTubeChannelEntity>> = dao.getAllChannels()

    fun getAllUpcomingClasses(): Flow<List<YouTubeUpcomingClassEntity>> = dao.getAllUpcomingClasses()

    /**
     * Resolves the channel input to an exact authoritative channelId first.
     * Never generates mock videos.
     */
    suspend fun addChannel(
        urlOrHandle: String,
        previewName: String,
        previewAvatar: String
    ): Result<YouTubeChannelEntity> = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()
        val resolveResult = YouTubeDiscoveryService.resolveChannel(urlOrHandle, apiKey)

        if (resolveResult.isFailure) {
            return@withContext Result.failure(
                resolveResult.exceptionOrNull() ?: IllegalArgumentException("Could not verify YouTube channel.")
            )
        }

        val resolved = resolveResult.getOrThrow()

        val channel = YouTubeChannelEntity(
            id = resolved.channelId,
            name = if (previewName.isNotBlank() && previewName != "Channel Name") previewName else resolved.name,
            handle = resolved.handle,
            channelUrl = resolved.channelUrl,
            avatarUrl = if (previewAvatar.isNotBlank()) previewAvatar else resolved.avatarUrl,
            notificationsEnabled = true,
            notify2HoursBefore = true,
            notify30MinutesBefore = true,
            notify5MinutesBefore = true,
            notifyWhenLive = true,
            filterClass12Only = true,
            lastSyncStatus = "SYNCING",
            lastSyncMessage = "Verifying upcoming broadcasts..."
        )

        dao.insertChannel(channel)

        // Perform discovery and verification for this channel
        syncChannel(channel.id)

        Result.success(channel)
    }

    suspend fun removeChannel(channelId: String) = withContext(Dispatchers.IO) {
        // 1. Cancel notifications for this channel
        val classes = dao.getAllUpcomingClassesSync().filter { it.channelId == channelId }
        classes.forEach { cancelAlarmsForClass(it) }

        // 2. Delete classes and channel
        dao.deleteClassesForChannel(channelId)
        dao.deleteChannel(channelId)
    }

    suspend fun updateChannel(channel: YouTubeChannelEntity) = withContext(Dispatchers.IO) {
        dao.updateChannel(channel)
        rescheduleAllAlarms()
    }

    suspend fun setClassLive(videoId: String, isLive: Boolean) = withContext(Dispatchers.IO) {
        dao.updateLiveStatus(videoId, isLive)
    }

    /**
     * Syncs broadcasts for a single channel with exact verification.
     */
    suspend fun syncChannel(channelId: String): ChannelSyncResult = withContext(Dispatchers.IO) {
        val channel = dao.getChannelById(channelId)
            ?: return@withContext ChannelSyncResult(emptyList(), "ERROR", "Channel not found")

        dao.updateChannelSyncStatus(channelId, "SYNCING", "Checking YouTube for upcoming broadcasts...", System.currentTimeMillis())

        val apiKey = getEffectiveApiKey()
        val syncResult = YouTubeDiscoveryService.fetchAndVerifyChannelClasses(channel, apiKey)

        // Update database with verified classes
        dao.deleteClassesForChannel(channelId)
        if (syncResult.classes.isNotEmpty()) {
            dao.insertClasses(syncResult.classes)
        }

        dao.updateChannelSyncStatus(
            channelId,
            syncResult.status,
            syncResult.message,
            System.currentTimeMillis()
        )

        rescheduleAllAlarms()
        syncResult
    }

    /**
     * Syncs all tracked channels sequentially.
     */
    suspend fun syncAllChannels() = withContext(Dispatchers.IO) {
        val channels = dao.getAllChannelsSync()
        for (channel in channels) {
            try {
                syncChannel(channel.id)
            } catch (e: Exception) {
                Log.w(TAG, "Failed syncing channel ${channel.id}: ${e.message}")
            }
        }
    }

    fun getEffectiveApiKey(): String? {
        val userKey = settingsManager?.youtubeApiKey?.trim()?.takeIf { it.isNotBlank() }
        if (userKey != null) return userKey

        return try {
            val buildConfigClass = Class.forName("com.example.BuildConfig")
            val field = buildConfigClass.getField("YOUTUBE_API_KEY")
            val key = field.get(null) as? String
            key?.trim()?.takeIf {
                it.isNotBlank() &&
                !it.startsWith("placeholder", ignoreCase = true) &&
                it != "MY_YOUTUBE_API_KEY" &&
                it != "YOUR_YOUTUBE_API_KEY"
            }
        } catch (_: Exception) {
            null
        }
    }

    fun rescheduleAllAlarms() {
        val alarmMgr = alarmManager ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val channels = dao.getAllChannelsSync().associateBy { it.id }
                val classes = dao.getAllUpcomingClassesSync()
                val now = System.currentTimeMillis()

                classes.forEach { classItem ->
                    val channel = channels[classItem.channelId] ?: return@forEach
                    if (!channel.notificationsEnabled) {
                        cancelAlarmsForClass(classItem)
                        return@forEach
                    }

                    val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
                    val timeString = timeFormat.format(Date(classItem.scheduledStartTimeMillis))

                    // 1. 2 Hours Before
                    if (channel.notify2HoursBefore) {
                        val triggerTime = classItem.scheduledStartTimeMillis - (120 * 60 * 1000L)
                        if (triggerTime > now) {
                            scheduleSingleAlarm(
                                alarmId = "${classItem.videoId}_2h".hashCode(),
                                triggerTimeMillis = triggerTime,
                                title = "📚 Class 12 LIVE in 2 hours",
                                body = "${classItem.title}\n${channel.name}\n$timeString",
                                videoUrl = classItem.videoUrl
                            )
                        }
                    }

                    // 2. 30 Minutes Before
                    if (channel.notify30MinutesBefore) {
                        val triggerTime = classItem.scheduledStartTimeMillis - (30 * 60 * 1000L)
                        if (triggerTime > now) {
                            scheduleSingleAlarm(
                                alarmId = "${classItem.videoId}_30m".hashCode(),
                                triggerTimeMillis = triggerTime,
                                title = "⏰ Class 12 LIVE starts in 30 minutes",
                                body = "${classItem.title}\n$timeString",
                                videoUrl = classItem.videoUrl
                            )
                        }
                    }

                    // 3. 5 Minutes Before
                    if (channel.notify5MinutesBefore) {
                        val triggerTime = classItem.scheduledStartTimeMillis - (5 * 60 * 1000L)
                        if (triggerTime > now) {
                            scheduleSingleAlarm(
                                alarmId = "${classItem.videoId}_5m".hashCode(),
                                triggerTimeMillis = triggerTime,
                                title = "🚨 Class 12 LIVE starts in 5 minutes",
                                body = "${classItem.title}\n${channel.name}",
                                videoUrl = classItem.videoUrl
                            )
                        }
                    }

                    // 4. When LIVE starts
                    if (channel.notifyWhenLive) {
                        val triggerTime = classItem.scheduledStartTimeMillis
                        if (triggerTime > now) {
                            scheduleSingleAlarm(
                                alarmId = "${classItem.videoId}_live".hashCode(),
                                triggerTimeMillis = triggerTime,
                                title = "🔴 Class 12 LIVE NOW",
                                body = "${classItem.title}\nTap to watch.",
                                videoUrl = classItem.videoUrl
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling YouTube class alarms: ${e.message}", e)
            }
        }
    }

    private fun scheduleSingleAlarm(
        alarmId: Int,
        triggerTimeMillis: Long,
        title: String,
        body: String,
        videoUrl: String
    ) {
        val alarmMgr = alarmManager ?: return
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("id", alarmId)
            putExtra("title", title)
            putExtra("body", body)
            putExtra("video_url", videoUrl)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmMgr.canScheduleExactAlarms()) {
                    alarmMgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
                } else {
                    alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
                }
            } else {
                alarmMgr.setExact(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            alarmMgr.set(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
        }
    }

    private fun cancelAlarmsForClass(classItem: YouTubeUpcomingClassEntity) {
        val alarmMgr = alarmManager ?: return
        val triggers = listOf("_2h", "_30m", "_5m", "_live")
        triggers.forEach { suffix ->
            val alarmId = "${classItem.videoId}$suffix".hashCode()
            val intent = Intent(context, NotificationReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarmId,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmMgr.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }
}
