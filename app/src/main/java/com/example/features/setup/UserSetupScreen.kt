package com.example.features.setup

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.features.StudyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSetupScreen(
    viewModel: StudyViewModel,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var wantTelegram by remember { mutableStateOf(false) }
    var botToken by remember { mutableStateOf("") }
    var chatId by remember { mutableStateOf("") }
    var isTokenVisible by remember { mutableStateOf(false) }

    var testStatus by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val backgroundGradient = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0F172A), // Slate 900
                Color(0xFF1E293B)  // Slate 800
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundGradient)
            .windowInsetsPadding(WindowInsets.systemBars),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 540.dp)
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("user_setup_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E293B)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // App Logo / Header Icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = "Welcome Icon",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Welcome to Study Companion",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Quick setup for your active study session",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                )

                if (errorMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // --- Step 1: Name ---
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text("आपका नाम (Full Name)") },
                    placeholder = { Text("e.g. Niraj Kumar") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "Name",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_name_input"),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // --- Step 2: Date of Birth ---
                OutlinedTextField(
                    value = dob,
                    onValueChange = {
                        dob = it
                        errorMessage = null
                    },
                    label = { Text("आपकी जन्म तिथि (Date of Birth)") },
                    placeholder = { Text("DD/MM/YYYY") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = "DOB",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_dob_input"),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // --- Step 3: Telegram Question ---
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF0F172A)
                    ),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = "Telegram",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "क्या आप Telegram पर notifications चाहते हैं?",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Toggle Choice Buttons: [ Yes / No ]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // YES Option
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (wantTelegram) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        else Color.White.copy(alpha = 0.05f)
                                    )
                                    .border(
                                        width = if (wantTelegram) 2.dp else 1.dp,
                                        color = if (wantTelegram) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        wantTelegram = true
                                        errorMessage = null
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = wantTelegram,
                                        onClick = null,
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "हाँ (Yes)",
                                        fontWeight = FontWeight.Bold,
                                        color = if (wantTelegram) Color.White else Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            // NO Option
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (!wantTelegram) Color.White.copy(alpha = 0.15f)
                                        else Color.White.copy(alpha = 0.05f)
                                    )
                                    .border(
                                        width = if (!wantTelegram) 2.dp else 1.dp,
                                        color = if (!wantTelegram) Color.White.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        wantTelegram = false
                                        errorMessage = null
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = !wantTelegram,
                                        onClick = null,
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = Color.White
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "नहीं (No)",
                                        fontWeight = FontWeight.Bold,
                                        color = if (!wantTelegram) Color.White else Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }

                        // Conditional Telegram Fields
                        AnimatedVisibility(
                            visible = wantTelegram,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp)
                            ) {
                                OutlinedTextField(
                                    value = botToken,
                                    onValueChange = {
                                        botToken = it
                                        errorMessage = null
                                        testStatus = null
                                    },
                                    label = { Text("Telegram Bot Token") },
                                    placeholder = { Text("e.g. 123456789:ABCdef...") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.VpnKey,
                                            contentDescription = "Bot Token",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
                                            Icon(
                                                imageVector = if (isTokenVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = "Toggle token visibility",
                                                tint = Color.White.copy(alpha = 0.6f)
                                            )
                                        }
                                    },
                                    visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("setup_telegram_token_input"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                                        unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = chatId,
                                    onValueChange = {
                                        chatId = it
                                        errorMessage = null
                                        testStatus = null
                                    },
                                    label = { Text("Telegram Chat ID") },
                                    placeholder = { Text("e.g. 987654321") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Chat,
                                            contentDescription = "Chat ID",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("setup_telegram_chatid_input"),
                                    shape = RoundedCornerShape(12.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                                        unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            if (botToken.isBlank() || chatId.isBlank()) {
                                                testStatus = "❌ Bot Token और Chat ID भरें"
                                                return@OutlinedButton
                                            }
                                            isTesting = true
                                            testStatus = "Connecting..."
                                            viewModel.testTelegramConnection(botToken.trim(), chatId.trim()) { res ->
                                                isTesting = false
                                                if (res.isSuccess && res.getOrDefault(false)) {
                                                    testStatus = "✅ Connected Successfully!"
                                                } else {
                                                    testStatus = "❌ Connection Failed: ${res.exceptionOrNull()?.message ?: "Check credentials"}"
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        if (isTesting) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text("Test Telegram Connection", fontSize = 12.sp)
                                    }

                                    if (testStatus != null) {
                                        Text(
                                            text = testStatus ?: "",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (testStatus?.contains("✅") == true) Color(0xFF4ADE80) else Color(0xFFF87171),
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Privacy Commitment Badge
                Surface(
                    color = Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Privacy Lock",
                            tint = Color(0xFF4ADE80),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Privacy Mandate: Your personal info and Telegram credentials remain strictly in-memory during this session and are never saved to disk, server, or local storage.",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Primary Start Button
                Button(
                    onClick = {
                        if (name.isBlank()) {
                            errorMessage = "कृपया अपना नाम दर्ज करें (Please enter your name)"
                            return@Button
                        }
                        if (wantTelegram) {
                            if (botToken.isBlank() || chatId.isBlank()) {
                                errorMessage = "कृपया Telegram Bot Token और Chat ID दोनों भरें"
                                return@Button
                            }
                        }
                        viewModel.completeSessionSetup(
                            name = name,
                            dob = dob,
                            wantTelegram = wantTelegram,
                            botToken = botToken,
                            chatId = chatId
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("start_app_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "शुरू करें (Start Study Session)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = "Proceed",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
