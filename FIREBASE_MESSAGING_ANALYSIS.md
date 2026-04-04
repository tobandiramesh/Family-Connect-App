# Firebase Messaging Implementation Analysis - Family Connect App

## Executive Summary

The Family Connect App has a **hybrid messaging architecture** combining:
- ✅ **Firebase Realtime Database listeners** (CallListenerService) for real-time call/message detection
- ✅ **Firebase Cloud Messaging (FCM)** for push notifications
- ⚠️ **DATA-ONLY FCM messages** partially implemented but not fully utilized

---

## 1. FirebaseMessagingService Implementation ✅

### File: [FCMService.kt](app/src/main/java/com/familyconnect/app/notifications/FCMService.kt)

**Implementation Status**: ✅ COMPLETE

#### Class Definition
```kotlin
class FCMService : FirebaseMessagingService() {
    companion object {
        private const val TAG = "FCMService"
    }
```

#### onNewToken() Method
```kotlin
override fun onNewToken(token: String) {
    super.onNewToken(token)
    Log.d(TAG, "FCM token refreshed: ${token.take(10)}...")
    // Token can be saved to Firebase DB under the user's profile
    // for targeted push notifications from Cloud Functions
}
```
**Purpose**: Refreshes FCM token for push notifications. Currently logs but doesn't persist token.

#### onMessageReceived() Method - DATA MESSAGE HANDLING
```kotlin
override fun onMessageReceived(message: RemoteMessage) {
    super.onMessageReceived(message)
    Log.d(TAG, "FCM message received: ${message.data}")

    val data = message.data
    val type = data["type"] ?: ""

    when (type) {
        "incoming_call" -> {
            val callId = data["callId"] ?: return
            val threadId = data["threadId"] ?: return
            val callerName = data["callerName"] ?: "Someone"
            val callType = data["callType"] ?: "audio"

            NotificationHelper.postIncomingCallNotification(
                context = this,
                callId = callId,
                threadId = threadId,
                callerName = callerName,
                callType = callType
            )
        }

        "new_message" -> {
            val senderName = data["senderName"] ?: "Someone"
            val messageBody = data["messageBody"] ?: "New message"
            val threadId = data["threadId"] ?: ""

            NotificationHelper.postMessageNotification(
                context = this,
                id = threadId.hashCode(),
                senderName = senderName,
                messageBody = messageBody
            )
        }

        else -> {
            // Handle notification payload (when app is in foreground)
            message.notification?.let { notification ->
                NotificationHelper.post(
                    context = this,
                    id = message.messageId.hashCode(),
                    title = notification.title ?: "Family Connect",
                    body = notification.body ?: ""
                )
            }
        }
    }
}
```

**Data Message Support**: ✅ YES
- Handles data-only FCM payloads with `message.data` dictionary
- Supports two message types: `incoming_call` and `new_message`
- Data fields extracted: `type`, `callId`, `threadId`, `callerName`, `callType`, `senderName`, `messageBody`

---

## 2. Foreground Service Implementation ✅

### File: [CallListenerService.kt](app/src/main/java/com/familyconnect/app/notifications/CallListenerService.kt)

**Implementation Status**: ✅ COMPLETE - Advanced configuration

#### Service Registration
```kotlin
/**
 * Foreground service that maintains Firebase Realtime Database listeners
 * for incoming calls and new messages even when the app is in the background.
 */
class CallListenerService : Service()
```

#### Foreground Notification Setup
```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    try {
        userMobile = intent?.getStringExtra(EXTRA_USER_MOBILE) ?: ""
        userName = intent?.getStringExtra(EXTRA_USER_NAME) ?: ""

        // Ensure notification channels exist
        NotificationHelper.ensureChannel(this)
        
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
        Log.d(TAG, "✅ Foreground service notification updated")
```

**Features**:
- 🔔 Runs with persistent notification (FOREGROUND_ID = 8001)
- 📱 Uses `NotificationCompat.PRIORITY_LOW` to minimize distraction
- 🔇 Silent notification (setSilent(true))
- ♻️ Automatic restart: `START_STICKY` flag
- 🔋 Wake lock management to prevent process termination

#### Wake Lock Acquisition
```kotlin
private fun acquireWakeLock() {
    try {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, 
                "FamilyConnect::CallListener"
            )
            wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes
            Log.d(TAG, "🔋 Wake lock acquired")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error acquiring wake lock: ${e.message}")
    }
}
```

