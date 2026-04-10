package com.familyconnect.app.data.repository

import android.util.Log
import com.familyconnect.app.data.model.ChatMessageData
import com.familyconnect.app.data.model.ChatThread
import com.familyconnect.app.data.model.OnlineUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
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
    private val database = FirebaseDatabase.getInstance("https://family-connect-app-a219b-default-rtdb.asia-southeast1.firebasedatabase.app")

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

            // Register onDisconnect: Firebase server writes these automatically
            // when the client loses connection (app killed, network drop, crash, etc.)
            presenceRef.child("isOnline").onDisconnect().setValue(false)
            presenceRef.child("lastSeen").onDisconnect().setValue(ServerValue.TIMESTAMP)

            Log.d(TAG, "User $mobile set online with onDisconnect hooks registered")
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
                            
                            if (mobile != "9999999999") { // Exclude admin; include online and offline users
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

    private fun normalizeMobile(mobile: String): String = mobile.filter(Char::isDigit)

    private fun sameMobile(a: String, b: String): Boolean {
        val aNorm = normalizeMobile(a)
        val bNorm = normalizeMobile(b)
        if (aNorm.isBlank() || bNorm.isBlank()) return false
        return aNorm == bNorm ||
            aNorm.endsWith(bNorm) ||
            bNorm.endsWith(aNorm)
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
        replyToMessageId: String? = null,
        replyToSenderName: String? = null,
        replyToBody: String? = null,
        recipientMobile: String? = null,
        senderLocation: String? = null,
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
                "mediaUri" to (mediaUri ?: ""),
                "replyToMessageId" to (replyToMessageId ?: ""),
                "replyToSenderName" to (replyToSenderName ?: ""),
                "replyToBody" to (replyToBody ?: ""),
                "senderLocation" to (senderLocation ?: "")
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
                    // Increment unread counter for the recipient
                    if (!recipientMobile.isNullOrBlank()) {
                        val threadRef = database.getReference("$CHATS_PATH/$threadId")
                        val unreadKey = "unread_$recipientMobile"
                        threadRef.child(unreadKey).get().addOnSuccessListener { snap ->
                            val current = (snap.value as? Long)?.toInt() ?: 0
                            threadRef.child(unreadKey).setValue(current + 1)
                        }
                    }
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
                            val replyToMessageId = (msgSnapshot.child("replyToMessageId").value as? String).orEmpty().ifBlank { null }
                            val replyToSenderName = (msgSnapshot.child("replyToSenderName").value as? String).orEmpty().ifBlank { null }
                            val replyToBody = (msgSnapshot.child("replyToBody").value as? String).orEmpty().ifBlank { null }
                            val senderLocation = (msgSnapshot.child("senderLocation").value as? String).orEmpty().ifBlank { null }
                            
                            messages.add(
                                ChatMessageData(
                                    messageId = messageId,
                                    senderMobile = senderMobile,
                                    senderName = senderName,
                                    body = body,
                                    timestamp = timestamp,
                                    read = read,
                                    mediaUri = mediaUri,
                                    replyToMessageId = replyToMessageId,
                                    replyToSenderName = replyToSenderName,
                                    replyToBody = replyToBody,
                                    senderLocation = senderLocation
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
                            val unreadCount = (threadSnapshot.child("unread_$userMobile").value as? Long)?.toInt() ?: 0
                            
                            // Include only threads where user is participant
                            if (p1Mobile == userMobile || p2Mobile == userMobile) {
                                threads.add(
                                    ChatThread(
                                        threadId = threadId,
                                        participant1Mobile = p1Mobile,
                                        participant2Mobile = p2Mobile,
                                        participant1Name = p1Name,
                                        participant2Name = p2Name,
                                        lastMessage = lastMessage,
                                        lastMessageTime = lastMessageTime,
                                        createdAt = createdAt,
                                        unreadCount = unreadCount
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

    fun markMessagesAsRead(threadId: String, currentUserMobile: String) {
        try {
            // Reset unread counter for this user on the thread
            database.getReference("$CHATS_PATH/$threadId/unread_$currentUserMobile").setValue(0)
            // Mark individual messages as read
            val messagesRef = database.getReference("$MESSAGES_PATH/$threadId")
            messagesRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (msgSnapshot in snapshot.children) {
                        val sender = msgSnapshot.child("senderMobile").value as? String ?: ""
                        if (sender != currentUserMobile) {
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

    fun formatLastSeen(timestamp: Long): String {
        if (timestamp == 0L) return ""
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < 60_000L -> "Last seen just now"
            diff < 3_600_000L -> "Last seen ${diff / 60_000} min ago"
            diff < 86_400_000L -> "Last seen ${diff / 3_600_000} hr ago"
            diff < 2 * 86_400_000L -> "Last seen yesterday"
            else -> "Last seen " + java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
        }
    }

    // ============ AUDIO CALL SIGNALING ============
    
    private const val CALLS_PATH = "calls"
    private const val SIGNALING_PATH = "signaling"
    private const val CHAT_CALL_REQUESTS_CHILD = "callRequests"
    private const val CHAT_SIGNALING_CHILD = "signaling"
    
    fun sendCallRequest(
        callId: String,
        fromUserId: String,
        fromUserName: String,
        toUserId: String,
        threadId: String,
        callType: String = "audio",
        onResult: (Boolean) -> Unit
    ) {
        try {
            val callData = mapOf(
                "callId" to callId,
                "fromUserId" to normalizeMobile(fromUserId),
                "fromUserName" to fromUserName,
                "toUserId" to normalizeMobile(toUserId),
                "threadId" to threadId,
                "status" to "pending",
                "callType" to callType,
                "createdAt" to System.currentTimeMillis()
            )

            // Primary path under chats thread (works with strict per-thread DB rules)
            database.getReference("$CHATS_PATH/$threadId/$CHAT_CALL_REQUESTS_CHILD/$callId").setValue(callData) { error, _ ->
                if (error != null) {
                    Log.e(TAG, "Error sending call request: ${error.message}")
                    onResult(false)
                } else {
                    // Legacy mirror path for backward compatibility (best effort only)
                    database.getReference("$CALLS_PATH/$threadId/$callId").setValue(callData)
                    Log.d(TAG, "Call request sent: $callId")
                    onResult(true)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in sendCallRequest: ${e.message}")
            onResult(false)
        }
    }
    
    fun observeCallRequests(userId: String): Flow<List<Map<String, Any>>> = callbackFlow {
        try {
            val normalizedUserId = normalizeMobile(userId)
            val callsRef = database.getReference(CHATS_PATH)
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val callRequests = mutableListOf<Map<String, Any>>()
                        for (threadSnapshot in snapshot.children) {
                            for (callSnapshot in threadSnapshot.child(CHAT_CALL_REQUESTS_CHILD).children) {
                                val parsedCallId = (callSnapshot.child("callId").value as? String)
                                    ?.takeIf { it.isNotBlank() }
                                    ?: callSnapshot.key.orEmpty()
                                val parsedThreadId = (callSnapshot.child("threadId").value as? String)
                                    ?.takeIf { it.isNotBlank() }
                                    ?: threadSnapshot.key.orEmpty()
                                val toUserId = callSnapshot.child("toUserId").value as? String ?: ""
                                val status = callSnapshot.child("status").value as? String ?: ""
                                
                                // Only include pending calls for this user
                                if (sameMobile(toUserId, normalizedUserId) && status == "pending") {
                                    val callData = mapOf(
                                        "callId" to parsedCallId,
                                        "fromUserId" to (callSnapshot.child("fromUserId").value as? String ?: ""),
                                        "fromUserName" to (callSnapshot.child("fromUserName").value as? String ?: ""),
                                        "toUserId" to toUserId,
                                        "threadId" to parsedThreadId,
                                        "createdAt" to (callSnapshot.child("createdAt").value as? Long ?: 0L),
                                        "status" to status,
                                        "callType" to (callSnapshot.child("callType").value as? String ?: "audio")
                                    )
                                    callRequests.add(callData)
                                }
                            }
                        }
                        trySend(callRequests)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing call requests: ${e.message}")
                        trySend(emptyList())
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error observing call requests: ${error.message}")
                    trySend(emptyList())
                }
            }
            callsRef.addValueEventListener(listener)
            awaitClose { callsRef.removeEventListener(listener) }
        } catch (e: Exception) {
            Log.e(TAG, "Error in observeCallRequests: ${e.message}")
            close(e)
        }
    }
    
    fun updateCallStatus(
        threadId: String,
        callId: String,
        status: String,
        onResult: (Boolean) -> Unit
    ) {
        try {
            // Primary path under chats thread
            database.getReference("$CHATS_PATH/$threadId/$CHAT_CALL_REQUESTS_CHILD/$callId/status").setValue(status) { error, _ ->
                if (error != null) {
                    Log.e(TAG, "Error updating call status: ${error.message}")
                    onResult(false)
                } else {
                    // Legacy mirror path (best effort)
                    database.getReference("$CALLS_PATH/$threadId/$callId/status").setValue(status)
                    Log.d(TAG, "Call status updated: $callId -> $status")
                    onResult(true)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in updateCallStatus: ${e.message}")
            onResult(false)
        }
    }

    fun observeCallById(threadId: String, callId: String): Flow<Map<String, Any>?> = callbackFlow {
        try {
            val callRef = database.getReference("$CHATS_PATH/$threadId/$CHAT_CALL_REQUESTS_CHILD/$callId")
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        trySend(null)
                        return
                    }

                    val callData = mapOf(
                        "callId" to (snapshot.child("callId").value as? String ?: ""),
                        "fromUserId" to (snapshot.child("fromUserId").value as? String ?: ""),
                        "fromUserName" to (snapshot.child("fromUserName").value as? String ?: ""),
                        "toUserId" to (snapshot.child("toUserId").value as? String ?: ""),
                        "threadId" to (snapshot.child("threadId").value as? String ?: ""),
                        "status" to (snapshot.child("status").value as? String ?: ""),
                        "createdAt" to (snapshot.child("createdAt").value as? Long ?: 0L)
                    )
                    trySend(callData)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error observing call by id: ${error.message}")
                    trySend(null)
                }
            }

            callRef.addValueEventListener(listener)
            awaitClose { callRef.removeEventListener(listener) }
        } catch (e: Exception) {
            Log.e(TAG, "Error in observeCallById: ${e.message}")
            close(e)
        }
    }
    
    fun sendCallSignaling(
        threadId: String,
        callId: String,
        type: String, // offer, answer
        sdp: String,
        senderId: String,
        onResult: (Boolean) -> Unit
    ) {
        try {
            val signalingData = mapOf(
                "type" to type,
                "sdp" to sdp,
                "senderId" to senderId,
                "timestamp" to System.currentTimeMillis()
            )
            
            val key = "${type}_${System.currentTimeMillis()}"
            // Primary path under chats thread
            database.getReference("$CHATS_PATH/$threadId/$CHAT_SIGNALING_CHILD/$callId/$key").setValue(signalingData) { error, _ ->
                if (error != null) {
                    Log.e(TAG, "Error sending signaling data: ${error.message}")
                    onResult(false)
                } else {
                    // Legacy mirror path (best effort)
                    database.getReference("$SIGNALING_PATH/$threadId/$callId/$key").setValue(signalingData)
                    Log.d(TAG, "Signaling data sent: $type")
                    onResult(true)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in sendCallSignaling: ${e.message}")
            onResult(false)
        }
    }
    
    fun observeCallSignaling(
        threadId: String,
        callId: String
    ): Flow<List<Map<String, Any>>> = callbackFlow {
        try {
            val signalingRef = database.getReference("$CHATS_PATH/$threadId/$CHAT_SIGNALING_CHILD/$callId")
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val signalingData = mutableListOf<Map<String, Any>>()
                        for (dataSnapshot in snapshot.children) {
                            val type = dataSnapshot.child("type").value as? String ?: ""
                            val data = mapOf(
                                "id" to dataSnapshot.key.orEmpty(),
                                "type" to type,
                                "sdp" to (dataSnapshot.child("sdp").value as? String ?: ""),
                                "candidate" to (dataSnapshot.child("candidate").value as? String ?: ""),
                                "sdpMLineIndex" to ((dataSnapshot.child("sdpMLineIndex").value as? Long)?.toInt() ?: -1),
                                "sdpMid" to (dataSnapshot.child("sdpMid").value as? String ?: ""),
                                "senderId" to (dataSnapshot.child("senderId").value as? String ?: ""),
                                "timestamp" to (dataSnapshot.child("timestamp").value as? Long ?: 0L)
                            )
                            if (type.isNotBlank()) {
                                signalingData.add(data)
                            }
                        }
                        trySend(signalingData)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing signaling data: ${e.message}")
                        trySend(emptyList())
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error observing signaling: ${error.message}")
                    trySend(emptyList())
                }
            }
            signalingRef.addValueEventListener(listener)
            awaitClose { signalingRef.removeEventListener(listener) }
        } catch (e: Exception) {
            Log.e(TAG, "Error in observeCallSignaling: ${e.message}")
            close(e)
        }
    }
    
    fun sendIceCandidate(
        threadId: String,
        callId: String,
        candidate: String,
        sdpMLineIndex: Int,
        sdpMid: String,
        senderId: String,
        onResult: (Boolean) -> Unit
    ) {
        try {
            val candidateData = mapOf(
                "type" to "candidate",
                "candidate" to candidate,
                "sdpMLineIndex" to sdpMLineIndex,
                "sdpMid" to sdpMid,
                "senderId" to senderId,
                "timestamp" to System.currentTimeMillis()
            )
            
            val key = "candidate_${System.currentTimeMillis()}"
            database.getReference("$CHATS_PATH/$threadId/$CHAT_SIGNALING_CHILD/$callId/$key").setValue(candidateData) { error, _ ->
                if (error != null) {
                    Log.e(TAG, "Error sending ICE candidate: ${error.message}")
                    onResult(false)
                } else {
                    // Legacy mirror path (best effort)
                    database.getReference("$SIGNALING_PATH/$threadId/$callId/$key").setValue(candidateData)
                    Log.d(TAG, "ICE candidate sent")
                    onResult(true)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in sendIceCandidate: ${e.message}")
            onResult(false)
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

    // 📝 Observe typing status for a thread (excludes current user)
    fun observeTypingStatus(threadId: String, currentUserMobile: String): Flow<List<com.familyconnect.app.data.model.TypingStatus>> = callbackFlow {
        try {
            val path = "typing/$threadId"
            Log.d(TAG, "Listening: $path")
            
            val typingRef = database.getReference(path)
            
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val typingList = mutableListOf<com.familyconnect.app.data.model.TypingStatus>()
                        snapshot.children.forEach { statusSnapshot ->
                            val status = statusSnapshot.getValue(com.familyconnect.app.data.model.TypingStatus::class.java)
                            if (status != null && status.userMobile != currentUserMobile) {
                                typingList.add(status)
                            }
                        }
                        trySend(typingList)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing typing status", e)
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    close(error.toException())
                }
            }
            
            typingRef.addValueEventListener(listener)
            Log.d(TAG, "Listener attached for typing status")
            
            awaitClose { 
                typingRef.removeEventListener(listener)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in observeTypingStatus", e)
            close(e)
        }
    }
}

