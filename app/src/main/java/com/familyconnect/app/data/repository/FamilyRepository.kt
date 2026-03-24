package com.familyconnect.app.data.repository

import android.content.Context
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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
            userDao.insert(UserEntity(name = "Family Admin", mobile = "9999999999", role = FamilyRole.ADMIN.name))
            userDao.insert(UserEntity(name = "Mom", mobile = "8888888888", role = FamilyRole.PARENT.name))
            userDao.insert(UserEntity(name = "Kid", mobile = "7777777777", role = FamilyRole.CHILD.name))
        }
    }

    suspend fun loginByMobile(mobile: String): UserProfile? {
        val user = userDao.getByMobile(mobile.trim()) ?: return null
        return UserProfile(
            id = user.id,
            name = user.name,
            mobile = user.mobile,
            role = runCatching { FamilyRole.valueOf(user.role) }.getOrDefault(FamilyRole.CHILD)
        )
    }

    suspend fun registerUser(name: String, mobile: String, role: FamilyRole): Result<Unit> {
        val cleanMobile = mobile.trim()
        if (name.trim().isEmpty()) {
            return Result.failure(IllegalArgumentException("Name is required"))
        }
        if (cleanMobile.length < 10) {
            return Result.failure(IllegalArgumentException("Mobile number must be at least 10 digits"))
        }
        if (userDao.getByMobile(cleanMobile) != null) {
            return Result.failure(IllegalArgumentException("Mobile number already exists"))
        }
        userDao.insert(UserEntity(name = name.trim(), mobile = cleanMobile, role = role.name))
        return Result.success(Unit)
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

    companion object {
        const val DEFAULT_ADMIN_SETUP_PIN = "2468"
        val ADMIN_SETUP_PIN_KEY = stringPreferencesKey("admin_setup_pin")
        val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
        val LARGE_TEXT_KEY = booleanPreferencesKey("large_text")
        val LANGUAGE_KEY = stringPreferencesKey("language")
    }
}