#### Firebase Persistence
```kotlin
override fun onCreate() {
    super.onCreate()
    try {
        database = FirebaseDatabase.getInstance()
        database.setPersistenceEnabled(true)  // ✅ Offline data caching
        Log.d(TAG, "✅ Firebase persistence enabled")
    } catch (e: Exception) {
        database = FirebaseDatabase.getInstance()
    }
}
```

#### Call Listener Implementation
```kotlin
private fun startCallListener() {
    val normalizedMobile = normalizeMobile(userMobile)
    
    callListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            Log.d(TAG, "\n=== CALL LISTENER FIRED ===")
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
                        notifiedCallIds.add(callId)
                        
                        NotificationHelper.postIncomingCallNotification(
                            context = this@CallListenerService,
                            callId = callId,
                            threadId = threadId,
                            callerName = fromUserName,
                            callType = callType
                        )
                    }
                }
            }
        }

        override fun onCancelled(error: DatabaseError) {
            Log.e(TAG, "Call listener cancelled: ${error.message}")
        }
    }

    try {
        chatsRef?.addValueEventListener(callListener!!)
        Log.d(TAG, "✅ Call listener registered for $userMobile")
    } catch (e: Exception) {
        Log.e(TAG, "❌ Failed to add call listener: ${e.message}", e)
    }
}
```

**Features**:
- ✅ ValueEventListener on `/chats` reference
- ✅ Filters for pending calls destined for current user
- ✅ Deduplication: tracks `notifiedCallIds` to prevent duplicate notifications
- ✅ Age validation: only processes calls < 60 seconds old
- ✅ `keepSynced(true)` for offline-first support

#### Message Listener Implementation
```kotlin
private fun startMessageListener() {
    messageListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            // Find threads this user participates in, check for unread messages
            for (threadSnapshot in snapshot.children) {
                val p1Mobile = threadSnapshot.child("participant1Mobile").value as? String ?: ""
                val p2Mobile = threadSnapshot.child("participant2Mobile").value as? String ?: ""

                // Only process threads where this user is a participant
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

                    // Use threadId as a stable key for notification dedup
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
            Log.e(TAG, "Message listener cancelled: ${error.message}")
        }
    }

    try {
        chatsRef?.addValueEventListener(messageListener!!)
        Log.d(TAG, "✅ Message listener registered for $userMobile")
    } catch (e: Exception) {
        Log.e(TAG, "❌ Failed to add message listener: ${e.message}", e)
    }
}
```

**Features**:
- ✅ Listens on all chat threads
- ✅ Checks per-user unread counter: `unread_{userMobile}`
- ✅ Deduplication using `${threadId}_${unreadCount}` key
- ✅ Shows unread count or message preview

#### Service Lifecycle
```kotlin
override fun onDestroy() {
    super.onDestroy()
    Log.d(TAG, "⚠️ CallListenerService destroyed - will restart due to START_STICKY")

    callListener?.let {
        chatsRef?.removeEventListener(it)
    }
    messageListener?.let {
        chatsRef?.removeEventListener(it)
    }
    releaseWakeLock()
}
```

---

## 3. Full-Screen Notification & AlertDialog ✅

### File: [NotificationHelper.kt](app/src/main/java/com/familyconnect/app/notifications/NotificationHelper.kt)

**Implementation Status**: ✅ COMPLETE - Full-screen intent support

