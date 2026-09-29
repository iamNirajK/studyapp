package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.features.StudyViewModel
import com.example.features.calendar.CalendarScreen
import com.example.features.dashboard.DashboardScreen
import com.example.features.planner.PlannerScreen
import com.example.features.setup.UserSetupScreen
import com.example.features.ai.NirajAiScreen
import com.example.features.settings.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: StudyViewModel = viewModel()
            val isDarkPref by viewModel.isDarkMode.collectAsState()
            val isSessionSetupDone by viewModel.isSessionSetupDone.collectAsState()

            // Resolve theme selection: preference manual override, or system default
            val darkTheme = isDarkPref ?: isSystemInDarkTheme()

            MyApplicationTheme(darkTheme = darkTheme) {
                if (!isSessionSetupDone) {
                    UserSetupScreen(viewModel = viewModel)
                } else {
                    MainAppScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun MainAppScreen(viewModel: StudyViewModel) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf("home") }

    // Request notification permission on startup (Android 13+)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (activeTab != "ai") {
                NavigationBar(
                    modifier = Modifier
                        .testTag("bottom_nav_bar")
                        .windowInsetsPadding(WindowInsets.navigationBars),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        selected = activeTab == "home",
                        onClick = { activeTab = "home" },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        modifier = Modifier.testTag("nav_home_tab")
                    )
                    NavigationBarItem(
                        selected = activeTab == "ai",
                        onClick = { activeTab = "ai" },
                        icon = { Icon(Icons.Default.Chat, contentDescription = "AI Chat") },
                        label = { Text("AI Chat") },
                        modifier = Modifier.testTag("nav_ai_tab")
                    )
                    NavigationBarItem(
                        selected = activeTab == "calendar",
                        onClick = { activeTab = "calendar" },
                        icon = { Icon(Icons.Default.CalendarToday, contentDescription = "Calendar") },
                        label = { Text("Calendar") },
                        modifier = Modifier.testTag("nav_calendar_tab")
                    )
                    NavigationBarItem(
                        selected = activeTab == "planner",
                        onClick = { activeTab = "planner" },
                        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "AI Planner") },
                        label = { Text("AI Planner") },
                        modifier = Modifier.testTag("nav_planner_tab")
                    )
                    NavigationBarItem(
                        selected = activeTab == "settings",
                        onClick = { activeTab = "settings" },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        modifier = Modifier.testTag("nav_settings_tab")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (activeTab == "ai") 0.dp else innerPadding.calculateBottomPadding())
        ) {
            when (activeTab) {
                "home" -> DashboardScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize(),
                    onNavigateToAi = { activeTab = "ai" },
                    onNavigateToSettings = { activeTab = "settings" },
                    onNavigateToPlanner = { activeTab = "planner" }
                )

                "ai" -> NirajAiScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                ) {
                    activeTab = "home"
                }

                "calendar" -> CalendarScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )

                "planner" -> PlannerScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                ) {
                    activeTab = "home"
                }

                "settings" -> SettingsScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize(),
                    onNavigateBack = { activeTab = "home" }
                )
            }
        }
    }
}
