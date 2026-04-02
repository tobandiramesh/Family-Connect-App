package com.familyconnect.app.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.familyconnect.app.data.local.UserDao
import com.familyconnect.app.data.local.UserEntity
import com.familyconnect.app.data.model.ChatMessage
import com.familyconnect.app.data.model.FamilyEvent
import com.familyconnect.app.data.model.FamilyRole
import com.familyconnect.app.data.model.MediaItem
import com.familyconnect.app.data.model.NoteItem
import com.familyconnect.app.data.model.TaskItem
import com.familyconnect.app.data.model.UserProfile
import com.familyconnect.app.notifications.NotificationHelper
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

private val Context.dataStore by preferencesDataStore(name = "family_settings")

class FamilyRepository(
    private val context: Context,
    private val userDao: UserDao
) {
    private val eventExpiryWindowMillis = TimeUnit.DAYS.toMillis(3)

    private val events = MutableStateFlow(
        listOf(
            FamilyEvent(1, "Parent Meeting", "2026-03-28 18:00", "Blue", recurring = false, reminderMinutes = 30, createdAtEpochMillis = System.currentTimeMillis()),
            FamilyEvent(2, "Sunday Dinner", "2026-03-29 20:00", "Green", recurring = true, reminderMinutes = 60, createdAtEpochMillis = System.currentTimeMillis())
        )
    )

    private val tasks = MutableStateFlow(
        listOf(
            TaskItem(1, "Clean study room", "CHILD", "2026-03-26", completed = false, rewardPoints = 20),
            TaskItem(2, "Pay electricity bill", "PARENT", "2026-03-25", completed = false, rewardPoints = null)
        )
    )

    private val messages = MutableStateFlow(
        listOf(
            ChatMessage(1, "Admin", "Group", "Welcome to Family Connect!", null, read = true, timestamp = "08:30"),
            ChatMessage(2, "Parent", "Child", "Please complete your homework.", null, read = false, timestamp = "09:15")
        )
    )

    private val media = MutableStateFlow(
        listOf(
            MediaItem(1, "Vacation Pic", "Photo", "local://sample/photo1", "Parent", "2026-03-21"),
            MediaItem(2, "Birthday Clip", "Video", "local://sample/video1", "Admin", "2026-03-22")
        )
    )

    private val notes = MutableStateFlow(
        listOf(
            NoteItem(1, "Grocery", "Milk, Eggs, Fruit", "Parent", "2026-03-23 10:00"),
            NoteItem(2, "Weekend Plan", "Park at 5 PM", "Child", "2026-03-23 10:30")
        )
    )

    val darkModeFlow: Flow<Boolean> = context.dataStore.data.map { it[DARK_MODE_KEY] ?: false }
    val languageFlow: Flow<String> = context.dataStore.data.map { it[LANGUAGE_KEY] ?: "en" }
    val accessibilityLargeTextFlow: Flow<Boolean> =
        context.dataStore.data.map { it[LARGE_TEXT_KEY] ?: false }
    val adminSetupPinFlow: Flow<String> =
        context.dataStore.data.map { it[ADMIN_SETUP_PIN_KEY] ?: DEFAULT_ADMIN_SETUP_PIN }

    val loggedInMobileFlow: Flow<String?> =
        context.dataStore.data.map { it[LOGGED_IN_MOBILE_KEY] }

    suspend fun saveLoggedInMobile(mobile: String) {
        context.dataStore.edit { it[LOGGED_IN_MOBILE_KEY] = mobile }
    }

    suspend fun clearLoggedInMobile() {
        context.dataStore.edit { it.remove(LOGGED_IN_MOBILE_KEY) }
    }

    val usersFlow: Flow<List<UserProfile>> = userDao.observeUsers().map { list ->
        list.map {
            UserProfile(
                id = it.id,
                name = it.name,
                mobile = it.mobile,
                role = runCatching { FamilyRole.valueOf(it.role) }.getOrDefault(FamilyRole.CHILD)
            )
        }
    }

    fun observeEvents(): Flow<List<FamilyEvent>> = events.map { list ->
        val now = System.currentTimeMillis()
        list.filter { event ->
            now <= event.createdAtEpochMillis + eventExpiryWindowMillis
        }.sortedByDescending { it.createdAtEpochMillis }
    }
    fun observeTasks(): Flow<List<TaskItem>> = tasks
    fun observeMessages(): Flow<List<ChatMessage>> = messages
    fun observeMedia(): Flow<List<MediaItem>> = media
    fun observeNotes(): Flow<List<NoteItem>> = notes

    suspend fun seedDefaultUsers() {
        if (userDao.countUsers() == 0) {
            Log.d("🔥 FamilyRepository", "📊 No users found, seeding default users...")
            
            // Add default users to local database
            try {
                userDao.insert(UserEntity(name = "Family Admin", mobile = "9999999999", role = FamilyRole.ADMIN.name))
                userDao.insert(UserEntity(name = "Mom", mobile = "8888888888", role = FamilyRole.PARENT.name))
                userDao.insert(UserEntity(name = "Kid", mobile = "7777777777", role = FamilyRole.CHILD.name))
                Log.d("🔥 FamilyRepository", "✅ Seeded 3 default users to local database")
            } catch (e: Exception) {
                Log.e("🔥 FamilyRepository", "❌ Error seeding local users: ${e.message}", e)
                return
            }
            
            // Also add default users to Firebase so they sync across devices
            try {
                Log.d("🔥 FamilyRepository", "🔥 Adding default users to Firebase...")
                val db = FirebaseDatabase.getInstance("https://family-connect-app-a219b-default-rtdb.asia-southeast1.firebasedatabase.app")
                val defaultUsers = listOf(
                    Triple("9999999999", "Family Admin", FamilyRole.ADMIN.name),
                    Triple("8888888888", "Mom", FamilyRole.PARENT.name),
                    Triple("7777777777", "Kid", FamilyRole.CHILD.name)
                )
                
                for ((mobile, name, role) in defaultUsers) {
                    try {
                        db.getReference("allowedUsers/$mobile").setValue(mapOf(
                            "name" to name,
                            "role" to role
                        )).await()
                        Log.d("🔥 FamilyRepository", "✅ Seeded user to Firebase: $mobile ($name)")
                    } catch (e: Exception) {
                        Log.e("🔥 FamilyRepository", "❌ Error seeding Firebase user $mobile: ${e.message}")
                    }
                }
                Log.d("🔥 FamilyRepository", "✅ Finished seeding Firebase users")
            } catch (e: Exception) {
                Log.e("🔥 FamilyRepository", "❌ Error in Firebase seeding: ${e.message}", e)
            }
        } else {
            Log.d("🔥 FamilyRepository", "ℹ️ Database already has users, skipping seed")
        }
    }

    suspend fun loginByMobile(mobile: String): UserProfile? {
        val cleanMobile = mobile.trim()
        Log.d("🔥 FamilyRepository", "🔐 LOGIN: Checking for mobile: $cleanMobile")
        
        // First try local database
        var user = userDao.getByMobile(cleanMobile)
        Log.d("🔥 FamilyRepository", "📱 Local DB check: ${if (user != null) "✅ Found: ${user.name}" else "❌ Not found"}")
        
        // If found locally, ensure they're also in Firebase allowedUsers (non-blocking, fire-and-forget)
        if (user != null) {
            try {
                Log.d("🔥 FamilyRepository", "🔥 Syncing local user to Firebase allowedUsers: $cleanMobile")
                FirebaseDatabase.getInstance()
                    .getReference("allowedUsers/$cleanMobile")
                    .setValue(mapOf(
                        "name" to user.name,
                        "role" to user.role
                    ))
                    .addOnSuccessListener {
                        Log.d("🔥 FamilyRepository", "✅ User synced to Firebase allowedUsers: $cleanMobile")
                    }
                    .addOnFailureListener { e ->
                        // Don't log permission errors as they're expected in test mode
                        if (e.message?.contains("Permission", ignoreCase = true) == true) {
                            Log.d("🔥 FamilyRepository", "⚠️ Firebase permission denied (expected in test mode) - local DB is sufficient")
                        } else {
                            Log.w("🔥 FamilyRepository", "⚠️ Failed to sync to Firebase: ${e.message}")
                        }
                    }
            } catch (e: Exception) {
                Log.e("🔥 FamilyRepository", "❌ Error syncing to Firebase: ${e.message}")
            }
        }
        
        // If not found locally, check Firebase allowed users
        if (user == null) {
            try {
                Log.d("🔥 FamilyRepository", "🔥 Firebase fallback: Checking Firebase for $cleanMobile...")
                
                val allowedUsersSnapshot = FirebaseDatabase.getInstance("https://family-connect-app-a219b-default-rtdb.asia-southeast1.firebasedatabase.app")
                    .getReference("allowedUsers")
                    .get()
                    .await()
                
                Log.d("🔥 FamilyRepository", "📱 Firebase snapshot: exists=${allowedUsersSnapshot.exists()}")
                
                if (allowedUsersSnapshot.exists()) {
                    val firebaseUser = allowedUsersSnapshot.children.find { snapshot -> 
                        snapshot.key == cleanMobile 
                    }
                    
                    if (firebaseUser != null) {
                        val name = firebaseUser.child("name").value as? String ?: "Family Member"
                        val role = firebaseUser.child("role").value as? String ?: FamilyRole.CHILD.name
                        
                        Log.d("🔥 FamilyRepository", "✅ Found in Firebase: $name (role: $role)")
                        
                        try {
                            // Auto-add to local database for faster future logins
                            userDao.insert(UserEntity(
                                name = name,
                                mobile = cleanMobile,
                                role = role
                            ))
                            
                            // Fetch from local database to get the auto-generated ID
                            user = userDao.getByMobile(cleanMobile)
                            Log.d("🔥 FamilyRepository", "✅ User synced from Firebase to local DB: $cleanMobile")
                        } catch (e: Exception) {
                            Log.e("🔥 FamilyRepository", "❌ Error adding user to local DB: ${e.message}")
                        }
                    } else {
                        Log.d("🔥 FamilyRepository", "❌ User not found in Firebase: $cleanMobile")
                    }
                } else {
                    Log.d("🔥 FamilyRepository", "⚠️ No data at allowedUsers path in Firebase")
                }
            } catch (e: Exception) {
                Log.e("🔥 FamilyRepository", "❌ Firebase error: ${e.message}", e)
            }
        }
        
        return user?.let {
            UserProfile(
                id = it.id,
                name = it.name,
                mobile = it.mobile,
                role = runCatching { FamilyRole.valueOf(it.role) }.getOrDefault(FamilyRole.CHILD)
            )
        }
    }

    /**
     * Sync all allowed users from Firebase to local database
     * This ensures that when the app starts, it has the latest family members from Firebase
     */
    suspend fun syncFirebaseUsersToLocal() {
        try {
            Log.d("🔥 FamilyRepository", "🔄 Starting Firebase to local sync...")
            
            val allowedUsersSnapshot = FirebaseDatabase.getInstance("https://family-connect-app-a219b-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference("allowedUsers")
                .get()
                .await()
            
            Log.d("🔥 FamilyRepository", "📱 Firebase snapshot retrieved, exists=${allowedUsersSnapshot.exists()}")
            
            if (allowedUsersSnapshot.exists()) {
                Log.d("🔥 FamilyRepository", "📝 Firebase snapshot exists, syncing users...")
                
                var syncCount = 0
                for (snapshot in allowedUsersSnapshot.children) {
                    val mobile = snapshot.key ?: continue
                    val name = snapshot.child("name").value as? String ?: "Family Member"
                    val role = snapshot.child("role").value as? String ?: FamilyRole.CHILD.name
                    
                    try {
                        // Always insert with REPLACE strategy - no need to check if exists
                        userDao.insert(UserEntity(
                            name = name,
                            mobile = mobile,
                            role = role
                        ))
                        syncCount++
                        Log.d("🔥 FamilyRepository", "✅ Synced user from Firebase: $mobile ($name)")
                    } catch (e: Exception) {
                        Log.e("🔥 FamilyRepository", "❌ Error syncing user $mobile: ${e.message}")
                    }
                }
                Log.d("🔥 FamilyRepository", "✅ Firebase sync completed - synced $syncCount users")
            } else {
                Log.d("🔥 FamilyRepository", "⚠️ No allowed users found in Firebase at path 'allowedUsers'")
            }
        } catch (e: Exception) {
            Log.e("🔥 FamilyRepository", "❌ Error syncing Firebase users: ${e.message}", e)
        }
    }

    suspend fun registerUser(name: String, mobile: String, role: FamilyRole): Result<Unit> {
        val cleanMobile = mobile.trim()
        if (name.trim().isEmpty()) {
            return Result.failure(IllegalArgumentException("Name is required"))
        }
        if (cleanMobile.length < 10) {
            return Result.failure(IllegalArgumentException("Mobile number must be at least 10 digits"))
        }
        try {
            // Insert or replace if already exists
            userDao.insert(UserEntity(name = name.trim(), mobile = cleanMobile, role = role.name))
            Log.d("FamilyRepository", "User registered/updated: $cleanMobile")
            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FamilyRepository", "Error registering user: ${e.message}", e)
            return Result.failure(e)
        }
    }

    suspend fun addEvent(event: FamilyEvent) {
        events.value = events.value + event
        NotificationHelper.post(context, event.id + 1000, "Event added", event.title)
    }

    suspend fun addTask(task: TaskItem) {
        tasks.value = tasks.value + task
        NotificationHelper.post(context, task.id + 2000, "Task assigned", task.title)
    }

    suspend fun toggleTask(taskId: Int) {
        tasks.value = tasks.value.map { task ->
            if (task.id == taskId) task.copy(completed = !task.completed) else task
        }
    }

    suspend fun sendMessage(message: ChatMessage) {
        messages.value = messages.value + message
        NotificationHelper.post(context, message.id + 3000, "New message", "From ${message.sender}")
    }

    suspend fun markMessageRead(messageId: Int) {
        messages.value = messages.value.map {
            if (it.id == messageId) it.copy(read = true) else it
        }
    }

    suspend fun addMedia(item: MediaItem) {
        media.value = media.value + item
    }

    suspend fun addNote(note: NoteItem) {
        notes.value = notes.value + note
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[DARK_MODE_KEY] = enabled }
    }

    suspend fun setLanguage(language: String) {
        context.dataStore.edit { it[LANGUAGE_KEY] = language }
    }

    suspend fun setLargeText(enabled: Boolean) {
        context.dataStore.edit { it[LARGE_TEXT_KEY] = enabled }
    }

    suspend fun setAdminSetupPin(pin: String) {
        context.dataStore.edit { it[ADMIN_SETUP_PIN_KEY] = pin.trim() }
    }

    // Chat feature methods
    fun observeOnlineUsers() = FirebaseService.observeOnlineUsers()

    fun observeUserChatThreads(userMobile: String) = FirebaseService.observeUserChatThreads(userMobile)

    fun observeThreadMessages(threadId: String) = FirebaseService.observeThreadMessages(threadId)

    fun setUserOnline(mobile: String, name: String) {
        FirebaseService.setUserOnline(mobile, name)
    }

    fun setUserOffline(mobile: String, name: String) {
        FirebaseService.setUserOffline(mobile, name)
    }

    fun createOrGetChatThread(
        currentUserMobile: String,
        currentUserName: String,
        otherUserMobile: String,
        otherUserName: String,
        onResult: (String?) -> Unit
    ) {
        FirebaseService.createOrGetChatThread(
            currentUserMobile,
            currentUserName,
            otherUserMobile,
            otherUserName,
            onResult
        )
    }

    fun sendChatMessage(
        threadId: String,
        senderMobile: String,
        senderName: String,
        body: String,
        mediaUri: String? = null,
        replyToMessageId: String? = null,
        replyToSenderName: String? = null,
        replyToBody: String? = null,
        recipientMobile: String? = null,
        senderLocation: String? = null,
        onResult: (Boolean) -> Unit
    ) {
        FirebaseService.sendMessage(
            threadId = threadId,
            senderMobile = senderMobile,
            senderName = senderName,
            body = body,
            mediaUri = mediaUri,
            replyToMessageId = replyToMessageId,
            replyToSenderName = replyToSenderName,
            replyToBody = replyToBody,
            recipientMobile = recipientMobile,
            senderLocation = senderLocation,
            onResult = onResult
        )
    }

    fun markMessagesAsRead(threadId: String, senderMobile: String) {
        FirebaseService.markMessagesAsRead(threadId, senderMobile)
    }

    fun formatTime(timestamp: Long): String = FirebaseService.formatTime(timestamp)
    fun formatDate(timestamp: Long): String = FirebaseService.formatDate(timestamp)
    fun formatLastSeen(timestamp: Long): String = FirebaseService.formatLastSeen(timestamp)

    // ============ AUDIO CALL METHODS ============
    
    fun sendCallRequest(
        callId: String,
        fromUserId: String,
        fromUserName: String,
        toUserId: String,
        threadId: String,
        callType: String = "audio",
        onResult: (Boolean) -> Unit
    ) {
        FirebaseService.sendCallRequest(
            callId, fromUserId, fromUserName, toUserId, threadId, callType, onResult
        )
    }

    fun observeCallRequests(userId: String) = FirebaseService.observeCallRequests(userId)

    fun updateCallStatus(
        threadId: String,
        callId: String,
        status: String,
        onResult: (Boolean) -> Unit
    ) {
        FirebaseService.updateCallStatus(threadId, callId, status, onResult)
    }

    fun sendCallSignaling(
        threadId: String,
        callId: String,
        type: String,
        sdp: String,
        senderId: String,
        onResult: (Boolean) -> Unit
    ) {
        FirebaseService.sendCallSignaling(threadId, callId, type, sdp, senderId, onResult)
    }

    fun observeCallSignaling(threadId: String, callId: String) =
        FirebaseService.observeCallSignaling(threadId, callId)

    fun observeCallById(threadId: String, callId: String) =
        FirebaseService.observeCallById(threadId, callId)

    fun sendIceCandidate(
        threadId: String,
        callId: String,
        candidate: String,
        sdpMLineIndex: Int,
        sdpMid: String,
        senderId: String,
        onResult: (Boolean) -> Unit
    ) {
        FirebaseService.sendIceCandidate(
            threadId, callId, candidate, sdpMLineIndex, sdpMid, senderId, onResult
        )
    }

    companion object {
        const val DEFAULT_ADMIN_SETUP_PIN = "2468"
        val ADMIN_SETUP_PIN_KEY = stringPreferencesKey("admin_setup_pin")
        val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
        val LARGE_TEXT_KEY = booleanPreferencesKey("large_text")
        val LANGUAGE_KEY = stringPreferencesKey("language")
        val LOGGED_IN_MOBILE_KEY = stringPreferencesKey("logged_in_mobile")
    }
}
