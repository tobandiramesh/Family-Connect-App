package com.familyconnect.app.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import android.content.ComponentName
import android.os.Bundle

/**
 * Foreground service that maintains Firebase Realtime Database listeners
 * for incoming calls and new messages even when the app is in the background.
 */
class CallListenerService : Service() {

    companion object {
        private const val TAG = "CallListenerService"
        private const val FOREGROUND_ID = 8001
        private const val EXTRA_USER_MOBILE = "extra_user_mobile"
        private const val EXTRA_USER_NAME = "extra_user_name"

        fun start(context: Context, userMobile: String, userName: String) {
            val intent = Intent(context, CallListenerService::class.java).apply {
                putExtra(EXTRA_USER_MOBILE, userMobile)
                putExtra(EXTRA_USER_NAME, userName)
            }
            Log.d(TAG, "🚀 START CALLED: userMobile=$userMobile")
            try {
                context.startForegroundService(intent)
                Log.d(TAG, "✅ startForegroundService() called successfully")
            } catch (e: Exception) {
                Log.e(TAG, "❌ ERROR calling startForegroundService: ${e.message}", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallListenerService::class.java))
        }
    }

    private lateinit var database: FirebaseDatabase
    private var callListener: ValueEventListener? = null
    private var messageListener: ValueEventListener? = null
    private var userMobile: String = ""
    private var userName: String = ""
    private var wakeLock: PowerManager.WakeLock? = null

    // Track which call/message IDs we've already shown notifications for
    private val notifiedCallIds = mutableSetOf<String>()
    private val notifiedMessageIds = mutableSetOf<String>()
    
    // Keep strong references to database paths
    private var chatsRef: com.google.firebase.database.DatabaseReference? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🔴 onCreate() called - Service created")
        
        // Initialize Firebase with persistence enabled
        try {
            database = FirebaseDatabase.getInstance()
            database.setPersistenceEnabled(true)
            Log.d(TAG, "   ✅ Firebase initialized")
        } catch (e: Exception) {
            Log.e(TAG, "   ❌ Firebase init failed: ${e.message}")
            database = FirebaseDatabase.getInstance()
        }
        
