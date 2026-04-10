package com.familyconnect.app.data.model

data class UserProfile(
    val id: Long,
    val name: String,
    val mobile: String,
    val role: FamilyRole
)

data class FamilyEvent(
    val id: Int,
    val title: String,
    val description: String = "",
    val location: String = "",
    val dateTime: Long = 0,  // epoch millis
    val colorTag: String = "Blue",
    val category: String = "Other",  // Birthday, Anniversary, Grocery, Medicine, Doctor, Other
    val recurring: Boolean = false,
    val reminderMinutes: Int = 1440,  // default 1 day before
    val invitedMembers: List<String> = emptyList(),  // mobile numbers
    val createdBy: String = "",
    val createdAtEpochMillis: Long = 0
)

data class TaskItem(
    val id: Int,
    val title: String,
    val assignedTo: String,
    val dueDate: String,
    val completed: Boolean,
    val rewardPoints: Int?
)

data class ChatMessage(
    val id: Int,
    val sender: String,
    val target: String,
    val body: String,
    val mediaUri: String?,
    val read: Boolean,
    val timestamp: String
)

// Chat feature models for Firebase
data class ChatThread(
    val threadId: String = "",
    val participant1Mobile: String = "",
    val participant2Mobile: String = "",
    val participant1Name: String = "",
    val participant2Name: String = "",
    val lastMessage: String = "",
    val lastMessageTime: Long = 0,
    val createdAt: Long = 0,
    val unreadCount: Int = 0   // unread messages for the current observer
)

data class ChatMessageData(
    val messageId: String = "",
    val senderMobile: String = "",
    val senderName: String = "",
    val body: String = "",
    val timestamp: Long = 0,
    val read: Boolean = false,
    val mediaUri: String? = null,
    val replyToMessageId: String? = null,
    val replyToSenderName: String? = null,
    val replyToBody: String? = null,
    val senderLocation: String? = null
)

// 📝 Typing indicator data model
data class TypingStatus(
    val userMobile: String = "",
    val userName: String = "",
    val timestamp: Long = 0,
    val isTyping: Boolean = true
)

data class OnlineUser(
    val mobile: String = "",
    val name: String = "",
    val lastSeen: Long = 0,
    val isOnline: Boolean = false
)

data class MediaItem(
    val id: Int,
    val title: String,
    val mediaType: String,
    val uri: String,
    val uploadedBy: String,
    val timestamp: String
)

data class NoteItem(
    val id: Int,
    val title: String,
    val content: String,
    val editedBy: String,
    val editedAt: String
)

// 📌 Reminder data model
data class ReminderItem(
    val id: String = "",
    val title: String = "",
    val details: String = "",
    val createdBy: String = "",
    val createdAtEpochMillis: Long = 0,
    val assignedMembers: List<String> = emptyList(),  // mobile numbers
    val snoozeOptions: List<Int> = listOf(5, 15, 30, 60, 1440),  // minutes: 5, 15, 30, 1hr, 1 day
    val lastSnoozedUntil: Long? = null,  // null means not snoozed
    val nextNotificationTime: Long? = null,  // when to show next notification
    val completed: Boolean = false
)
