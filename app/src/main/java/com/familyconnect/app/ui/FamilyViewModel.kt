package com.familyconnect.app.ui

import android.content.Context
import android.location.Geocoder
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyconnect.app.data.model.ChatMessage
import com.familyconnect.app.data.model.ChatMessageData
import com.familyconnect.app.data.model.ChatThread
import com.familyconnect.app.data.model.FamilyEvent
import com.familyconnect.app.data.model.FamilyRole
import com.familyconnect.app.data.model.MediaItem
import com.familyconnect.app.data.model.NoteItem
import com.familyconnect.app.data.model.OnlineUser
import com.familyconnect.app.data.model.ReminderItem
import com.familyconnect.app.data.model.TaskItem
import com.familyconnect.app.data.model.TypingStatus
import com.familyconnect.app.data.model.UserProfile
import com.familyconnect.app.data.repository.FamilyRepository
import com.familyconnect.app.data.repository.UserManagementService
import com.familyconnect.app.data.repository.AllowedUser
import com.familyconnect.app.notifications.CallListenerService
import com.familyconnect.app.notifications.NotificationHelper
import kotlinx.coroutines.flow.Flow
import com.familyconnect.app.webrtc.CallRequest
import com.familyconnect.app.webrtc.CallStatus
import com.familyconnect.app.webrtc.CallState
import com.familyconnect.app.webrtc.CallType
import com.familyconnect.app.webrtc.WebRTCManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.storage.FirebaseStorage
import com.familyconnect.app.notifications.FCMService
import com.familyconnect.app.utils.CredentialsManager
import com.familyconnect.app.utils.TypingStatusManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.UUID

