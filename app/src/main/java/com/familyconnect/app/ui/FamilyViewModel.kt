package com.familyconnect.app.ui

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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class FamilyViewModel(
    private val repository: FamilyRepository
) : ViewModel() {
    private val eventExpiryWindowMillis = TimeUnit.DAYS.toMillis(3)

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

    val userChatThreads = run {
        val mobile = currentUser?.mobile
        if (mobile != null) {
            repository.observeUserChatThreads(mobile).stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList<ChatThread>()
            )
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList<ChatThread>()).stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList<ChatThread>()
            )
        }
    }

    val currentThreadMessages = run {
        val id = selectedChatThread?.threadId
        if (id != null) {
            repository.observeThreadMessages(id).stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList<ChatMessageData>()
            )
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList<ChatMessageData>()).stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList<ChatMessageData>()
            )
        }
    }

    init {
        viewModelScope.launch {
            repository.seedDefaultUsers()
        }
    }

    fun login() {
        viewModelScope.launch {
            val result = repository.loginByMobile(loginMobile)
            if (result != null) {
                currentUser = result
                loginError = null
                // Set user as online in Firebase
                repository.setUserOnline(result.mobile, result.name)
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
        currentUser = null
        selectedChatThread = null
    }

    // Chat feature methods
    fun selectChatThread(thread: ChatThread) {
        selectedChatThread = thread
        currentUser?.let {
            repository.markMessagesAsRead(thread.threadId, it.mobile)
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
                // Find and select the thread
                viewModelScope.launch {
                    val thread = userChatThreads.value.find { it.threadId == threadId }
                    if (thread != null) {
                        selectChatThread(thread)
                    }
                }
            }
        }
    }

    fun sendChatMessageToThread(body: String, mediaUri: String? = null) {
        val current = currentUser ?: return
        val thread = selectedChatThread ?: return

        if (body.isBlank()) return

        repository.sendChatMessage(
            threadId = thread.threadId,
            senderMobile = current.mobile,
            senderName = current.name,
            body = body,
            mediaUri = mediaUri
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
}
