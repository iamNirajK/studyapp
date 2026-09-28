package com.example.features

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.db.AppDatabase
import com.example.core.db.DailyScheduleEntity
import com.example.core.db.TaskEntity
import com.example.core.notifications.StudyNotificationManager
import com.example.core.repository.StudyRepository
import com.example.core.scheduler.GeminiScheduler
import com.example.core.settings.SettingsManager
import com.example.core.telegram.TelegramService
import android.graphics.Bitmap
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StudyViewModel(application: Application) : AndroidViewModel(application) {

    val repository: StudyRepository
    val settingsManager: SettingsManager

    init {
        val database = AppDatabase.getDatabase(application)
        repository = StudyRepository(database.scheduleDao(), database.chatDao())
        settingsManager = SettingsManager(application)
    }


    private val _selectedDate = MutableStateFlow(getTodayDateString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    // Session Setup state
    private val _isSessionSetupDone = MutableStateFlow(settingsManager.isSessionSetupCompleted)
    val isSessionSetupDone: StateFlow<Boolean> = _isSessionSetupDone.asStateFlow()

    // Screen states
    private val _generateState = MutableStateFlow<PlannerState>(PlannerState.Idle)
    val generateState: StateFlow<PlannerState> = _generateState.asStateFlow()

    private val _telegramState = MutableStateFlow<TelegramState>(TelegramState.Idle)
    val telegramState: StateFlow<TelegramState> = _telegramState.asStateFlow()

    // Observe active schedule and tasks dynamically
    val currentSchedule: StateFlow<DailyScheduleEntity?> = _selectedDate
        .flatMapLatest { date -> repository.getSchedule(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentTasks: StateFlow<List<TaskEntity>> = _selectedDate
        .flatMapLatest { date -> repository.getTasks(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scheduledNotifications: StateFlow<List<com.example.core.db.ScheduledNotificationEntity>> = _selectedDate
        .flatMapLatest { date -> repository.getScheduledNotifications(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Observe historical data for AI assistant context
    val allSchedules: StateFlow<List<DailyScheduleEntity>> = repository.getAllSchedules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTasks: StateFlow<List<TaskEntity>> = repository.getAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Theme state
    private val _isDarkMode = MutableStateFlow(settingsManager.isDarkMode)
    val isDarkMode: StateFlow<Boolean?> = _isDarkMode.asStateFlow()

    fun selectDate(date: String) {
        _selectedDate.value = date
    }

    fun setDarkMode(enabled: Boolean?) {
        settingsManager.isDarkMode = enabled
        _isDarkMode.value = enabled
    }

    fun generateDailySchedule(
        date: String,
        activeClasses: List<String>,
        revisionSubjects: List<String>,
        extraTasks: List<String>,
        targetStudyHours: Double
    ) {
        viewModelScope.launch {
            _generateState.value = PlannerState.Generating
            try {
                val result = GeminiScheduler.generateSchedule(
                    date = date,
                    activeClasses = activeClasses,
                    revisionSubjects = revisionSubjects,
                    extraTasks = extraTasks,
                    targetStudyHours = targetStudyHours
                )

                repository.saveSchedule(result.first, result.second)

                // Schedule system notifications
                if (settingsManager.isNotificationsEnabled) {
                    StudyNotificationManager.scheduleAlarmsForTasks(getApplication(), result.second)
                }

                _generateState.value = PlannerState.Success(result.first)

                // Trigger automatic Telegram update if enabled
                if (settingsManager.isTelegramEnabled) {
                    sendScheduleToTelegramDirect(result.first, result.second)
                }

            } catch (e: Exception) {
                _generateState.value = PlannerState.Error(e.message ?: "Failed to generate schedule.")
            }
        }
    }

    fun updateTaskStatus(task: TaskEntity, status: String) {
        viewModelScope.launch {
            repository.updateTaskStatus(task.taskId, status, task.date)

            // Trigger completion Summary to Telegram if status changes to Completed and 100% is reached
            if (settingsManager.isTelegramEnabled && status == "Completed") {
                val updatedTasks = repository.getTasksSync(task.date)
                val completedCount = updatedTasks.count { it.status == "Completed" }
                val totalStudyTasks = updatedTasks.count {
                    it.type !in listOf("Break", "Meal", "Sleep", "Water Reminder")
                }
                if (completedCount == totalStudyTasks && totalStudyTasks > 0) {
                    val schedule = repository.getScheduleSync(task.date)
                    if (schedule != null) {
                        sendNotificationToTelegram("🏆 <b>Mission accomplished!</b>\n100% of study tasks completed for today.")
                    }
                }
            }
        }
    }

    fun updateTaskNotes(taskId: String, notes: String) {
        viewModelScope.launch {
            repository.updateTaskNotes(taskId, notes)
        }
    }

    fun deleteSchedule(date: String) {
        viewModelScope.launch {
            repository.deleteSchedule(date)
        }
    }

    fun testTelegramConnection(botToken: String, chatId: String, onResult: (Result<Boolean>) -> Unit) {
        viewModelScope.launch {
            val res = TelegramService.testConnection(botToken, chatId)
            onResult(res)
        }
    }

    fun saveTelegramSettings(enabled: Boolean, botToken: String, chatId: String) {
        settingsManager.isTelegramEnabled = enabled
        settingsManager.telegramBotToken = botToken
        settingsManager.telegramChatId = chatId
    }

    fun completeSessionSetup(
        name: String,
        dob: String,
        wantTelegram: Boolean,
        botToken: String,
        chatId: String
    ) {
        settingsManager.userName = name.trim()
        settingsManager.userDob = dob.trim()
        settingsManager.isTelegramEnabled = wantTelegram
        settingsManager.telegramBotToken = if (wantTelegram) botToken.trim() else ""
        settingsManager.telegramChatId = if (wantTelegram) chatId.trim() else ""
        settingsManager.isSessionSetupCompleted = true
        _isSessionSetupDone.value = true
    }

    fun sendScheduleToTelegram() {
        val schedule = currentSchedule.value ?: return
        val tasks = currentTasks.value
        if (tasks.isEmpty()) return

        viewModelScope.launch {
            _telegramState.value = TelegramState.Sending
            val result = sendScheduleToTelegramDirect(schedule, tasks)
            if (result.isSuccess) {
                _telegramState.value = TelegramState.Success
            } else {
                _telegramState.value = TelegramState.Error(result.exceptionOrNull()?.message ?: "Failed to send.")
            }
        }
    }

    private suspend fun sendScheduleToTelegramDirect(schedule: DailyScheduleEntity, tasks: List<TaskEntity>): Result<Boolean> {
        val token = settingsManager.telegramBotToken
        val chat = settingsManager.telegramChatId
        if (token.isBlank() || chat.isBlank()) {
            val error = "Telegram configuration is missing."
            repository.updateScheduleTelegramStatus(schedule.date, false, error)
            return Result.failure(Exception(error))
        }

        val stats = mapOf(
            "Study Duration" to String.format("%.1f Hours", schedule.studyHours),
            "Revision Time" to String.format("%.1f Hours", schedule.revisionHours),
            "Break Duration" to String.format("%.1f Hours", schedule.breakHours),
            "Target Focus" to schedule.focusSubject,
            "Productivity Index" to "${schedule.productivityScore}%"
        )

        val text = TelegramService.formatScheduleMessage(
            date = schedule.date,
            dayOfWeek = schedule.dayOfWeek,
            tasks = tasks,
            motivation = schedule.motivation,
            stats = stats
        )

        val result = TelegramService.sendMessage(token, chat, text)
        if (result.isSuccess) {
            repository.updateScheduleTelegramStatus(schedule.date, true, null)
        } else {
            repository.updateScheduleTelegramStatus(schedule.date, false, result.exceptionOrNull()?.message)
        }
        return result
    }

    private suspend fun sendNotificationToTelegram(text: String) {
        val token = settingsManager.telegramBotToken
        val chat = settingsManager.telegramChatId
        if (token.isNotBlank() && chat.isNotBlank()) {
            TelegramService.sendMessage(token, chat, text)
        }
    }

    fun rescheduleAlarms() {
        viewModelScope.launch {
            val date = getTodayDateString()
            val tasks = repository.getTasksSync(date)
            val schedule = repository.getScheduleSync(date)
            if (settingsManager.isNotificationsEnabled) {
                StudyNotificationManager.scheduleAlarmsForTasks(
                    getApplication(),
                    tasks,
                    schedule?.motivation ?: "Let's achieve 100% completion today!"
                )
            } else {
                // Passes empty list to cancel all scheduled reminders
                StudyNotificationManager.scheduleAlarmsForTasks(getApplication(), emptyList())
            }
        }
    }

    fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    fun resetPlannerState() {
        _generateState.value = PlannerState.Idle
    }

    fun resetTelegramState() {
        _telegramState.value = TelegramState.Idle
    }

    // --- Niraj AI Chat & Voice Assistant ---

    val chatSearchQuery = MutableStateFlow("")
    val activeSessionId = MutableStateFlow<String?>(null)
    val isAiResponding = MutableStateFlow(false)
    val lastAiResponseEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)

    val allChatSessions: StateFlow<List<com.example.core.db.ChatSessionEntity>> = combine(
        repository.allChatSessions,
        chatSearchQuery
    ) { sessions, query ->
        val filtered = if (query.isBlank()) {
            sessions
        } else {
            sessions.filter { it.title.contains(query, ignoreCase = true) }
        }
        filtered.sortedWith(compareByDescending<com.example.core.db.ChatSessionEntity> { it.isPinned }
            .thenByDescending { it.updatedAt })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeMessages: StateFlow<List<com.example.core.db.ChatMessageEntity>> = activeSessionId
        .flatMapLatest { id ->
            if (id != null) repository.getMessagesForSession(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectSession(id: String?) {
        activeSessionId.value = id
    }

    fun startNewChatSession(title: String = "Naya Chat") {
        viewModelScope.launch {
            val sessionId = UUID.randomUUID().toString()
            val newSession = com.example.core.db.ChatSessionEntity(
                id = sessionId,
                title = title,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            repository.insertSession(newSession)
            activeSessionId.value = sessionId
        }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch {
            repository.deleteSession(id)
            if (activeSessionId.value == id) {
                activeSessionId.value = null
            }
        }
    }

    fun togglePinSession(id: String, currentPinned: Boolean) {
        viewModelScope.launch {
            repository.updatePinned(id, !currentPinned)
        }
    }

    fun renameSession(id: String, newTitle: String) {
        viewModelScope.launch {
            repository.updateSessionTitle(id, newTitle)
        }
    }

    fun sendMessage(text: String, attachedBitmap: Bitmap? = null, attachedPdfText: String? = null) {
        if (text.isBlank() && attachedBitmap == null && attachedPdfText == null) return

        viewModelScope.launch {
            // Ensure we have an active chat session; create one if absent
            var currentSessionId = activeSessionId.value
            if (currentSessionId == null) {
                currentSessionId = UUID.randomUUID().toString()
                val sessionTitle = if (text.length > 20) text.take(20) + "..." else text.ifBlank { "Attached Doc" }
                val newSession = com.example.core.db.ChatSessionEntity(
                    id = currentSessionId,
                    title = sessionTitle,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                repository.insertSession(newSession)
                activeSessionId.value = currentSessionId
            }

            // Insert User Message
            val userMsgId = UUID.randomUUID().toString()
            val userMsg = com.example.core.db.ChatMessageEntity(
                messageId = userMsgId,
                sessionId = currentSessionId,
                role = "user",
                text = text,
                timestamp = System.currentTimeMillis(),
                localImagePath = if (attachedBitmap != null) "attached_image_placeholder" else null,
                docText = attachedPdfText
            )
            repository.insertMessage(userMsg)

            // Auto-update conversation title if it was named "Naya Chat" or placeholder
            val currentSession = repository.allChatSessions.firstOrNull()?.find { it.id == currentSessionId }
            if (currentSession != null && (currentSession.title == "Naya Chat" || currentSession.title == "Naya Chat Assistant")) {
                val newTitle = if (text.length > 25) text.take(25) + "..." else text.ifBlank { "Attached Doc" }
                repository.updateSessionTitle(currentSessionId, newTitle)
            }

            isAiResponding.value = true

            // Gather context
            val date = getTodayDateString()
            val todaySched = currentSchedule.value
            val todayTsk = currentTasks.value
            val allSched = allSchedules.value
            val allTsk = allTasks.value
            val history = repository.getMessagesForSessionSync(currentSessionId)

            // Generate AI Response
            val responseText = com.example.core.scheduler.NirajAiService.getAiResponse(
                prompt = text,
                history = history,
                todayDate = date,
                todaySchedule = todaySched,
                todayTasks = todayTsk,
                allSchedules = allSched,
                allTasks = allTsk,
                settings = settingsManager,
                attachedBitmap = attachedBitmap,
                attachedPdfText = attachedPdfText
            )

            // Insert AI Response Message
            val aiMsgId = UUID.randomUUID().toString()
            val aiMsg = com.example.core.db.ChatMessageEntity(
                messageId = aiMsgId,
                sessionId = currentSessionId,
                role = "model",
                text = responseText,
                timestamp = System.currentTimeMillis()
            )
            repository.insertMessage(aiMsg)

            isAiResponding.value = false
            lastAiResponseEvent.emit(responseText)
        }
    }

    fun editMessage(messageId: String, newText: String) {
        viewModelScope.launch {
            repository.updateMessageText(messageId, newText)
            
            // Re-trigger generation for AI response if desired, or let user click regenerate
            val sessionId = activeSessionId.value ?: return@launch
            val messages = repository.getMessagesForSessionSync(sessionId)
            val index = messages.indexOfFirst { it.messageId == messageId }
            if (index != -1 && index < messages.size - 1) {
                // Remove following model messages so it is a natural regeneration
                for (i in index + 1 until messages.size) {
                    if (messages[i].role == "model") {
                        repository.deleteMessage(messages[i].messageId)
                    }
                }
            }
            // Trigger generation with updated text
            isAiResponding.value = true
            val date = getTodayDateString()
            val history = repository.getMessagesForSessionSync(sessionId).take(index)
            val responseText = com.example.core.scheduler.NirajAiService.getAiResponse(
                prompt = newText,
                history = history,
                todayDate = date,
                todaySchedule = currentSchedule.value,
                todayTasks = currentTasks.value,
                allSchedules = allSchedules.value,
                allTasks = allTasks.value,
                settings = settingsManager
            )
            val aiMsgId = UUID.randomUUID().toString()
            val aiMsg = com.example.core.db.ChatMessageEntity(
                messageId = aiMsgId,
                sessionId = sessionId,
                role = "model",
                text = responseText,
                timestamp = System.currentTimeMillis()
            )
            repository.insertMessage(aiMsg)
            isAiResponding.value = false
            lastAiResponseEvent.emit(responseText)
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
        }
    }

    fun regenerateResponse() {
        val sessionId = activeSessionId.value ?: return
        viewModelScope.launch {
            val messages = repository.getMessagesForSessionSync(sessionId)
            val lastUserMsg = messages.lastOrNull { it.role == "user" } ?: return@launch
            
            // Delete the last AI response if it exists
            val lastMsg = messages.lastOrNull()
            if (lastMsg != null && lastMsg.role == "model") {
                repository.deleteMessage(lastMsg.messageId)
            }

            isAiResponding.value = true
            val date = getTodayDateString()
            val history = repository.getMessagesForSessionSync(sessionId).filter { it.messageId != lastMsg?.messageId }
            
            val responseText = com.example.core.scheduler.NirajAiService.getAiResponse(
                prompt = lastUserMsg.text,
                history = history.dropLast(1),
                todayDate = date,
                todaySchedule = currentSchedule.value,
                todayTasks = currentTasks.value,
                allSchedules = allSchedules.value,
                allTasks = allTasks.value,
                settings = settingsManager,
                attachedPdfText = lastUserMsg.docText
            )

            val aiMsgId = UUID.randomUUID().toString()
            val aiMsg = com.example.core.db.ChatMessageEntity(
                messageId = aiMsgId,
                sessionId = sessionId,
                role = "model",
                text = responseText,
                timestamp = System.currentTimeMillis()
            )
            repository.insertMessage(aiMsg)
            isAiResponding.value = false
            lastAiResponseEvent.emit(responseText)
        }
    }

    fun continueGenerating() {
        val sessionId = activeSessionId.value ?: return
        viewModelScope.launch {
            val messages = repository.getMessagesForSessionSync(sessionId)
            val lastModelMsg = messages.lastOrNull { it.role == "model" } ?: return@launch

            isAiResponding.value = true
            val date = getTodayDateString()
            
            val responseText = com.example.core.scheduler.NirajAiService.getAiResponse(
                prompt = "Please continue where you left off. Continue your response completely.",
                history = messages,
                todayDate = date,
                todaySchedule = currentSchedule.value,
                todayTasks = currentTasks.value,
                allSchedules = allSchedules.value,
                allTasks = allTasks.value,
                settings = settingsManager
            )

            // Concatenate or insert as continuous message block
            val updatedText = lastModelMsg.text + "\n\n" + responseText
            repository.updateMessageText(lastModelMsg.messageId, updatedText)
            
            isAiResponding.value = false
            lastAiResponseEvent.emit(updatedText)
        }
    }

    fun clearMemory() {
        viewModelScope.launch {
            // Delete all chats & conversation history safely
            val sessions = repository.allChatSessions.firstOrNull() ?: emptyList()
            for (session in sessions) {
                repository.deleteSession(session.id)
            }
            activeSessionId.value = null
        }
    }
}


sealed interface PlannerState {
    object Idle : PlannerState
    object Generating : PlannerState
    data class Success(val schedule: DailyScheduleEntity) : PlannerState
    data class Error(val message: String) : PlannerState
}

sealed interface TelegramState {
    object Idle : TelegramState
    object Sending : TelegramState
    object Success : TelegramState
    data class Error(val message: String) : TelegramState
}
