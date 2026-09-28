package com.example.core.utils

import java.text.SimpleDateFormat
import java.util.Locale

object TimeUtils {
    /**
     * Converts "HH:mm" (24-hour) to "hh:mm AM/PM" (12-hour) format.
     * E.g., "07:00" -> "07:00 AM", "13:30" -> "01:30 PM", "23:00" -> "11:00 PM"
     */
    fun formatTo12Hour(time24: String?): String {
        if (time24.isNullOrBlank()) return ""
        return try {
            val sdf24 = SimpleDateFormat("HH:mm", Locale.US)
            val sdf12 = SimpleDateFormat("hh:mm a", Locale.US)
            val date = sdf24.parse(time24.trim())
            if (date != null) {
                sdf12.format(date).uppercase(Locale.US)
            } else {
                time24
            }
        } catch (e: Exception) {
            time24
        }
    }
}
