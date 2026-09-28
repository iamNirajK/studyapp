package com.example.core.scheduler

import com.example.core.db.DailyScheduleEntity
import com.example.core.db.TaskEntity
import java.text.SimpleDateFormat
import java.util.*

object LocalScheduler {

    data class FixedClass(val name: String, val startTime: String, val endTime: String)

    val ALL_FIXED_CLASSES = listOf(
        FixedClass("Chemistry", "07:00", "08:30"),
        FixedClass("Maths", "12:00", "13:30"),
        FixedClass("English", "18:00", "19:30"),
        FixedClass("Physics", "19:30", "21:00"),
        FixedClass("Hindi", "21:30", "23:00")
    )

    fun generateLocalSchedule(
        date: String,
        activeClasses: List<String>,
        revisionSubjects: List<String>,
        extraTasks: List<String>,
        targetStudyHours: Double
    ): Pair<DailyScheduleEntity, List<TaskEntity>> {
        val tasks = mutableListOf<TaskEntity>()

        // 1. Always schedule Sleep (23:00 to 06:00)
        tasks.add(
            createTask(
                date, "00:00", "06:00", "Rest & Sleep", "General", "Sleep", "Low", fixed = true,
                notes = "Ensure 7-8 hours of quality sleep for peak memory consolidation."
            )
        )
        tasks.add(
            createTask(
                date, "23:00", "23:59", "Night Sleep", "General", "Sleep", "Low", fixed = true,
                notes = "Wind down and prepare for sleep."
            )
        )

        // 2. Schedule Live Classes selected by the user
        val activeFixed = ALL_FIXED_CLASSES.filter { it.name in activeClasses }
        activeFixed.forEach { live ->
            tasks.add(
                createTask(
                    date, live.startTime, live.endTime, "${live.name} Live Class", live.name, "Live Class", "Critical", fixed = true,
                    notes = "Live interactive lecture. Keep your notes ready!"
                )
            )
        }

        // 3. Schedule Lunch & Dinner
        tasks.add(
            createTask(
                date, "13:30", "14:15", "Lunch Break", "General", "Meal", "Medium", fixed = true,
                notes = "Rehydrate and enjoy a healthy meal."
            )
        )
        // Adjust Dinner if Hindi class is active (9:30-11:00 PM). Physics ends at 9:00 PM.
        // So dinner at 9:00 - 9:30 is perfect if Hindi is active, or 21:00-21:30 standard.
        tasks.add(
            createTask(
                date, "21:00", "21:30", "Dinner Break", "General", "Meal", "Medium", fixed = true,
                notes = "Light dinner to prevent sluggishness."
            )
        )

        // 4. Fill in Revision Blocks
        // Strategy: Try to schedule revision before class if class is active, else schedule during free afternoon hours.
        revisionSubjects.forEach { subject ->
            val correspondingLive = activeFixed.find { it.name == subject }
            if (correspondingLive != null) {
                // Schedule revision before class
                val revStart = subtractMinutes(correspondingLive.startTime, 75) // 1 hour revision + 15 min buffer
                val revEnd = subtractMinutes(correspondingLive.startTime, 15)
                if (isTimeSlotFree(tasks, revStart, revEnd)) {
                    tasks.add(
                        createTask(
                            date, revStart, revEnd, "$subject Revision", subject, "Revision", "High", fixed = false,
                            notes = "Pre-class review of formulas & concepts."
                        )
                    )
                } else {
                    // Place after class if before is occupied
                    val postStart = addMinutes(correspondingLive.endTime, 15)
                    val postEnd = addMinutes(correspondingLive.endTime, 75)
                    if (isTimeSlotFree(tasks, postStart, postEnd)) {
                        tasks.add(
                            createTask(
                                date, postStart, postEnd, "$subject Revision", subject, "Revision", "High", fixed = false,
                                notes = "Post-class summary review."
                            )
                        )
                    }
                }
            } else {
                // Standard revision in the afternoon
                if (isTimeSlotFree(tasks, "14:30", "15:30")) {
                    tasks.add(
                        createTask(
                            date, "14:30", "15:30", "$subject Revision", subject, "Revision", "High", fixed = false,
                            notes = "Focused subject revision."
                        )
                    )
                } else if (isTimeSlotFree(tasks, "16:00", "17:00")) {
                    tasks.add(
                        createTask(
                            date, "16:00", "17:00", "$subject Revision", subject, "Revision", "High", fixed = false,
                            notes = "Afternoon revision session."
                        )
                    )
                }
            }
        }

        // 5. Fill in Extra Tasks
        extraTasks.forEach { taskType ->
            when (taskType) {
                "Mock Test" -> {
                    // Needs at least 90 mins, not immediately before live class
                    if (isTimeSlotFree(tasks, "09:00", "11:00")) {
                        tasks.add(
                            createTask(
                                date, "09:00", "11:00", "Mock Test", "General", "Mock Test", "High", fixed = false,
                                notes = "Solve with full exam mindset. Do not look at answers."
                            )
                        )
                        // Add Analysis block 15 mins later
                        tasks.add(
                            createTask(
                                date, "11:15", "11:45", "Mock Test Analysis", "General", "Notes", "Medium", fixed = false,
                                notes = "Review weak questions and wrong choices."
                            )
                        )
                    } else if (isTimeSlotFree(tasks, "14:30", "16:30")) {
                        tasks.add(
                            createTask(
                                date, "14:30", "16:30", "Mock Test", "General", "Mock Test", "High", fixed = false,
                                notes = "Focused mock test session."
                            )
                        )
                    }
                }
                "PYQ" -> {
                    if (isTimeSlotFree(tasks, "15:30", "16:30")) {
                        tasks.add(
                            createTask(
                                date, "15:30", "16:30", "PYQ Practice", "General", "PYQ", "Medium", fixed = false,
                                notes = "Solve past 5 years chapter questions."
                            )
                        )
                    }
                }
                "Assignment" -> {
                    if (isTimeSlotFree(tasks, "16:45", "17:45")) {
                        tasks.add(
                            createTask(
                                date, "16:45", "17:45", "Assignment Completion", "General", "Practice", "Medium", fixed = false,
                                notes = "Complete school or coaching worksheets."
                            )
                        )
                    }
                }
                "Notes" -> {
                    if (isTimeSlotFree(tasks, "21:30", "22:15")) {
                        tasks.add(
                            createTask(
                                date, "21:30", "22:15", "Note Making & Summary", "General", "Notes", "Medium", fixed = false,
                                notes = "Synthesize today's formulas and key learnings."
                            )
                        )
                    }
                }
            }
        }

        // 6. Fill in Self Study slots if we still need more hours to meet target study hours
        var currentStudyMins = tasks.filter {
            it.type in listOf("Live Class", "Revision", "Self Study", "Mock Test", "PYQ", "Practice")
        }.sumOf { it.durationMinutes }

        val targetMins = (targetStudyHours * 60).toInt()

        if (currentStudyMins < targetMins) {
            // Find free morning study slot
            if (isTimeSlotFree(tasks, "08:45", "10:15")) {
                tasks.add(
                    createTask(
                        date, "08:45", "10:15", "Self Study (Concepts)", "General", "Self Study", "Medium", fixed = false,
                        notes = "Deep concept work on difficult topics."
                    )
                )
                currentStudyMins += 90
            }
            // Find free afternoon slot
            if (currentStudyMins < targetMins && isTimeSlotFree(tasks, "14:30", "15:30")) {
                tasks.add(
                    createTask(
                        date, "14:30", "15:30", "Self Study (Problem Solving)", "General", "Self Study", "Medium", fixed = false,
                        notes = "Practice advanced level textbook questions."
                    )
                )
                currentStudyMins += 60
            }
            // Find evening free slot
            if (currentStudyMins < targetMins && isTimeSlotFree(tasks, "17:00", "18:00")) {
                tasks.add(
                    createTask(
                        date, "17:00", "18:00", "Self Study (Quick Revision)", "General", "Self Study", "Medium", fixed = false,
                        notes = "Quick formula revision of general topics."
                    )
                )
                currentStudyMins += 60
            }
        }

        // 7. Add Buffer & Micro Breaks
        // Reserve 20-30 min buffer time in the afternoon
        if (isTimeSlotFree(tasks, "17:30", "17:55")) {
            tasks.add(
                createTask(
                    date, "17:30", "17:55", "Daily Buffer Time", "General", "Buffer", "Low", fixed = true,
                    notes = "Used to cover backlog, technical delays, or unexpected calls."
                )
            )
        }

        // 8. Insert small micro-breaks between study sessions automatically
        val sortedStudyTasks = tasks.filter { it.type != "Sleep" && it.type != "Meal" }.sortedBy { it.startTime }
        val breaksToAdd = mutableListOf<TaskEntity>()
        for (i in 0 until sortedStudyTasks.size - 1) {
            val curr = sortedStudyTasks[i]
            val next = sortedStudyTasks[i + 1]
            if (curr.endTime < next.startTime) {
                val gap = getMinutesGap(curr.endTime, next.startTime)
                if (gap >= 15) {
                    breaksToAdd.add(
                        createTask(
                            date, curr.endTime, addMinutes(curr.endTime, 10), "Micro Break", "General", "Break", "Low", fixed = true,
                            notes = "Get up, stretch, and rest your eyes."
                        )
                    )
                }
            }
        }
        tasks.addAll(breaksToAdd)

        // Final sorting of tasks by start time
        val finalTasks = tasks.sortedBy { it.startTime }

        // Calculate stats
        val totalStudy = finalTasks.filter {
            it.type in listOf("Live Class", "Self Study", "Mock Test", "PYQ", "Practice")
        }.sumOf { it.durationMinutes } / 60.0

        val totalRevision = finalTasks.filter { it.type == "Revision" }.sumOf { it.durationMinutes } / 60.0
        val totalBreak = finalTasks.filter { it.type == "Break" || it.type == "Meal" }.sumOf { it.durationMinutes } / 60.0

        val focusSubject = if (revisionSubjects.isNotEmpty()) revisionSubjects.first() else if (activeClasses.isNotEmpty()) activeClasses.first() else "General"

        val dayOfWeek = getDayOfWeekName(date)

        val scheduleEntity = DailyScheduleEntity(
            date = date,
            dayOfWeek = dayOfWeek,
            studyHours = totalStudy,
            revisionHours = totalRevision,
            breakHours = totalBreak,
            completion = 0, // start at 0 completion
            focusSubject = focusSubject,
            productivityScore = (80 + (targetStudyHours * 2).toInt()).coerceIn(60, 98),
            motivation = "Good Morning! Today's mission is waiting. Let's achieve 100% completion."
        )

        return Pair(scheduleEntity, finalTasks)
    }