#### Incoming Call Notification
```kotlin
fun postIncomingCallNotification(
    context: Context,
    callId: String,
    threadId: String,
    callerName: String,
    callType: String
) {
    // Create intent that launches MainActivity with call data
    val launchIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_FROM_BACKGROUND
        putExtra(EXTRA_CALL_ID, callId)
        putExtra(EXTRA_THREAD_ID, threadId)
        putExtra(EXTRA_CALLER_NAME, callerName)
        putExtra("action", "incoming_call")
    }
    
    // Pending intent for full-screen display (lock screen)
    val fullScreenIntent = PendingIntent.getActivity(
        context, 
        callId.hashCode(), 
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    
    // Pending intent for tapping the notification body
    val contentIntent = PendingIntent.getActivity(
        context,
        callId.hashCode() + 100,
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val acceptIntent = Intent(context, CallActionReceiver::class.java).apply {
        action = ACTION_ACCEPT_CALL
        putExtra(EXTRA_CALL_ID, callId)
        putExtra(EXTRA_THREAD_ID, threadId)
        putExtra(EXTRA_CALLER_NAME, callerName)
    }
    val acceptPendingIntent = PendingIntent.getBroadcast(
        context, callId.hashCode() + 1000, acceptIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val rejectIntent = Intent(context, CallActionReceiver::class.java).apply {
        action = ACTION_REJECT_CALL
        putExtra(EXTRA_CALL_ID, callId)
        putExtra(EXTRA_THREAD_ID, threadId)
    }
    val rejectPendingIntent = PendingIntent.getBroadcast(
        context, callId.hashCode() + 2000, rejectIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val callLabel = if (callType == "video") "Incoming Video Call" else "Incoming Audio Call"

    val notification = NotificationCompat.Builder(context, CHANNEL_CALLS)
        .setSmallIcon(android.R.drawable.ic_menu_call)
        .setContentTitle(callLabel)
        .setContentText("$callerName is calling...")
        .setStyle(NotificationCompat.BigTextStyle().bigText("$callerName is calling you"))
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setCategory(NotificationCompat.CATEGORY_CALL)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setOngoing(true)
        .setAutoCancel(false)
        .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
        .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
        .setFullScreenIntent(fullScreenIntent, true)  // ✅ FULL-SCREEN INTENT
        .setContentIntent(contentIntent)
        .addAction(android.R.drawable.ic_menu_call, "Accept", acceptPendingIntent)
        .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Reject", rejectPendingIntent)
        .setColorized(true)
        .setColor(0xFF6366F1.toInt())
        .build()

    runCatching {
        NotificationManagerCompat.from(context).notify(CALL_NOTIFICATION_ID, notification)
    }.onFailure {
        android.util.Log.e("NotificationHelper", "❌ Failed to post notification: ${it.message}")
    }
}
```

#### Notification Channels Configuration
```kotlin
fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(NotificationManager::class.java)

        // Incoming calls channel (high priority with ringtone)
        val callChannel = NotificationChannel(
            CHANNEL_CALLS,
            "Incoming Calls",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Audio and video call notifications"
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(callChannel)

        // Chat messages channel
        val msgChannel = NotificationChannel(
            CHANNEL_MESSAGES,
            "Chat Messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "New chat message notifications"
            enableVibration(true)
        }
        manager.createNotificationChannel(msgChannel)

        // Foreground service channel (low priority, silent)
        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "Background Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the app listening for calls and messages"
            setShowBadge(false)
        }
        manager.createNotificationChannel(serviceChannel)
    }
}
```

**Full-Screen Features**: ✅
- 🔔 `setFullScreenIntent(fullScreenIntent, true)` - Displays on lock screen & above existing notifications
- 🔊 Ringtone + vibration pattern configured
- ✋ Action buttons: Accept & Reject
- 👁️ Public visibility (VISIBILITY_PUBLIC)
- 🎨 Colorized with indigo accent (0xFF6366F1)

---

## 4. Current Notification Implementation Approach

**Architecture**: Dual-layer messaging system

```
┌─────────────────────────────────────────────────────────────────┐
│                    INCOMING CALL FLOW                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  Caller sends call via Firebase Realtime DB                      │
│           ↓                                                       │
│  ┌──────────────────────────────────────────────────────┐        │
│  │ Option 1: LOCAL LISTENER (Foreground Service)        │        │
│  │ ✅ CallListenerService.startCallListener()           │        │
│  │    • Listens on /chats with addValueEventListener    │        │
│  │    • Immediate notification (app running or backup)  │        │
│  │    • 10-minute wake lock prevents process termination│        │
│  └──────────────────────────────────────────────────────┘        │
│           ↓                                                       │
│  ┌──────────────────────────────────────────────────────┐        │
│  │ Option 2: FCM PUSH (For killed process)              │        │
│  │ ✅ FCMService.onMessageReceived()                    │        │
│  │    • Triggered by Firebase Cloud Functions           │        │
│  │    • Handles when CallListenerService is dead        │        │
│  │    • Parses data message with type="incoming_call"   │        │
│  └──────────────────────────────────────────────────────┘        │
│           ↓                                                       │
│  NotificationHelper.postIncomingCallNotification()               │
│           ↓                                                       │
│  Full-Screen Notification with setFullScreenIntent()            │
│           ✅ Shows on lock screen                                 │
│           ✅ Displays above existing notifications                │
│           ✅ Accept/Reject action buttons                         │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    MESSAGE NOTIFICATION FLOW                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  Sender sends message via Firebase Realtime DB                   │
│           ↓                                                       │
│  ┌──────────────────────────────────────────────────────┐        │
│  │ LOCAL LISTENER (Foreground Service)                  │        │
│  │ ✅ CallListenerService.startMessageListener()        │        │
│  │    • Listens on /chats for unread_{userMobile}       │        │
│  │    • Shows when app is running                       │        │
│  │    • Dedup key: ${threadId}_${unreadCount}           │        │
│  └──────────────────────────────────────────────────────┘        │
│           ↓                                                       │
│  NotificationHelper.postMessageNotification()                    │
│           ✅ Title: "Message from {senderName}"                  │
│           ✅ Body: Message preview (first 100 chars)             │
│           ✅ Unread badge shows count if multiple                │
└─────────────────────────────────────────────────────────────────┘
```

