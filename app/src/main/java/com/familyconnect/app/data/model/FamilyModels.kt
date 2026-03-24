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
