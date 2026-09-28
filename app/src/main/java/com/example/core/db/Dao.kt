package com.example.core.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: DailyScheduleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Query("SELECT * FROM daily_schedules WHERE date = :date")
    fun getScheduleByDate(date: String): Flow<DailyScheduleEntity?>

    @Query("SELECT * FROM daily_schedules WHERE date = :date")
    suspend fun getScheduleByDateSync(date: String): DailyScheduleEntity?

    @Query("SELECT * FROM tasks WHERE date = :date ORDER BY startTime ASC")
    fun getTasksByDate(date: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE date = :date ORDER BY startTime ASC")
    suspend fun getTasksByDateSync(date: String): List<TaskEntity>

    @Query("UPDATE tasks SET status = :status WHERE taskId = :taskId")
    suspend fun updateTaskStatus(taskId: String, status: String)

    @Query("UPDATE tasks SET notes = :notes WHERE taskId = :taskId")
    suspend fun updateTaskNotes(taskId: String, notes: String)

    @Update
    suspend fun updateSchedule(schedule: DailyScheduleEntity)

    @Query("DELETE FROM daily_schedules WHERE date = :date")
    suspend fun deleteScheduleByDate(date: String)

    @Query("DELETE FROM tasks WHERE date = :date")
    suspend fun deleteTasksByDate(date: String)

    @Query("SELECT * FROM daily_schedules ORDER BY date DESC")
    fun getAllSchedules(): Flow<List<DailyScheduleEntity>>

    @Query("SELECT * FROM tasks ORDER BY date DESC, startTime ASC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduledNotifications(notifications: List<ScheduledNotificationEntity>)

    @Query("SELECT * FROM scheduled_notifications WHERE date = :date ORDER BY notificationTimeMillis ASC")
    fun getScheduledNotifications(date: String): Flow<List<ScheduledNotificationEntity>>

    @Query("SELECT * FROM scheduled_notifications WHERE date = :date ORDER BY notificationTimeMillis ASC")
    suspend fun getScheduledNotificationsSync(date: String): List<ScheduledNotificationEntity>

    @Query("DELETE FROM scheduled_notifications WHERE date = :date")
    suspend fun deleteScheduledNotificationsByDate(date: String)

    @Query("DELETE FROM scheduled_notifications")
    suspend fun deleteAllScheduledNotifications()

    @Transaction
    suspend fun saveFullSchedule(schedule: DailyScheduleEntity, tasks: List<TaskEntity>) {
        deleteScheduleByDate(schedule.date)
        deleteTasksByDate(schedule.date)
        insertSchedule(schedule)
        insertTasks(tasks)
    }
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_sessions ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    suspend fun getSessionSync(id: String): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity)

    @Query("UPDATE chat_sessions SET isPinned = :pinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePinned(id: String, pinned: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chat_sessions SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("DELETE FROM chat_messages WHERE sessionId = :id")
    suspend fun deleteMessagesBySessionId(id: String)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSessionSync(sessionId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE messageId = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("UPDATE chat_messages SET text = :text WHERE messageId = :messageId")
    suspend fun updateMessageText(messageId: String, text: String)

    @Transaction
    suspend fun deleteSessionWithMessages(id: String) {
        deleteMessagesBySessionId(id)
        deleteSession(id)
    }
}

