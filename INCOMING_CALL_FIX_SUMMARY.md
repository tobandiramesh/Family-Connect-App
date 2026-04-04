# Incoming Call UI Fix - Implementation Summary

## Overview
Fixed critical issues preventing incoming call notifications from working when the app is minimized. The solution implements three essential components:

---

## 🔧 Changes Implemented

### 1. **FCM Token Persistence** ✅
**File:** [FCMService.kt](app/src/main/java/com/familyconnect/app/notifications/FCMService.kt)

**Problem:** FCM tokens were generated but never saved to Firebase, preventing the server from sending targeted push notifications.

**Solution:**
- Added `saveFCMTokenForUser(context: Context, mobile: String)` static method
- Saves token to Firebase at: `/users/{mobile}/fcm_token`
- Auto-saves token on refresh if user is logged in
- Stores mobile number in SharedPreferences for persistence

**New Methods:**
```kotlin
fun saveFCMTokenForUser(context: Context, mobile: String) {
    // Gets current FCM token and saves to Firebase at /users/{mobile}/fcm_token
}

private fun saveFCMTokenToFirebase(token: String, mobile: String) {
    // Persists token with error handling and local backup
}
```

**When it's called:**
- `onNewToken()` - Automatically when Firebase refreshes token
- `FamilyViewModel.login()` - Immediately after user login
- `FamilyViewModel.init{}` - On app startup for auto-restored sessions

---

### 2. **Wake Lock Renewal Mechanism** ✅
**File:** [CallListenerService.kt](app/src/main/java/com/familyconnect/app/notifications/CallListenerService.kt)

**Problem:** Fixed 10-minute wake lock would expire, killing the background process and stopping call notifications.

**Solution:**
- Changed from `acquire(10 * 60 * 1000L)` to indefinite `acquire()`
- Added automatic renewal every 5 minutes
- Uses coroutine job to refresh the lock without timing out

**Before (❌ fails after 10 min):**
```kotlin
wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes max
```

**After (✅ indefinite with renewal):**
```kotlin
wakeLock?.acquire() // Indefinite
startWakeLockRenewal() // Renews every 5 minutes
```

**New Methods:**
```kotlin
private var wakeLockrenewalJob: Job? = null

private fun startWakeLockRenewal() {
    // Cancels existing job and starts new 5-minute renewal loop
}

private fun launchWakeLockRenewal(): Job {
    // Renews wake lock every 5 minutes indefinitely
}
```

**Renewal Cycle:**
```
Time 0min ──> acquire() + startRenewal()
Time 5min ──> release() + re-acquire() + log
Time 10min ─> release() + re-acquire() + log
Time 15min ─> release() + re-acquire() + log
... continues indefinitely while service is running
```

---

### 3. **FCM Token Save on User Login** ✅
**File:** [FamilyViewModel.kt](app/src/main/java/com/familyconnect/app/ui/FamilyViewModel.kt)

**Problem:** Token was refreshed but user's mobile number wasn't known when token was generated.

**Solution:**
- Added `FCMService.saveFCMTokenForUser()` call immediately after login
- Ensures token is saved with correct user mobile number
- Added to both auto-restore login (init block) and manual login

**Locations where FCM token is saved:**

**1. Auto-restore on app startup (init block, ~line 247):**
```kotlin
currentUser = result
repository.setUserOnline(result.mobile, result.name)

// 🔔 CRITICAL FIX: Save FCM token for this user
Log.d("🔥 FamilyViewModel", "💾 Saving FCM token for user: ${result.mobile}")
FCMService.saveFCMTokenForUser(context, result.mobile)
```

**2. Manual login (login() function, ~line 289):**
```kotlin
repository.setUserOnline(result.mobile, result.name)

// 🔔 CRITICAL FIX: Save FCM token for this user
Log.d("🔥 FamilyViewModel", "💾 Saving FCM token for user: ${result.mobile}")
FCMService.saveFCMTokenForUser(context, result.mobile)
```

**Added Import:**
```kotlin
import com.familyconnect.app.notifications.FCMService
```

---

## 📊 How It Works Together

### Data Flow:
```
┌─────────────────────────────────────────┐
│ User Logs In → FamilyViewModel.login()  │
└──────────────┬──────────────────────────┘
               ↓
      ┌────────────────────────┐
      │ repository.setUserOnline()
      │ Set online status in Firebase
      └────────────┬───────────┘
                   ↓
      ┌────────────────────────┐
      │ FCMService.saveFCMTokenForUser()
      │ Get FCM token + Save to /users/{mobile}/fcm_token
      └────────────┬───────────┘
                   ↓
      ┌────────────────────────┐
      │ CallListenerService starts
      │ Maintains indefinite wake lock
      │ Renews every 5 minutes
      └────────────┬───────────┘
                   ↓
      ┌────────────────────────┐
      │ Server sends FCM push when call arrives
      │ (Now has valid FCM token saved)
      │ App wakes up & shows notification
      └────────────────────────┘
```

### Incoming Call Flow (with fixes):
```
SENDER: Creates call request in Firebase DB
           ↓
SERVER: FCM sends notification to /users/{mobile}/fcm_token ← ✅ Now available!
           ↓
FCMService.onMessageReceived() ← ✅ Process wakes up (wake lock active!)
           ↓
NotificationHelper.postIncomingCallNotification()
           ↓
Full-screen call notification shows on lock screen
(even with app minimized) ← ✅ Fixed!
```

