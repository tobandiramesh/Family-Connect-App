package com.familyconnect.app.data.repository

import android.util.Log
import com.familyconnect.app.data.model.ChatMessageData
import com.familyconnect.app.data.model.ChatThread
import com.familyconnect.app.data.model.OnlineUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object FirebaseService {
    private const val TAG = "FirebaseService"
    private val database = FirebaseDatabase.getInstance()

    // Reference paths
    private const val PRESENCE_PATH = "presence"
    private const val CHATS_PATH = "chats"
    private const val MESSAGES_PATH = "messages"

    fun setUserOnline(mobile: String, userName: String) {
        try {
            val presenceRef = database.getReference("$PRESENCE_PATH/$mobile")
            presenceRef.child("name").setValue(userName)
            presenceRef.child("isOnline").setValue(true)
            presenceRef.child("lastSeen").setValue(System.currentTimeMillis())
            Log.d(TAG, "User $mobile set online")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting user online: ${e.message}")
        }
    }

    fun setUserOffline(mobile: String, userName: String) {
        try {
            val presenceRef = database.getReference("$PRESENCE_PATH/$mobile")
            presenceRef.child("isOnline").setValue(false)
            presenceRef.child("lastSeen").setValue(System.currentTimeMillis())
            Log.d(TAG, "User $mobile set offline")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting user offline: ${e.message}")
        }
    }

    fun observeOnlineUsers(): Flow<List<OnlineUser>> = callbackFlow {
        try {
            val presenceRef = database.getReference(PRESENCE_PATH)
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val users = mutableListOf<OnlineUser>()
                        for (userSnapshot in snapshot.children) {
                            val mobile = userSnapshot.key ?: continue
                            val name = userSnapshot.child("name").value as? String ?: "Unknown"
                            val isOnline = userSnapshot.child("isOnline").value as? Boolean ?: false
                            val lastSeen = userSnapshot.child("lastSeen").value as? Long ?: 0L
                            
                            if (isOnline && mobile != "9999999999") { // Exclude admin
                                users.add(OnlineUser(mobile, name, lastSeen, isOnline))
                            }
                        }
                        trySend(users)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing online users: ${e.message}")
                        trySend(emptyList())
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error observing online users: ${error.message}")
                    trySend(emptyList())
                }
            }
            presenceRef.addValueEventListener(listener)
            awaitClose { presenceRef.removeEventListener(listener) }
        } catch (e: Exception) {
            Log.e(TAG, "Error in observeOnlineUsers: ${e.message}")
            close(e)
        }
    }

    private fun getThreadId(mobile1: String, mobile2: String): String {
        return if (mobile1 < mobile2) {
            "${mobile1}_${mobile2}"
        } else {
            "${mobile2}_${mobile1}"
        }
    }

    fun createOrGetChatThread(
        currentUserMobile: String,
        currentUserName: String,
        otherUserMobile: String,
        otherUserName: String,
        onResult: (String?) -> Unit
    ) {
        try {
            val threadId = getThreadId(currentUserMobile, otherUserMobile)
            val threadRef = database.getReference("$CHATS_PATH/$threadId")
            
            threadRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        val threadData = mapOf(
                            "threadId" to threadId,
                            "participant1Mobile" to currentUserMobile,
                            "participant1Name" to currentUserName,
                            "participant2Mobile" to otherUserMobile,
                            "participant2Name" to otherUserName,
                            "lastMessage" to "",
                            "lastMessageTime" to System.currentTimeMillis(),
                            "createdAt" to System.currentTimeMillis()
                        )
                        threadRef.setValue(threadData) { error, _ ->
                            if (error != null) {
                                Log.e(TAG, "Error creating thread: ${error.message}")
                                onResult(null)
                            } else {
                                Log.d(TAG, "Thread created: $threadId")
                                onResult(threadId)
                            }
                        }
                    } else {
                        Log.d(TAG, "Thread exists: $threadId")
                        onResult(threadId)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error creating/getting thread: ${error.message}")
                    onResult(null)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error in createOrGetChatThread: ${e.message}")
            onResult(null)
        }
    }

    fun sendMessage(
        threadId: String,
        senderMobile: String,
        senderName: String,
        body: String,
        mediaUri: String? = null,
        onResult: (Boolean) -> Unit
    ) {
        try {
            val messageId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            
            val messageData = mapOf(
                "messageId" to messageId,
                "senderMobile" to senderMobile,
                "senderName" to senderName,
                "body" to body,
                "timestamp" to timestamp,
                "read" to false,
                "mediaUri" to (mediaUri ?: "")
            )
            
            val messagesRef = database.getReference("$MESSAGES_PATH/$threadId/$messageId")
            messagesRef.setValue(messageData) { error, _ ->
                if (error != null) {
                    Log.e(TAG, "Error sending message: ${error.message}")
                    onResult(false)
                } else {
                    // Update thread metadata
                    database.getReference("$CHATS_PATH/$threadId").apply {
                        child("lastMessage").setValue(body)
                        child("lastMessageTime").setValue(timestamp)
                    }
                    Log.d(TAG, "Message sent: $messageId")
                    onResult(true)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in sendMessage: ${e.message}")
            onResult(false)
        }
    }

    fun observeThreadMessages(threadId: String): Flow<List<ChatMessageData>> = callbackFlow {
        try {
            val messagesRef = database.getReference("$MESSAGES_PATH/$threadId")
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val messages = mutableListOf<ChatMessageData>()
                        for (msgSnapshot in snapshot.children) {
                            val messageId = msgSnapshot.child("messageId").value as? String ?: continue
                            val senderMobile = msgSnapshot.child("senderMobile").value as? String ?: ""
                            val senderName = msgSnapshot.child("senderName").value as? String ?: ""
                            val body = msgSnapshot.child("body").value as? String ?: ""
                            val timestamp = msgSnapshot.child("timestamp").value as? Long ?: 0L
                            val read = msgSnapshot.child("read").value as? Boolean ?: false
                            val mediaUri = msgSnapshot.child("mediaUri").value as? String
                            
                            messages.add(
                                ChatMessageData(
                                    messageId, senderMobile, senderName, body, timestamp, read, mediaUri
                                )
                            )
                        }
                        // Sort by timestamp
                        messages.sortBy { it.timestamp }
                        trySend(messages)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing messages: ${e.message}")
                        trySend(emptyList())
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error observing messages: ${error.message}")
                    trySend(emptyList())
                }
            }
            messagesRef.addValueEventListener(listener)
            awaitClose { messagesRef.removeEventListener(listener) }
        } catch (e: Exception) {
            Log.e(TAG, "Error in observeThreadMessages: ${e.message}")
            close(e)
        }
    }

    fun observeUserChatThreads(userMobile: String): Flow<List<ChatThread>> = callbackFlow {
        try {
            val chatsRef = database.getReference(CHATS_PATH)
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val threads = mutableListOf<ChatThread>()
                        for (threadSnapshot in snapshot.children) {
                            val threadId = threadSnapshot.child("threadId").value as? String ?: continue
                            val p1Mobile = threadSnapshot.child("participant1Mobile").value as? String ?: ""
                            val p1Name = threadSnapshot.child("participant1Name").value as? String ?: ""
                            val p2Mobile = threadSnapshot.child("participant2Mobile").value as? String ?: ""
                            val p2Name = threadSnapshot.child("participant2Name").value as? String ?: ""
                            val lastMessage = threadSnapshot.child("lastMessage").value as? String ?: ""
                            val lastMessageTime = threadSnapshot.child("lastMessageTime").value as? Long ?: 0L
                            val createdAt = threadSnapshot.child("createdAt").value as? Long ?: 0L
                            
                            // Include only threads where user is participant
                            if (p1Mobile == userMobile || p2Mobile == userMobile) {
                                threads.add(
                                    ChatThread(
                                        threadId, p1Mobile, p1Name, p2Mobile, p2Name, lastMessage, lastMessageTime, createdAt
                                    )
                                )
                            }
                        }
                        // Sort by last message time (newest first)
                        threads.sortByDescending { it.lastMessageTime }
                        trySend(threads)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing threads: ${e.message}")
                        trySend(emptyList())
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error observing threads: ${error.message}")
                    trySend(emptyList())
                }
            }
            chatsRef.addValueEventListener(listener)
            awaitClose { chatsRef.removeEventListener(listener) }
        } catch (e: Exception) {
            Log.e(TAG, "Error in observeUserChatThreads: ${e.message}")
            close(e)
        }
    }

    fun markMessagesAsRead(threadId: String, senderMobile: String) {
        try {
            val messagesRef = database.getReference("$MESSAGES_PATH/$threadId")
            messagesRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (msgSnapshot in snapshot.children) {
                        val currentSender = msgSnapshot.child("senderMobile").value as? String ?: ""
                        if (currentSender == senderMobile) {
                            msgSnapshot.ref.child("read").setValue(true)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error marking as read: ${error.message}")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error in markMessagesAsRead: ${e.message}")
        }
    }

    fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

