package com.example.core.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface YouTubeDao {

    @Query("SELECT * FROM youtube_channels ORDER BY createdAt ASC")
    fun getAllChannels(): Flow<List<YouTubeChannelEntity>>

    @Query("SELECT * FROM youtube_channels ORDER BY createdAt ASC")
    suspend fun getAllChannelsSync(): List<YouTubeChannelEntity>

    @Query("SELECT * FROM youtube_channels WHERE id = :id")
    suspend fun getChannelById(id: String): YouTubeChannelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: YouTubeChannelEntity)

    @Update
    suspend fun updateChannel(channel: YouTubeChannelEntity)

    @Query("DELETE FROM youtube_channels WHERE id = :id")
    suspend fun deleteChannel(id: String)

    @Query("SELECT * FROM youtube_upcoming_classes ORDER BY isLive DESC, scheduledStartTimeMillis ASC")
    fun getAllUpcomingClasses(): Flow<List<YouTubeUpcomingClassEntity>>

    @Query("SELECT * FROM youtube_upcoming_classes ORDER BY isLive DESC, scheduledStartTimeMillis ASC")
    suspend fun getAllUpcomingClassesSync(): List<YouTubeUpcomingClassEntity>

    @Query("SELECT * FROM youtube_upcoming_classes WHERE channelId = :channelId ORDER BY scheduledStartTimeMillis ASC")
    fun getClassesForChannel(channelId: String): Flow<List<YouTubeUpcomingClassEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClasses(classes: List<YouTubeUpcomingClassEntity>)

    @Query("DELETE FROM youtube_upcoming_classes WHERE channelId = :channelId")
    suspend fun deleteClassesForChannel(channelId: String)

    @Query("UPDATE youtube_upcoming_classes SET notificationsSentFlags = :flags WHERE videoId = :videoId")
    suspend fun updateNotificationFlags(videoId: String, flags: Int)

    @Query("UPDATE youtube_upcoming_classes SET isLive = :isLive WHERE videoId = :videoId")
    suspend fun updateLiveStatus(videoId: String, isLive: Boolean)

    @Query("DELETE FROM youtube_upcoming_classes WHERE videoId = :videoId")
    suspend fun deleteClass(videoId: String)

    @Query("UPDATE youtube_channels SET lastSyncStatus = :status, lastSyncMessage = :message, lastSyncTimestamp = :timestamp WHERE id = :channelId")
    suspend fun updateChannelSyncStatus(channelId: String, status: String, message: String, timestamp: Long)

    @Query("DELETE FROM youtube_upcoming_classes WHERE videoId LIKE 'dhe_%' OR videoId LIKE 'dsc_%' OR videoId LIKE 'channel_%'")
    suspend fun deleteLegacyMockClasses()

    @Query("DELETE FROM youtube_upcoming_classes")
    suspend fun deleteAllClasses()
}
