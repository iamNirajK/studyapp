package com.example.core.scheduler

import android.util.Log
import com.example.BuildConfig
import com.example.core.db.DailyScheduleEntity
import com.example.core.db.TaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiScheduler {

    private const val TAG = "GeminiScheduler"

    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    suspend fun generateSchedule(
        date: String,
        activeClasses: List<String>,
        revisionSubjects: List<String>,
        extraTasks: List<String>,
        targetStudyHours: Double
    ): Pair<DailyScheduleEntity, List<TaskEntity>> {
        // Fallback checks
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY" || apiKey.startsWith("placeholder", ignoreCase = true)) {
            Log.d(TAG, "No valid Gemini API key found. Falling back to Local Scheduler.")
            return LocalScheduler.generateLocalSchedule(date, activeClasses, revisionSubjects, extraTasks, targetStudyHours)
        }

        val prompt = buildPrompt(date, activeClasses, revisionSubjects, extraTasks, targetStudyHours)

        return withContext(Dispatchers.IO) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
                
                val payload = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply { put("text", prompt) })
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("responseMimeType", "application/json")
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
                        parseGeminiResponse(date, responseStr)
                    } else {
                        Log.e(TAG, "Gemini API failed with code ${response.code}. Falling back.")
                        LocalScheduler.generateLocalSchedule(date, activeClasses, revisionSubjects, extraTasks, targetStudyHours)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gemini scheduler threw exception: ${e.message}. Falling back.")
                LocalScheduler.generateLocalSchedule(date, activeClasses, revisionSubjects, extraTasks, targetStudyHours)
            }
        }
    }

    private fun buildPrompt(
        date: String,
        activeClasses: List<String>,
        revisionSubjects: List<String>,
        extraTasks: List<String>,
        targetStudyHours: Double
    ): String {
        return """
            You are a world-class academic study planner assistant. Create a highly structured, realistic daily study schedule for the date: $date.
            
            Inputs:
            - Active Locked Live Classes today: ${activeClasses.joinToString(", ")}
            - Subjects that require Revision today: ${revisionSubjects.joinToString(", ")}
            - Extra tasks to complete today: ${extraTasks.joinToString(", ")}
            - Target study/learning hours: $targetStudyHours hours (approx. including live classes and revisions).

            Locked Live Class Slots (If active, lock these exact times permanently; NEVER change or overlap them):
            - Chemistry: 07:00 to 08:30 (90 mins)
            - Maths: 12:00 to 13:30 (90 mins)
            - English: 18:00 to 19:30 (90 mins)
            - Physics: 19:30 to 21:00 (90 mins)
            - Hindi: 21:30 to 23:00 (90 mins)

            Rules & Guidelines:
            1. All live classes that are active MUST be locked at their exact times.
            2. Schedule Sleep (23:00 to 06:00) and Meals (Lunch: 13:30-14:15, Dinner: 21:00-21:30).
            3. Schedule Revision sessions for selected subjects. Prefer placing revision BEFORE the corresponding live class (with a 15-min gap), else place it after or in other free afternoon/evening slots.
            4. Place Extra Tasks (e.g., PYQ, Mock Test, Assignment) logically. Leave at least a 15-minute gap between a Mock Test and any live class.
            5. Study blocks should be 30, 45, 60, or 90 minutes. Never schedule study blocks longer than 90 minutes.
            6. Automatically insert 5-15 minute break blocks after each study session.
            7. Reserve at least one 20-30 minute "Daily Buffer Time" block in the afternoon for unexpected delays.
            8. Schedule hard conceptual or numerical problem-solving tasks in the morning/afternoon, and lighter summary/revision/notes work late at night.
            9. Ensure there are absolutely NO overlapping tasks and every minute of the active day (06:00 to 23:00) is accounted for or clearly has free slots/breaks.

            You MUST return a JSON object matching this schema exactly:
            {
              "focusSubject": "String (The primary subject focus of today based on revision and live classes)",
              "motivation": "String (At beginning of day, display high-energy academic motivation)",
              "productivityScore": Int (predicted score 60-100),
              "studyHours": Double (sum of live classes, self study, revision, and practice hours in decimal),
              "revisionHours": Double (sum of revision hours in decimal),
              "breakHours": Double (sum of breaks and meal durations in decimal),
              "tasks": [
                {
                  "title": "String (e.g., Chemistry Live Class, Maths Revision, Physics Self Study)",
                  "subject": "String (Chemistry, Maths, English, Physics, Hindi, General, Custom)",
                  "type": "String (Live Class, Revision, Self Study, Mock Test, PYQ, Notes, Break, Meal, Buffer, Sleep)",
                  "priority": "String (High, Medium, Low, Critical)",
                  "startTime": "String (HH:mm 24-hour format, e.g., '07:00')",
                  "endTime": "String (HH:mm 24-hour format, e.g., '08:30')",
                  "duration": Int (duration in minutes),
                  "notes": "String (Brief guidance on what to cover)"
                }
              ]
            }
        """.trimIndent()
    }

    private fun parseGeminiResponse(date: String, jsonResponse: String): Pair<DailyScheduleEntity, List<TaskEntity>> {
        val root = JSONObject(jsonResponse)
        val candidates = root.optJSONArray("candidates")
        val content = candidates?.optJSONObject(0)?.optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        val rawText = parts?.optJSONObject(0)?.optString("text") ?: ""

        // Sanitize raw text to find JSON object bounds if there's any markdown wrapper
        val jsonStart = rawText.indexOf("{")
        val jsonEnd = rawText.lastIndexOf("}") + 1
        val sanitizedJson = if (jsonStart >= 0 && jsonEnd > jsonStart) {
            rawText.substring(jsonStart, jsonEnd)
        } else {
            rawText
        }

        val data = JSONObject(sanitizedJson)
        val focusSubject = data.optString("focusSubject", "General")
        val motivation = data.optString("motivation", "Let's achieve 100% completion today!")
        val productivityScore = data.optInt("productivityScore", 85)
        val studyHours = data.optDouble("studyHours", 5.0)
        val revisionHours = data.optDouble("revisionHours", 1.5)
        val breakHours = data.optDouble("breakHours", 1.5)

        val taskArray = data.optJSONArray("tasks") ?: JSONArray()
        val tasksList = mutableListOf<TaskEntity>()

        for (i in 0 until taskArray.length()) {
            val taskObj = taskArray.getJSONObject(i)
            val title = taskObj.optString("title", "Study Block")
            val subject = taskObj.optString("subject", "General")
            val type = taskObj.optString("type", "Self Study")
            val priority = taskObj.optString("priority", "Medium")
            val startTime = taskObj.optString("startTime", "08:00")
            val endTime = taskObj.optString("endTime", "09:00")
            val duration = taskObj.optInt("duration", 60)
            val notes = taskObj.optString("notes", "")

            val fixed = type == "Live Class" || type == "Sleep" || type == "Meal"

            tasksList.add(
                TaskEntity(
                    taskId = "${date}_${startTime.replace(":", "")}",
                    date = date,
                    title = title,
                    subject = subject,
                    type = type,
                    priority = priority,
                    startTime = startTime,
                    endTime = endTime,
                    durationMinutes = duration,
                    status = "Pending",
                    fixed = fixed,
                    notes = notes
                )
            )
        }

        // Just in case Gemini missed sorting, sort tasks by start time
        val sortedTasks = tasksList.sortedBy { it.startTime }

        val schedule = DailyScheduleEntity(
            date = date,
            dayOfWeek = LocalScheduler.generateLocalSchedule(date, emptyList(), emptyList(), emptyList(), 0.0).first.dayOfWeek,
            studyHours = studyHours,
            revisionHours = revisionHours,
            breakHours = breakHours,
            completion = 0,
            focusSubject = focusSubject,
            productivityScore = productivityScore,
            motivation = motivation
        )

        return Pair(schedule, sortedTasks)
    }
}
