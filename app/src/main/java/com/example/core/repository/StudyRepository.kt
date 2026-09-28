package com.example.core.repository

import com.example.core.db.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map


class StudyRepository(
    private val scheduleDao: ScheduleDao,
    private val chatDao: ChatDao
) {

    // --- Chat & Conversation History ---
    val allChatSessions: Flow<List<com.example.core.db.ChatSessionEntity>> = chatDao.getAllSessions()

    fun getMessagesForSession(sessionId: String): Flow<List<com.example.core.db.ChatMessageEntity>> {
        return chatDao.getMessagesForSession(sessionId)
    }

    suspend fun getMessagesForSessionSync(sessionId: String): List<com.example.core.db.ChatMessageEntity> {
        return chatDao.getMessagesForSessionSync(sessionId)
    }

    suspend fun insertSession(session: com.example.core.db.ChatSessionEntity) {
        chatDao.insertSession(session)
    }

    suspend fun updatePinned(id: String, pinned: Boolean) {
        chatDao.updatePinned(id, pinned)
    }

    suspend fun updateSessionTitle(id: String, title: String) {
        chatDao.updateTitle(id, title)
    }

    suspend fun deleteSession(id: String) {
        chatDao.deleteSessionWithMessages(id)
    }

    suspend fun insertMessage(message: com.example.core.db.ChatMessageEntity) {
        chatDao.insertMessage(message)
    }

    suspend fun deleteMessage(messageId: String) {
        chatDao.deleteMessage(messageId)
    }

    suspend fun updateMessageText(messageId: String, text: String) {
        chatDao.updateMessageText(messageId, text)
    }

    // --- Schedule & Task Management ---
    fun getSchedule(date: String): Flow<DailyScheduleEntity?> {

        return scheduleDao.getScheduleByDate(date)
    }

    suspend fun getScheduleSync(date: String): DailyScheduleEntity? {
        return scheduleDao.getScheduleByDateSync(date)
    }

    fun getTasks(date: String): Flow<List<TaskEntity>> {
        return scheduleDao.getTasksByDate(date)
    }

    suspend fun getTasksSync(date: String): List<TaskEntity> {
        return scheduleDao.getTasksByDateSync(date)
    }

    suspend fun saveSchedule(schedule: DailyScheduleEntity, tasks: List<TaskEntity>) {
        scheduleDao.saveFullSchedule(schedule, tasks)
    }

    suspend fun updateTaskStatus(taskId: String, status: String, date: String) {
        scheduleDao.updateTaskStatus(taskId, status)
        recalculateProgress(date)
    }

    suspend fun updateTaskNotes(taskId: String, notes: String) {
        scheduleDao.updateTaskNotes(taskId, notes)
    }

    fun getAllSchedules(): Flow<List<DailyScheduleEntity>> {
        return scheduleDao.getAllSchedules()
    }

    fun getAllTasks(): Flow<List<TaskEntity>> {
        return scheduleDao.getAllTasks()
    }

    suspend fun deleteSchedule(date: String) {
        scheduleDao.deleteScheduleByDate(date)
        scheduleDao.deleteTasksByDate(date)
    }

    suspend fun updateScheduleTelegramStatus(date: String, sent: Boolean, error: String?) {
        val current = scheduleDao.getScheduleByDateSync(date)
        if (current != null) {
            scheduleDao.updateSchedule(
                current.copy(
                    telegramSent = sent,
                    telegramError = error,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    // Helper to recalculate today's progress percentage dynamically in the database
    private suspend fun recalculateProgress(date: String) {
        val schedule = scheduleDao.getScheduleByDateSync(date) ?: return
        val tasks = scheduleDao.getTasksByDateSync(date)
        if (tasks.isEmpty()) return

        // Only count study, revision, live class, mock test, pyq, practice, notes (exclude breaks/meals/sleep/water for strict study hours calculation where needed,
        // but user-visible completion can be percentage of total non-break/meal/sleep tasks or all tasks)
        // Let's count completion of all scheduled interactive tasks (exclude Sleep, Breaks, Meals from completion or include? Let's check:
        // completion of study-oriented tasks is highly motivating!)
        val relevantTasks = tasks.filter {
            it.type != "Break" && it.type != "Meal" && it.type != "Sleep" && it.type != "Water Reminder"
        }
        val completedCount = relevantTasks.count { it.status == "Completed" }
        val completionPercentage = if (relevantTasks.isEmpty()) {
            val total = tasks.size
            if (total == 0) 100 else (tasks.count { it.status == "Completed" } * 100 / total)
        } else {
            (completedCount * 100 / relevantTasks.size)
        }

        scheduleDao.updateSchedule(
            schedule.copy(
                completion = completionPercentage,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun getScheduledNotifications(date: String): Flow<List<com.example.core.db.ScheduledNotificationEntity>> {
        return scheduleDao.getScheduledNotifications(date)
    }

    suspend fun getScheduledNotificationsSync(date: String): List<com.example.core.db.ScheduledNotificationEntity> {
        return scheduleDao.getScheduledNotificationsSync(date)
    }

    suspend fun saveScheduledNotifications(date: String, notifications: List<com.example.core.db.ScheduledNotificationEntity>) {
        scheduleDao.deleteScheduledNotificationsByDate(date)
        scheduleDao.insertScheduledNotifications(notifications)
    }

    suspend fun deleteScheduledNotifications(date: String) {
        scheduleDao.deleteScheduledNotificationsByDate(date)
    }
}
