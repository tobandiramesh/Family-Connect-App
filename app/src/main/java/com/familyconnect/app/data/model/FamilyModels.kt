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
    val dateTime: String,
    val colorTag: String,
    val recurring: Boolean,
    val reminderMinutes: Int,
    val createdAtEpochMillis: Long
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
    val replyToBody: String? = null
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