        NotificationHelper.ensureChannel(this)
        acquireWakeLock()
        Log.d(TAG, "   ✅ Notification channels and wake lock ready")
    }
    
    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FamilyConnect::CallListener")
                wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes
            }
        } catch (e: Exception) {
            // Silent failure
        }
    }
    
    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            // Silent failure
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return try {
            userMobile = intent?.getStringExtra(EXTRA_USER_MOBILE) ?: ""
            userName = intent?.getStringExtra(EXTRA_USER_NAME) ?: ""
            
            Log.d(TAG, "📱 onStartCommand() called")
            Log.d(TAG, "   userMobile: '$userMobile'")
            Log.d(TAG, "   userName: '$userName'")
            Log.d(TAG, "   flags: $flags, startId: $startId")

            if (userMobile.isBlank()) {
                Log.e(TAG, "   ❌ userMobile is blank! Stopping service")
                stopSelf()
                return START_NOT_STICKY
            }
            
            Log.d(TAG, "   ✅ User mobile valid, continuing service startup...")

            // Renew wake lock on every call
            releaseWakeLock()
            acquireWakeLock()

            try {
                // Ensure notification channels exist
                NotificationHelper.ensureChannel(this)
                Log.d(TAG, "   📢 Notification channels created")
                
                // Start as a foreground service with a silent persistent notification
                val notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_SERVICE)
                    .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                    .setContentTitle("Family Connect")
                    .setContentText("📱 Listening for calls ($userMobile)")
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true)
                    .setSilent(true)
                    .setShowWhen(false)
                    .build()

                startForeground(FOREGROUND_ID, notification)
                Log.d(TAG, "   ✅ startForeground() called with notification")
            } catch (e: Exception) {
                Log.e(TAG, "   ❌ Error starting foreground: ${e.message}", e)
                stopSelf()
                return START_NOT_STICKY
            }

            try {
                // Get fresh database reference each time
                chatsRef = database.getReference("chats")
                chatsRef?.keepSynced(true) // Keep data synced even in background
                Log.d(TAG, "   ✅ Database reference created and keep synced enabled")
                
                // Start listening for incoming calls and messages
                Log.d(TAG, "   🔊 Starting call listener...")
                startCallListener()
                Log.d(TAG, "   ✅ Call listener started")
                
                Log.d(TAG, "   📨 Starting message listener...")
                startMessageListener()
                Log.d(TAG, "   ✅ Message listener started")
                
                Log.d(TAG, "✅✅✅ SERVICE FULLY STARTED AND LISTENING ✅✅✅")
            } catch (e: Exception) {
                Log.e(TAG, "   ❌ Error starting listeners: ${e.message}", e)
            }

            // Restart service if killed
            Log.d(TAG, "   ⚙️ Returning START_STICKY (will restart if killed)")
            START_STICKY
        } catch (e: Exception) {
            Log.e(TAG, "❌ FATAL ERROR in onStartCommand: ${e.message}", e)
            stopSelf()
            START_NOT_STICKY
        }
    }

    private fun normalizeMobile(mobile: String): String = mobile.filter(Char::isDigit)

    private fun sameMobile(a: String, b: String): Boolean {
        val aNorm = normalizeMobile(a)
        val bNorm = normalizeMobile(b)
        if (aNorm.isBlank() || bNorm.isBlank()) return false
        return aNorm == bNorm || aNorm.endsWith(bNorm) || bNorm.endsWith(aNorm)
    }

    private fun startCallListener() {
        callListener?.let {
            chatsRef?.removeEventListener(it)
        }

        val normalizedMobile = normalizeMobile(userMobile)
        if (chatsRef == null) {
            chatsRef = database.getReference("chats")
            chatsRef?.keepSynced(true)
        }

        callListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (threadSnapshot in snapshot.children) {
                    for (callSnapshot in threadSnapshot.child("callRequests").children) {
                        val callId = (callSnapshot.child("callId").value as? String)
                            ?.takeIf { it.isNotBlank() }
                            ?: callSnapshot.key.orEmpty()
                        val toUserId = callSnapshot.child("toUserId").value as? String ?: ""
                        val status = callSnapshot.child("status").value as? String ?: ""
                        val fromUserName = callSnapshot.child("fromUserName").value as? String ?: "Someone"
                        val threadId = (callSnapshot.child("threadId").value as? String)
                            ?.takeIf { it.isNotBlank() }
                            ?: threadSnapshot.key.orEmpty()
                        val callType = callSnapshot.child("callType").value as? String ?: "audio"
                        val createdAt = callSnapshot.child("createdAt").value as? Long ?: 0L

                        // Only process pending calls for this user, and not too old (within 60s)
                        if (sameMobile(toUserId, normalizedMobile)
                            && status == "pending"
                            && callId.isNotBlank()
                            && callId !in notifiedCallIds
                            && (System.currentTimeMillis() - createdAt) < 60_000
                        ) {
                            Log.d(TAG, "📞 Incoming call detected: $callId from $fromUserName")
                            notifiedCallIds.add(callId)

                            // 🔥 Use Telecom Framework for system-level call handling
                            Log.d(TAG, "🚀 Triggering incoming call via TelecomManager...")
                            try {
                                val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
                                if (telecomManager == null) {
                                    Log.e(TAG, "   ❌ TelecomManager not available, falling back to CallForegroundService")
                                    fallbackToCallForegroundService(callId, threadId, fromUserName, callType)
                                } else {
                                    val handle = android.telecom.PhoneAccountHandle(
                                        ComponentName(this@CallListenerService, com.familyconnect.app.telecom.MyConnectionService::class.java),
                                        "FamilyConnectCall"
                                    )

                                    val extras = Bundle().apply {
                                        putString("callId", callId)
                                        putString("threadId", threadId)
                                        putString("callerName", fromUserName)
                                        putString("callType", callType)
                                    }

                                    telecomManager.addNewIncomingCall(handle, extras)
                                    Log.d(TAG, "   ✅ Telecom incoming call triggered")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "   ❌ Error triggering telecom call: ${e.message}", e)
                                fallbackToCallForegroundService(callId, threadId, fromUserName, callType)
                            }
                            
                            // Set pending call for UI overlay (for when app already open)
                            try {
                                Log.d(TAG, "📄 Setting pending call state: $callId")
                                val app = applicationContext as com.familyconnect.app.FamilyConnectApp
                                app.setPendingCall(com.familyconnect.app.PendingCallIntent(
                                    callId = callId,
                                    threadId = threadId,
                                    callerName = fromUserName,
                                    callType = callType
                                ))
                                Log.d(TAG, "✅ Pending call state set, UI should show call screen")
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Error setting pending call: ${e.message}", e)
                            }
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Silent failure
            }
        }

        try {
            chatsRef?.addValueEventListener(callListener!!)
        } catch (e: Exception) {
            // Silent failure
        }
    }

    private fun startMessageListener() {
        messageListener?.let {
            chatsRef?.removeEventListener(it)
        }

        if (chatsRef == null) {
            chatsRef = database.getReference("chats")
            chatsRef?.keepSynced(true)
        }

        messageListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (threadSnapshot in snapshot.children) {
                    val p1Mobile = threadSnapshot.child("participant1Mobile").value as? String ?: ""
                    val p2Mobile = threadSnapshot.child("participant2Mobile").value as? String ?: ""

                    if (p1Mobile != userMobile && p2Mobile != userMobile) continue

                    val threadId = threadSnapshot.child("threadId").value as? String ?: continue
                    val unreadKey = "unread_$userMobile"
                    val unreadCount = (threadSnapshot.child(unreadKey).value as? Long)?.toInt() ?: 0

                    if (unreadCount > 0) {
                        val lastMessage = threadSnapshot.child("lastMessage").value as? String ?: ""
                        val otherName = if (p1Mobile == userMobile) {
                            threadSnapshot.child("participant2Name").value as? String ?: "Someone"
                        } else {
                            threadSnapshot.child("participant1Name").value as? String ?: "Someone"
                        }

                        val notifKey = "${threadId}_${unreadCount}"
                        if (notifKey !in notifiedMessageIds) {
                            notifiedMessageIds.add(notifKey)

                            val body = if (unreadCount > 1) {
                                "$unreadCount new messages"
                            } else {
                                lastMessage.take(100)
                            }
                            NotificationHelper.postMessageNotification(
                                context = this@CallListenerService,
                                id = threadId.hashCode(),
                                senderName = otherName,
                                messageBody = body
                            )
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Silent failure
            }
        }

        try {
            chatsRef?.addValueEventListener(messageListener!!)
        } catch (e: Exception) {
            // Silent failure
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        callListener?.let {
            chatsRef?.removeEventListener(it)
        }
        messageListener?.let {
            chatsRef?.removeEventListener(it)
        }
        callListener = null
        messageListener = null
        releaseWakeLock()
        
        scheduleServiceRestart()
    }
    
    private fun scheduleServiceRestart() {
        try {
            if (userMobile.isNotBlank()) {
                val intent = Intent(this, CallListenerService::class.java).apply {
                    putExtra(EXTRA_USER_MOBILE, userMobile)
                    putExtra(EXTRA_USER_NAME, userName)
                }
                startForegroundService(intent)
            }
        } catch (e: Exception) {
            // Silent failure
        }
    }

    private fun fallbackToCallForegroundService(
        callId: String,
        threadId: String,
        callerName: String,
        callType: String
    ) {
        Log.d(TAG, "   🔄 Falling back to CallForegroundService...")
        val serviceIntent = Intent(this, CallForegroundService::class.java).apply {
            putExtra(NotificationHelper.EXTRA_CALL_ID, callId)
            putExtra(NotificationHelper.EXTRA_THREAD_ID, threadId)
            putExtra(NotificationHelper.EXTRA_CALLER_NAME, callerName)
            putExtra(NotificationHelper.EXTRA_CALL_TYPE, callType)
        }
        
        try {
            androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent)
            Log.d(TAG, "   ✅ CallForegroundService started")
        } catch (e: Exception) {
            Log.e(TAG, "   ❌ Error starting foreground service: ${e.message}", e)
        }
    }
}
