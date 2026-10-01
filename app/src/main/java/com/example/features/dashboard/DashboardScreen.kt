package com.example.features.dashboard

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.db.DailyScheduleEntity
import com.example.core.db.TaskEntity
import com.example.features.StudyViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import android.content.Intent
import android.net.Uri
import com.example.features.youtube.UpcomingClassesSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: StudyViewModel,
    modifier: Modifier = Modifier,
    onNavigateToAi: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToPlanner: () -> Unit
) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val schedule by viewModel.currentSchedule.collectAsState()
    val tasks by viewModel.currentTasks.collectAsState()
    val notifications by viewModel.scheduledNotifications.collectAsState()

    var activeDetailTask by remember { mutableStateOf<TaskEntity?>(null) }
    var liveTimeText by remember { mutableStateOf("") }

    val context = androidx.compose.ui.platform.LocalContext.current

    var isPermissionGranted by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        isPermissionGranted = granted
    }

    // Dynamic countdown timer calculation
    var nextNotificationCountdown by remember { mutableStateOf("No upcoming reminders") }
    var nextNotificationTitle by remember { mutableStateOf("") }

    // Real-time dynamic clock update (12-hour format with AM/PM) and alarm countdown
    LaunchedEffect(notifications) {
        while (true) {
            val cal = Calendar.getInstance()
            val clockFormat = SimpleDateFormat("EEEE, dd MMMM yyyy • hh:mm:ss a", Locale.US)
            liveTimeText = clockFormat.format(cal.time).uppercase(Locale.US)

            val now = System.currentTimeMillis()
            val next = notifications.filter { it.notificationTimeMillis > now }
                .minByOrNull { it.notificationTimeMillis }

            if (next != null) {
                val diffMs = next.notificationTimeMillis - now
                val diffSec = diffMs / 1000
                val hours = diffSec / 3600
                val minutes = (diffSec % 3600) / 60
                val seconds = diffSec % 60
                nextNotificationTitle = next.title
                nextNotificationCountdown = if (hours > 0) {
                    String.format(Locale.US, "%02d:%02d:%02d remaining", hours, minutes, seconds)
                } else {
                    String.format(Locale.US, "%02d:%02d remaining", minutes, seconds)
                }
            } else {
                nextNotificationTitle = ""
                nextNotificationCountdown = "No upcoming reminders"
            }
            delay(1000)
        }
    }

    // Auto-request permission on first launch (for Android 13+)
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val isGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!isGranted) {
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Determine current notification status
    val settings = viewModel.settingsManager
    val isQuietHours = remember(notifications, liveTimeText) {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val timeMinutes = hour * 60 + minute

        val startParts = settings.quietHoursStart.split(":")
        val startHour = startParts.getOrNull(0)?.toIntOrNull() ?: 22
        val startMin = startParts.getOrNull(1)?.toIntOrNull() ?: 0
        val startMinutes = startHour * 60 + startMin

        val endParts = settings.quietHoursEnd.split(":")
        val endHour = endParts.getOrNull(0)?.toIntOrNull() ?: 7
        val endMin = endParts.getOrNull(1)?.toIntOrNull() ?: 0
        val endMinutes = endHour * 60 + endMin

        settings.isQuietHoursEnabled && if (startMinutes < endMinutes) {
            timeMinutes in startMinutes..endMinutes
        } else {
            timeMinutes >= startMinutes || timeMinutes <= endMinutes
        }
    }

    val notificationStatusText = when {
        !settings.isNotificationsEnabled -> "Disabled 🔕"
        !isPermissionGranted -> "Permission Denied ⚠️"
        isQuietHours -> "Quiet Hours Active 🌙"
        else -> "Active & Monitoring 🟢"
    }

    val backgroundBrush = remember {
        Brush.verticalGradient(
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Premium Profile Header Card
            item {
                ProfileHeaderCard(
                    viewModel = viewModel,
                    schedule = schedule,
                    tasks = tasks,
                    liveTimeText = liveTimeText,
                    onTelegramShare = { viewModel.sendScheduleToTelegram() },
                    onNavigateToSettings = onNavigateToSettings
                )
            }

            // 1b. Permission Warning Banner (if denied)
            if (!isPermissionGranted && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "🔔 Notification Access Required",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "To ensure you receive timely, critical alerts for study blocks, live classes, water reminders, and daily summaries, please grant notification access.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = {
                                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Retry Grant", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = android.net.Uri.fromParts("package", context.packageName, null)
                                        }
                                        context.startActivity(intent)
                                    },
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Open Settings", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            // 1c. Notification Engine Status Panel
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "System Notification Engine",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = notificationStatusText,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (notificationStatusText.startsWith("Active")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (nextNotificationTitle.isNotEmpty()) nextNotificationTitle else "Next Alert",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = nextNotificationCountdown,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { viewModel.rescheduleAlarms() },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Recalculate Notifications",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 1d. 📚 Upcoming Classes Section (YouTube Class Live Tracker)
            item {
                UpcomingClassesSection(
                    viewModel = viewModel,
                    onNavigateToSettings = onNavigateToSettings
                )
            }

            // 2. Schedule Existence Check
            if (schedule == null || tasks.isEmpty()) {
                item {
                    EmptyStateCard(onNavigateToPlanner)
                }
            } else {
                val currentSched = schedule!!

                // 4. Next Session Countdown Card
                val nextTask = getNextStudyTask(tasks)
                if (nextTask != null) {
                    item {
                        NextSessionCard(nextTask) {
                            viewModel.updateTaskStatus(nextTask, "Completed")
                        }
                    }
                }

                // 5. Quick Statistics
                item {
                    QuickStatisticsPanel(currentSched, tasks)
                }

                // 6. Motivation Card
                item {
                    MotivationCard(currentSched.motivation)
                }

                // 7. Timeline Label
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Daily Timeline",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${tasks.size} Items",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // 8. Timetable Item Cards
                items(tasks, key = { it.taskId }) { task ->
                    TimelineItemCard(task = task) {
                        activeDetailTask = task
                    }
                }
            }
        }

        // 9. Task Detail Bottom Dialog Sheet
        activeDetailTask?.let { task ->
            TaskDetailDialog(
                task = task,
                onDismiss = { activeDetailTask = null },
                onStatusChange = { newStatus ->
                    viewModel.updateTaskStatus(task, newStatus)
                    activeDetailTask = null
                },
                onSaveNotes = { updatedNotes ->
                    viewModel.updateTaskNotes(task.taskId, updatedNotes)
                }
            )
        }

        // 10. Glowing "Niraj AI" Floating Action Button
        FloatingActionButton(
            onClick = onNavigateToAi,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 24.dp, end = 24.dp)
                .testTag("floating_ai_assistant_fab"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Niraj AI Assistant"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Niraj AI",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun EmptyStateCard(onNavigateToPlanner: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Schedule Today",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Get started by generating your personalized, AI-optimized day timetable based on your active live lectures and revisions.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onNavigateToPlanner,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("onboarding_planner_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Let's Create Study Plan", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ProfileHeaderCard(
    viewModel: StudyViewModel,
    schedule: DailyScheduleEntity?,
    tasks: List<TaskEntity>,
    liveTimeText: String,
    onTelegramShare: () -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val savedName = userProfile?.name?.trim()?.takeIf { it.isNotEmpty() }
        ?: viewModel.settingsManager.userName.trim().takeIf { it.isNotEmpty() }
        ?: "Student"

    val welcomeGreeting = "Welcome, $savedName!"
    val initials = savedName.split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .map { it.first().uppercase() }
        .joinToString("")
        .ifEmpty { "S" }

    val completionPercent = schedule?.completion ?: 0
    val totalInteractive = tasks.count {
        it.type !in listOf("Break", "Meal", "Sleep", "Water Reminder")
    }
    val completedInteractive = tasks.count {
        it.type !in listOf("Break", "Meal", "Sleep", "Water Reminder") && it.status == "Completed"
    }

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        else -> "Good Evening"
    }

    val todayDateFormatted = remember {
        SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US).format(Date())
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Circular Avatar with user's initials
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // 2. Personalized Name & Welcome Greeting
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$greeting,",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = welcomeGreeting,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("dashboard_welcome_text")
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = todayDateFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Share Button
                    IconButton(
                        onClick = onTelegramShare,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .testTag("telegram_share_button")
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Send plan to Telegram",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Settings Button
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .testTag("home_settings_button")
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Divider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                thickness = 1.dp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Progress Indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Current Study Progress",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (schedule != null) "$completedInteractive of $totalInteractive major tasks finished" else "No active schedule for today",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(64.dp)
                ) {
                    CircularProgressIndicator(
                        progress = completionPercent / 100f,
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 6.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                    Text(
                        text = "$completionPercent%",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
fun NextSessionCard(task: TaskEntity, onMarkCompleted: () -> Unit) {
    val nextStart12 = com.example.core.utils.TimeUtils.formatTo12Hour(task.startTime)
    val nextEnd12 = com.example.core.utils.TimeUtils.formatTo12Hour(task.endTime)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "NEXT SESSION",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$nextStart12 – $nextEnd12 (${task.durationMinutes} Minutes)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Button(
                    onClick = onMarkCompleted,
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("start_next_session_button"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Done", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun QuickStatisticsPanel(schedule: DailyScheduleEntity, tasks: List<TaskEntity>) {
    val liveCount = tasks.count { it.type == "Live Class" }
    val mockCount = tasks.count { it.type == "Mock Test" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Quick Statistics",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("Study", "${String.format("%.1f", schedule.studyHours)}h", Modifier.weight(1f))
                StatItem("Revision", "${String.format("%.1f", schedule.revisionHours)}h", Modifier.weight(1f))
                StatItem("Live Class", "$liveCount", Modifier.weight(1f))
                StatItem("Mock Tests", "$mockCount", Modifier.weight(1f))
                StatItem("Productivity", "${schedule.productivityScore}%", Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun MotivationCard(quote: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = quote,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.2.sp
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun TimelineItemCard(task: TaskEntity, onClick: () -> Unit) {
    val indicatorColor = when (task.type) {
        "Live Class" -> MaterialTheme.colorScheme.primary
        "Revision" -> MaterialTheme.colorScheme.tertiary
        "Self Study" -> MaterialTheme.colorScheme.secondary
        "Mock Test" -> MaterialTheme.colorScheme.error
        "Break", "Meal" -> Color(0xFF94A3B8)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    }

    val subjectIcon = when (task.subject) {
        "Physics" -> "⚛️"
        "Chemistry" -> "🧪"
        "Maths" -> "📐"
        "English" -> "📖"
        "Hindi" -> "📚"
        else -> when (task.type) {
            "Meal" -> "🍽️"
            "Break" -> "☕"
            "Sleep" -> "😴"
            "Water Reminder" -> "💧"
            else -> "📝"
        }
    }

    val start12 = com.example.core.utils.TimeUtils.formatTo12Hour(task.startTime)
    val end12 = com.example.core.utils.TimeUtils.formatTo12Hour(task.endTime)

    val durationText = remember(task.durationMinutes) {
        val h = task.durationMinutes / 60
        val m = task.durationMinutes % 60
        if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("task_item_${task.startTime}")
            .graphicsLayer {
                alpha = if (task.status == "Completed") 0.65f else 1f
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Subject Icon inside circular badge
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(indicatorColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = subjectIcon, fontSize = 22.sp)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Text(
                    text = "$start12 – $end12",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Duration: $durationText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Status Chip & checkmark
            Column(horizontalAlignment = Alignment.End) {
                StatusChip(status = task.status)
                if (task.status == "Completed") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "✓ Done",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF10B981)
                    )
                }
            }
        }
    }
}

@Composable
fun StatusChip(status: String) {
    val (bgColor, textColor, labelText) = when (status) {
        "Completed" -> Triple(Color(0xFF10B981).copy(alpha = 0.15f), Color(0xFF10B981), "✓ Completed")
        "In Progress" -> Triple(Color(0xFFF59E0B).copy(alpha = 0.15f), Color(0xFFF59E0B), "⏳ In Progress")
        "Skipped" -> Triple(Color(0xFFEF4444).copy(alpha = 0.15f), Color(0xFFEF4444), "✗ Skipped")
        else -> Triple(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), "○ Pending")
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = labelText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
fun TaskDetailDialog(
    task: TaskEntity,
    onDismiss: () -> Unit,
    onStatusChange: (String) -> Unit,
    onSaveNotes: (String) -> Unit
) {
    var notesText by remember { mutableStateOf(task.notes) }
    val detailStart12 = com.example.core.utils.TimeUtils.formatTo12Hour(task.startTime)
    val detailEnd12 = com.example.core.utils.TimeUtils.formatTo12Hour(task.endTime)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "📅 Duration: $detailStart12 – $detailEnd12 (${task.durationMinutes} Minutes)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "🏷️ Subject: ${task.subject}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "⚠️ Priority: ${task.priority}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = notesText,
                    onValueChange = {
                        notesText = it
                        onSaveNotes(it)
                    },
                    label = { Text("Session Notes") },
                    placeholder = { Text("E.g., Formulas to learn, topics completed...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .testTag("notes_textfield"),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onStatusChange("Completed") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("Complete")
                }
                if (!task.fixed) {
                    Button(
                        onClick = { onStatusChange("Skipped") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Text("Skip")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Close", textAlign = TextAlign.Center)
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

private fun getNextStudyTask(tasks: List<TaskEntity>): TaskEntity? {
    val currentTimeStr = SimpleDateFormat("HH:mm", Locale.US).format(Date())
    // Return first upcoming pending task of a major study type
    return tasks.firstOrNull { task ->
        task.startTime > currentTimeStr &&
                task.status == "Pending" &&
                task.type in listOf("Live Class", "Revision", "Self Study", "Mock Test", "PYQ", "Practice", "Notes")
    }
}