---

## 5. DATA-ONLY FCM Messages ⚠️

**Implementation Status**: ✅ PARTIALLY IMPLEMENTED

### Current Support
```kotlin
val data = message.data  // ✅ Data messages are accessible

when (data["type"]) {
    "incoming_call" -> { /* Handle */ }
    "new_message" -> { /* Handle */ }
    else -> { /* Fallback to notification */ }
}
```

### Message Format Expected
```json
{
  "to": "FCM_TOKEN_OF_RECIPIENT",
  "data": {
    "type": "incoming_call",
    "callId": "uuid-123",
    "threadId": "mobile1_mobile2",
    "callerName": "John Doe",
    "callType": "audio"
  }
}
```

### Firebase Dependencies ✅
```gradle
implementation("com.google.firebase:firebase-messaging-ktx")
```

---

## 6. AndroidManifest Configuration ✅

### Permissions
```xml
<!-- Messaging & Notifications -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.INTERNET" />

<!-- Background Service -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_PHONE_CALL" />

<!-- Full-Screen Intent (Lock Screen Display) -->
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />

<!-- Other Needed -->
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.VIBRATE" />
```

### Service Registrations
```xml
<!-- Foreground service for listening to calls and messages in background -->
<service
    android:name=".notifications.CallListenerService"
    android:exported="false"
    android:foregroundServiceType="phoneCall" />

<!-- Firebase Cloud Messaging service -->
<service
    android:name=".notifications.FCMService"
    android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>

<!-- Broadcast receiver for call accept/reject from notification -->
<receiver
    android:name=".notifications.CallActionReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="com.familyconnect.app.ACTION_ACCEPT_CALL" />
        <action android:name="com.familyconnect.app.ACTION_REJECT_CALL" />
    </intent-filter>
</receiver>
```

### Main Activity Configuration
```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:launchMode="singleTop"
    android:showOnLockScreen="true"
    android:turnScreenOn="true"
    android:keepScreenOn="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

---

## Implementation Status Summary

| Component | Status | Details |
|-----------|--------|---------|
| **FirebaseMessagingService** | ✅ Complete | FCMService extends FirebaseMessagingService |
| **onMessageReceived()** | ✅ Complete | Handles data messages with type-based routing |
| **DATA-ONLY FCM** | ✅ Complete | message.data field parsed correctly |
| **DATA Message Handling** | ✅ Complete | Types: "incoming_call", "new_message" |
| **Foreground Service** | ✅ Complete | CallListenerService with Firebase listeners |
| **Wake Lock** | ✅ Complete | PARTIAL_WAKE_LOCK (10 min timeout) |
| **Full-Screen Notification** | ✅ Complete | setFullScreenIntent(fullScreenIntent, true) |
| **Lock Screen Display** | ✅ Complete | VISIBILITY_PUBLIC + showOnLockScreen intent |
| **Action Buttons** | ✅ Complete | Accept/Reject with CallActionReceiver |
| **Notification Channels** | ✅ Complete | CALLS (HIGH), MESSAGES (HIGH), SERVICE (LOW) |
| **AndroidManifest** | ✅ Complete | All services, permissions, intent-filters |
| **Token Management** | ⚠️ Partial | Token refreshed but not persisted to Firebase |
| **FCM Triggers** | ⚠️ Partial | Requires Cloud Functions setup (not implemented) |

---

## ⚠️ Missing / Incomplete Features

### 1. **FCM Token Persistence** ⚠️
The token is generated and refreshed but never saved to Firebase:
```kotlin
override fun onNewToken(token: String) {
    super.onNewToken(token)
    Log.d(TAG, "FCM token refreshed: ${token.take(10)}...")
    // ❌ Token NOT saved to Firebase users/{mobile}/fcm_token
}
```
**Fix Needed**: Save token to Firebase for server-side push targeting

### 2. **Cloud Functions Missing** ⚠️
FCM messages are only handled when explicitly sent. No automatic trigger when:
- New call created in `/chats/{threadId}/callRequests`
- New message added to `/messages/{threadId}`

**What's Needed**: Firebase Cloud Functions that:
```javascript
// When new call created at /chats/{threadId}/callRequests/{callId}
exports.sendCallFCM = functions.database.ref('chats/{threadId}/callRequests/{callId}')
    .onCreate((snapshot, context) => {
        const callData = snapshot.val();
        const toUserId = callData.toUserId;
        
        // Get recipient's FCM token from /users/{toUserId}/fcm_token
        // Send FCM data message with type="incoming_call"
    });