---

## 🧪 Testing Checklist

After building the APK, verify all fixes with this checklist:

- [ ] **Test 1: Token Saving**
  - Open app, login with a user mobile number
  - Check Firebase Realtime DB: `database.firebaseapp.com/users/{mobile}/fcm_token`
  - Should show FCM token string
  
- [ ] **Test 2: Auto-Restore Login**
  - Kill app completely
  - Reopen app (should auto-login if session was saved)
  - Check logs for "💾 Saving FCM token for user"
  
- [ ] **Test 3: Wake Lock Renewal**
  - Login, minimize app
  - Send test incoming call after 10+ minutes
  - Notification should still appear (wake lock renewed)
  - Check logs for "🔄 Wake lock renewal" messages
  
- [ ] **Test 4: Incoming Call with App Minimized**
  - Have user1 logged in on device A
  - Have user2 logged in on device B
  - Minimize app on device A completely
  - User2 makes call to user1
  - ✅ Full-screen notification should appear on device A
  - ✅ Sound and vibration should work
  
- [ ] **Test 5: Incoming Call with Screen Off**
  - Enable screen timeout (short duration for testing)
  - Let screen turn off on device A
  - User2 makes call to user1
  - ✅ Incoming call notification should wake screen
  - ✅ Full-screen intent should activate

---

## 📱 Firebase Database Structure

The fixed implementation reads/writes to:

```
/users/{mobile}/fcm_token  ← ✅ Now populated by FCMService
/chats/{threadId}/callRequests/{callId}  ← Call data
/presence/{mobile}/  ← Online status
```

Example Firebase structure after login:
```json
{
  "users": {
    "9999999999": {
      "fcm_token": "exX...abY",  // ← ✅ Saved by FCMService
      "name": "Mom",
      "role": "ADMIN"
    }
  },
  "chats": { ... },
  "presence": { ... }
}
```

---

## ⚠️ Important Notes

### 1. **Cloud Functions Still Needed**
For fully automated FCM:
- Deploy Firebase Cloud Functions that trigger FCM when calls/messages are created
- Without Cloud Functions, FCM only works if backend explicitly sends it
- Current fix ensures the app CAN receive FCM (token is saved), but doesn't auto-trigger sending

### 2. **Wake Lock Drains Battery**
- Indefinite wake lock keeps CPU running
- Only active during active listening
- For production, consider:
  - Timeout after inactivity
  - User preference to disable background notifications
  - Efficient polling instead of listeners

### 3. **Process Death Recovery**
- `CallListenerService` has `START_STICKY` to auto-restart
- Wake lock should keep process alive
- On rare kills, service restarts automatically

### 4. **Token Refresh Handling**
- Tokens may refresh while user is logged in
- `onNewToken()` now auto-saves if user is known
- Tokens may expire if app stays in background too long
- Consider periodic refresh every 7-14 days

---

## 📝 Log Messages to Look For

After implementing fixes, you should see logs like:

**On Login:**
```
🔥 FamilyViewModel: 💾 Saving FCM token for user: 9999999999
FCMService: 📱 Saving FCM token for user: 9999999999
FCMService: ✅ FCM token saved to Firebase for: 9999999999
```

**On Token Refresh:**
```
FCMService: 🔔 FCM token refreshed: exX...
FCMService: 💾 Auto-saving FCM token for: 9999999999
FCMService: ✅ FCM token saved to Firebase for: 9999999999
```

**Wake Lock Renewal:**
```
CallListenerService: 🔋 Wake lock acquired (indefinite)
CallListenerService: 🔄 Wake lock renewal started (every 5 min)
CallListenerService: 🔋 Wake lock refreshed
CallListenerService: 🔋 Wake lock re-acquired
```

---

## 🚀 Next Steps

1. **Build APK:** `./gradlew assembleDebug`
2. **Install:** `adb install app/build/outputs/apk/debug/app-debug.apk`
3. **Test:** Follow testing checklist above
4. **Deploy Cloud Functions:** (For production to auto-send FCM)
5. **Monitor:** Check crash logs and Firebase Realtime DB

---

## 📚 Related Files Modified

| File | Change | Lines |
|------|--------|-------|
| FCMService.kt | Added token persistence | ~40 new lines |
| CallListenerService.kt | Added wake lock renewal | ~50 new lines |
| FamilyViewModel.kt | Added token save on login | ~4 lines per login path |

---

## ✅ All Three Requirements Met

- ✅ **Data Message:** Already implemented, using `.data` payload
- ✅ **DATA-ONLY FCM:** Already implemented, type-based routing  
- ✅ **FirebaseMessagingService:** Already implemented with `onMessageReceived()`
- ✅ **FULL-SCREEN Notification:** Already implemented with `setFullScreenIntent()`
- ✅ **Foreground Service:** Already implemented with `CallListenerService`
- ✅ **AndroidManifest:** Already configured with all permissions
- ✅ **FCM Token Persistence:** ← **NEW - FIXED**
- ✅ **Wake Lock Renewal:** ← **NEW - FIXED**
- ✅ **Token Save on Login:** ← **NEW - FIXED**
