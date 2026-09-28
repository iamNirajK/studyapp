package com.example.core.scheduler

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.core.db.DailyScheduleEntity
import com.example.core.db.TaskEntity
import com.example.core.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object NirajAiService {

    private const val TAG = "NirajAiService"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    // Helper to convert Bitmap to Base64
    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    suspend fun getAiResponse(
        prompt: String,
        history: List<com.example.core.db.ChatMessageEntity>,
        todayDate: String,
        todaySchedule: DailyScheduleEntity?,
        todayTasks: List<TaskEntity>,
        allSchedules: List<DailyScheduleEntity>,
        allTasks: List<TaskEntity>,
        settings: SettingsManager,
        attachedBitmap: Bitmap? = null,
        attachedPdfText: String? = null
    ): String = withContext(Dispatchers.IO) {
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY" || apiKey.startsWith("placeholder", ignoreCase = true)) {
            return@withContext "Mujhe lagta hai aapne abhi tak **Gemini API Key** set nahi ki hai settings mein ya `.env` file mein. Please Settings panel mein jaakar ek valid API key secure karein taaki main aapki padhai me madad kar sakun! 🛡️"
        }

        // Build System Prompt injected with exact app context
        val systemPrompt = buildSystemPrompt(todayDate, todaySchedule, todayTasks, allSchedules, allTasks, settings)

        // Construct Request payload for gemini-3.5-flash
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        try {
            val contentsArray = JSONArray()

            // Add Chat History (limit last 20 turns to prevent context overflow)
            val filteredHistory = history.takeLast(20)
            for (msg in filteredHistory) {
                contentsArray.put(JSONObject().apply {
                    put("role", if (msg.role == "user") "user" else "model")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", msg.text)
                        })
                    })
                })
            }

            // Add current turn
            contentsArray.put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply {
                    // Current text prompt
                    val textBuilder = StringBuilder()
                    if (!attachedPdfText.isNullOrBlank()) {
                        textBuilder.append("[USER UPLOADED DOCUMENT CONTENT]:\n")
                        textBuilder.append(attachedPdfText)
                        textBuilder.append("\n\n---\n\n")
                    }
                    textBuilder.append(prompt)
                    
                    put(JSONObject().apply {
                        put("text", textBuilder.toString())
                    })

                    // Attached Image Part
                    if (attachedBitmap != null) {
                        put(JSONObject().apply {
                            put("inlineData", JSONObject().apply {
                                put("mimeType", "image/jpeg")
                                put("data", attachedBitmap.toBase64())
                            })
                        })
                    }
                })
            })

            val payload = JSONObject().apply {
                put("contents", contentsArray)
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemPrompt)
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                })
            }

            val body = payload.toString().toRequestBody(mediaTypeJson)
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val jsonResponse = JSONObject(responseStr)
                    val candidates = jsonResponse.optJSONArray("candidates")
                    val firstCandidate = candidates?.optJSONObject(0)
                    val content = firstCandidate?.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    val firstPart = parts?.optJSONObject(0)
                    
                    return@withContext firstPart?.optString("text") ?: "Mujhe koi response nahi mila. Dobara prayas karein."
                } else {
                    Log.e(TAG, "Gemini API Request failed with code: ${response.code}, body: $responseStr")
                    return@withContext "API Error: Gemini API ne response code ${response.code} diya. Kripya apna internet aur API Key check karein."
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getAiResponse: ${e.message}", e)
            return@withContext "Error: ${e.message}. Kripya apna network check karein."
        }
    }

    private fun buildSystemPrompt(
        todayDate: String,
        todaySchedule: DailyScheduleEntity?,
        todayTasks: List<TaskEntity>,
        allSchedules: List<DailyScheduleEntity>,
        allTasks: List<TaskEntity>,
        settings: SettingsManager
    ): String {
        val scheduleText = if (todaySchedule != null) {
            """
            - Focus Subject: ${todaySchedule.focusSubject}
            - Target Study Hours: ${todaySchedule.studyHours} hours
            - Revision Target: ${todaySchedule.revisionHours} hours
            - Break Allocation: ${todaySchedule.breakHours} hours
            - Completion Rate: ${todaySchedule.completion}%
            - Productivity Score: ${todaySchedule.productivityScore}/100
            - Day's Motivation Phrase: ${todaySchedule.motivation}
            """.trimIndent()
        } else {
            "No active schedule generated for today yet."
        }

        val tasksText = if (todayTasks.isNotEmpty()) {
            todayTasks.joinToString("\n") { task ->
                "- [${task.status}] ${task.startTime} to ${task.endTime} (${task.durationMinutes} min) | ${task.title} [${task.subject}] (Category: ${task.type}, Priority: ${task.priority}) ${if (task.notes.isNotEmpty()) "Notes: ${task.notes}" else ""}"
            }
        } else {
            "No individual tasks found for today."
        }

        return """
            You are "Niraj AI", a friendly, patient, highly intelligent, motivating, and professional digital study mentor and voice assistant for the student Niraj (and users of this app).
            
            CRITICAL ROLE & RULES:
            1. Your name is Niraj AI.
            2. Speak naturally in Hindi, English, and Hinglish. Mix languages like an Indian mentor would ("Beta, ye concept clear hai?", "Let's crack JEE!", "Aapka schedule bohot accha hai!").
            3. Actively support continuous voice interruptions. Give concise, warm, and highly engaging voice-friendly replies.
            4. If the user asks "What should I study now?", look closely at today's tasks and the current local time to suggest the most appropriate pending task. Never invent app data.
            5. Provide notes, formulate quizzes (MCQs/PYQs), explain coding concepts, and solve doubts instantly.
            
            USER CURRENT LOCAL DATE: $todayDate
            
            TODAY'S SCHEDULE ($todayDate):
            $scheduleText
            
            TODAY'S PLAN OF ACTION (TASKS):
            $tasksText
            
            APP CONFIGURATION:
            - Telegram Integration Active: ${settings.isTelegramEnabled}
            - Telegram Chat ID: ${settings.telegramChatId}
            - Notifications Enabled: ${settings.isNotificationsEnabled}
            - Water Reminders Active: ${settings.isWaterRemindersEnabled}
            
            COACHING PERSONALITY:
            - Encouraging, supportive, structured, precise, and professional.
            - Never rude, never lie, never hallucinate.
            - If details of any subject are missing, ask them what subject they want to cover.
        """.trimIndent()
    }
}
