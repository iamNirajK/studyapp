package com.example.core.telegram

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object TelegramService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    fun validateBotToken(botToken: String): Boolean {
        if (botToken.isBlank()) return false
        val regex = Regex("^\\d+:[a-zA-Z0-9_-]+$")
        return regex.matches(botToken)
    }

    fun validateChatId(chatId: String): Boolean {
        if (chatId.isBlank()) return false
        if (chatId.toLongOrNull() != null) {
            return true
        }
        val usernameRegex = Regex("^@[a-zA-Z0-9_]{5,32}$")
        return usernameRegex.matches(chatId)
    }

    suspend fun testConnection(botToken: String, chatId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val trimmedToken = botToken.trim()
        val trimmedChatId = chatId.trim()

        if (trimmedToken.isEmpty()) {
            return@withContext Result.failure(Exception("Bot Token cannot be empty."))
        }
        if (!validateBotToken(trimmedToken)) {
            return@withContext Result.failure(Exception(
                "Invalid Bot Token format.\n\n" +
                "👉 A valid Telegram token looks like '123456789:ABC_def...'\n" +
                "Please check your token from @BotFather."
            ))
        }

        if (trimmedChatId.isEmpty()) {
            return@withContext Result.failure(Exception("Chat ID / Channel ID cannot be empty."))
        }
        if (!validateChatId(trimmedChatId)) {
            return@withContext Result.failure(Exception(
                "Invalid Chat ID format.\n\n" +
                "👉 For a user or group, it must be a numeric value (e.g., 987654321 or -1001234567890).\n" +
                "👉 For a public channel, it can be a username starting with '@' (e.g., @mychannel)."
            ))
        }

        val url = "https://api.telegram.org/bot$trimmedToken/getMe"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        var botUsername = ""
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val json = try { JSONObject(body) } catch (e: Exception) { null }
                    val description = json?.optString("description") ?: "HTTP error ${response.code}"
                    return@withContext Result.failure(Exception("Authentication failed: $description"))
                } else {
                    val json = try { JSONObject(body) } catch (e: Exception) { null }
                    botUsername = json?.optJSONObject("result")?.optString("username") ?: ""
                }
            }
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("Network error: ${e.message}"))
        }

        // Try to send a simple greeting to Chat ID to confirm Chat ID validity and permission
        sendMessageDirect(
            botToken = trimmedToken,
            chatId = trimmedChatId,
            text = "🔄 <b>SK Study Planner Connected!</b>\nTelegram integration configured successfully. 🚀",
            botUsername = botUsername
        )
    }

    suspend fun sendMessage(botToken: String, chatId: String, messageText: String): Result<Boolean> {
        return sendMessageDirect(botToken.trim(), chatId.trim(), messageText)
    }

    private suspend fun sendMessageDirect(
        botToken: String,
        chatId: String,
        text: String,
        botUsername: String = ""
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val trimmedToken = botToken.trim()
        val trimmedChatId = chatId.trim()

        if (trimmedToken.isEmpty() || trimmedChatId.isEmpty()) {
            return@withContext Result.failure(Exception("Bot Token or Chat ID is missing."))
        }
        if (!validateBotToken(trimmedToken)) {
            return@withContext Result.failure(Exception("Invalid Bot Token format."))
        }
        if (!validateChatId(trimmedChatId)) {
            return@withContext Result.failure(Exception("Invalid Chat ID format."))
        }

        val url = "https://api.telegram.org/bot$trimmedToken/sendMessage"
        val payload = JSONObject().apply {
            put("chat_id", trimmedChatId)
            put("text", text)
            put("parse_mode", "HTML")
        }

        val body = payload.toString().toRequestBody(mediaTypeJson)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    val json = try { JSONObject(responseStr) } catch (e: Exception) { null }
                    val description = json?.optString("description") ?: "HTTP error ${response.code}"
                    
                    val userFriendlyError = when {
                        description.contains("bot can't send messages to the user", ignoreCase = true) ||
                        description.contains("bot can't initiate conversation", ignoreCase = true) -> {
                            val linkText = if (botUsername.isNotBlank()) "t.me/$botUsername" else "your bot"
                            "Forbidden: The bot cannot send you messages because you haven't started a conversation with it yet.\n\n" +
                            "👉 Please open Telegram, go to $linkText, tap 'START', and then try again!"
                        }
                        description.contains("bot was blocked by the user", ignoreCase = true) -> {
                            val linkText = if (botUsername.isNotBlank()) "@$botUsername" else "the bot"
                            "Forbidden: The bot was blocked by you.\n\n" +
                            "👉 Please unblock $linkText in Telegram and try again!"
                        }
                        description.contains("chat not found", ignoreCase = true) -> {
                            "Chat Not Found: The provided Chat ID/Channel ID could not be found.\n\n" +
                            "👉 If it's a private chat, ensure you have started the bot.\n" +
                            "👉 If it's a channel or group, make sure you added the bot as an administrator or member."
                        }
                        else -> "Telegram API Error: $description"
                    }
                    Result.failure(Exception(userFriendlyError))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Telegram API Error: ${e.message}"))
        }
    }

    fun formatScheduleMessage(date: String, dayOfWeek: String, tasks: List<com.example.core.db.TaskEntity>, motivation: String, stats: Map<String, String>): String {
        val cleanDate = date.split("-").reversed().joinToString("/") // converts yyyy-MM-dd to dd/MM/yyyy
        val builder = StringBuilder()
        builder.append("📅 <b>SK Study Plan: $cleanDate ($dayOfWeek)</b>\n")
        builder.append("━━━━━━━━━━━━━━━━━━━━\n")
        builder.append("<i>\"$motivation\"</i>\n\n")

        tasks.forEach { task ->
            val icon = when (task.type) {
                "Live Class" -> "🧪"
                "Revision" -> "📖"
                "Self Study" -> "📐"
                "Mock Test" -> "📝"
                "PYQ" -> "📚"
                "Practice" -> "⚙️"
                "Notes" -> "✏️"
                "Meal" -> "🍽️"
                "Break" -> "☕"
                "Sleep" -> "😴"
                "Water Reminder" -> "💧"
                else -> "⚡"
            }
            val statusIcon = if (task.status == "Completed") "✅ " else ""
            val titleText = if (task.fixed) "<b>${task.title} (LIVE)</b>" else task.title
            val start12 = com.example.core.utils.TimeUtils.formatTo12Hour(task.startTime)
            val end12 = com.example.core.utils.TimeUtils.formatTo12Hour(task.endTime)
            builder.append("$start12–$end12\n")
            builder.append("$statusIcon$icon $titleText (${task.durationMinutes}m)\n")
            if (task.notes.isNotBlank()) {
                builder.append("  ↳ <i>${task.notes}</i>\n")
            }
            builder.append("\n")
        }

        builder.append("━━━━━━━━━━━━━━━━━━━━\n")
        builder.append("📊 <b>Today's Summary:</b>\n")
        stats.forEach { (key, value) ->
            builder.append("• $key: <b>$value</b>\n")
        }
        builder.append("━━━━━━━━━━━━━━━━━━━━\n")
        builder.append("Let's achieve 100% completion today! 🚀")

        return builder.toString()
    }
}
