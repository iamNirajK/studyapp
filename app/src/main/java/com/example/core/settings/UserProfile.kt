package com.example.core.settings

data class UserProfile(
    val setupCompleted: Boolean = true,
    val name: String,
    val dateOfBirth: String,
    val telegramEnabled: Boolean,
    val telegramChatId: String? = null,
    val telegramBotToken: String? = null
)