    private fun createTask(
        date: String,
        startTime: String,
        endTime: String,
        title: String,
        subject: String,
        type: String,
        priority: String,
        fixed: Boolean,
        notes: String
    ): TaskEntity {
        return TaskEntity(
            taskId = "${date}_${startTime.replace(":", "")}",
            date = date,
            title = title,
            subject = subject,
            type = type,
            priority = priority,
            startTime = startTime,
            endTime = endTime,
            durationMinutes = getMinutesGap(startTime, endTime),
            status = "Pending",
            fixed = fixed,
            notes = notes
        )
    }

    private fun isTimeSlotFree(tasks: List<TaskEntity>, start: String, end: String): Boolean {
        tasks.forEach { task ->
            if (isOverlapping(start, end, task.startTime, task.endTime)) {
                return false
            }
        }
        return true
    }

    private fun isOverlapping(s1: String, e1: String, s2: String, e2: String): Boolean {
        return (s1 < e2 && s2 < e1)
    }

    private fun getMinutesGap(start: String, end: String): Int {
        val sParts = start.split(":")
        val eParts = end.split(":")
        if (sParts.size != 2 || eParts.size != 2) return 0
        val sMin = sParts[0].toInt() * 60 + sParts[1].toInt()
        val eMin = eParts[0].toInt() * 60 + eParts[1].toInt()
        return (eMin - sMin).coerceAtLeast(0)
    }

    private fun addMinutes(time: String, minutes: Int): String {
        val parts = time.split(":")
        var h = parts[0].toInt()
        var m = parts[1].toInt()
        m += minutes
        h += m / 60
        m %= 60
        h %= 24
        return String.format("%02d:%02d", h, m)
    }

    private fun subtractMinutes(time: String, minutes: Int): String {
        val parts = time.split(":")
        var h = parts[0].toInt()
        var m = parts[1].toInt()
        m -= minutes
        while (m < 0) {
            m += 60
            h -= 1
        }
        if (h < 0) h += 24
        return String.format("%02d:%02d", h, m)
    }

    private fun getDayOfWeekName(dateStr: String): String {
        return try {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr) ?: Date()
            SimpleDateFormat("EEEE", Locale.US).format(date)
        } catch (e: Exception) {
            "Monday"
        }
    }
}