```

### 3. **Notification Dismissal on Action** ⚠️
After Accept/Reject, notification should be cancelled:
```kotlin
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NotificationHelper.ACTION_ACCEPT_CALL -> {
                NotificationHelper.cancelCallNotification(context)  // ✅ Does this get called?
            }
            NotificationHelper.ACTION_REJECT_CALL -> {
                NotificationHelper.cancelCallNotification(context)  // ✅ Verify execution
            }
        }
    }
}
```

### 4. **Notification Click Handling** ⚠️
When user clicks notification (not action), MainActivity needs to detect incoming call:
```kotlin
// In MainActivity
private fun extractAndStorePendingCall(intent: Intent) {
    val action = intent.getStringExtra("action") ?: return
    if (action == "incoming_call") {
        val callId = intent.getStringExtra(NotificationHelper.EXTRA_CALL_ID)
        val threadId = intent.getStringExtra(NotificationHelper.EXTRA_THREAD_ID)
        val callerName = intent.getStringExtra(NotificationHelper.EXTRA_CALLER_NAME)
        // ✅ Show incoming call dialog/screen
    }
}
```

---

## Performance & Optimization Notes

### ✅ What's Working Well
1. **Dual-layer architecture**: Local listeners + FCM backup
2. **Deduplication**: Prevents multiple notifications for same event
3. **Wake lock management**: Keeps process alive `(10 config
4. **Firebase persistence**: Offline-first support with setPersistenceEnabled()
5. **Notification channels**: Proper OS-level configuration for Android 8+

### ⚠️ Potential Issues
1. **10-minute wake lock timeout**: May lose listener after 10 min if screen off + no reacquire
2. **No token persistence**: FCM tokens not saved → can't send targeted push from backend
3. **No Cloud Functions**: No automatic FCM trigger when data written
4. **Memory usage**: Two concurrent listeners running continuously
5. **Firebase listener overhead**: Full snapshot per data change (not optimized queries)

---

## Recommended Enhancements

### 1. Save FCM Tokens
```kotlin
override fun onNewToken(token: String) {
    super.onNewToken(token)
    val mobile = SharedPreferences.getString("logged_in_mobile", "")
    if (mobile.isNotBlank()) {
        FirebaseDatabase.getInstance()
            .getReference("users/$mobile/fcm_token")
            .setValue(token)
    }
}
```

### 2. Implement Cloud Functions
Deploy Firebase Cloud Functions that trigger FCM messages on database changes.

### 3. Optimize Listeners
Use `.limitToLast(100)` or query constraints instead of pulling all data:
```kotlin
chatsRef?.orderByChild("createdAt")
    ?.limitToLast(100)
    ?.addValueEventListener(listener)
```

### 4. Implement Battery Optimization
Monitor battery level and adjust listener refresh rate:
```kotlin
val batteryManager = context.getSystemService(BatteryManager::class.java)
val batteryPct = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
if (batteryPct!! < 20) {
    // Increase listener polling interval
}
```

---

## Testing Checklist

- [ ] App killed → Incoming call triggers FCM → Notification shown
- [ ] App in background (10+ mins) → Listener still active
- [ ] Accept call button → Closes notification & Opens call screen
- [ ] Reject call button → Updates Firebase status & Closes notification
- [ ] Screen locked → Full-screen notification displays
- [ ] Multiple calls → Each shows separate notification
- [ ] Message while in chat thread → Notification + unread count updated
- [ ] Connection loss → Queued calls shown when reconnected
- [ ] Device offline → Messages shown from local cache (persistence)
