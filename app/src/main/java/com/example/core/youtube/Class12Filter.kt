package com.example.core.youtube

import java.util.Locale

object Class12Filter {

    private val class12Keywords = listOf(
        "class 12", "class xii", "12th", "12वीं", "12 वीं", "intermediate", "inter",
        "bihar board 12", "bseb 12", "cbse 12", "board 12", "kaksha 12", "कक्षा 12", "कक्षा 12वीं"
    )

    private val conflictingClasses = listOf(
        "class 10", "class 11", "10th", "11th", "कक्षा 10", "कक्षा 11", "matric", "मैट्रिक", "class 9", "9th"
    )

    private val subjectKeywords = listOf(
        "physics", "chemistry", "mathematics", "maths", "math", "hindi", "english",
        "science", "pcm", "भौतिक", "रसायन", "गणित", "हिंदी", "अंग्रेजी", "biology", "जीव"
    )

    fun isClass12Content(title: String, description: String = ""): Boolean {
        val combined = "$title $description".lowercase(Locale.ROOT)
        // Mandatory: Must contain explicit Class 12 or Intermediate indicator
        val hasClass12Keyword = class12Keywords.any { combined.contains(it) }
        if (!hasClass12Keyword) {
            return false
        }
        // Reject if it is solely about another class unless 12 is also explicitly present
        val hasConflict = conflictingClasses.any { combined.contains(it) }
        if (hasConflict && !class12Keywords.any { combined.contains(it) }) {
            return false
        }
        return true
    }

    fun detectSubject(title: String): String {
        val t = title.lowercase(Locale.ROOT)
        return when {
            t.contains("physics") || t.contains("भौतिक") -> "Physics"
            t.contains("chemistry") || t.contains("रसायन") -> "Chemistry"
            t.contains("math") || t.contains("गणित") -> "Mathematics"
            t.contains("hindi") || t.contains("हिंदी") -> "Hindi"
            t.contains("english") || t.contains("अंग्रेजी") -> "English"
            t.contains("biology") || t.contains("जीव") -> "Biology"
            else -> "Science"
        }
    }
}
