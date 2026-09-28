package com.example.features.calendar

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.db.DailyScheduleEntity
import com.example.core.db.TaskEntity
import com.example.features.StudyViewModel
import com.example.features.dashboard.TimelineItemCard
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CalendarScreen(
    viewModel: StudyViewModel,
    modifier: Modifier = Modifier
) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val allSchedules by viewModel.allSchedules.collectAsState()

    var currentYearMonth by remember { mutableStateOf(Calendar.getInstance()) }
    val daysInMonth = getDaysInMonthList(currentYearMonth)

    val currentSelectedSchedule = allSchedules.find { it.date == selectedDate }
    val currentSelectedTasks = remember { mutableStateOf<List<TaskEntity>>(emptyList()) }

    // Query tasks for selected date
    LaunchedEffect(selectedDate) {
        viewModel.repository.getTasks(selectedDate).collect {
            currentSelectedTasks.value = it
        }
    }

    val backgroundBrush = remember {
        androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(
                Color(0xFF14171E), // Sophisticated Dark top
                Color(0xFF0B0D10)  // Deepest slate bottom
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            // Header
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "History & Calendar",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )
            Text(
                text = "Track your historical study streaks, completion rates, and archived timetables.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Monthly Navigation Control
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    val cal = currentYearMonth.clone() as Calendar
                    cal.add(Calendar.MONTH, -1)
                    currentYearMonth = cal
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Prev Month")
                }
                Text(
                    text = getYearMonthString(currentYearMonth),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = {
                    val cal = currentYearMonth.clone() as Calendar
                    cal.add(Calendar.MONTH, 1)
                    currentYearMonth = cal
                }) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "Next Month")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Calendar Grid View Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Weekday Labels
                    Row(modifier = Modifier.fillMaxWidth()) {
                        val days = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
                        days.forEach { d ->
                            Text(
                                text = d,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Days Grid
                    val chunkedDays = daysInMonth.chunked(7)
                    chunkedDays.forEach { week ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            week.forEach { dateObj ->
                                if (dateObj == null) {
                                    Box(modifier = Modifier.weight(1f))
                                } else {
                                    val isSelected = dateObj.dateString == selectedDate
                                    val daySchedule = allSchedules.find { it.date == dateObj.dateString }
                                    val completionColor = when {
                                        daySchedule == null -> Color.Transparent
                                        daySchedule.completion >= 90 -> Color(0xFF10B981) // High Green
                                        daySchedule.completion >= 50 -> Color(0xFFF59E0B) // Medium Orange
                                        else -> Color(0xFFEF4444) // Low Red
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                                else Color.Transparent
                                            )
                                            .clickable { viewModel.selectDate(dateObj.dateString) }
                                            .testTag("calendar_day_${dateObj.day}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = dateObj.day.toString(),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (daySchedule != null) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(completionColor)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Details panel below grid
            Text(
                text = "Details for ${selectedDate.split("-").reversed().joinToString("/")}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (currentSelectedSchedule == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No study plans generated for this date.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "🎯 Daily Highlights",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = "Primary Focus: ${currentSelectedSchedule.focusSubject}", fontWeight = FontWeight.Medium)
                                Text(text = "Completion rate reached: ${currentSelectedSchedule.completion}%", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "Stats: Study: ${String.format("%.1f", currentSelectedSchedule.studyHours)}h | Revisions: ${String.format("%.1f", currentSelectedSchedule.revisionHours)}h",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    items(currentSelectedTasks.value) { task ->
                        TimelineItemCard(task = task) {}
                    }
                }
            }
            Spacer(modifier = Modifier.height(100.dp)) // navigation spacer
        }
    }
}

data class CalendarDay(val day: Int, val dateString: String)

private fun getDaysInMonthList(calendar: Calendar): List<CalendarDay?> {
    val list = mutableListOf<CalendarDay?>()
    val cal = calendar.clone() as Calendar
    cal.set(Calendar.DAY_OF_MONTH, 1)

    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0-indexed
    for (i in 0 until firstDayOfWeek) {
        list.add(null)
    }

    val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    for (day in 1..maxDay) {
        cal.set(Calendar.DAY_OF_MONTH, day)
        list.add(CalendarDay(day, sdf.format(cal.time)))
    }

    return list
}

private fun getYearMonthString(calendar: Calendar): String {
    return SimpleDateFormat("MMMM yyyy", Locale.US).format(calendar.time)
}
