package com.example.core.settings

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "study_planner_settings"
        private const val KEY_SETUP_COMPLETED = "user_setup_completed"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_DOB = "user_dob"
        private const val KEY_TELEGRAM_ENABLED = "telegram_enabled"
        private const val KEY_TELEGRAM_BOT_TOKEN = "telegram_bot_token"
        private const val KEY_TELEGRAM_CHAT_ID = "telegram_chat_id"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_NOTIFS_ENABLED = "notifs_enabled"
        private const val KEY_WATER_REMINDERS = "water_reminders"
        private const val KEY_DAILY_MOTIVATION = "daily_motivation"
        private const val KEY_LEAD_TIME = "lead_time_minutes"
        private const val KEY_QUIET_HOURS_ENABLED = "quiet_hours_enabled"
        private const val KEY_QUIET_HOURS_START = "quiet_hours_start"
        private const val KEY_QUIET_HOURS_END = "quiet_hours_end"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_REMINDER_30M = "reminder_30m_enabled"
        private const val KEY_REMINDER_5M = "reminder_5m_enabled"

        private const val KEY_VOICE_ENABLED = "voice_enabled"
        private const val KEY_FEMALE_VOICE = "female_voice"
        private const val KEY_SPEECH_SPEED = "speech_speed"
        private const val KEY_SPEECH_PITCH = "speech_pitch"
        private const val KEY_VOICE_VOLUME = "voice_volume"
        private const val KEY_VOICE_LANGUAGE = "voice_language"
        private const val KEY_AUTO_SPEAK = "auto_speak"
        private const val KEY_YOUTUBE_API_KEY = "youtube_api_key"
    }

    // --- Persistent User Profile ---
    var isSetupCompleted: Boolean
        get() = prefs.getBoolean(KEY_SETUP_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_SETUP_COMPLETED, value).apply()

    var userName: String
        get() = prefs.getString(KEY_USER_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    var userDob: String
        get() = prefs.getString(KEY_USER_DOB, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_DOB, value).apply()

    var isTelegramEnabled: Boolean
        get() = prefs.getBoolean(KEY_TELEGRAM_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_TELEGRAM_ENABLED, value).apply()

    var telegramBotToken: String
        get() = prefs.getString(KEY_TELEGRAM_BOT_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TELEGRAM_BOT_TOKEN, value).apply()

    var telegramChatId: String
        get() = prefs.getString(KEY_TELEGRAM_CHAT_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TELEGRAM_CHAT_ID, value).apply()

    // Backward compatibility alias
    var isSessionSetupCompleted: Boolean
        get() = isSetupCompleted
        set(value) {
            isSetupCompleted = value
        }

    /**
     * Persistently saves the user profile and immediately reads it back to verify.
     * Returns true only after successful storage verification.
     */
    fun saveUserProfile(profile: UserProfile): Boolean {
        val editor = prefs.edit()
            .putBoolean(KEY_SETUP_COMPLETED, true)
            .putString(KEY_USER_NAME, profile.name.trim())
            .putString(KEY_USER_DOB, profile.dateOfBirth.trim())
            .putBoolean(KEY_TELEGRAM_ENABLED, profile.telegramEnabled)

        if (profile.telegramEnabled) {
            editor.putString(KEY_TELEGRAM_BOT_TOKEN, profile.telegramBotToken?.trim() ?: "")
            editor.putString(KEY_TELEGRAM_CHAT_ID, profile.telegramChatId?.trim() ?: "")
        } else {
            editor.remove(KEY_TELEGRAM_BOT_TOKEN)
            editor.remove(KEY_TELEGRAM_CHAT_ID)
        }

        val committed = editor.commit()
        if (!committed) return false

        // Verification: Read back profile from persistent storage
        val readBack = getUserProfile()
        return readBack != null &&
                readBack.setupCompleted &&
                readBack.name == profile.name.trim() &&
                readBack.dateOfBirth == profile.dateOfBirth.trim() &&
                readBack.telegramEnabled == profile.telegramEnabled
    }

    /**
     * Retrieves the saved user profile from persistent storage.
     * Returns null if setup has not been completed or required data is missing.
     */
    fun getUserProfile(): UserProfile? {
        val completed = prefs.getBoolean(KEY_SETUP_COMPLETED, false)
        val name = prefs.getString(KEY_USER_NAME, "") ?: ""
        if (!completed || name.isBlank()) {
            return null
        }
        val dob = prefs.getString(KEY_USER_DOB, "") ?: ""
        val telegram = prefs.getBoolean(KEY_TELEGRAM_ENABLED, false)
        val botToken = if (telegram) prefs.getString(KEY_TELEGRAM_BOT_TOKEN, null) else null
        val chatId = if (telegram) prefs.getString(KEY_TELEGRAM_CHAT_ID, null) else null

        return UserProfile(
            setupCompleted = true,
            name = name,
            dateOfBirth = dob,
            telegramEnabled = telegram,
            telegramChatId = chatId,
            telegramBotToken = botToken
        )
    }

    /**
     * Clears persistent user profile data so that onboarding can be performed again.
     */
    fun resetUserProfile(): Boolean {
        return prefs.edit()
            .putBoolean(KEY_SETUP_COMPLETED, false)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_DOB)
            .remove(KEY_TELEGRAM_ENABLED)
            .remove(KEY_TELEGRAM_BOT_TOKEN)
            .remove(KEY_TELEGRAM_CHAT_ID)
            .commit()
    }


    var isDarkMode: Boolean?
        get() {
            return if (prefs.contains(KEY_DARK_MODE)) prefs.getBoolean(KEY_DARK_MODE, false) else null
        }
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_DARK_MODE).apply()
            } else {
                prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()
            }
        }

    var isNotificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFS_ENABLED, value).apply()

    var isWaterRemindersEnabled: Boolean
        get() = prefs.getBoolean(KEY_WATER_REMINDERS, true)
        set(value) = prefs.edit().putBoolean(KEY_WATER_REMINDERS, value).apply()

    var isDailyMotivationEnabled: Boolean
        get() = prefs.getBoolean(KEY_DAILY_MOTIVATION, true)
        set(value) = prefs.edit().putBoolean(KEY_DAILY_MOTIVATION, value).apply()

    var leadTimeMinutes: Int
        get() = prefs.getInt(KEY_LEAD_TIME, 10)
        set(value) = prefs.edit().putInt(KEY_LEAD_TIME, value).apply()

    var isQuietHoursEnabled: Boolean
        get() = prefs.getBoolean(KEY_QUIET_HOURS_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_QUIET_HOURS_ENABLED, value).apply()

    var quietHoursStart: String
        get() = prefs.getString(KEY_QUIET_HOURS_START, "22:00") ?: "22:00"
        set(value) = prefs.edit().putString(KEY_QUIET_HOURS_START, value).apply()

    var quietHoursEnd: String
        get() = prefs.getString(KEY_QUIET_HOURS_END, "07:00") ?: "07:00"
        set(value) = prefs.edit().putString(KEY_QUIET_HOURS_END, value).apply()

    var isSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()

    var isVibrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, value).apply()

    var is30MinReminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMINDER_30M, true)
        set(value) = prefs.edit().putBoolean(KEY_REMINDER_30M, value).apply()

    var is5MinReminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMINDER_5M, false)
        set(value) = prefs.edit().putBoolean(KEY_REMINDER_5M, value).apply()

    // --- Voice Assistant Settings ---
    var isVoiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOICE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_ENABLED, value).apply()

    var isFemaleVoice: Boolean
        get() = prefs.getBoolean(KEY_FEMALE_VOICE, true)
        set(value) = prefs.edit().putBoolean(KEY_FEMALE_VOICE, value).apply()

    var speechSpeed: Float
        get() = prefs.getFloat(KEY_SPEECH_SPEED, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_SPEECH_SPEED, value).apply()

    var speechPitch: Float
        get() = prefs.getFloat(KEY_SPEECH_PITCH, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_SPEECH_PITCH, value).apply()

    var voiceVolume: Float
        get() = prefs.getFloat(KEY_VOICE_VOLUME, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_VOICE_VOLUME, value).apply()

    var voiceLanguage: String
        get() = prefs.getString(KEY_VOICE_LANGUAGE, "Hinglish") ?: "Hinglish"
        set(value) = prefs.edit().putString(KEY_VOICE_LANGUAGE, value).apply()

    var isAutoSpeakEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SPEAK, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SPEAK, value).apply()

    var youtubeApiKey: String
        get() = prefs.getString(KEY_YOUTUBE_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_YOUTUBE_API_KEY, value).apply()

    fun clearTelegramConfig() {
        isTelegramEnabled = false
        telegramBotToken = ""
        telegramChatId = ""
        prefs.edit()
            .remove(KEY_TELEGRAM_ENABLED)
            .remove(KEY_TELEGRAM_BOT_TOKEN)
            .remove(KEY_TELEGRAM_CHAT_ID)
            .apply()
    }
}

