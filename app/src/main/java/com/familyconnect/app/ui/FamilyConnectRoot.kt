package com.familyconnect.app.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.familyconnect.app.FamilyConnectApp
import com.familyconnect.app.data.model.ChatMessageData
import com.familyconnect.app.webrtc.CallType
import com.familyconnect.app.data.model.FamilyRole
import com.familyconnect.app.ui.theme.FamilyConnectTheme
import com.familyconnect.app.webrtc.CallStatus

private enum class HomeTab {
    CHAT,
    SETTINGS
}

@Composable
fun Root(app: Application, onViewModelReady: (FamilyViewModel) -> Unit = {}) {
    val context = LocalContext.current
    val familyApp = app as FamilyConnectApp
    
    val viewModel: FamilyViewModel = viewModel(
        factory = FamilyViewModelFactory(familyApp.repository, context)
    )
    
    // Call the callback when ViewModel is ready so Activity can store reference
    LaunchedEffect(viewModel) {
        onViewModelReady(viewModel)
    }
    
    FamilyConnectRoot(viewModel = viewModel)
}

@Composable
fun FamilyConnectRoot(viewModel: FamilyViewModel) {
    val darkMode by viewModel.darkMode.collectAsState(initial = false)
    val callState = viewModel.callState
    val context = LocalContext.current
    
    // Use key to force recomposition when call state changes
    val callKey = "${callState.activeCallId}_${callState.status}"
    
    // Check for pending call from notification on first composition
    LaunchedEffect(Unit) {
        val app = context.applicationContext as com.familyconnect.app.FamilyConnectApp
        val pendingCall = app.pendingCallIntent
        if (pendingCall != null) {
            Log.d("FamilyConnectRoot", "🔔 LaunchedEffect: PENDING CALL DETECTED: ${pendingCall.callId}")
            Log.d("FamilyConnectRoot", "   Clearing pending call and accepting...")
            app.pendingCallIntent = null
            viewModel.acceptCall(pendingCall.callId, pendingCall.threadId, pendingCall.callerName)
            Log.d("FamilyConnectRoot", "✅ LaunchedEffect: acceptCall invoked")
        }
    }
    
    FamilyConnectTheme(darkTheme = darkMode) {
        Surface(modifier = Modifier.fillMaxSize()) {
            // Explicitly check all possible call states
            val isIncomingCall = callState.status == CallStatus.REQUESTING ||
                    callState.status == CallStatus.CONNECTING ||
                    callState.status == CallStatus.ACTIVE
            
            val app = context.applicationContext as com.familyconnect.app.FamilyConnectApp
            val hasPendingCall = app.pendingCallIntent != null
            
            Log.d("FamilyConnectRoot", "📱 Rendering (key=$callKey): isIncoming=$isIncomingCall, pending=$hasPendingCall, status=${callState.status}")
            
            // PRIORITY 1: Always show call screen if ANY call state exists
            when {
                isIncomingCall -> {
                    Log.d("FamilyConnectRoot", "🎯 RENDERING CALL SCREEN ✅")
                    if (callState.callType == CallType.VIDEO) {
                        VideoCallScreen(
                            threadName = callState.activeCallPartyName ?: "Unknown",
                            callState = callState,
                            webRTCManager = viewModel.getWebRTCManager(),
                            onToggleMute = { enabled -> viewModel.toggleLocalAudio(enabled) },
                            onToggleVideo = { enabled -> viewModel.toggleLocalVideo(enabled) },
                            onSwitchCamera = { viewModel.switchCamera() },
                            onEndCall = { viewModel.endCall(callState.activeThreadId) }
                        )
                    } else {
                        AudioCallScreen(
                            threadName = callState.activeCallPartyName ?: "Unknown",
                            callState = callState,
                            onToggleMute = { enabled -> viewModel.toggleLocalAudio(enabled) },
                            onToggleSpeaker = { enabled -> viewModel.toggleRemoteAudio(enabled) },
                            onEndCall = { viewModel.endCall(callState.activeThreadId) }
                        )
                    }
                }
                
                hasPendingCall -> {
                    Log.d("FamilyConnectRoot", "⏳ Pending call detected, showing loading state")
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Incoming call...", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                
                viewModel.currentUser == null -> {
                    Log.d("FamilyConnectRoot", "📱 No user logged in → AuthScreen")
                    AuthScreen(viewModel = viewModel)
                }
                
                else -> {
                    Log.d("FamilyConnectRoot", "🏠 User logged in → HomeScreen")
                    HomeScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun AuthScreen(viewModel: FamilyViewModel) {
    val users by viewModel.users.collectAsState(initial = emptyList())
    var showAdminSetup by remember { mutableStateOf(false) }
    var adminMobile by remember { mutableStateOf("") }
    var adminPin by remember { mutableStateOf("") }
    val adminUnlocked = viewModel.isAdminSetupAuthorized(adminMobile, adminPin)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with gradient background
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "👨‍👩‍👧‍👦 Bandi Family",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                "Connect & Share",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
            )
        }

        // Login with modern design
        Text(
            "Welcome Back",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        OutlinedTextField(
            value = viewModel.loginMobile,
            onValueChange = { viewModel.loginMobile = it.filter(Char::isDigit) },
            label = { Text("Mobile Number") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Button(
            onClick = { viewModel.login() },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Login to Chat", style = MaterialTheme.typography.titleMedium)
        }

        viewModel.loginError?.let {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Info card with rounded corners
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📋 Login Policy", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text("Only registered family members can log in. Contact your admin to add new members.", style = MaterialTheme.typography.bodySmall)
            }
        }

        TextButton(
            onClick = { showAdminSetup = !showAdminSetup },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (showAdminSetup) "← Hide Admin Setup" else "Open Admin Setup →")
        }

        if (showAdminSetup) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("🔐 Admin Setup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Enter admin credentials to manage family members",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )

                    OutlinedTextField(
                        value = adminMobile,
                        onValueChange = { adminMobile = it.filter(Char::isDigit) },
                        label = { Text("Admin Mobile Number") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = adminPin,
                        onValueChange = { adminPin = it.filter(Char::isDigit) },
                        label = { Text("Admin Setup PIN") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (adminUnlocked) {
                        OutlinedTextField(
                            value = viewModel.registerName,
                            onValueChange = { viewModel.registerName = it },
                            label = { Text("Name") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = viewModel.registerMobile,
                            onValueChange = { viewModel.registerMobile = it.filter(Char::isDigit) },
                            label = { Text("Allowed Mobile Number") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FamilyRole.entries.forEach { role ->
                                FilterChip(
                                    selected = viewModel.registerRole == role,
                                    onClick = { viewModel.registerRole = role },
                                    label = { Text(role.name) }
                                )
                            }
                        }

                        Button(onClick = { viewModel.register() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Store Allowed Number")
                        }

                        viewModel.registerMessage?.let {
                            Text(it)
                        }

                        Text("Stored Mobile Numbers", style = MaterialTheme.typography.titleMedium)
                        users.forEach { user ->
                            Text("${user.name} (${user.role.name}) - ${user.mobile}")
                        }
                    } else {
                        Text(
                            "Use an existing admin mobile number such as 9999999999 and the current setup PIN to unlock setup.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HomeScreen(viewModel: FamilyViewModel) {
    val user = viewModel.currentUser ?: return
    val callState = viewModel.callState
    val context = LocalContext.current
    val pendingMicAction = remember { mutableStateOf<(() -> Unit)?>(null) }
    val pendingCallAction = remember { mutableStateOf<(() -> Unit)?>(null) }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingMicAction.value?.invoke()
        }
        pendingMicAction.value = null
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingCallAction.value?.invoke()
        }
        pendingCallAction.value = null
    }

    // Location permission for attaching city name to messages
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.refreshLocation()
        }
    }

    // Request location on first composition
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            viewModel.refreshLocation()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    fun runWithMicPermission(action: () -> Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            action()
        } else {
            pendingMicAction.value = action
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun runWithCallPermissions(needCamera: Boolean, action: () -> Unit) {
        val micGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val camGranted = !needCamera || ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (micGranted && camGranted) {
            action()
        } else if (!micGranted) {
            // Ask mic first, then camera
            pendingMicAction.value = {
                if (needCamera && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    pendingCallAction.value = action
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                } else {
                    action()
                }
            }
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            // Mic granted but camera not
            pendingCallAction.value = action
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var selectedTab by remember { mutableStateOf(HomeTab.CHAT) }

    // Determine if a full-screen call overlay should be shown
    val isInCall = callState.status == CallStatus.REQUESTING ||
            callState.status == CallStatus.CONNECTING ||
            callState.status == CallStatus.ACTIVE

    // When a call is active, render the call screen full-screen OUTSIDE the Scaffold
    if (isInCall) {
        if (callState.callType == CallType.VIDEO) {
            VideoCallScreen(
                threadName = callState.activeCallPartyName ?: "Unknown",
                callState = callState,
                webRTCManager = viewModel.getWebRTCManager(),
                onToggleMute = { enabled -> viewModel.toggleLocalAudio(enabled) },
                onToggleVideo = { enabled -> viewModel.toggleLocalVideo(enabled) },
                onSwitchCamera = { viewModel.switchCamera() },
                onEndCall = { viewModel.endCall(callState.activeThreadId) }
            )
        } else {
            AudioCallScreen(
                threadName = callState.activeCallPartyName ?: "Unknown",
                callState = callState,
                onToggleMute = { enabled -> viewModel.toggleLocalAudio(enabled) },
                onToggleSpeaker = { enabled -> viewModel.toggleRemoteAudio(enabled) },
                onEndCall = { viewModel.endCall(callState.activeThreadId) }
            )
        }
    } else {
        Scaffold(
            topBar = {
                // Hide top bar entirely when a chat thread is open - like WhatsApp
                if (!(selectedTab == HomeTab.CHAT && viewModel.selectedChatThread != null)) {
                    TopAppBar(
                        title = {
                            Column {
                                Text("👨‍👩‍👧‍👦 Family Chat", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text("${user.name} • ${user.role.name}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                            }
                        },
                        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            },
            bottomBar = {
                // Hide bottom nav when a chat thread is open so keyboard can push layout up properly
                if (!(selectedTab == HomeTab.CHAT && viewModel.selectedChatThread != null)) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.height(70.dp)
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == HomeTab.CHAT,
                            onClick = { selectedTab = HomeTab.CHAT },
                            icon = { Icon(Icons.Default.Chat, contentDescription = "Chat", modifier = Modifier.size(24.dp)) },
                            label = { Text("💬 Chat", style = MaterialTheme.typography.labelSmall) }
                        )
                        NavigationBarItem(
                            selected = selectedTab == HomeTab.SETTINGS,
                            onClick = { selectedTab = HomeTab.SETTINGS },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(24.dp)) },
                            label = { Text("⚙️ Settings", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.padding(innerPadding)) {
                    when (selectedTab) {
                        HomeTab.CHAT -> ChatScreen(
                            viewModel = viewModel,
                            onStartAudioCall = {
                                runWithMicPermission {
                                    viewModel.initiateCallForSelectedThread()
                                }
                            },
                            onStartVideoCall = {
                                runWithCallPermissions(needCamera = true) {
                                    viewModel.initiateVideoCallForSelectedThread()
                                }
                            }
                        )
                        HomeTab.SETTINGS -> SettingsScreen(viewModel, user.role)
                    }
                }

                if (callState.status == CallStatus.RINGING && callState.incomingCallRequest != null) {
                    IncomingCallOverlay(
                        callerName = callState.incomingCallRequest.fromUserName.ifBlank { "Unknown" },
                        isVideoCall = callState.callType == CallType.VIDEO,
                        onAccept = {
                            runWithCallPermissions(needCamera = callState.callType == CallType.VIDEO) {
                                viewModel.acceptCall(
                                    callState.incomingCallRequest.callId,
                                    callState.incomingCallRequest.threadId,
                                    callState.incomingCallRequest.fromUserName
                                )
                            }
                        },
                        onReject = {
                            viewModel.rejectCall(
                                callState.incomingCallRequest.callId,
                                callState.incomingCallRequest.threadId
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardScreen(
    viewModel: FamilyViewModel,
    onOpenCalendar: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenMedia: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val events by viewModel.events.collectAsState(initial = emptyList())
    val messages by viewModel.messages.collectAsState(initial = emptyList())
    val tasks by viewModel.tasks.collectAsState(initial = emptyList())
    val notes by viewModel.notes.collectAsState(initial = emptyList())
    val unreadCount = viewModel.unreadMessageCount()
    val latestMessage = viewModel.latestMessage()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Family Dashboard", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Choose an icon to open chats, events, photos, or settings.")

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardTile("Chats", Icons.Default.Chat, unreadCount.takeIf { it > 0 }?.toString(), onOpenChat, Modifier.weight(1f))
            DashboardTile("Events", Icons.Default.DateRange, events.size.toString(), onOpenCalendar, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardTile("Photos", Icons.Default.PermMedia, null, onOpenMedia, Modifier.weight(1f))
            DashboardTile("Tasks", Icons.Default.CheckCircle, tasks.count { !it.completed }.toString(), onOpenTasks, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardTile("Notes", Icons.Default.Note, notes.size.toString(), onOpenNotes, Modifier.weight(1f))
            DashboardTile("Settings", Icons.Default.Settings, null, onOpenSettings, Modifier.weight(1f))
        }

        if (messages.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Mail, contentDescription = null)
                        Text("Messages after login", fontWeight = FontWeight.Bold)
                    }
                    Text("Unread messages: $unreadCount")
                    latestMessage?.let {
                        Text("Latest: ${it.sender} - ${it.body}")
                    }
                }
            }
        }

        if (events.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.DateRange, contentDescription = null)
                        Text("Active events", fontWeight = FontWeight.Bold)
                    }
                    events.take(3).forEach { event ->
                        Text("${event.title} | ${event.dateTime} | expires in ${viewModel.eventDaysLeft(event)} day(s)")
                    }
                }
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = null)
                        Text("No active events", fontWeight = FontWeight.Bold)
                    }
                    Text("Created events remain visible for 3 days and then expire automatically.")
                }
            }
        }
    }
}

@Composable
private fun TasksScreen(viewModel: FamilyViewModel, canEdit: Boolean) {
    val tasks by viewModel.tasks.collectAsState(initial = emptyList())
    var title by remember { mutableStateOf("") }
    var assignedTo by remember { mutableStateOf("CHILD") }
    var dueDate by remember { mutableStateOf("") }
    var reward by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("Task & Chore Management", style = MaterialTheme.typography.titleLarge)
        }

        if (canEdit) {
            item {
                OutlinedTextField(title, { title = it }, label = { Text("Task title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(assignedTo, { assignedTo = it }, label = { Text("Assign to role/user") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(dueDate, { dueDate = it }, label = { Text("Due date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(reward, { reward = it.filter(Char::isDigit) }, label = { Text("Reward points (optional)") }, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = {
                        if (title.isNotBlank() && dueDate.isNotBlank()) {
                            viewModel.addTask(title, assignedTo, dueDate, reward.toIntOrNull())
                            title = ""
                            dueDate = ""
                            reward = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add Task") }
            }
        }

        items(tasks) { task ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(task.title, fontWeight = FontWeight.Bold)
                    Text("Assigned to: ${task.assignedTo}")
                    Text("Due: ${task.dueDate}")
                    Text("Status: ${if (task.completed) "Completed" else "Pending"}")
                    task.rewardPoints?.let { Text("Reward: $it points") }
                    TextButton(onClick = { viewModel.toggleTask(task.id) }) {
                        Text(if (task.completed) "Mark Pending" else "Mark Complete")
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardTile(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badge: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .aspectRatio(1f),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = title, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            if (badge != null) {
                Text(badge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun CalendarScreen(viewModel: FamilyViewModel, canEdit: Boolean) {
    val events by viewModel.events.collectAsState(initial = emptyList())
    var title by remember { mutableStateOf("") }
    var dateTime by remember { mutableStateOf("") }
    var colorTag by remember { mutableStateOf("Blue") }
    var recurring by remember { mutableStateOf(false) }
    var reminder by remember { mutableStateOf("30") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("Shared Family Calendar", style = MaterialTheme.typography.titleLarge)
        }

        if (canEdit) {
            item {
                OutlinedTextField(title, { title = it }, label = { Text("Event title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(dateTime, { dateTime = it }, label = { Text("Date & Time (YYYY-MM-DD HH:mm)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(colorTag, { colorTag = it }, label = { Text("Color tag") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(reminder, { reminder = it.filter(Char::isDigit) }, label = { Text("Reminder minutes") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Recurring")
                    Switch(checked = recurring, onCheckedChange = { recurring = it })
                }
                Button(
                    onClick = {
                        if (title.isNotBlank() && dateTime.isNotBlank()) {
                            viewModel.addEvent(title, dateTime, colorTag, recurring, reminder.toIntOrNull() ?: 30)
                            title = ""
                            dateTime = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add Event") }
            }
        }

        items(events) { event ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(event.title, fontWeight = FontWeight.Bold)
                    Text("When: ${event.dateTime}")
                    Text("Color: ${event.colorTag}")
                    Text("Recurring: ${if (event.recurring) "Yes" else "No"}")
                    Text("Reminder: ${event.reminderMinutes} mins")
                    Text("Expires in: ${viewModel.eventDaysLeft(event)} day(s)")
                }
            }
        }
    }
}

@Composable
private fun ChatScreen(
    viewModel: FamilyViewModel,
    onStartAudioCall: () -> Unit,
    onStartVideoCall: () -> Unit = {}
) {
    val onlineUsers by viewModel.onlineUsers.collectAsState(initial = emptyList())
    val selectedThread = viewModel.selectedChatThread
    val threadMessages by viewModel.currentThreadMessages.collectAsState(initial = emptyList())
    var messageBody by remember { mutableStateOf("") }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showAttachActions by remember { mutableStateOf(false) }
    var replyingToMessage by remember(selectedThread?.threadId) { mutableStateOf<ChatMessageData?>(null) }
    val uploadProgress = viewModel.uploadProgress
    val uploadError = viewModel.uploadError
    val context = LocalContext.current
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, threadMessages.size - 1))
    var previousMessageCount by remember(selectedThread?.threadId) { mutableStateOf(0) }

    LaunchedEffect(viewModel.currentUser?.mobile) {
        viewModel.startObservingIncomingCalls()
    }

    // Always jump to latest message when switching/opening a thread.
    LaunchedEffect(selectedThread?.threadId) {
        previousMessageCount = threadMessages.size
        if (selectedThread != null && threadMessages.isNotEmpty()) {
            listState.scrollToItem(threadMessages.lastIndex)
        }
    }

    // Auto-scroll for new messages only when user is already near the bottom.
    LaunchedEffect(selectedThread?.threadId, threadMessages.size) {
        if (selectedThread != null && threadMessages.isNotEmpty()) {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val isNearBottom = lastVisibleIndex >= threadMessages.lastIndex - 2
            val hasNewMessage = threadMessages.size > previousMessageCount

            if (hasNewMessage && (isNearBottom || previousMessageCount <= 1)) {
                listState.animateScrollToItem(threadMessages.lastIndex)
            }

            previousMessageCount = threadMessages.size
        }
    }

    // File picker launchers
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && selectedThread != null) {
            val fileName = "image_${System.currentTimeMillis()}.jpg"
            viewModel.uploadFileToStorage(uri, fileName) { downloadUrl ->
                if (downloadUrl != null) {
                    viewModel.sendChatMessageToThread("📷 Shared an image", downloadUrl, replyingToMessage)
                    replyingToMessage = null
                }
            }
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && selectedThread != null) {
            val fileName = "document_${System.currentTimeMillis()}.pdf"
            viewModel.uploadFileToStorage(uri, fileName) { downloadUrl ->
                if (downloadUrl != null) {
                    viewModel.sendChatMessageToThread("📎 Shared a document", downloadUrl, replyingToMessage)
                    replyingToMessage = null
                }
            }
        }
    }

    if (selectedThread == null) {
        // Show list of online users with beautiful design
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(
                        "Messages",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Tap to start a conversation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(onlineUsers) { user ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.openDirectMessage(user) },
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp),
                    elevation = androidx.compose.material3.CardDefaults.cardElevation(
                        defaultElevation = if (user.isOnline) 8.dp else 4.dp
                    ),
                    border = if (user.isOnline) 
                        androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFE8DFFF))
                    else 
                        androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0F0F0))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Avatar with online badge and unread count
                            Box(modifier = Modifier.size(64.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(50.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.AccountCircle,
                                        contentDescription = "Avatar",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                                // Green online indicator at BottomEnd
                                if (user.isOnline) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(22.dp)
                                            .background(color = Color(0xFF4CAF50), shape = RoundedCornerShape(50.dp))
                                            .border(width = 2.dp, color = Color.White, shape = RoundedCornerShape(50.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(color = Color.White, shape = RoundedCornerShape(50.dp))
                                        )
                                    }
                                }
                                // Red unread count badge at TopEnd
                                val unreadCount = viewModel.getUnreadCountForUser(user.mobile)
                                if (unreadCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                                            .background(Color(0xFFE53935), RoundedCornerShape(50.dp))
                                            .border(1.5.dp, Color.White, RoundedCornerShape(50.dp))
                                            .padding(horizontal = 3.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            if (unreadCount > 99) "99+" else "$unreadCount",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    user.name,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFF1A1A1A),
                                    fontSize = 16.sp
                                )
                                Text(
                                    user.mobile,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF666666),
                                    fontSize = 13.sp
                                )
                                // Last message preview
                                val lastMsg = viewModel.getLastMessageForUser(user.mobile)
                                if (lastMsg.isNotBlank()) {
                                    Text(
                                        lastMsg,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF888888),
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                // Always show online / last-seen status row
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(
                                                color = if (user.isOnline) Color(0xFF4CAF50) else Color(0xFF999999),
                                                shape = RoundedCornerShape(50.dp)
                                            )
                                    )
                                    // Show "typing..." if user is typing, otherwise show status
                                    val isTyping = viewModel.isUserTyping(user.mobile)
                                    Text(
                                        if (isTyping) "typing..."
                                        else if (user.isOnline) "Active now"
                                        else viewModel.formatLastSeen(user.lastSeen).ifBlank { "Offline" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isTyping) Color(0xFF6366F1) else if (user.isOnline) Color(0xFF4CAF50) else Color(0xFF999999),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                        // Right side: show unread count pill if there are unread messages
                        val rightUnreadCount = viewModel.getUnreadCountForUser(user.mobile)
                        if (rightUnreadCount > 0) {
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 28.dp, minHeight = 28.dp)
                                    .background(Color(0xFFE53935), RoundedCornerShape(14.dp))
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (rightUnreadCount > 99) "99+" else "$rightUnreadCount",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else if (user.isOnline) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = "Online",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Show chat thread - imePadding on the Column so keyboard pushes content up (WhatsApp style)
        Column(modifier = Modifier
            .fillMaxSize()
            .imePadding()
        ) {
            // Chat header - Clean and professional
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp, 12.dp, 12.dp, 0.dp),
                color = Color.White,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(50.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.AccountCircle,
                                contentDescription = "User",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                if (selectedThread.participant1Mobile == viewModel.currentUser?.mobile)
                                    selectedThread.participant2Name
                                else
                                    selectedThread.participant1Name,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFF333333),
                                fontSize = 18.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(color = Color(0xFF4CAF50), shape = RoundedCornerShape(50.dp))
                                )
                                Text(
                                    "Online",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF4CAF50),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                onStartAudioCall()
                            },
                            modifier = Modifier.size(46.dp),
                            shape = RoundedCornerShape(50.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6C63FF)
                            ),
                            contentPadding = PaddingValues(0.dp),
                            elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                                defaultElevation = 3.dp
                            )
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = "Audio Call", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Button(
                            onClick = { onStartVideoCall() },
                            modifier = Modifier.size(46.dp),
                            shape = RoundedCornerShape(50.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF5B9EFF)
                            ),
                            contentPadding = PaddingValues(0.dp),
                            elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                                defaultElevation = 3.dp
                            )
                        ) {
                            Icon(Icons.Filled.VideoCall, contentDescription = "Video Call", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Button(
                            onClick = { viewModel.clearSelectedThread() },
                            modifier = Modifier.size(46.dp),
                            shape = RoundedCornerShape(50.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00C853)
                            ),
                            contentPadding = PaddingValues(0.dp),
                            elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                                defaultElevation = 3.dp
                            )
                        ) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }

            // Messages
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(12.dp)
                    .background(MaterialTheme.colorScheme.background),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                reverseLayout = false,
                state = listState
            ) {
                items(threadMessages) { message ->
                    val isCurrentUser = message.senderMobile == viewModel.currentUser?.mobile
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start
                    ) {
                        if (!isCurrentUser) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    message.senderName,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    message.senderMobile,
                                    fontWeight = FontWeight.Normal,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(if (isCurrentUser) 0.76f else 0.72f)
                                .pointerInput(message.messageId) {
                                    detectTapGestures(
                                        onLongPress = {
                                        replyingToMessage = message
                                        showAttachActions = false
                                        showEmojiPicker = false
                                        }
                                    )
                                },
                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = if (isCurrentUser)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(
                                topStart = 18.dp,
                                topEnd = 18.dp,
                                bottomStart = if (isCurrentUser) 18.dp else 2.dp,
                                bottomEnd = if (isCurrentUser) 2.dp else 18.dp
                            ),
                            elevation = androidx.compose.material3.CardDefaults.cardElevation(
                                defaultElevation = 4.dp
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(
                                    horizontal = if (isCurrentUser) 14.dp else 12.dp,
                                    vertical = if (isCurrentUser) 12.dp else 10.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (!message.replyToBody.isNullOrBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isCurrentUser)
                                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
                                        else
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = "Replying to ${message.replyToSenderName ?: "Message"}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isCurrentUser)
                                                    MaterialTheme.colorScheme.onPrimary
                                                else
                                                    MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = message.replyToBody.orEmpty(),
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                color = if (isCurrentUser)
                                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                                else
                                                    Color(0xFF1A1A1A)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    message.body,
                                    color = if (isCurrentUser)
                                        Color.White
                                    else
                                        Color(0xFF1A1A1A),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontSize = 17.sp,
                                    lineHeight = 23.sp
                                )
                                if (!message.mediaUri.isNullOrEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(message.mediaUri))
                                                context.startActivity(intent)
                                            }
                                            .background(
                                                color = if (isCurrentUser)
                                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)
                                                else
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .padding(10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                if (message.body.contains("image", ignoreCase = true)) Icons.Filled.PermMedia else Icons.Filled.AttachFile,
                                                contentDescription = "Media",
                                                tint = if (isCurrentUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                if (message.body.contains("image", ignoreCase = true)) "View Image" else "View Document",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isCurrentUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        viewModel.formatTime(message.timestamp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isCurrentUser)
                                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                    )
                                    if (!message.senderLocation.isNullOrBlank()) {
                                        Text(
                                            "•",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isCurrentUser)
                                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                        )
                                        Icon(
                                            Icons.Filled.LocationOn,
                                            contentDescription = null,
                                            modifier = Modifier.size(10.dp),
                                            tint = if (isCurrentUser)
                                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                        )
                                        Text(
                                            message.senderLocation,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isCurrentUser)
                                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Message input with emoji and media
            // Message input - WhatsApp style flat bottom bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    if (replyingToMessage != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "Replying to ${replyingToMessage?.senderName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = replyingToMessage?.body.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { replyingToMessage = null }, modifier = Modifier.size(28.dp)) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Cancel reply",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Attachment actions grouped under one attach icon
                    if (showAttachActions) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = {
                                        imagePickerLauncher.launch("image/*")
                                        showAttachActions = false
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color(0xFF5B9EFF), shape = RoundedCornerShape(12.dp))
                                ) {
                                    Icon(
                                        Icons.Filled.Camera,
                                        contentDescription = "Gallery",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text("Pic", style = MaterialTheme.typography.labelSmall, color = Color(0xFF5B9EFF))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = {
                                        documentPickerLauncher.launch("*/*")
                                        showAttachActions = false
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color(0xFF00C853), shape = RoundedCornerShape(12.dp))
                                ) {
                                    Icon(
                                        Icons.Filled.AttachFile,
                                        contentDescription = "Document",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text("Doc", style = MaterialTheme.typography.labelSmall, color = Color(0xFF00A845))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = {
                                        showEmojiPicker = !showEmojiPicker
                                        showAttachActions = false
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(
                                            if (showEmojiPicker) Color(0xFFFFA500) else Color(0xFFFFB300),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                ) {
                                    Icon(
                                        Icons.Filled.EmojiEmotions,
                                        contentDescription = "Emoji",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text("Smile", style = MaterialTheme.typography.labelSmall, color = Color(0xFFCC8A00))
                            }
                        }
                    }

                    // Emoji picker (shown above input row)
                    if (showEmojiPicker) {
                        val emojiList = listOf(
                            "😀", "😂", "❤️", "👍", "🔥", "✨", "🎉", "😍",
                            "😘", "😊", "😎", "🤔", "😢", "😡", "🤣", "💯",
                            "👋", "🙌", "💪", "🤝", "🎵", "🎮", "📱", "💻",
                            "📸", "🎥", "☎️", "📞", "⏰", "⭐", "🌟", "💫"
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            emojiList.forEach { emoji ->
                                TextButton(
                                    onClick = { messageBody += emoji; showEmojiPicker = false },
                                    contentPadding = PaddingValues(4.dp),
                                    modifier = Modifier.size(36.dp)
                                ) { Text(emoji, fontSize = 18.sp) }
                            }
                        }
                    }
                    // Upload progress
                    if (uploadProgress > 0f && uploadProgress < 1f) {
                        LinearProgressIndicator(progress = uploadProgress, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
                    }
                    // Upload error
                    if (uploadError != null) {
                        Text("⚠️ ${uploadError}", color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                        TextButton(onClick = { viewModel.clearUploadError() }) { Text("Dismiss") }
                    }
                    // Single input row: [attach] [textfield] [send]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = { showAttachActions = !showAttachActions },
                            modifier = Modifier.size(40.dp).background(
                                if (showAttachActions) Color(0xFFFFA500) else Color(0xFF00C853),
                                shape = RoundedCornerShape(12.dp)
                            )
                        ) { Icon(Icons.Filled.AttachFile, contentDescription = "Attach", tint = Color.White, modifier = Modifier.size(20.dp)) }
                        OutlinedTextField(
                            messageBody,
                            { messageBody = it },
                            placeholder = { Text("Type a message...", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFAAAAAA)) },
                            modifier = Modifier.weight(1f).heightIn(min = 42.dp),
                            shape = RoundedCornerShape(22.dp),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            maxLines = 4,
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color(0xFFE0E0E0),
                                focusedBorderColor = Color(0xFF6C63FF),
                                unfocusedContainerColor = Color(0xFFF5F5F5),
                                focusedContainerColor = Color(0xFFF5F5F5)
                            )
                        )
                        Button(
                            onClick = {
                                if (messageBody.isNotBlank()) {
                                    viewModel.sendChatMessageToThread(messageBody, null, replyingToMessage)
                                    messageBody = ""
                                    replyingToMessage = null
                                    showAttachActions = false
                                    showEmojiPicker = false
                                }
                            },
                            modifier = Modifier.size(46.dp),
                            shape = RoundedCornerShape(50.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF)),
                            contentPadding = PaddingValues(0.dp),
                            elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
                        ) { Icon(Icons.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(22.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioCallScreen(
    threadName: String,
    callState: com.familyconnect.app.webrtc.CallState,
    onToggleMute: (Boolean) -> Unit,
    onToggleSpeaker: (Boolean) -> Unit,
    onEndCall: () -> Unit
) {
    val callStatus = when (callState.status) {
        CallStatus.REQUESTING -> "Ringing..."
        CallStatus.RINGING -> "Incoming call"
        CallStatus.CONNECTING -> "Connecting..."
        CallStatus.ACTIVE -> formatCallDuration(callState.callDuration)
        CallStatus.ENDED -> "Call ended"
        else -> "Waiting..."
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Audio wave animation - pulsing effect
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(60.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Call, contentDescription = "Audio Call", modifier = Modifier.size(60.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                "Audio Call with",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                threadName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Call timer/status
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .padding(horizontal = 16.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        callStatus,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        callState.status.name.replace("_", " "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Call controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute button
                Button(
                    onClick = { onToggleMute(!callState.localAudioEnabled) },
                    modifier = Modifier.size(64.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = if (!callState.localAudioEnabled)
                            MaterialTheme.colorScheme.errorContainer 
                        else 
                            MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(32.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        if (!callState.localAudioEnabled) Icons.Filled.MicOff else Icons.Filled.Mic,
                        contentDescription = "Mute",
                        tint = if (!callState.localAudioEnabled) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                // End call button (prominent)
                Button(
                    onClick = onEndCall,
                    modifier = Modifier.size(72.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(36.dp),
                    contentPadding = PaddingValues(0.dp),
                    elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                        defaultElevation = 8.dp
                    )
                ) {
                    Icon(Icons.Filled.CallEnd, contentDescription = "End Call", tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(32.dp))
                }
                
                // Speaker button
                Button(
                    onClick = { onToggleSpeaker(!callState.remoteAudioEnabled) },
                    modifier = Modifier.size(64.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = if (callState.remoteAudioEnabled)
                            MaterialTheme.colorScheme.secondaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(32.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        if (callState.remoteAudioEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeMute,
                        contentDescription = "Speaker",
                        tint = if (callState.remoteAudioEnabled) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Debug info and troubleshooting
            if (callState.callDuration > 5000 && callState.status == CallStatus.REQUESTING) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(horizontal = 16.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "⏱️ Call is taking too long to connect",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Troubleshooting:\n• Check if other person is online (ask them to open the app)\n• Verify internet connection on both devices\n• End this call and try calling again\n• Check Firebase Database rules allow /calls write access",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            
            // Status info
            Text(
                "Call Status: ${callState.status.name}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                "Tap the red button to end the call",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formatCallDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
private fun IncomingCallOverlay(
    callerName: String,
    isVideoCall: Boolean = false,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val context = LocalContext.current
    val ringtone = remember {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            RingtoneManager.getRingtone(context, uri)
        }.getOrNull()
    }

    DisposableEffect(Unit) {
        try {
            ringtone?.play()
        } catch (_: Exception) {
        }
        onDispose {
            try {
                ringtone?.stop()
            } catch (_: Exception) {
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF121212)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .background(Color(0xFF2A2A2A), RoundedCornerShape(70.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.AccountCircle,
                    contentDescription = "Caller",
                    tint = Color.White,
                    modifier = Modifier.size(100.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                callerName,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Incoming ${if (isVideoCall) "video" else "audio"} call",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFBDBDBD)
            )

            Spacer(modifier = Modifier.height(36.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onReject,
                    modifier = Modifier.size(78.dp),
                    shape = RoundedCornerShape(39.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFC62828)
                    )
                ) {
                    Icon(Icons.Filled.CallEnd, contentDescription = "Reject", tint = Color.White, modifier = Modifier.size(34.dp))
                }

                Button(
                    onClick = onAccept,
                    modifier = Modifier.size(78.dp),
                    shape = RoundedCornerShape(39.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E7D32)
                    )
                ) {
                    Icon(Icons.Filled.Call, contentDescription = "Accept", tint = Color.White, modifier = Modifier.size(34.dp))
                }
            }
        }
    }
}

@Composable
private fun VideoCallScreen(
    threadName: String,
    callState: com.familyconnect.app.webrtc.CallState,
    webRTCManager: com.familyconnect.app.webrtc.WebRTCManager,
    onToggleMute: (Boolean) -> Unit,
    onToggleVideo: (Boolean) -> Unit,
    onSwitchCamera: () -> Unit,
    onEndCall: () -> Unit
) {
    val callStatus = when (callState.status) {
        CallStatus.REQUESTING -> "Ringing..."
        CallStatus.RINGING -> "Incoming video call"
        CallStatus.CONNECTING -> "Connecting..."
        CallStatus.ACTIVE -> formatCallDuration(callState.callDuration)
        CallStatus.ENDED -> "Call ended"
        else -> "Waiting..."
    }

    // Track whether the remote video has arrived so we can trigger recomposition
    var hasRemoteVideo by remember { mutableStateOf(webRTCManager.remoteVideoTrack != null) }

    // Listen for remote video track arriving asynchronously
    DisposableEffect(Unit) {
        val previous = webRTCManager.onRemoteVideoTrackReceived
        webRTCManager.onRemoteVideoTrackReceived = { _ ->
            hasRemoteVideo = true
        }
        onDispose {
            webRTCManager.onRemoteVideoTrackReceived = previous
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Video area with preview in corner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF1A1A1A))
            ) {
                // Remote video – use remember + DisposableEffect so init/release happen exactly once
                val remoteRendererRef = remember { mutableStateOf<org.webrtc.SurfaceViewRenderer?>(null) }
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx ->
                        org.webrtc.SurfaceViewRenderer(ctx).also { renderer ->
                            webRTCManager.initRemoteRenderer(renderer)
                            remoteRendererRef.value = renderer
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                // Clean up remote renderer when composable leaves
                DisposableEffect(Unit) {
                    onDispose {
                        webRTCManager.releaseRemoteRenderer()
                    }
                }

                // Status overlay when not connected yet
                if (callState.status != CallStatus.ACTIVE) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xCC000000))
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.VideoCall,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            threadName,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            callStatus,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFFBDBDBD)
                        )
                    }
                }

                // Local video preview in corner
                if (callState.localVideoEnabled) {
                    Card(
                        modifier = Modifier
                            .size(120.dp, 160.dp)
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = Color(0xFF333333)
                        )
                    ) {
                        val localRendererRef = remember { mutableStateOf<org.webrtc.SurfaceViewRenderer?>(null) }
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { ctx ->
                                org.webrtc.SurfaceViewRenderer(ctx).also { renderer ->
                                    webRTCManager.initLocalRenderer(renderer)
                                    localRendererRef.value = renderer
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        DisposableEffect(Unit) {
                            onDispose {
                                webRTCManager.releaseLocalRenderer()
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .size(120.dp, 160.dp)
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = Color(0xFF333333)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                            Text("Camera Off", style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }
                }

                // Call duration / status bar at top
                if (callState.status == CallStatus.ACTIVE) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0x99000000)
                    ) {
                        Text(
                            callStatus,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Call controls bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1A1A1A)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mute
                    Button(
                        onClick = { onToggleMute(!callState.localAudioEnabled) },
                        modifier = Modifier.size(56.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = if (!callState.localAudioEnabled) Color(0xFFE53935) else Color(0xFF424242)
                        ),
                        shape = RoundedCornerShape(28.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            if (!callState.localAudioEnabled) Icons.Filled.MicOff else Icons.Filled.Mic,
                            contentDescription = "Mute",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Toggle camera on/off
                    Button(
                        onClick = { onToggleVideo(!callState.localVideoEnabled) },
                        modifier = Modifier.size(56.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = if (!callState.localVideoEnabled) Color(0xFFE53935) else Color(0xFF424242)
                        ),
                        shape = RoundedCornerShape(28.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            Icons.Filled.VideoCall,
                            contentDescription = "Toggle Video",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Flip camera
                    Button(
                        onClick = { onSwitchCamera() },
                        modifier = Modifier.size(56.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF424242)
                        ),
                        shape = RoundedCornerShape(28.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Switch Camera", tint = Color.White, modifier = Modifier.size(24.dp))
                    }

                    // End call
                    Button(
                        onClick = onEndCall,
                        modifier = Modifier.size(64.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE53935)
                        ),
                        shape = RoundedCornerShape(32.dp),
                        contentPadding = PaddingValues(0.dp),
                        elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                    ) {
                        Icon(Icons.Filled.CallEnd, contentDescription = "End Call", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaScreen(viewModel: FamilyViewModel) {
    val media by viewModel.media.collectAsState(initial = emptyList())
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Photo") }
    var uri by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("Photo & Media Sharing", style = MaterialTheme.typography.titleLarge)
        }
        item {
            OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(type, { type = it }, label = { Text("Type (Photo/Video)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(uri, { uri = it }, label = { Text("Media URI") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    if (title.isNotBlank() && uri.isNotBlank()) {
                        viewModel.addMedia(title, type, uri)
                        title = ""
                        uri = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Upload") }
        }

        items(media) { item ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(item.title, fontWeight = FontWeight.Bold)
                    Text("Type: ${item.mediaType}")
                    Text("By: ${item.uploadedBy}")
                    Text("URI: ${item.uri}")
                    Text("At: ${item.timestamp}")
                }
            }
        }
    }
}

@Composable
private fun NotesScreen(viewModel: FamilyViewModel) {
    val notes by viewModel.notes.collectAsState(initial = emptyList())
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("Shared Notes & Lists", style = MaterialTheme.typography.titleLarge)
        }

        item {
            OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(content, { content = it }, label = { Text("Content") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    if (title.isNotBlank() && content.isNotBlank()) {
                        viewModel.addNote(title, content)
                        title = ""
                        content = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save Note") }
        }

        items(notes) { note ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(note.title, fontWeight = FontWeight.Bold)
                    Text(note.content)
                    Text("Edited by ${note.editedBy} at ${note.editedAt}")
                }
            }
        }
    }
}


@Composable
private fun SettingsScreen(viewModel: FamilyViewModel, role: FamilyRole) {
    val darkMode by viewModel.darkMode.collectAsState(initial = false)
    var showMemberManagement by remember { mutableStateOf(false) }
    var showAdminSetup by remember { mutableStateOf(false) }
    
    if (showMemberManagement) {
        MemberManagementScreen(viewModel, onBack = { showMemberManagement = false })
    } else if (showAdminSetup) {
        AdminSetupScreen(viewModel, onBack = { showAdminSetup = false })
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "⚙️ Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Profile Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("👤 Your Role", fontWeight = FontWeight.Bold)
                    Text("Role: ${role.name}", style = MaterialTheme.typography.bodyMedium)
                    Text("✅ Notifications enabled", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Family Members Section
            if (role == FamilyRole.ADMIN) {
                Text(
                    "👥 Family Members",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Manage Family", fontWeight = FontWeight.Bold)
                        Button(
                            onClick = { showMemberManagement = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("View/Add Members")
                        }
                    }
                }

                // Admin Controls
                Text(
                    "🔐 Admin Controls",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Admin Features", fontWeight = FontWeight.Bold)
                        Button(
                            onClick = { showAdminSetup = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Admin Setup")
                        }
                    }
                }
            }

            // Display Settings Section
            Text(
                "🎨 Display Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("🌙 Dark Mode", fontWeight = FontWeight.Bold)
                        Switch(
                            checked = darkMode,
                            onCheckedChange = { viewModel.setDarkMode(it) }
                        )
                    }
                }
            }

            // Logout Section
            Text(
                "Account",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { viewModel.logout() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD32F2F)
                        )
                    ) {
                        Icon(Icons.Filled.Logout, contentDescription = "Logout", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Logout")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun MemberManagementScreen(viewModel: FamilyViewModel, onBack: () -> Unit) {
    val allowedUsers by viewModel.allowedUsers.collectAsState(initial = emptyList())
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("← Back to Settings")
        }

        Text(
            "👥 Member Management",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Add New Member", fontWeight = FontWeight.Bold)
                
                OutlinedTextField(
                    value = viewModel.manageUserName,
                    onValueChange = { viewModel.manageUserName = it },
                    label = { Text("Member Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = viewModel.manageUserMobile,
                    onValueChange = { viewModel.manageUserMobile = it.filter(Char::isDigit).take(10) },
                    label = { Text("Mobile Number") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Text("Role:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = viewModel.manageUserRole == FamilyRole.PARENT,
                        onClick = { viewModel.manageUserRole = FamilyRole.PARENT },
                        label = { Text("Parent") }
                    )
                    FilterChip(
                        selected = viewModel.manageUserRole == FamilyRole.CHILD,
                        onClick = { viewModel.manageUserRole = FamilyRole.CHILD },
                        label = { Text("Child") }
                    )
                }

                Button(
                    onClick = { viewModel.addOrUpdateUser() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("➕ Add Member")
                }

                viewModel.userManagementMessage?.let {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Text(it, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Display list of members from Firebase
        if (allowedUsers.isNotEmpty()) {
            Text(
                "📋 Current Members (${allowedUsers.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                allowedUsers.forEach { user ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    user.name,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    user.mobile,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    user.role,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    "No members added yet. Add one above.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun AdminSetupScreen(viewModel: FamilyViewModel, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("← Back to Settings")
        }

        Text(
            "🔐 Admin Setup",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Change Admin PIN", fontWeight = FontWeight.Bold)
                Text("Current PIN: 2468", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                OutlinedTextField(
                    value = viewModel.newAdminSetupPin,
                    onValueChange = { viewModel.newAdminSetupPin = it.filter(Char::isDigit) },
                    label = { Text("New PIN (numbers only)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
                OutlinedTextField(
                    value = viewModel.confirmAdminSetupPin,
                    onValueChange = { viewModel.confirmAdminSetupPin = it.filter(Char::isDigit) },
                    label = { Text("Confirm New PIN") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
                Button(
                    onClick = { viewModel.updateAdminSetupPin() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("🔐 Update PIN")
                }
                viewModel.adminPinMessage?.let {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Text(it, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
