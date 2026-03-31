package com.familyconnect.app.ui

import android.content.Context
import android.location.Geocoder
import android.net.Uri
import android.util.Log
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
import com.familyconnect.app.data.model.TaskItem
import com.familyconnect.app.data.model.UserProfile
import com.familyconnect.app.data.repository.FamilyRepository
import com.familyconnect.app.notifications.NotificationHelper
import com.familyconnect.app.webrtc.CallRequest
import com.familyconnect.app.webrtc.CallStatus
import com.familyconnect.app.webrtc.CallState
import com.familyconnect.app.webrtc.CallType
import com.familyconnect.app.webrtc.WebRTCManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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

    init {
        viewModelScope.launch {
            repository.seedDefaultUsers()
        }
        // Auto-restore session from DataStore
        viewModelScope.launch {
            repository.loggedInMobileFlow.first().let { savedMobile ->
                if (!savedMobile.isNullOrBlank()) {
                    val result = repository.loginByMobile(savedMobile)
                    if (result != null) {
                        currentUser = result
                        repository.setUserOnline(result.mobile, result.name)
                        startObservingIncomingCalls()
                        refreshLocation()
                        repository.observeUserChatThreads(result.mobile).collect { threads ->
                            _userChatThreadsFlow.value = threads
                        }
                    }
                }
            }
        }
    }

    fun login() {
        viewModelScope.launch {
            val result = repository.loginByMobile(loginMobile)
            if (result != null) {
                currentUser = result
                loginError = null
                repository.saveLoggedInMobile(result.mobile)
                // Set user as online in Firebase
                repository.setUserOnline(result.mobile, result.name)
                startObservingIncomingCalls()
                
                // Start observing chat threads for this user
                repository.observeUserChatThreads(result.mobile).collect { threads ->
                    _userChatThreadsFlow.value = threads
                }
            } else {
                loginError = "This mobile number is not allowed to use the app"
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
        viewModelScope.launch {
            repository.clearLoggedInMobile()
        }
        currentUser = null
        selectedChatThread = null
        _userChatThreadsFlow.value = emptyList()
        _currentThreadMessagesFlow.value = emptyList()
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

    fun clearSelectedThread() {
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
        return userChatThreads.value.find { thread ->
            (thread.participant1Mobile == current.mobile && thread.participant2Mobile == userMobile) ||
            (thread.participant2Mobile == current.mobile && thread.participant1Mobile == userMobile)
        }?.unreadCount ?: 0
    }

    fun addEvent(title: String, dateTime: String, colorTag: String, recurring: Boolean, reminderMinutes: Int) {
        viewModelScope.launch {
            val id = (events.value.maxOfOrNull { it.id } ?: 0) + 1
            repository.addEvent(
                FamilyEvent(
                    id = id,
                    title = title,
                    dateTime = dateTime,
                    colorTag = colorTag,
                    recurring = recurring,
                    reminderMinutes = reminderMinutes,
                    createdAtEpochMillis = System.currentTimeMillis()
                )
            )
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
                            val incomingCallType = if ((incoming["callType"] as? String) == "video") CallType.VIDEO else CallType.AUDIO
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
                )
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

        callState = callState.copy(callType = callType)
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

    fun acceptCall(callId: String, threadId: String, fromUserNameOverride: String? = null) {
        currentUser?.let {
            viewModelScope.launch {
                val fromUserName =
                    fromUserNameOverride
                        ?: callState.incomingCallRequest?.fromUserName
                        ?: incomingCallRequests.value.firstOrNull {
                            (it["callId"] as? String ?: "") == callId
                        }?.get("fromUserName") as? String
                        ?: "User"

                incomingActionInProgress = true
                callState = callState.copy(
                    status = CallStatus.CONNECTING,
                    incomingCallRequest = null,
                    activeCallId = callId,
                    activeThreadId = threadId,
                    activeCallPartyName = fromUserName,
                    isCallConnected = false,
                    callDuration = 0
                )

                repository.updateCallStatus(threadId, callId, "accepted") { success ->
                    incomingActionInProgress = false
                    if (success) {
                        _incomingCallRequests.value = incomingCallRequests.value.filter {
                            (it["callId"] as? String ?: "") != callId
                        }
                        initializeWebRtcSession(threadId, callId, isCaller = false)
                        observeActiveCall(callId, threadId, isCaller = false)
                    } else {
                        resetCallState()
                    }
                }
            }
        }
    }

    fun rejectCall(callId: String, threadId: String) {
        incomingActionInProgress = true
        viewModelScope.launch {
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
                            callState = callState.copy(status = CallStatus.CONNECTING)
                            if (isCaller && signalingObserverJob == null) {
                                initializeWebRtcSession(threadId, callId, isCaller = true)
                            }
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
