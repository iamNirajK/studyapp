package com.example.features.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.core.db.ChatMessageEntity
import com.example.core.db.ChatSessionEntity
import com.example.core.utils.DocParser
import com.example.core.utils.VoiceManager
import com.example.features.StudyViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NirajAiScreen(
    viewModel: StudyViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // ViewModel State Observables
    val sessions by viewModel.allChatSessions.collectAsState()
    val messages by viewModel.activeMessages.collectAsState()
    val activeSessionId by viewModel.activeSessionId.collectAsState()
    val isAiResponding by viewModel.isAiResponding.collectAsState()
    val chatSearchQuery by viewModel.chatSearchQuery.collectAsState()

    // Screen State variables
    var messageText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedDocUri by remember { mutableStateOf<Uri?>(null) }
    var selectedDocText by remember { mutableStateOf<String?>(null) }
    var selectedDocName by remember { mutableStateOf<String?>(null) }

    // Session dialog states
    var showRenameDialog by remember { mutableStateOf<ChatSessionEntity?>(null) }
    var renameText by remember { mutableStateOf("") }

    // Message long press action sheet / dialog
    var selectedMessageForActions by remember { mutableStateOf<ChatMessageEntity?>(null) }
    var showEditMessageDialog by remember { mutableStateOf<ChatMessageEntity?>(null) }
    var editTextVal by remember { mutableStateOf("") }

    // Voice Conversation Mode Overlay State
    var showVoiceOverlay by remember { mutableStateOf(false) }
    var voiceState by remember { mutableStateOf(VoiceManager.VoiceState.IDLE) }
    var voiceManager by remember { mutableStateOf<VoiceManager?>(null) }

    // Init Voice Manager safely
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showVoiceOverlay = true
        } else {
            Toast.makeText(context, "Microphone permission required for Voice Assistant", Toast.LENGTH_SHORT).show()
        }
    }

    // Initialize Voice Manager on overlay visible
    DisposableEffect(showVoiceOverlay) {
        if (showVoiceOverlay) {
            val vm = VoiceManager(
                context = context,
                settings = viewModel.settingsManager,
                onSpeechRecognized = { text ->
                    if (text.isNotBlank()) {
                        viewModel.sendMessage(text)
                    }
                },
                onSpeechStateChanged = { state ->
                    voiceState = state
                }
            )
            voiceManager = vm
            vm.startListening()
        } else {
            voiceManager?.destroy()
            voiceManager = null
        }
        onDispose {
            voiceManager?.destroy()
            voiceManager = null
        }
    }

    // Sync TTS Speech playback when a new model response is generated
    LaunchedEffect(Unit) {
        viewModel.lastAiResponseEvent.collectLatest { response ->
            if (showVoiceOverlay && viewModel.settingsManager.isAutoSpeakEnabled) {
                voiceManager?.speak(response)
            }
        }
    }

    // Attachment Launchers
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    selectedImageBitmap = BitmapFactory.decodeStream(stream)
                }
            } catch (e: Exception) {
                Log.e("NirajAiScreen", "Failed to load image: ${e.message}")
            }
        }
    }

    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedDocUri = uri
            selectedDocName = uri.lastPathSegment ?: "Document"
            coroutineScope.launch {
                val text = DocParser.extractTextFromUri(context, uri)
                selectedDocText = text
                if (text.startsWith("Error")) {
                    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Document attached successfully!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val lazyListState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            lazyListState.animateScrollToItem(messages.size - 1)
        }
    }

    // Modal Drawer wrapper
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .width(310.dp)
                    .fillMaxHeight(),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Conversation History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )

                // Search Box
                OutlinedTextField(
                    value = chatSearchQuery,
                    onValueChange = { viewModel.chatSearchQuery.value = it },
                    placeholder = { Text("Search chats...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (chatSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.chatSearchQuery.value = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("chat_search_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // New Chat Button
                Button(
                    onClick = {
                        viewModel.startNewChatSession("Naya Chat Assistant")
                        coroutineScope.launch { drawerState.close() }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("btn_new_chat_session"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Naya Chat")
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // Sessions List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (sessions.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No chats found",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    } else {
                        items(sessions, key = { it.id }) { session ->
                            val isSelected = session.id == activeSessionId
                            NavigationDrawerItem(
                                label = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (session.isPinned) {
                                            Icon(
                                                Icons.Default.PushPin,
                                                contentDescription = "Pinned",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .padding(end = 4.dp)
                                            )
                                        }
                                        Text(
                                            text = session.title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.weight(1f)
                                        )

                                        // Pin Action
                                        IconButton(
                                            onClick = { viewModel.togglePinSession(session.id, session.isPinned) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (session.isPinned) Icons.Default.PushPin else Icons.Default.PushPin,
                                                contentDescription = "Pin/Unpin",
                                                tint = if (session.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        // Rename Action
                                        IconButton(
                                            onClick = {
                                                renameText = session.title
                                                showRenameDialog = session
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Rename",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        // Delete Action
                                        IconButton(
                                            onClick = { viewModel.deleteSession(session.id) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                selected = isSelected,
                                onClick = {
                                    viewModel.selectSession(session.id)
                                    coroutineScope.launch { drawerState.close() }
                                },
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 2.dp)
                                    .testTag("chat_session_item_${session.id}"),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }

                // Sidebar footer with clear options
                Divider()
                TextButton(
                    onClick = {
                        viewModel.clearMemory()
                        Toast.makeText(context, "All chats and contexts cleared! 🛡️", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .testTag("btn_clear_all_chats")
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear all")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear All Chats")
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { onBack() }) {
                            Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = {
                        val activeTitle = sessions.find { it.id == activeSessionId }?.title ?: "Niraj AI"
                        Column {
                            Text(
                                text = activeTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Niraj App Assistant",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    },
                    actions = {
                        // Open History Button
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.History, contentDescription = "Chat History")
                        }

                        // Voice Mode Toggle Mic
                        IconButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    showVoiceOverlay = true
                                } else {
                                    requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Voice Assistant Mode",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.shadow(1.dp)
                )
            }
        ) { padding ->
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Messages List or Welcome Screen
                if (messages.isEmpty() && !isAiResponding) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // High quality visual accent
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(
                                        Brush.sweepGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.secondary,
                                                MaterialTheme.colorScheme.tertiary,
                                                MaterialTheme.colorScheme.primary
                                            )
                                        ),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = "Niraj AI logo",
                                    tint = Color.White,
                                    modifier = Modifier.size(42.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Namaste, Main hun Niraj AI! 🌟",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Main aapka dynamic personalized study companion hun. Main aapke aaj ke schedule, weak subjects aur mocks ko samajhta hun.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))

                            // Action suggestions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Card(
                                    onClick = { messageText = "Aaj ka study schedule kya hai?" },
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Icon(Icons.Default.Today, null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Today's Schedule", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                        Text("Analyze today's plan", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Card(
                                    onClick = { messageText = "Flutter/Firebase basics samjhao." },
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Icon(Icons.Default.Code, null, tint = MaterialTheme.colorScheme.secondary)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Coding Doubt", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                        Text("Explain Chapters/code", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(messages, key = { it.messageId }) { msg ->
                            ChatBubble(
                                message = msg,
                                onClick = { selectedMessageForActions = msg }
                            )
                        }

                        if (isAiResponding) {
                            item {
                                ModelTypingIndicator()
                            }
                        }
                    }
                }

                // Attachment Previews Panel
                if (selectedImageUri != null || selectedDocUri != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedImageUri != null) {
                            Box(modifier = Modifier.size(60.dp)) {
                                AsyncImage(
                                    model = selectedImageUri,
                                    contentDescription = "Attached image",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                IconButton(
                                    onClick = {
                                        selectedImageUri = null
                                        selectedImageBitmap = null
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(20.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(12.dp))
                                }
                            }
                        }

                        if (selectedDocUri != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = selectedDocName ?: "PDF Document",
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.widthIn(max = 120.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = {
                                        selectedDocUri = null
                                        selectedDocText = null
                                        selectedDocName = null
                                    },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(Icons.Default.Close, null, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }

                // Chat text input bar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 2.dp,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Image attachment button
                        IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                            Icon(Icons.Default.Image, contentDescription = "Attach image")
                        }

                        // PDF/Document attachment button
                        IconButton(onClick = { docPickerLauncher.launch("application/pdf") }) {
                            Icon(Icons.Default.AttachFile, contentDescription = "Attach document")
                        }

                        // Message Text Input
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            placeholder = { Text("Ask Niraj AI...") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_message_input"),
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 4,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Send
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (messageText.isNotBlank() || selectedImageBitmap != null || selectedDocText != null) {
                                        viewModel.sendMessage(messageText, selectedImageBitmap, selectedDocText)
                                        messageText = ""
                                        selectedImageUri = null
                                        selectedImageBitmap = null
                                        selectedDocUri = null
                                        selectedDocText = null
                                        selectedDocName = null
                                    }
                                }
                            )
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        // Send Button
                        val canSend = messageText.isNotBlank() || selectedImageBitmap != null || selectedDocText != null
                        IconButton(
                            onClick = {
                                viewModel.sendMessage(messageText, selectedImageBitmap, selectedDocText)
                                messageText = ""
                                selectedImageUri = null
                                selectedImageBitmap = null
                                selectedDocUri = null
                                selectedDocText = null
                                selectedDocName = null
                            },
                            enabled = canSend,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (canSend) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Default.Send, contentDescription = "Send Message", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    // --- RENAME CONVERSATION DIALOG ---
    if (showRenameDialog != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Rename Chat") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rename_chat_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val session = showRenameDialog
                        if (session != null && renameText.isNotBlank()) {
                            viewModel.renameSession(session.id, renameText)
                        }
                        showRenameDialog = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- ACTIONS DIALOG FOR MESSAGE (Long press action) ---
    if (selectedMessageForActions != null) {
        val msg = selectedMessageForActions!!
        Dialog(onDismissRequest = { selectedMessageForActions = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Message Actions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Copy Message Text
                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(msg.text))
                            Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
                            selectedMessageForActions = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ContentCopy, null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Copy Message Text", textAlign = TextAlign.Start)
                        }
                    }

                    // Edit Message (Only for User)
                    if (msg.role == "user") {
                        TextButton(
                            onClick = {
                                editTextVal = msg.text
                                showEditMessageDialog = msg
                                selectedMessageForActions = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Edit, null)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Edit Message", textAlign = TextAlign.Start)
                            }
                        }
                    }

                    // Delete Message
                    TextButton(
                        onClick = {
                            viewModel.deleteMessage(msg.messageId)
                            selectedMessageForActions = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Delete, null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Delete Message", textAlign = TextAlign.Start)
                        }
                    }

                    // Regenerate Response (Available on model responses, or on user's last message)
                    TextButton(
                        onClick = {
                            viewModel.regenerateResponse()
                            selectedMessageForActions = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Regenerate Response", textAlign = TextAlign.Start)
                        }
                    }

                    // Continue generating (If the model text is cut off)
                    if (msg.role == "model") {
                        TextButton(
                            onClick = {
                                viewModel.continueGenerating()
                                selectedMessageForActions = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ArrowForward, null)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Continue Generating", textAlign = TextAlign.Start)
                            }
                        }
                    }

                    // Close Action Menu
                    TextButton(
                        onClick = { selectedMessageForActions = null },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }

    // --- EDIT MESSAGE DIALOG ---
    if (showEditMessageDialog != null) {
        val msg = showEditMessageDialog!!
        AlertDialog(
            onDismissRequest = { showEditMessageDialog = null },
            title = { Text("Edit Message") },
            text = {
                OutlinedTextField(
                    value = editTextVal,
                    onValueChange = { editTextVal = it },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editTextVal.isNotBlank() && editTextVal != msg.text) {
                            viewModel.editMessage(msg.messageId, editTextVal)
                        }
                        showEditMessageDialog = null
                    }
                ) {
                    Text("Submit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditMessageDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- VOICE OVERLAY SCREEN ---
    if (showVoiceOverlay) {
        Dialog(onDismissRequest = { showVoiceOverlay = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                tonalElevation = 8.dp
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Close Overlay Button
                    IconButton(
                        onClick = { showVoiceOverlay = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Voice Assistant")
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Niraj Voice Assistant",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "FEMALE VOICE MODE",
                            style = MaterialTheme.typography.bodySmall,
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        Spacer(modifier = Modifier.height(48.dp))

                        // Glowing / Pulsing interactive mic wave canvas
                        Box(
                            modifier = Modifier.size(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val pulseTransition = rememberInfiniteTransition()
                            val pulseScale by pulseTransition.animateFloat(
                                initialValue = 0.8f,
                                targetValue = 1.3f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1200, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                )
                            )

                            if (voiceState == VoiceManager.VoiceState.LISTENING || voiceState == VoiceManager.VoiceState.SPEAKING) {
                                Canvas(modifier = Modifier.fillMaxSize().scale(pulseScale)) {
                                    drawCircle(
                                        color = if (voiceState == VoiceManager.VoiceState.LISTENING) Color(0xFF4CAF50).copy(alpha = 0.2f) else Color(0xFF2196F3).copy(alpha = 0.2f),
                                        radius = size.minDimension / 2
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    // Interruption: Tap to stop TTS and speak instantly
                                    voiceManager?.stopSpeaking()
                                    voiceManager?.startListening()
                                },
                                shape = CircleShape,
                                modifier = Modifier.size(96.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = when (voiceState) {
                                        VoiceManager.VoiceState.LISTENING -> Color(0xFF4CAF50)
                                        VoiceManager.VoiceState.SPEAKING -> Color(0xFF2196F3)
                                        VoiceManager.VoiceState.THINKING -> Color(0xFFFF9800)
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                )
                            ) {
                                Icon(
                                    imageVector = when (voiceState) {
                                        VoiceManager.VoiceState.LISTENING -> Icons.Default.Mic
                                        VoiceManager.VoiceState.SPEAKING -> Icons.Default.VolumeUp
                                        VoiceManager.VoiceState.THINKING -> Icons.Default.AutoAwesome
                                        else -> Icons.Default.Mic
                                    },
                                    contentDescription = "Tap to interrupt",
                                    modifier = Modifier.size(42.dp),
                                    tint = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(40.dp))

                        // Dynamic text indicators
                        Text(
                            text = when (voiceState) {
                                VoiceManager.VoiceState.LISTENING -> "Listening continuously... (Boliye, main sun raha hun)"
                                VoiceManager.VoiceState.SPEAKING -> "Speaking... (Niraj AI bol rahi hai)"
                                VoiceManager.VoiceState.THINKING -> "Thinking... (Main soch rahi hun)"
                                else -> "Tap the mic button to speak!"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Supports continuous Hinglish speech and live interruptions. No button press required.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    message: ChatMessageEntity,
    onClick: () -> Unit
) {
    val isModel = message.role == "model"
    val alignment = if (isModel) Alignment.Start else Alignment.End
    val containerColor = if (isModel) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer
    val contentColor = if (isModel) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
    
    val timestampStr = remember(message.timestamp) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onClick
            ),
        horizontalAlignment = alignment
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalArrangement = if (isModel) Arrangement.Start else Arrangement.End
        ) {
            if (isModel) {
                // Avatar icon for Niraj AI
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .align(Alignment.Top),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isModel) 4.dp else 16.dp,
                        bottomEnd = if (isModel) 16.dp else 4.dp
                    ),
                    color = containerColor,
                    contentColor = contentColor,
                    tonalElevation = 1.dp
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Image attachment description or metadata if present in user message
                        if (message.localImagePath != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                                Icon(Icons.Default.Image, null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Analyzed image", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (message.docText != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                                Icon(Icons.Default.Description, null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Parsed PDF document text", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Parse and render basic markdown text & inline code blocks
                        val formattedText = parseBasicMarkdown(message.text)
                        
                        // Check if text is a code block
                        if (message.text.startsWith("```")) {
                            CodeBlockSection(code = message.text)
                        } else {
                            Text(
                                text = formattedText,
                                style = MaterialTheme.typography.bodyMedium,
                                letterSpacing = 0.25.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                Text(
                    text = timestampStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .padding(top = 4.dp, start = if (isModel) 2.dp else 0.dp, end = if (isModel) 0.dp else 2.dp)
                        .align(if (isModel) Alignment.Start else Alignment.End)
                )
            }
        }
    }
}

@Composable
fun CodeBlockSection(code: String) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val cleanCode = code.replace("```", "").trim()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2D2D2D))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Code Block",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFFD4D4D4),
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(cleanCode))
                        Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, "Copy code", tint = Color(0xFFD4D4D4), modifier = Modifier.size(14.dp))
                }
            }
            Text(
                text = cleanCode,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = Color(0xFF9CDCFE),
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun ModelTypingIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val transition = rememberInfiniteTransition()
                val dot1Y by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = -6f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(400, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
                val dot2Y by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = -6f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(400, delayMillis = 150, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
                val dot3Y by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = -6f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(400, delayMillis = 300, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )

                Box(modifier = Modifier.size(6.dp).offset(y = dot1Y.dp).background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
                Box(modifier = Modifier.size(6.dp).offset(y = dot2Y.dp).background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
                Box(modifier = Modifier.size(6.dp).offset(y = dot3Y.dp).background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
            }
        }
    }
}

// Basic markdown helper for rendering bold asterisks and inline code blocks
fun parseBasicMarkdown(text: String): AnnotatedString {
    val cleanText = text.replace("```", "")
    return buildAnnotatedString {
        var cursor = 0
        val length = cleanText.length

        while (cursor < length) {
            val boldStart = cleanText.indexOf("**", cursor)
            val codeStart = cleanText.indexOf("`", cursor)

            if (boldStart == -1 && codeStart == -1) {
                append(cleanText.substring(cursor))
                break
            }

            if (boldStart != -1 && (codeStart == -1 || boldStart < codeStart)) {
                // Render text up to bold marker
                append(cleanText.substring(cursor, boldStart))
                val boldEnd = cleanText.indexOf("**", boldStart + 2)
                if (boldEnd != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(cleanText.substring(boldStart + 2, boldEnd))
                    }
                    cursor = boldEnd + 2
                } else {
                    append("**")
                    cursor = boldStart + 2
                }
            } else {
                // Render text up to inline code marker
                append(cleanText.substring(cursor, codeStart))
                val codeEnd = cleanText.indexOf("`", codeStart + 1)
                if (codeEnd != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            background = Color.Gray.copy(alpha = 0.2f),
                            color = Color(0xFFE06C75)
                        )
                    ) {
                        append(cleanText.substring(codeStart + 1, codeEnd))
                    }
                    cursor = codeEnd + 1
                } else {
                    append("`")
                    cursor = codeStart + 1
                }
            }
        }
    }
}