class FamilyViewModel(
    private val repository: FamilyRepository,
    private val context: Context
) : ViewModel() {
    private val eventExpiryWindowMillis = TimeUnit.DAYS.toMillis(3)
    private var previousMessageIds = setOf<String>()
    private val webRTCManager = WebRTCManager(context)
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    
    // Typing status manager - initialized lazily with viewModelScope and context
    private val typingStatusManager: TypingStatusManager by lazy { TypingStatusManager(viewModelScope, context) }

    /** Cached city/locality name resolved from the last known location. */
    var lastKnownLocation by mutableStateOf<String?>(null)
        private set

    fun getWebRTCManager(): WebRTCManager = webRTCManager

    /**
     * Refresh the cached location name. Call this when location permission is granted.
     * Uses coarse/fine location to get a city name via Geocoder.
     */
    @android.annotation.SuppressLint("MissingPermission")
    fun refreshLocation() {
        try {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                CancellationTokenSource().token
            ).addOnSuccessListener { location ->
                if (location != null) {
                    try {
                        @Suppress("DEPRECATION")
                        val addresses = Geocoder(context, java.util.Locale.getDefault())
                            .getFromLocation(location.latitude, location.longitude, 1)
                        val addr = addresses?.firstOrNull()
                        val area = addr?.subLocality ?: addr?.thoroughfare
                        val city = addr?.locality ?: addr?.subAdminArea ?: addr?.adminArea
                        val locationText = listOfNotNull(area, city).distinct().joinToString(", ")
                        if (locationText.isNotBlank()) {
                            lastKnownLocation = locationText
                        }
                    } catch (e: Exception) {
                        Log.e("FamilyViewModel", "Geocoder error: ${e.message}")
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e("FamilyViewModel", "Location permission not granted: ${e.message}")
        }
    }

    var currentUser by mutableStateOf<UserProfile?>(null)
        private set

    var loginMobile by mutableStateOf("")
    var loginError by mutableStateOf<String?>(null)

    var registerName by mutableStateOf("")
    var registerMobile by mutableStateOf("")
    var registerRole by mutableStateOf(FamilyRole.CHILD)
    var registerMessage by mutableStateOf<String?>(null)
    var newAdminSetupPin by mutableStateOf("")
    var confirmAdminSetupPin by mutableStateOf("")
    var adminPinMessage by mutableStateOf<String?>(null)

    // Chat feature state
    var selectedChatThread by mutableStateOf<ChatThread?>(null)
        private set

    val users = repository.usersFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Simply observe all events - filtering by user happens in repository
    val events = repository.observeEvents().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val tasks = repository.observeTasks().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val messages = repository.observeMessages().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val media = repository.observeMedia().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val notes = repository.observeNotes().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // 📌 Reminders observation
    val reminders = repository.observeReminders().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val darkMode = repository.darkModeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val language = repository.languageFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "en"
    )

    val largeText = repository.accessibilityLargeTextFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val adminSetupPin = repository.adminSetupPinFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FamilyRepository.DEFAULT_ADMIN_SETUP_PIN
    )

    // Chat feature flows  
    val onlineUsers = repository.observeOnlineUsers().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _userChatThreadsFlow = MutableStateFlow<List<ChatThread>>(emptyList())
    val userChatThreads = _userChatThreadsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = emptyList()
    )

    private val _currentThreadMessagesFlow = MutableStateFlow<List<ChatMessageData>>(emptyList())
    val currentThreadMessages = _currentThreadMessagesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = emptyList()
    )

    // 📝 Typing status for current thread
    private val _typingStatusFlow = MutableStateFlow<List<TypingStatus>>(emptyList())
    val typingStatus = _typingStatusFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = emptyList()
    )

    // 📝 Typing status by thread (for contact list display)
    private val _typingByThreadFlow = MutableStateFlow<Map<String, TypingStatus>>(emptyMap())
    val typingByThread = _typingByThreadFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = emptyMap()
    )

    // 📝 Typing status for all threads, tracked per thread (used in message list view)
    // Map of threadId -> List of TypingStatus
    private val _threadTypingStatusMap = mutableMapOf<String, List<TypingStatus>>()
    private val _allThreadsTypingFlow = MutableStateFlow<Map<String, TypingStatus>>(emptyMap())
    val allThreadsTyping = _allThreadsTypingFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    // User Management state
    val allowedUsers = UserManagementService.observeAllowedUsers().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Form state for adding/editing users
    var manageUserName by mutableStateOf("")
    var manageUserMobile by mutableStateOf("")
    var manageUserRole by mutableStateOf(FamilyRole.CHILD)
    var editingUserId by mutableStateOf<String?>(null)
    var userManagementMessage by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch {
            // Verify Firebase connection first
            Log.d("🔥 FamilyViewModel", "📡 STARTUP: Verifying Firebase connection...")
            val fbReady = UserManagementService.verifyFirebaseConnection()
            if (!fbReady) {
                Log.e("🔥 FamilyViewModel", "❌ CRITICAL: Firebase not working! App will not sync data properly")
            } else {
                Log.d("🔥 FamilyViewModel", "✅ Firebase verified and ready")
            }
        }
        
        viewModelScope.launch {
            // Sync all Firebase users to local database at app startup
            Log.d("🔥 FamilyViewModel", "📡 STARTUP: Syncing Firebase users on app startup...")
            repository.syncFirebaseUsersToLocal()
            Log.d("🔥 FamilyViewModel", "📡 STARTUP: Firebase sync completed")
        }
        
        viewModelScope.launch {
            Log.d("🔥 FamilyViewModel", "📡 STARTUP: Seeding default users...")
            repository.seedDefaultUsers()
            Log.d("🔥 FamilyViewModel", "📡 STARTUP: Default user seeding completed")
        }
        // Auto-restore session from DataStore
        viewModelScope.launch {
            try {
                repository.loggedInMobileFlow.first().let { savedMobile ->
                    if (!savedMobile.isNullOrBlank()) {
                        val result = repository.loginByMobile(savedMobile)
                        if (result != null) {
                            currentUser = result
                            repository.setUserOnline(result.mobile, result.name)
                        
                        // 🔔 CRITICAL FIX: Save FCM token for this user
                        Log.d("🔥 FamilyViewModel", "💾 Saving FCM token for user: ${result.mobile}")
                        FCMService.saveFCMTokenForUser(context, result.mobile)
                        
                            
                            // Start observing chat threads for this user
                            // Use launchIn instead of collect to avoid blocking
                            repository.observeUserChatThreads(result.mobile)
                                .onEach { threads -> 
                                    _userChatThreadsFlow.value = threads
                                    // 📝 Start observing typing for ALL threads
                                    startCombinedTypingObserver(threads, result.mobile)
                                }
                                .launchIn(viewModelScope)
                            
            // Start background listener service with error handling  
                            try {
                                Log.d("FamilyViewModel", "🔔 STARTING CallListenerService for: ${result.mobile}")
                                CallListenerService.start(context, result.mobile, result.name)
                                Log.d("FamilyViewModel", "✅ CallListenerService started successfully")
                            } catch (e: Exception) {
                                Log.e("FamilyViewModel", "❌ Failed to start CallListenerService on session restore: ${e.message}", e)
                                e.printStackTrace()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FamilyViewModel", "Session restore error: ${e.message}", e)
            }
        }
    }

    fun login() {
        viewModelScope.launch {
            try {
                Log.d("🔥 FamilyViewModel", "🔐 LOGIN ATTEMPT: ${loginMobile}")
                
                val result = repository.loginByMobile(loginMobile)
                if (result != null) {
                    Log.d("🔥 FamilyViewModel", "✅ LOGIN SUCCESSFUL: ${result.name} (${result.mobile})")
                    
                    try {
                        currentUser = result
                        loginError = null
                        
                        Log.d("🔥 FamilyViewModel", "💾 Saving login session...")
                        repository.saveLoggedInMobile(result.mobile)
                        
                        // 💾 PERSISTENT: Save credentials to SharedPreferences for post-kill recovery
                        CredentialsManager.saveCredentials(context, result.mobile, result.name)
                        Log.d("🔥 FamilyViewModel", "✅ Credentials saved to SharedPreferences")
                        
                        Log.d("🔥 FamilyViewModel", "🔥 Setting user online in Firebase...")
                        repository.setUserOnline(result.mobile, result.name)
                        
                        // � CRITICAL: Reload events after login to get fresh Firebase data
                        Log.d("🔥 FamilyViewModel", "🔄 Reloading events from Firebase after login...")
                        repository.forceReloadEventsFromFirebase()
                        
                        // �🔔 CRITICAL FIX: Save FCM token for this user
                        Log.d("🔥 FamilyViewModel", "💾 Saving FCM token for user: ${result.mobile}")
                        FCMService.saveFCMTokenForUser(context, result.mobile)
                        
                        Log.d("🔥 FamilyViewModel", "📞 Starting incoming calls observer...")
                        startObservingIncomingCalls()
                        
                        // Start observing chat threads for this user
                        // Use launchIn instead of collect to avoid blocking
                        Log.d("🔥 FamilyViewModel", "💬 Observing chat threads...")
                        repository.observeUserChatThreads(result.mobile)
                            .onEach { threads ->
                                Log.d("🔥 FamilyViewModel", "💬 Chat threads: ${threads.size} found")
                                _userChatThreadsFlow.value = threads
                                // 📝 Start observing typing for ALL threads
                                startCombinedTypingObserver(threads, result.mobile)
                            }
                            .launchIn(viewModelScope)
                        
                        // Start background listener service after a small delay to ensure Room/Firebase are ready
                        try {
                            Log.d("🔥 FamilyViewModel", "⏳ Delaying before CallListenerService...")
                            kotlinx.coroutines.delay(200)
                            
                            Log.d("🔥 FamilyViewModel", "🎧 Starting CallListenerService...")
                            CallListenerService.start(context, result.mobile, result.name)
                            Log.d("🔥 FamilyViewModel", "✅ CallListenerService started")
                        } catch (e: Exception) {
                            Log.e("🔥 FamilyViewModel", "❌ CallListenerService failed: ${e.message}", e)
                        }
                        
                        Log.d("🔥 FamilyViewModel", "✅ LOGIN FLOW COMPLETE")
                    } catch (e: Exception) {
                        Log.e("🔥 FamilyViewModel", "❌ Post-login error: ${e.message}", e)
                        loginError = "Setup failed: ${e.message.orEmpty().take(50)}"
                        currentUser = null
                    }
                } else {
                    Log.e("🔥 FamilyViewModel", "❌ LOGIN FAILED: Mobile not found in database: $loginMobile")
                    loginError = "This mobile number is not allowed to use the app"
                }
            } catch (e: Exception) {
                Log.e("🔥 FamilyViewModel", "❌ LOGIN ERROR: ${e.message}", e)
                loginError = "Login failed: ${e.message.orEmpty().take(50)}"
            }
        }
    }

    fun isAdminSetupAuthorized(adminMobile: String, adminPin: String): Boolean {
        return users.value.any {
            it.mobile == adminMobile.trim() && it.role == FamilyRole.ADMIN
        } && adminSetupPin.value == adminPin.trim()
    }

    fun register() {
        viewModelScope.launch {
            val response = repository.registerUser(
                name = registerName,
                mobile = registerMobile,
                role = registerRole
            )
            response.onSuccess {
                registerMessage = "User registered"
                registerName = ""
                registerMobile = ""
            }.onFailure {
                registerMessage = it.message ?: "Registration failed"
            }
        }
    }

    fun logout() {
        currentUser?.let {
            repository.setUserOffline(it.mobile, it.name)
        }
        // 💾 PERSISTENT: Clear saved credentials
        CredentialsManager.clearCredentials(context)
        Log.d("🔥 FamilyViewModel", "🗑️ Credentials cleared on logout")
        
        // Stop background listener service
        CallListenerService.stop(context)
        viewModelScope.launch {
            repository.clearLoggedInMobile()
        }
        currentUser = null
        selectedChatThread = null
        _userChatThreadsFlow.value = emptyList()
        _currentThreadMessagesFlow.value = emptyList()
        _typingStatusFlow.value = emptyList()
        _threadTypingStatusMap.clear()
        _allThreadsTypingFlow.value = emptyMap()
        previousMessageIds = emptySet()
    }
    fun setUserOfflineOnExit() {
        currentUser?.let {
            repository.setUserOffline(it.mobile, it.name)
        }
    }

    // Chat feature methods
    fun selectChatThread(thread: ChatThread) {
        selectedChatThread = thread
        // Clear typing status when opening a new thread
        _typingStatusFlow.value = emptyList()
        
        currentUser?.let {
            repository.markMessagesAsRead(thread.threadId, it.mobile)
        }
        // Start observing messages for this thread
        viewModelScope.launch {
            repository.observeThreadMessages(thread.threadId).collect { messages ->
                // Check for new messages and post notifications
                val newMessageIds = messages.map { it.messageId }.toSet()
                val addedMessages = messages.filter { it.messageId !in previousMessageIds }
                
                // Post notification for new messages from other users
                for (message in addedMessages) {
                    if (message.senderMobile != currentUser?.mobile) {
                        NotificationHelper.post(
                            context,
                            message.messageId.hashCode(),
                            "Message from ${message.senderName}",
                            message.body.take(100)
                        )
                    }
                }
                
                previousMessageIds = newMessageIds
                _currentThreadMessagesFlow.value = messages
            }
        }
        
        // 📝 Start observing typing status for this thread (for message view)
        currentUser?.let { user ->
            viewModelScope.launch {
                repository.observeTypingStatus(thread.threadId, user.mobile).collect { typingUsers: List<TypingStatus> ->
                    _typingStatusFlow.value = typingUsers
                }
            }
        }
    }

    fun openDirectMessage(otherUser: OnlineUser) {
        val current = currentUser ?: return
        repository.createOrGetChatThread(
            currentUserMobile = current.mobile,
            currentUserName = current.name,
            otherUserMobile = otherUser.mobile,
            otherUserName = otherUser.name
        ) { threadId ->
            if (threadId != null) {
                // Wait a bit for Firebase to sync, then find and select the thread
                viewModelScope.launch {
                    // Give Firebase a moment to update the flow
                    kotlinx.coroutines.delay(500)
                    val thread = userChatThreads.value.find { it.threadId == threadId }
                    if (thread != null) {
                        selectChatThread(thread)
                    } else {
                        // If not found in userChatThreads, create it manually from the threadId
                        val manualThread = ChatThread(
                            threadId = threadId,
                            participant1Mobile = current.mobile,
                            participant1Name = current.name,
                            participant2Mobile = otherUser.mobile,
                            participant2Name = otherUser.name,
                            lastMessage = "",
                            lastMessageTime = System.currentTimeMillis(),
                            createdAt = System.currentTimeMillis()
                        )
                        selectChatThread(manualThread)
                    }
                }
            }
        }
    }

    fun sendChatMessageToThread(
        body: String,
        mediaUri: String? = null,
        replyTo: ChatMessageData? = null
    ) {
        val current = currentUser ?: return
        val thread = selectedChatThread ?: return

        if (body.isBlank()) return

        val recipientMobile = if (thread.participant1Mobile == current.mobile)
            thread.participant2Mobile else thread.participant1Mobile

        // Stop typing when message is sent
        typingStatusManager.stopTyping(thread.threadId, current.mobile)

        repository.sendChatMessage(
            threadId = thread.threadId,
            senderMobile = current.mobile,
            senderName = current.name,
            body = body,
            mediaUri = mediaUri,
            replyToMessageId = replyTo?.messageId,
            replyToSenderName = replyTo?.senderName,
            replyToBody = replyTo?.body,
            recipientMobile = recipientMobile,
            senderLocation = lastKnownLocation
        ) { success ->
            if (!success) {
                // Handle error
            }
        }
    }

    // 📝 Notify other users that current user is typing
    fun onTextChanged() {
        val current = currentUser
        val thread = selectedChatThread
        
        if (current == null) {
            return
        }
        if (thread == null) {
            return
        }
        
        typingStatusManager.onTextChanged(thread.threadId, current.mobile, current.name)
    }

    // 📝 Stop typing notification
    fun stopTyping() {
        val current = currentUser ?: return
        val thread = selectedChatThread ?: return
        typingStatusManager.stopTyping(thread.threadId, current.mobile)
    }

    fun clearSelectedThread() {
        val current = currentUser
        val thread = selectedChatThread
        if (current != null && thread != null) {
            typingStatusManager.clearAll(thread.threadId, current.mobile)  // ✅ Use correct mobile
        }
        selectedChatThread = null
    }

    fun getOtherUserInThread(thread: ChatThread): OnlineUser? {
        val current = currentUser ?: return null
        return onlineUsers.value.find { user ->
            (thread.participant1Mobile == current.mobile && user.mobile == thread.participant2Mobile) ||
            (thread.participant2Mobile == current.mobile && user.mobile == thread.participant1Mobile)
        }
    }

    fun formatTime(timestamp: Long): String = repository.formatTime(timestamp)
    fun formatDate(timestamp: Long): String = repository.formatDate(timestamp)
    fun formatLastSeen(timestamp: Long): String = repository.formatLastSeen(timestamp)

    fun getUnreadCountForUser(userMobile: String): Int {
        val current = currentUser ?: return 0
        val count = userChatThreads.value.find { thread ->
            (thread.participant1Mobile == current.mobile && thread.participant2Mobile == userMobile) ||
            (thread.participant2Mobile == current.mobile && thread.participant1Mobile == userMobile)
        }?.unreadCount ?: 0
        return count
    }

    fun getLastMessageForUser(userMobile: String): String {
        val current = currentUser ?: return ""
        return userChatThreads.value.find { thread ->
            (thread.participant1Mobile == current.mobile && thread.participant2Mobile == userMobile) ||
            (thread.participant2Mobile == current.mobile && thread.participant1Mobile == userMobile)
        }?.lastMessage?.take(50) ?: ""
    }

    // 📝 Global typing observer for all threads (shows on home screen)
    private var typingObserverJob: Job? = null
    
    private fun startCombinedTypingObserver(threads: List<ChatThread>, currentUserMobile: String) {
        try {
            // Cancel previous job
            typingObserverJob?.cancel()
            
            Log.d("FamilyViewModel", "🔄 Starting combined typing observer for ${threads.size} threads")
            
            // Launch combined observer
            typingObserverJob = viewModelScope.launch {
                observeTypingForAllThreads(threads, currentUserMobile)
            }
        } catch (e: Exception) {
            Log.e("FamilyViewModel", "Error in startCombinedTypingObserver: ${e.message}", e)
        }
    }

    private suspend fun observeTypingForAllThreads(threads: List<ChatThread>, currentUserMobile: String) {
        try {
            if (threads.isEmpty()) {
                Log.d("FamilyViewModel", "No threads to observe for typing")
                return
            }
            
            Log.d("FamilyViewModel", "📝 Observing typing for ${threads.size} threads")
            
            // Launch a coroutine for each thread to observe typing independently
            threads.forEach { thread ->
                viewModelScope.launch {
                    try {
                        Log.d("FamilyViewModel", "📝 Added typing observer for thread: ${thread.threadId}")
                        repository.observeTypingStatus(thread.threadId, currentUserMobile).collect { typingUsers ->
                            // Update typing map by threadId
                            val currentMap = _typingByThreadFlow.value.toMutableMap()
                            
                            // Get the first typing user (if any)
                            val typingUser = typingUsers.firstOrNull { it.isTyping }
                            
                            if (typingUser != null) {
                                currentMap[thread.threadId] = typingUser
                            } else {
                                currentMap.remove(thread.threadId)
                            }
                            
                            Log.d("FamilyViewModel", "📝 Typing updated from thread ${thread.threadId}: ${currentMap.size} threads with typing")
                            _typingByThreadFlow.value = currentMap
                        }
                    } catch (e: Exception) {
                        Log.e("FamilyViewModel", "Error observing thread ${thread.threadId}: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FamilyViewModel", "Error in observeTypingForAllThreads: ${e.message}", e)
        }
    }

    fun addEvent(event: FamilyEvent) {
        Log.d("FamilyViewModel", "addEvent: ${event.title} (invites: ${event.invitedMembers.size})")
        viewModelScope.launch {
            repository.addEvent(event)
            repository.forceReloadEventsFromFirebase()
        }
    }

    fun deleteEvent(event: FamilyEvent) {
        viewModelScope.launch {
            repository.deleteEvent(event)
            repository.forceReloadEventsFromFirebase()
        }
    }

    fun markEventComplete(event: FamilyEvent) {
        // Mark as complete by showing a notification
        viewModelScope.launch {
            repository.deleteEvent(event)
            repository.forceReloadEventsFromFirebase()
            NotificationHelper.post(context, event.id + 5000, "Event Completed", "✅ ${event.title} marked as done")
        }
    }

    // ============ REMINDER METHODS ============

    fun addReminder(title: String, details: String, assignedMembers: List<String>) {
        viewModelScope.launch {
            val reminder = ReminderItem(
                title = title,
                details = details,
                createdBy = currentUser?.mobile ?: "unknown",
                createdAtEpochMillis = System.currentTimeMillis(),
                assignedMembers = assignedMembers,
                snoozeOptions = listOf(5, 15, 30, 60, 1440),  // 5min, 15min, 30min, 1hr, 1 day
                completed = false
            )
            repository.addReminder(reminder)
            
            // Post notification to assigned members
            val reminderTitle = "📌 New Reminder from ${currentUser?.name}"
            NotificationHelper.post(context, reminder.id.hashCode(), reminderTitle, title)
        }
    }

    fun deleteReminder(reminder: ReminderItem) {
        viewModelScope.launch {
            repository.deleteReminder(reminder.id)
        }
    }

    fun markReminderComplete(reminder: ReminderItem) {
        viewModelScope.launch {
            repository.markReminderComplete(reminder.id)
            NotificationHelper.post(context, reminder.id.hashCode() + 1000, "Reminder Completed", "✅ ${reminder.title} marked as done")
        }
    }

    fun snoozeReminderNotification(reminder: ReminderItem, snoozeMinutes: Int) {
        viewModelScope.launch {
            repository.snoozeReminderNotification(reminder.id, snoozeMinutes)
            NotificationHelper.post(context, reminder.id.hashCode() + 2000, "Reminder Snoozed", "⏱️ ${reminder.title} snoozed for $snoozeMinutes minutes")
        }
    }

    fun addTask(title: String, assignedTo: String, dueDate: String, rewardPoints: Int?) {
        viewModelScope.launch {
            val id = (tasks.value.maxOfOrNull { it.id } ?: 0) + 1
            repository.addTask(
                TaskItem(id, title, assignedTo, dueDate, completed = false, rewardPoints = rewardPoints)
            )
        }
    }

    fun toggleTask(taskId: Int) {
        viewModelScope.launch {
            repository.toggleTask(taskId)
        }
    }

    fun sendMessage(target: String, body: String, mediaUri: String?) {
        val senderName = currentUser?.name ?: "Unknown"
        viewModelScope.launch {
            val id = (messages.value.maxOfOrNull { it.id } ?: 0) + 1
            repository.sendMessage(
                ChatMessage(
                    id = id,
                    sender = senderName,
                    target = target,
                    body = body,
                    mediaUri = mediaUri,
                    read = false,
                    timestamp = java.time.LocalTime.now().toString().take(5)
                )
            )
        }
    }

    fun markMessageRead(id: Int) {
        viewModelScope.launch {
            repository.markMessageRead(id)
        }
    }

    fun addMedia(title: String, type: String, uri: String) {
        val uploader = currentUser?.name ?: "Unknown"
        viewModelScope.launch {
            val id = (media.value.maxOfOrNull { it.id } ?: 0) + 1
            repository.addMedia(
                MediaItem(id, title, type, uri, uploader, java.time.LocalDate.now().toString())
            )
        }
    }

    fun addNote(title: String, content: String) {
        val editor = currentUser?.name ?: "Unknown"
        viewModelScope.launch {
            val id = (notes.value.maxOfOrNull { it.id } ?: 0) + 1
            repository.addNote(
                NoteItem(id, title, content, editor, java.time.LocalDateTime.now().toString().take(16))
            )
        }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { repository.setDarkMode(enabled) }
    }

    fun setLanguage(language: String) {
        viewModelScope.launch { repository.setLanguage(language) }
    }

    fun setLargeText(enabled: Boolean) {
        viewModelScope.launch { repository.setLargeText(enabled) }
    }

    fun updateAdminSetupPin() {
        val newPin = newAdminSetupPin.trim()
        val confirmPin = confirmAdminSetupPin.trim()
        if (newPin.length < 4) {
            adminPinMessage = "PIN must be at least 4 digits"
            return
        }
        if (newPin != confirmPin) {
            adminPinMessage = "PIN confirmation does not match"
            return
        }
        viewModelScope.launch {
            repository.setAdminSetupPin(newPin)
            adminPinMessage = "Admin setup PIN updated"
            newAdminSetupPin = ""
            confirmAdminSetupPin = ""
        }
    }

    fun unreadMessageCount(): Int = messages.value.count { !it.read }

    fun latestMessage(): ChatMessage? = messages.value.maxByOrNull { it.id }

    fun eventDaysLeft(event: FamilyEvent): Long {
        val remaining = (event.createdAtEpochMillis + eventExpiryWindowMillis) - System.currentTimeMillis()
        return if (remaining <= 0) 0 else (remaining + TimeUnit.DAYS.toMillis(1) - 1) / TimeUnit.DAYS.toMillis(1)
    }

    // File upload functionality
    var uploadProgress by mutableStateOf(0f)
        private set

    var uploadError by mutableStateOf<String?>(null)
        private set
    
    fun clearUploadError() {
        uploadError = null
    }

    fun uploadFileToStorage(fileUri: Uri, fileName: String, callback: (String?) -> Unit) {
        val storage = FirebaseStorage.getInstance()
        val storageRef = storage.reference

        // Create a path: chats/{threadId}/{timestamp}_{filename}
        val thread = selectedChatThread ?: return
        val timestamp = System.currentTimeMillis()
        val filePath = "chats/${thread.threadId}/${timestamp}_$fileName"
        val fileRef = storageRef.child(filePath)

        viewModelScope.launch {
            try {
                uploadProgress = 0f
                uploadError = null

                val uploadTask = fileRef.putFile(fileUri)
                uploadTask
                    .addOnProgressListener { taskSnapshot ->
                        val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toFloat() / 100f
                        uploadProgress = progress
                    }
                    .addOnSuccessListener {
                        // File uploaded successfully, now get the download URL
                        fileRef.downloadUrl
                            .addOnSuccessListener { downloadUri ->
                                uploadProgress = 1f
                                uploadError = null
                                callback(downloadUri.toString())
                                Log.d("Upload", "File uploaded successfully: $downloadUri")
                            }
                            .addOnFailureListener { e ->
                                uploadError = "Failed to get download URL: ${e.message}"
                                uploadProgress = 0f
                                callback(null)
                                Log.e("Upload", "Failed to get URL: ${e.message}", e)
                            }
                    }
                    .addOnFailureListener { e ->
                        uploadError = "Upload failed: ${e.message}"
                        uploadProgress = 0f
                        callback(null)
                        Log.e("Upload", "Upload failed: ${e.message}", e)
                    }
            } catch (e: Exception) {
                uploadError = "Error: ${e.message}"
                uploadProgress = 0f
                callback(null)
                Log.e("Upload", "Exception: ${e.message}", e)
            }
        }
    }

    // ============ AUDIO CALL MANAGEMENT ============

    var callState by mutableStateOf(
        CallState(
            status = CallStatus.IDLE,
            incomingCallRequest = null,
            activeCallId = null,
            activeThreadId = null,
            activeCallPartyName = null,
            callDuration = 0,
            localAudioEnabled = true,
            remoteAudioEnabled = true,
            isCallConnected = false
        )
    )
        private set

    private val _incomingCallRequests = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val incomingCallRequests = _incomingCallRequests.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private var incomingCallsJob: Job? = null
    private var activeCallObserverJob: Job? = null
    private var signalingObserverJob: Job? = null
    private var callTimerJob: Job? = null
    private var connectionTimeoutJob: Job? = null
    private val seenIncomingCallIds = mutableSetOf<String>()
    private val processedSignalingIds = mutableSetOf<String>()
    private var incomingActionInProgress = false

    fun startObservingIncomingCalls() {
        currentUser?.let { user ->
            incomingCallsJob?.cancel()
            incomingCallsJob = viewModelScope.launch {
                repository.observeCallRequests(user.mobile).collect { requests ->
                    _incomingCallRequests.value = requests

                    val incoming = requests.firstOrNull()
                    if (incoming != null) {
                        val callId = incoming["callId"] as? String ?: ""
                        val fromName = incoming["fromUserName"] as? String ?: "Someone"

                        if (callId.isNotBlank() && seenIncomingCallIds.add(callId)) {
                            NotificationHelper.post(
                                context,
                                incoming.hashCode(),
                                "Incoming Call",
                                "$fromName is calling..."
                            )
                        }

                        if (callState.status == CallStatus.IDLE && callId.isNotBlank()) {
                            val callTypeFromFirebase = incoming["callType"] as? String
                            val incomingCallType = if (callTypeFromFirebase == "video") CallType.VIDEO else CallType.AUDIO
                            
                            Log.d("FamilyViewModel", "📞 INCOMING CALL DETECTED:")
                            Log.d("FamilyViewModel", "   callId=$callId")
                            Log.d("FamilyViewModel", "   from=$fromName")
                            Log.d("FamilyViewModel", "   callType from Firebase: '$callTypeFromFirebase' ⭐⭐⭐")
                            Log.d("FamilyViewModel", "   parsed as: $incomingCallType")
                            
                            callState = callState.copy(
                                status = CallStatus.RINGING,
                                callType = incomingCallType,
                                incomingCallRequest = CallRequest(
                                    callId = callId,
                                    fromUserId = incoming["fromUserId"] as? String ?: "",
                                    fromUserName = fromName,
                                    toUserId = incoming["toUserId"] as? String ?: user.mobile,
                                    threadId = incoming["threadId"] as? String ?: "",
                                    createdAt = incoming["createdAt"] as? Long ?: System.currentTimeMillis(),
                                    status = incoming["status"] as? String ?: "pending",
                                    callType = incoming["callType"] as? String ?: "audio"
                                ),
                                activeCallId = callId,
                                activeThreadId = incoming["threadId"] as? String,
                                activeCallPartyName = fromName
                            )
                        }
                    } else if (callState.status == CallStatus.RINGING && !incomingActionInProgress) {
                        callState = callState.copy(
                            status = CallStatus.IDLE,
                            incomingCallRequest = null,
                            activeCallId = null,
                            activeThreadId = null,
                            activeCallPartyName = null
                        )
                    }
                }
            }
        }
    }

    fun initiateCall(toUserId: String, threadId: String, toUserName: String = "User") {
        currentUser?.let { user ->
            viewModelScope.launch {
                val callId = UUID.randomUUID().toString()
                
                // ✅ FIX: Log callType BEFORE updating state
                Log.d("FamilyViewModel", "🔔 INITIATING CALL: callType=${callState.callType}")
                Log.d("FamilyViewModel", "🔔 INITIATING CALL: from=${user.mobile}, to=$toUserId, threadId=$threadId, callId=$callId")

                // Update UI immediately so user sees call start feedback
                callState = callState.copy(
                    status = CallStatus.REQUESTING,
                    activeCallId = callId,
                    activeThreadId = threadId,
                    activeCallPartyName = toUserName,
                    incomingCallRequest = null,
                    callDuration = 0,
                    isCallConnected = false
                    // ✅ FIX: PRESERVE callType that was set by initiateVideoCallForSelectedThread()
                    // callType is already set before initiateCall() is called, don't overwrite it
                )
                
                // ✅ Log callType AFTER update to verify it's preserved
                Log.d("FamilyViewModel", "✅ After copy(): callState.callType=${callState.callType}")
                
                // 🔥 CRITICAL FIX: Initialize WebRTC immediately for caller with correct video flag
                Log.d("FamilyViewModel", "🎬 Initializing WebRTC IMMEDIATELY for caller with callType=${callState.callType}")
                try {
                    initializeWebRtcSession(threadId, callId, isCaller = true)
                    Log.d("FamilyViewModel", "✅ Caller WebRTC initialized with Video=${callState.callType == CallType.VIDEO}")
                } catch (e: Exception) {
                    Log.e("FamilyViewModel", "❌ Failed to initialize WebRTC: ${e.message}", e)
                }
                
                observeActiveCall(callId, threadId, isCaller = true)

                repository.sendCallRequest(
                    callId = callId,
                    fromUserId = user.mobile,
                    fromUserName = user.name,
                    toUserId = toUserId,
                    threadId = threadId,
                    callType = if (callState.callType == CallType.VIDEO) "video" else "audio"
                ) { success ->
                    Log.d("FamilyViewModel", "📞 Call request sent: success=$success, callId=$callId")
                    if (success) {
                        // already set above; keep current state
                    } else {
                        Log.e("FamilyViewModel", "❌ Failed to send call request to Firebase")
                        viewModelScope.launch {
                            delay(2000)
                            Log.d("FamilyViewModel", "🔄 Retrying call request...")
                            repository.sendCallRequest(
                                callId = callId,
                                fromUserId = user.mobile,
                                fromUserName = user.name,
                                toUserId = toUserId,
                                threadId = threadId,
                                callType = if (callState.callType == CallType.VIDEO) "video" else "audio"
                            ) { retrySuccess ->
                                Log.d("FamilyViewModel", "📞 Retry call request: success=$retrySuccess")
                                if (retrySuccess) {
                                    // state already in REQUESTING
                                } else {
                                    resetCallState()
                                }
                            }
                        }
                    }
                }
                
                // 🔥 ALSO SEND SIMPLIFIED CALL via Firebase Realtime DB
                // This enables phone-to-phone calling even without the complex repo infrastructure
                Log.d("FamilyViewModel", "🔥 Also initiating simplified call to $toUserId")
                try {
                    com.familyconnect.app.data.repository.CallService.initiateCall(
                        receiverUserId = toUserId,
                        callerId = user.mobile,
                        callerName = user.name,
                        callType = if (callState.callType == CallType.VIDEO) "video" else "audio"
                    )
                    Log.d("FamilyViewModel", "✅ Simplified call initiated")
                } catch (e: Exception) {
                    Log.w("FamilyViewModel", "Could not initiate simplified call: ${e.message}")
                }
            }
        } ?: run {
            Log.e("FamilyViewModel", "❌ Cannot initiate call: currentUser is null")
        }
    }

    fun initiateCallForSelectedThread(callType: CallType = CallType.AUDIO) {
        val current = currentUser ?: return
        val thread = selectedChatThread ?: return

        fun normalizeMobile(value: String): String = value.filter(Char::isDigit)

        val directOther = when {
            thread.participant1Mobile == current.mobile -> thread.participant2Mobile
            thread.participant2Mobile == current.mobile -> thread.participant1Mobile
            else -> ""
        }

        // Fallback to threadId structure (mobile1_mobile2) if participant fields are stale/corrupted.
        val fromThreadId = thread.threadId.split("_")
        val fallbackOther = if (fromThreadId.size == 2) {
            if (fromThreadId[0] == current.mobile) fromThreadId[1] else fromThreadId[0]
        } else {
            ""
        }

        val recipientMobile = normalizeMobile(
            directOther.takeIf { it.any(Char::isDigit) } ?: fallbackOther
        )
        if (recipientMobile.isBlank()) {
            Log.e("FamilyViewModel", "❌ Unable to resolve call recipient mobile for thread=${thread.threadId}")
            return
        }

        val otherName = if (thread.participant1Mobile == current.mobile) {
            thread.participant2Name
        } else {
            thread.participant1Name
        }

        Log.d("FamilyViewModel", "🎬 initiateCallForSelectedThread called with callType=$callType")
        Log.d("FamilyViewModel", "   Setting: callState.callType = $callType")
        callState = callState.copy(callType = callType)
        Log.d("FamilyViewModel", "   After copy: callState.callType=${callState.callType}")
        
        initiateCall(recipientMobile, thread.threadId, otherName.ifBlank { "User" })
    }

    fun initiateVideoCallForSelectedThread() {
        initiateCallForSelectedThread(CallType.VIDEO)
    }

    fun toggleLocalVideo(enabled: Boolean) {
        webRTCManager.toggleLocalVideo(enabled)
        callState = callState.copy(localVideoEnabled = enabled)
    }

    fun switchCamera() {
        webRTCManager.switchCamera()
        callState = callState.copy(isFrontCamera = !callState.isFrontCamera)
    }

    /**
     * Set incoming call state to RINGING when notification is received.
     * This just shows the IncomingCallOverlay - does NOT auto-accept the call.
     * The user must click Accept button to actually accept.
     */
    fun setIncomingCallRinging(callId: String, threadId: String, callerName: String, callType: String = "audio") {
        Log.d("FamilyViewModel", "📞 setIncomingCallRinging: callId=$callId, from=$callerName, type=$callType ⭐")
        
        val incomingCallType = if (callType == "video") CallType.VIDEO else CallType.AUDIO
        
        Log.d("FamilyViewModel", "   📱 Parsed callType: $incomingCallType")
        
        val callRequest = CallRequest(
            callId = callId,
            threadId = threadId,
            fromUserName = callerName,
            status = "pending",
            callType = callType  // ← CRITICAL: Pass the actual callType!
        )
        
        callState = callState.copy(
            status = CallStatus.RINGING,
            incomingCallRequest = callRequest,
            callType = incomingCallType
        )
        
        Log.d("FamilyViewModel", "✅ Call state set to RINGING with callType=$incomingCallType (waiting for Accept/Reject)")
    }

    fun acceptCall(callId: String, threadId: String, fromUserNameOverride: String? = null) {
        val TAG = "FamilyViewModel.acceptCall"
        Log.d(TAG, "📞 START: callId=$callId, threadId=$threadId")
        Log.d(TAG, "   Current callType: ${callState.callType}")
        
        // Step 1: Extract caller name and verify call type
        val fromUserName = fromUserNameOverride 
            ?: callState.incomingCallRequest?.fromUserName 
            ?: "User"
        
        // CRITICAL: Get the call type from incoming request
        val incomingCallType = callState.incomingCallRequest?.let { 
            val type = it.callType  // ← Direct property access (CallRequest object)
            if (type == "video") CallType.VIDEO else CallType.AUDIO
        } ?: callState.callType
        
        Log.d(TAG, "✅ Step 1: fromUserName=$fromUserName")
        Log.d(TAG, "   📱 incomingCallType from request: $incomingCallType")
        
        // Step 2: Update local state immediately - PRESERVE CALL TYPE
        incomingActionInProgress = true
        callState = callState.copy(
            status = CallStatus.CONNECTING,
            incomingCallRequest = null,
            activeCallId = callId,
            activeThreadId = threadId,
            activeCallPartyName = fromUserName,
            isCallConnected = false,
            callDuration = 0,
            callType = incomingCallType  // ← PRESERVE THE INCOMING CALL TYPE!
        )
        Log.d(TAG, "✅ Step 2: Local state updated to CONNECTING with callType=$incomingCallType")
        
        // 🔥 Step 2.5: Cleanup simplified call path in Firebase
        // This removes the call data from calls/{currentUserId}
        viewModelScope.launch {
            try {
                val userMobile = currentUser?.mobile
                if (userMobile != null) {
                    Log.d(TAG, "🧹 Cleaning up simplified call path for $userMobile")
                    com.familyconnect.app.data.repository.CallService.acceptCall(userMobile)
                    Log.d(TAG, "✅ Simplified call cleaned up")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not cleanup simplified call: ${e.message}")
            }
        }
        
        // Step 3: Update Firebase status - CRITICAL PART
        Log.d(TAG, "📡 Step 3: Updating Firebase status to 'accepted'...")
        repository.updateCallStatus(threadId, callId, "accepted") { success ->
            Log.d(TAG, "📡 Firebase callback: success=$success")
            
            incomingActionInProgress = false
            
            if (success) {
                Log.d(TAG, "✅ Step 4: Firebase updated successfully")
                
                // Clear from incoming requests
                _incomingCallRequests.value = incomingCallRequests.value.filter {
                    (it["callId"] as? String ?: "") != callId
                }
                Log.d(TAG, "✅ Step 5: Cleared from incoming requests")
                
                // Step 6: Initialize WebRTC with the correct call type
                Log.d(TAG, "🎬 Step 6: Initializing WebRTC with ${callState.callType}...")
                try {
                    initializeWebRtcSession(threadId, callId, isCaller = false)
                    Log.d(TAG, "✅ Step 6a: WebRTC session initialized with callType=${callState.callType}")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Step 6a: WebRTC init failed: ${e.message}", e)
                }
                
                // Step 7: Observe the active call
                Log.d(TAG, "👁️  Step 7: Observing active call...")
                try {
                    observeActiveCall(callId, threadId, isCaller = false)
                    Log.d(TAG, "✅ Step 7: Now observing active call")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Step 7: observeActiveCall failed: ${e.message}", e)
                }
                
                Log.d(TAG, "✅✅✅ ACCEPT COMPLETED SUCCESSFULLY with callType=${callState.callType}")
                
            } else {
                Log.e(TAG, "❌ Step 4: Firebase update FAILED")
                resetCallState()
            }
        }
        
        Log.d(TAG, "📞 END: Function returned (Firebase callback pending)")
    }

    fun rejectCall(callId: String, threadId: String) {
        incomingActionInProgress = true
        viewModelScope.launch {
            // 🔥 Cleanup simplified call path in Firebase
            try {
                val userMobile = currentUser?.mobile
                if (userMobile != null) {
                    Log.d("FamilyViewModel.rejectCall", "🧹 Cleaning up simplified call path for $userMobile")
                    com.familyconnect.app.data.repository.CallService.rejectCall(userMobile)
                    Log.d("FamilyViewModel.rejectCall", "✅ Simplified call cleaned up")
                }
            } catch (e: Exception) {
                Log.w("FamilyViewModel.rejectCall", "Could not cleanup simplified call: ${e.message}")
            }
            
            repository.updateCallStatus(threadId, callId, "rejected") { success ->
                incomingActionInProgress = false
                if (success) {
                    _incomingCallRequests.value = incomingCallRequests.value.filter {
                        (it["callId"] as? String ?: "") != callId
                    }
                    if (callState.activeCallId == callId) {
                        resetCallState()
                    }
                } else {
                    resetCallState()
                }
            }
        }
    }

    fun endCall(threadId: String? = callState.activeThreadId) {
        val callId = callState.activeCallId
        if (threadId.isNullOrBlank() || callId.isNullOrBlank()) {
            resetCallState()
            return
        }

        viewModelScope.launch {
            repository.updateCallStatus(threadId, callId, "ended") { success ->
                if (success) {
                    resetCallState(ended = true)
                }
            }
        }
    }

    fun updateCallConnection(connected: Boolean) {
        callState = callState.copy(
            isCallConnected = connected,
            status = if (connected) CallStatus.ACTIVE else CallStatus.CONNECTING
        )
    }

    fun toggleLocalAudio(enabled: Boolean) {
        webRTCManager.toggleLocalAudio(enabled)
        callState = callState.copy(localAudioEnabled = enabled)
    }

    fun toggleRemoteAudio(enabled: Boolean) {
        webRTCManager.toggleRemoteAudio(enabled)
        callState = callState.copy(remoteAudioEnabled = enabled)
    }

    private fun observeActiveCall(callId: String, threadId: String, isCaller: Boolean) {
        activeCallObserverJob?.cancel()
        activeCallObserverJob = viewModelScope.launch {
            repository.observeCallById(threadId, callId).collect { callData ->
                val status = callData?.get("status") as? String ?: return@collect
                when (status) {
                    "accepted" -> {
                        if (callState.activeCallId == callId && callState.activeThreadId == threadId) {
                            Log.d("FamilyViewModel", "📱 Call status: ACCEPTED - Remote peer ready for signaling")
                            callState = callState.copy(status = CallStatus.CONNECTING)
                            // ✅ WebRTC already initialized by caller in initiateCall() when button pressed
                            // ✅ signaling observer already running, just continue normally
                        }
                    }
                    "rejected" -> resetCallState(ended = true)
                    "ended" -> resetCallState(ended = true)
                    "pending" -> {
                        if (isCaller && callState.status == CallStatus.IDLE) {
                            callState = callState.copy(
                                status = CallStatus.REQUESTING,
                                activeCallId = callId,
                                activeThreadId = threadId
                            )
                        }
                    }
                }
            }
        }
    }

    private fun initializeWebRtcSession(threadId: String, callId: String, isCaller: Boolean) {
        val selfId = currentUser?.mobile ?: return

        webRTCManager.onLocalOfferCreated = { sdp ->
            repository.sendCallSignaling(
                threadId = threadId,
                callId = callId,
                type = "offer",
                sdp = sdp,
                senderId = selfId
            ) {}
        }

        webRTCManager.onLocalAnswerCreated = { sdp ->
            repository.sendCallSignaling(
                threadId = threadId,
                callId = callId,
                type = "answer",
                sdp = sdp,
                senderId = selfId
            ) {}
        }

        webRTCManager.onIceCandidateGenerated = { candidate, index, mid ->
            repository.sendIceCandidate(
                threadId = threadId,
                callId = callId,
                candidate = candidate,
                sdpMLineIndex = index,
                sdpMid = mid,
                senderId = selfId
            ) {}
        }

        webRTCManager.onConnectionStateChanged = { connected ->
            if (connected) {
                connectionTimeoutJob?.cancel()
                startCallTimer()
                callState = callState.copy(
                    status = CallStatus.ACTIVE,
                    isCallConnected = true
                )
            } else {
                stopCallTimer()
                if (callState.status == CallStatus.ACTIVE || callState.status == CallStatus.CONNECTING) {
                    callState = callState.copy(
                        status = CallStatus.CONNECTING,
                        isCallConnected = false
                    )
                }
            }
        }

        val withVideo = callState.callType == CallType.VIDEO
        webRTCManager.initializePeerConnection(withVideo = withVideo)
        webRTCManager.startAudioCall()
        startObservingSignaling(threadId, callId)

        if (isCaller) {
            webRTCManager.createOffer()
        }

        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = viewModelScope.launch {
            delay(25_000)
            if (!callState.isCallConnected && callState.status == CallStatus.CONNECTING) {
                Log.e("FamilyViewModel", "❌ Call connection timeout")
                resetCallState(ended = true)
            }
        }
    }

    private fun startObservingSignaling(threadId: String, callId: String) {
        signalingObserverJob?.cancel()
        processedSignalingIds.clear()

        signalingObserverJob = viewModelScope.launch {
            val selfId = currentUser?.mobile ?: return@launch
            repository.observeCallSignaling(threadId, callId).collect { packets ->
                packets.sortedBy { (it["timestamp"] as? Long) ?: 0L }.forEach { packet ->
                    val packetId = (packet["id"] as? String).orEmpty().ifBlank {
                        val type = packet["type"] as? String ?: ""
                        val sender = packet["senderId"] as? String ?: ""
                        val ts = packet["timestamp"] as? Long ?: 0L
                        val sdpOrCandidate = (packet["sdp"] as? String).orEmpty() + (packet["candidate"] as? String).orEmpty()
                        "${type}_${sender}_${ts}_${sdpOrCandidate.hashCode()}"
                    }

                    if (!processedSignalingIds.add(packetId)) return@forEach

                    val senderId = packet["senderId"] as? String ?: ""
                    if (senderId == selfId) return@forEach

                    when (packet["type"] as? String ?: "") {
                        "offer" -> {
                            val sdp = packet["sdp"] as? String ?: ""
                            if (sdp.isNotBlank()) {
                                webRTCManager.handleRemoteOffer(sdp)
                            }
                        }
                        "answer" -> {
                            val sdp = packet["sdp"] as? String ?: ""
                            if (sdp.isNotBlank()) {
                                webRTCManager.handleRemoteAnswer(sdp)
                            }
                        }
                        "candidate" -> {
                            val candidate = packet["candidate"] as? String ?: ""
                            val index = packet["sdpMLineIndex"] as? Int ?: -1
                            val mid = packet["sdpMid"] as? String ?: ""
                            webRTCManager.addRemoteIceCandidate(candidate, index, mid)
                        }
                    }
                }
            }
        }
    }

    private fun startCallTimer() {
        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                callState = callState.copy(callDuration = callState.callDuration + 1000)
            }
        }
    }

    private fun stopCallTimer() {
        callTimerJob?.cancel()
    }

    private fun resetCallState(ended: Boolean = false) {
        stopCallTimer()
        activeCallObserverJob?.cancel()
        signalingObserverJob?.cancel()
        signalingObserverJob = null
        connectionTimeoutJob?.cancel()
        processedSignalingIds.clear()
        incomingActionInProgress = false
        webRTCManager.stopAudioCall()
        callState = CallState(
            status = if (ended) CallStatus.ENDED else CallStatus.IDLE,
            callDuration = 0
        )
        if (ended) {
            viewModelScope.launch {
                delay(1200)
                if (callState.status == CallStatus.ENDED) {
                    callState = callState.copy(status = CallStatus.IDLE)
                }
            }
        }
    }

    // User Management Functions
    fun addOrUpdateUser() {
        if (manageUserName.isBlank() || manageUserMobile.isBlank()) {
            userManagementMessage = "Please fill all fields"
            return
        }

        viewModelScope.launch {
            try {
                val cleanMobile = manageUserMobile.filter { it.isDigit() }
                val userName = manageUserName.trim()
                val userRole = manageUserRole
                
                userManagementMessage = "Saving user..."
                Log.d("🔥 FamilyViewModel", "Starting to add/update user: $cleanMobile")
                
                // Add/update to local database first (faster)
                Log.d("🔥 FamilyViewModel", "Saving to local database...")
                val localResult = repository.registerUser(
                    name = userName,
                    mobile = cleanMobile,
                    role = userRole
                )
                
                localResult.onSuccess {
                    Log.d("🔥 FamilyViewModel", "✅ User added to local database: $cleanMobile")
                }.onFailure { error ->
                    Log.e("🔥 FamilyViewModel", "❌ Local DB error: ${error.message}", error)
                    userManagementMessage = "Local DB Error: ${error.message}"
                    return@launch  // Don't continue if local save fails
                }
                
                // Then add to Firebase
                Log.d("🔥 FamilyViewModel", "Saving to Firebase...")
                var firebaseSuccess = false
                var firebaseError: String? = null
                
                UserManagementService.addOrUpdateUser(
                    mobile = cleanMobile,
                    name = userName,
                    role = userRole,
                    onSuccess = {
                        firebaseSuccess = true
                        Log.d("🔥 FamilyViewModel", "✅ User added/updated in Firebase: $cleanMobile")
                    },
                    onError = { error ->
                        firebaseError = error
                        Log.e("🔥 FamilyViewModel", "❌ Firebase error: $error")
                    }
                )
                
                // Wait for Firebase to complete (with longer timeout)
                var attempts = 0
                while (!firebaseSuccess && firebaseError == null && attempts < 50) {
                    delay(100)
                    attempts++
                }
                
                if (firebaseSuccess) {
                    Log.d("🔥 FamilyViewModel", "✅ Firebase save confirmed for: $cleanMobile")
                    userManagementMessage = "✅ User added successfully and synced to Firebase!\nOther devices will now see this member."
                    clearManagementForm()
                } else {
                    val error = firebaseError
                    if (error != null) {
                        Log.e("🔥 FamilyViewModel", "❌ Firebase save failed: $error")
                        userManagementMessage = "❌ FIREBASE ERROR:\n$error\n\n⚠️ User saved LOCALLY only!\n\nTo fix:\n1. Check Firebase has Realtime Database created\n2. Verify security rules are published\n3. Ensure rules have: \".write\": true\n\nWithout Firebase, other devices won't see this user!"
                    } else {
                        Log.w("🔥 FamilyViewModel", "⚠️ Firebase save timeout after 5 seconds")
                        userManagementMessage = "⚠️ FIREBASE TIMEOUT:\nUser saved locally but Firebase didn't respond.\n\nOther devices won't see this user.\n\nPlease check your internet and Firebase configuration."
                        clearManagementForm()
                    }
                }
            } catch (e: Exception) {
                Log.e("🔥 FamilyViewModel", "❌ Error in addOrUpdateUser: ${e.message}", e)
                userManagementMessage = "❌ ERROR: ${e.message}"
            }
        }
    }

    fun deleteUser(userId: String) {
        viewModelScope.launch {
            try {
                UserManagementService.deleteUser(
                    mobile = userId,
                    onSuccess = {
                        userManagementMessage = "User deleted successfully"
                    },
                    onError = { error ->
                        userManagementMessage = "Error: $error"
                    }
                )
            } catch (e: Exception) {
                Log.e("FamilyViewModel", "Error deleting user: ${e.message}", e)
                userManagementMessage = "Error: ${e.message}"
            }
        }
    }

    fun startEditingUser(user: AllowedUser) {
        editingUserId = user.mobile  // Use mobile as identifier
        manageUserName = user.name
        manageUserMobile = user.mobile
        manageUserRole = FamilyRole.valueOf(user.role)
    }

    fun clearManagementForm() {
        manageUserName = ""
        manageUserMobile = ""
        manageUserRole = FamilyRole.CHILD
        editingUserId = null
        userManagementMessage = null
    }

    override fun onCleared() {
        super.onCleared()
        stopCallTimer()
        activeCallObserverJob?.cancel()
        signalingObserverJob?.cancel()
        connectionTimeoutJob?.cancel()
        incomingCallsJob?.cancel()
        webRTCManager.dispose()
    }
}
