# Family Connect App — Implementation Document

**Version:** 1.0  
**Date:** March 27, 2026  
**Package:** `com.familyconnect.app`  
**Status:** Production Ready

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [System Architecture](#2-system-architecture)
3. [Technology Stack](#3-technology-stack)
4. [Project Structure](#4-project-structure)
5. [Connectivity & Backend Services](#5-connectivity--backend-services)
6. [Data Models](#6-data-models)
7. [Feature Implementation Details](#7-feature-implementation-details)
8. [Use Cases](#8-use-cases)
9. [Screen Flow & Navigation](#9-screen-flow--navigation)
10. [Security & Permissions](#10-security--permissions)
11. [Notification System](#11-notification-system)
12. [Data Flow Diagrams](#12-data-flow-diagrams)
13. [Third-Party Dependencies](#13-third-party-dependencies)
14. [Firebase Database Schema](#14-firebase-database-schema)
15. [Testing Credentials](#15-testing-credentials)
16. [Known Limitations & Future Scope](#16-known-limitations--future-scope)

---

## 1. Executive Summary

**Family Connect App** (branded "Bandi Family Connect") is a native Android application designed for families to stay connected through real-time messaging, audio calling, calendar management, task tracking, media sharing, and shared notes. The app uses **Firebase Realtime Database** as its cloud backend for real-time communication, **WebRTC** for peer-to-peer audio calls, **Room** for local user storage, and **Jetpack Compose** with Material Design 3 for a modern, responsive UI.

### Key Capabilities
- Real-time 1:1 chat with presence tracking, read receipts, and media attachments
- Peer-to-peer audio calling via WebRTC with Firebase signaling
- Family calendar with event creation, color tagging, and auto-expiry
- Task management with role assignment and reward points
- Shared media gallery and collaborative notes
- Admin-controlled user management with role-based access (PARENT, CHILD, ADMIN)
- Dark mode, large text accessibility, and language preferences

---

## 2. System Architecture

### 2.1 Architecture Pattern: MVVM + Repository

```
┌─────────────────────────────────────────────────────────┐
│                    UI LAYER (Compose)                    │
│  FamilyConnectRoot.kt                                   │
│  ┌──────────┐ ┌──────────┐ ┌───────────┐ ┌───────────┐ │
│  │AuthScreen│ │ChatScreen│ │AudioCall  │ │Settings   │ │
│  │HomeScreen│ │MediaScr  │ │IncomingCall│ │Calendar   │ │
│  │Dashboard │ │NotesScr  │ │VideoCall  │ │Tasks      │ │
│  └──────────┘ └──────────┘ └───────────┘ └───────────┘ │
└────────────────────┬────────────────────────────────────┘
                     │  Compose State / StateFlow
┌────────────────────▼────────────────────────────────────┐
│                 VIEWMODEL LAYER                          │
│  FamilyViewModel.kt                                     │
│  ┌───────────┐ ┌──────────┐ ┌────────────────────────┐  │
│  │State Mgmt │ │Coroutines│ │WebRTCManager           │  │
│  │(mutableSt)│ │(viewModel│ │(Peer connections,      │  │
│  │           │ │   Scope) │ │ SDP, ICE candidates)   │  │
│  └───────────┘ └──────────┘ └────────────────────────┘  │
└────────────────────┬────────────────────────────────────┘
                     │  Repository interface
┌────────────────────▼────────────────────────────────────┐
│                 REPOSITORY LAYER                         │
│  FamilyRepository.kt                                    │
│  ┌──────────────────────────────────────────────────┐   │
│  │ Aggregates local & remote data sources           │   │
│  │ Delegates Firebase ops to FirebaseService        │   │
│  │ Manages DataStore preferences                    │   │
│  │ Manages in-memory event/task/media/note lists    │   │
│  └──────────────────────────────────────────────────┘   │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│                   DATA LAYER                             │
│  ┌──────────────┐ ┌───────────────┐ ┌────────────────┐  │
│  │ Room Database│ │FirebaseService│ │DataStore Prefs │  │
│  │ (UserDao,    │ │(Realtime DB,  │ │(dark mode,     │  │
│  │  UserEntity) │ │ Storage,      │ │ language,      │  │
│  │              │ │ Signaling)    │ │ large text,    │  │
│  │              │ │               │ │ admin PIN)     │  │
│  └──────────────┘ └───────────────┘ └────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### 2.2 Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| Single Activity + Compose | Modern Android architecture; eliminates Fragment lifecycle complexity |
| Firebase Realtime DB (not Firestore) | Lower latency for real-time chat and call signaling |
| WebRTC for audio | True P2P audio; no server-side media processing needed |
| Firebase as signaling server | Eliminates need for custom WebSocket server; leverages existing infrastructure |
| Room for user whitelist | Offline-capable user authentication; fast local lookups |
| DataStore for preferences | Type-safe, coroutine-based replacement for SharedPreferences |
| MutableStateFlow for local data | Events, tasks, media, notes stored in-memory (no cloud sync for these features yet) |

---

## 3. Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Kotlin | 1.9.24 |
| UI Framework | Jetpack Compose + Material 3 | BOM 2024.06.00 |
| Build System | Gradle (Kotlin DSL) | 9.0.0 |
| Android Gradle Plugin | AGP | 8.4.2 |
| Min SDK | Android 7.0 | API 24 |
| Target SDK | Android 14 | API 34 |
| JDK | Java 17 | - |
| Cloud Database | Firebase Realtime Database | BOM 33.1.0 |
| Cloud Storage | Firebase Storage | BOM 33.1.0 |
| Cloud Auth | Firebase Auth (included but not active) | BOM 33.1.0 |
| Local Database | Room | 2.6.1 |
| Preferences | DataStore Preferences | 1.1.1 |
| Audio/Video Calls | WebRTC | 125.6422.07 |
| Async | Kotlin Coroutines + Flow | 1.8.1 |
| Navigation | Jetpack Navigation Compose | 2.7.7 |
| Desugaring | desugar_jdk_libs | 2.1.2 |

---

## 4. Project Structure

```
app/src/main/java/com/familyconnect/app/
├── FamilyConnectApp.kt              # Application class — initializes Room DB + Repository
├── MainActivity.kt                  # Single Compose activity — requests permissions
│
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt           # Room database ("family_connect.db")
│   │   ├── UserDao.kt               # DAO: observeUsers, getByMobile, insert, countUsers
│   │   └── UserEntity.kt            # Entity: id, name, mobile, role (unique index on mobile)
│   ├── model/
│   │   ├── FamilyModels.kt          # All data classes (UserProfile, FamilyEvent, TaskItem,
│   │   │                            #   ChatMessage, ChatThread, ChatMessageData, OnlineUser,
│   │   │                            #   MediaItem, NoteItem)
│   │   └── FamilyRole.kt            # Enum: PARENT, CHILD, ADMIN
│   └── repository/
│       ├── FamilyRepository.kt      # Main repository — aggregates all data sources
│       └── FirebaseService.kt       # Firebase Realtime DB operations (singleton object)
│
├── notifications/
│   └── NotificationHelper.kt        # Local notification channel + post helper
│
├── ui/
│   ├── FamilyViewModel.kt           # Central ViewModel — all state management
│   ├── FamilyViewModelFactory.kt    # ViewModelProvider.Factory
│   ├── FamilyConnectRoot.kt         # All Compose screens (~2,300 lines)
│   └── theme/
│       ├── Color.kt                 # Color palette (Indigo primary, Emerald secondary)
│       ├── Theme.kt                 # Material3 light/dark theme
│       └── Type.kt                  # Typography definitions
│
└── webrtc/
    ├── CallModels.kt                # CallStatus enum, CallRequest, CallSignaling,
    │                                #   IceCandidateData, CallState
    └── WebRTCManager.kt             # WebRTC peer connection, SDP, ICE, audio management
```

---

## 5. Connectivity & Backend Services

### 5.1 Firebase Realtime Database

The primary cloud backend for all real-time features. The app establishes persistent WebSocket connections to Firebase for instant data synchronization.

**Connection Lifecycle:**
1. App starts → Firebase SDK auto-connects
2. User logs in → `setUserOnline()` writes presence data
3. `onDisconnect()` hooks registered — Firebase server automatically marks user offline on network loss, app kill, or crash
4. Real-time listeners (`ValueEventListener`) active for: online users, chat threads, messages, call requests, call signaling
5. User logs out or app exits → `setUserOffline()` called explicitly

**Firebase Services Used:**

| Service | Purpose | Reference Path |
|---------|---------|---------------|
| Realtime Database | Presence tracking | `presence/{mobile}/` |
| Realtime Database | Chat thread metadata | `chats/{threadId}/` |
| Realtime Database | Chat messages | `messages/{threadId}/{messageId}/` |
| Realtime Database | Call requests | `chats/{threadId}/callRequests/{callId}/` |
| Realtime Database | Call signaling (SDP/ICE) | `chats/{threadId}/signaling/{callId}/` |
| Realtime Database | Legacy call mirror | `calls/{threadId}/{callId}/` |
| Realtime Database | Legacy signaling mirror | `signaling/{threadId}/{callId}/` |
| Firebase Storage | Media file uploads | `chats/{threadId}/{timestamp}_{filename}` |
| Firebase Auth | Available (not actively used) | — |

**Real-Time Data Listeners (Active Flows):**

| Flow | Source | Trigger |
|------|--------|---------|
| `observeOnlineUsers()` | `presence/` | Any user signs on/off |
| `observeUserChatThreads(mobile)` | `chats/` | New thread created or thread metadata updated |
| `observeThreadMessages(threadId)` | `messages/{threadId}/` | New message sent in thread |
| `observeCallRequests(userId)` | `chats/*/callRequests/` | New call request targeting this user |
| `observeCallById(threadId, callId)` | `chats/{threadId}/callRequests/{callId}` | Call status change (accepted/rejected/ended) |
| `observeCallSignaling(threadId, callId)` | `chats/{threadId}/signaling/{callId}` | New SDP offer/answer or ICE candidate |

### 5.2 WebRTC (Peer-to-Peer Audio)

WebRTC is used for real-time audio calls between two users. Firebase Realtime Database serves as the signaling server for SDP and ICE candidate exchange.

**ICE Server Configuration:**

| Type | Server | Purpose |
|------|--------|---------|
| STUN | `stun:stun.l.google.com:19302` | NAT traversal — discover public IP |
| STUN | `stun:stun1.l.google.com:19302` | Redundant STUN server |
| TURN | `turn:openrelay.metered.ca:80` (UDP) | Relay fallback for strict NATs |
| TURN | `turn:openrelay.metered.ca:443` (UDP) | Relay over HTTPS port |
| TURN | `turn:openrelay.metered.ca:443?transport=tcp` | TCP relay for restricted networks |

**Audio Constraints:**
- Echo Cancellation (AEC): enabled
- Auto Gain Control (AGC): enabled
- Noise Suppression: enabled
- High-Pass Filter: enabled

**PeerConnection Flow:**
```
Caller                    Firebase                    Callee
  │                          │                          │
  ├──createOffer()──────────►│                          │
  │  (SDP Offer)             ├──────────────────────────► observes signaling
  │                          │                          │
  │                          │◄──handleRemoteOffer()────┤
  │                          │   (creates SDP Answer)   │
  │  observes signaling ◄────┤                          │
  ├──handleRemoteAnswer()    │                          │
  │                          │                          │
  ├──ICE candidates──────────►◄──ICE candidates─────────┤
  │                          │                          │
  │◄═══════════════ P2P Audio Connection ══════════════►│
```

### 5.3 Room Database (Local)

**Database:** `family_connect.db`

Used exclusively for user whitelist management. The admin registers family members' mobile numbers; only whitelisted users can log in.

| Table | Columns | Indexes |
|-------|---------|---------|
| `users` | `id` (PK, auto), `name`, `mobile`, `role` | Unique index on `mobile` |

**DAO Operations:**
- `observeUsers()` → `Flow<List<UserEntity>>` (reactive)
- `getByMobile(mobile)` → `UserEntity?` (login lookup)
- `insert(user)` → adds new user (REPLACE on conflict)
- `countUsers()` → `Int` (seed check)

### 5.4 DataStore Preferences

Key-value preferences stored locally using Jetpack DataStore:

| Key | Type | Default | Purpose |
|-----|------|---------|---------|
| `dark_mode` | Boolean | `false` | Toggle dark theme |
| `language` | String | `"en"` | Selected language |
| `large_text` | Boolean | `false` | Accessibility large text mode |
| `admin_setup_pin` | String | `"2468"` | PIN required for admin user management |

### 5.5 Firebase Storage

Used for uploading media files (images, documents) shared in chat conversations.

**Upload Flow:**
1. User picks a file via Android file picker / camera
2. `uploadFileToStorage(fileUri, fileName)` called
3. File uploaded to `chats/{threadId}/{timestamp}_{filename}`
4. Progress tracked via `addOnProgressListener`
5. On success, download URL retrieved via `fileRef.downloadUrl`
6. Download URL sent as `mediaUri` in the chat message

---

## 6. Data Models

### 6.1 Core Data Classes

```kotlin
// User identity (from Room DB)
data class UserProfile(
    val id: Long,
    val name: String,
    val mobile: String,         // unique identifier, used as Firebase key
    val role: FamilyRole        // PARENT, CHILD, ADMIN
)

// Calendar event (in-memory, auto-expires after 3 days)
data class FamilyEvent(
    val id: Int,
    val title: String,
    val dateTime: String,       // formatted date-time string
    val colorTag: String,       // "Blue", "Green", etc.
    val recurring: Boolean,
    val reminderMinutes: Int,
    val createdAtEpochMillis: Long
)

// Task with assignment and rewards
data class TaskItem(
    val id: Int,
    val title: String,
    val assignedTo: String,     // role name (e.g., "CHILD", "PARENT")
    val dueDate: String,
    val completed: Boolean,
    val rewardPoints: Int?      // nullable — not all tasks have rewards
)

// Firebase chat thread metadata
data class ChatThread(
    val threadId: String,       // "{mobile1}_{mobile2}" (sorted)
    val participant1Mobile: String,
    val participant2Mobile: String,
    val participant1Name: String,
    val participant2Name: String,
    val lastMessage: String,
    val lastMessageTime: Long,
    val createdAt: Long,
    val unreadCount: Int        // per-user unread count
)

// Firebase chat message
data class ChatMessageData(
    val messageId: String,      // UUID
    val senderMobile: String,
    val senderName: String,
    val body: String,
    val timestamp: Long,
    val read: Boolean,
    val mediaUri: String?,      // Firebase Storage download URL
    val replyToMessageId: String?,
    val replyToSenderName: String?,
    val replyToBody: String?
)

// Firebase presence data
data class OnlineUser(
    val mobile: String,
    val name: String,
    val lastSeen: Long,
    val isOnline: Boolean
)

// Media gallery item (in-memory)
data class MediaItem(
    val id: Int,
    val title: String,
    val mediaType: String,      // "Photo", "Video"
    val uri: String,
    val uploadedBy: String,
    val timestamp: String
)

// Shared note (in-memory)
data class NoteItem(
    val id: Int,
    val title: String,
    val content: String,
    val editedBy: String,
    val editedAt: String
)
```

### 6.2 Call State Models

```kotlin
enum class CallStatus {
    IDLE,           // No active call
    REQUESTING,     // Outgoing call request sent, waiting for callee
    RINGING,        // Incoming call detected
    CONNECTING,     // Call accepted, WebRTC negotiation in progress
    ACTIVE,         // Audio call connected and ongoing
    ENDED           // Call terminated
}

data class CallRequest(
    val callId: String,
    val fromUserId: String,     // caller's mobile
    val fromUserName: String,
    val toUserId: String,       // callee's mobile
    val threadId: String,
    val createdAt: Long,
    val status: String          // "pending", "accepted", "rejected", "ended"
)

data class CallState(
    val status: CallStatus,
    val incomingCallRequest: CallRequest?,
    val activeCallId: String?,
    val activeThreadId: String?,
    val activeCallPartyName: String?,
    val callDuration: Long,       // milliseconds
    val localAudioEnabled: Boolean,
    val remoteAudioEnabled: Boolean,
    val isCallConnected: Boolean
)
```

### 6.3 User Role Enum

```kotlin
enum class FamilyRole {
    PARENT,     // Standard parent user
    CHILD,      // Child user
    ADMIN       // Administrator — can manage users, change setup PIN
}
```

---

## 7. Feature Implementation Details

### 7.1 Authentication & User Management

**Implementation Files:** `FamilyViewModel.kt`, `FamilyRepository.kt`, `UserDao.kt`, `AuthScreen` composable

**How It Works:**
- **Whitelist-based login:** Only pre-registered mobile numbers can log in
- **Default seed users:** On first launch, 3 users auto-created:
  - Admin: `9999999999` (Family Admin)
  - Parent: `8888888888` (Mom)
  - Child: `7777777777` (Kid)
- **Admin registration:** Admin user can add new family members via Settings screen, protected by a 4+ digit PIN (default: `2468`)
- **No password required:** Login is mobile-number-only (family trust model)

**Login Flow:**
```
User enters mobile → login() → UserDao.getByMobile() 
  → if found: set currentUser, call setUserOnline(), start observing calls/threads
  → if not found: show "This mobile number is not allowed to use the app"
```

**Registration Flow (Admin only):**
```
Admin enters PIN → isAdminSetupAuthorized() validates
  → Enter name + mobile + role → register() → UserDao.insert()
  → Mobile uniqueness enforced by Room unique index
```

---

### 7.2 Real-Time Chat

**Implementation Files:** `FirebaseService.kt`, `FamilyRepository.kt`, `FamilyViewModel.kt`, `ChatScreen` composable

**Architecture:**
- 1:1 direct messaging between any two family members
- Thread-based: each pair of users shares a single chat thread
- Thread ID format: `{smallerMobile}_{largerMobile}` (deterministic)
- Messages stored at `messages/{threadId}/{messageId}/` in Firebase

**Features:**

| Feature | Implementation |
|---------|---------------|
| Send text message | `sendChatMessageToThread()` → `FirebaseService.sendMessage()` |
| Media attachments | File picker → `uploadFileToStorage()` → Firebase Storage → send download URL |
| Message replies | Tap message → set `replyTo` fields → sent with message data |
| Emoji picker | 32 pre-defined emoji quick-select panel |
| Read receipts | `markMessagesAsRead()` — sets `read: true` on all non-self messages |
| Unread badges | `unread_{mobile}` counter on thread metadata, incremented per send, reset on read |
| Online presence | Green dot + "Online" / "Last seen X min ago" |
| Auto-scroll | `LazyColumn` scrolls to bottom on thread open and new message |

**Message Send Flow:**
```
User types text → sendChatMessageToThread()
  → FamilyRepository.sendChatMessage()
  → FirebaseService.sendMessage()
    → Write to messages/{threadId}/{messageId}
    → Update chats/{threadId}/lastMessage, lastMessageTime
    → Increment chats/{threadId}/unread_{recipientMobile}
```

**Thread Creation Flow:**
```
User taps on online user → openDirectMessage()
  → createOrGetChatThread()
    → Generate threadId from sorted mobiles
    → Check Firebase: exists? → return threadId
    → Doesn't exist? → Write thread metadata → return threadId
  → selectChatThread() → start observing messages
```

---

### 7.3 Audio Calling (WebRTC)

**Implementation Files:** `WebRTCManager.kt`, `CallModels.kt`, `FamilyViewModel.kt`, `FirebaseService.kt`, `AudioCallScreen` + `IncomingCallOverlay` composables

**Architecture:**
- Peer-to-peer audio using WebRTC native library
- Firebase Realtime Database as the signaling server
- No server-side media relay (except TURN servers for NAT traversal)

**Call Initiation Flow:**
```
1. Caller taps call button → initiateCallForSelectedThread()
2. Resolve recipient mobile from thread data
3. Generate callId (UUID) → set CallStatus.REQUESTING
4. Write CallRequest to chats/{threadId}/callRequests/{callId}
   status: "pending"
5. Caller starts observeActiveCall() for status changes

6. Callee's observeCallRequests() fires
7. Callee sees pending request → CallStatus.RINGING
8. IncomingCallOverlay shows with ringtone + notification
```

**Call Accept Flow:**
```
1. Callee taps Accept → acceptCall()
2. Update call status to "accepted" in Firebase
3. Both sides enter CallStatus.CONNECTING
4. initializeWebRtcSession() on both sides:
   a. Create PeerConnection with STUN/TURN servers
   b. Attach local audio track (mic)
   c. Register callbacks for SDP + ICE
5. Caller creates SDP offer → sends via Firebase signaling
6. Callee receives offer → creates SDP answer → sends via Firebase
7. Both exchange ICE candidates via Firebase
8. PeerConnection.IceConnectionState.CONNECTED → CallStatus.ACTIVE
9. Call timer starts (1-second intervals)
```

**Call End Flow:**
```
1. Either party taps End → endCall()
2. Update call status to "ended" in Firebase
3. Other party observes status change
4. Both sides: stopAudioCall(), dispose peer connection
5. Reset callState → CallStatus.ENDED → (1.2s delay) → CallStatus.IDLE
```

**Call Controls:**
- **Mute microphone:** `toggleLocalAudio(false)` — disables local audio track + mutes system mic
- **Toggle speaker:** `toggleRemoteAudio(true/false)` — switches between earpiece and speaker
- **Auto-timeout:** 25-second connection timeout; if WebRTC doesn't connect, call auto-ends

**Call Rejection Flow:**
```
Callee taps Reject → rejectCall()
  → Update status to "rejected"
  → Caller observes → resetCallState()
```

---

### 7.4 Calendar & Events

**Implementation Files:** `FamilyRepository.kt`, `FamilyViewModel.kt`, `CalendarScreen` composable

**Storage:** In-memory `MutableStateFlow<List<FamilyEvent>>` (not persisted to Firebase)

**Features:**
- Create events with title, date/time, color tag, recurring flag, reminder duration
- Events auto-expire after 3 days from creation (`eventExpiryWindowMillis`)
- Color tags: Blue, Green, and other user-selected colors
- Reminder minutes: 15, 30, 60, etc. (metadata stored; local notification on creation)
- `eventDaysLeft()` calculates remaining days before expiry

**Default Seed Events:**
- "Parent Meeting" — 2026-03-28, Blue, non-recurring, 30-min reminder
- "Sunday Dinner" — 2026-03-29, Green, recurring, 60-min reminder

---

### 7.5 Task Management

**Implementation Files:** `FamilyRepository.kt`, `FamilyViewModel.kt`, `TasksScreen` composable

**Storage:** In-memory `MutableStateFlow<List<TaskItem>>`

**Features:**
- Create tasks with title, assigned role, due date, and optional reward points
- Toggle task completion status
- Filter by complete/pending states
- Assigned to roles (PARENT, CHILD), not specific users

**Default Seed Tasks:**
- "Clean study room" — assigned to CHILD, 20 reward points
- "Pay electricity bill" — assigned to PARENT, no reward points

---

### 7.6 Media Gallery

**Implementation Files:** `FamilyRepository.kt`, `FamilyViewModel.kt`, `MediaScreen` composable

**Storage:** In-memory `MutableStateFlow<List<MediaItem>>`

**Features:**
- Add media with title, type (Photo/Video), URI, and uploader name
- View shared media items in a list

**Note:** Media items added here are stored in-memory only. Chat media attachments use Firebase Storage with persistent download URLs.

---

### 7.7 Shared Notes

**Implementation Files:** `FamilyRepository.kt`, `FamilyViewModel.kt`, `NotesScreen` composable

**Storage:** In-memory `MutableStateFlow<List<NoteItem>>`

**Features:**
- Create notes with title and content
- View all shared notes
- Tracks editor name and edit timestamp

---

### 7.8 Settings & Preferences

**Implementation Files:** `FamilyRepository.kt`, `FamilyViewModel.kt`, `SettingsScreen` composable

| Setting | Storage | Values |
|---------|---------|--------|
| Dark Mode | DataStore | Boolean on/off |
| Language | DataStore | "en", "es", "hi" |
| Large Text (Accessibility) | DataStore | Boolean on/off |
| Admin Setup PIN | DataStore | String (min 4 digits) |
| User Management | Room DB | Add/remove users (admin only) |
| Logout | ViewModel state | Clears currentUser, calls setUserOffline |

---

### 7.9 Dashboard

**Implementation Files:** `DashboardScreen` composable

**Features:**
- Quick-access tiles for all features (Calendar, Tasks, Chat, Media, Notes, Settings)
- Shows unread message count badge
- Tile-based navigation with icons

---

## 8. Use Cases

### UC-01: First-Time Setup (Admin Registration)

| Field | Description |
|-------|-------------|
| **Actor** | Family Administrator |
| **Precondition** | Fresh app install, no users in database |
| **Flow** | 1. App launches → AuthScreen shown<br>2. App auto-seeds default users (Admin: 9999999999, Mom: 8888888888, Kid: 7777777777)<br>3. Admin logs in with mobile 9999999999<br>4. Navigates to Settings → Admin Setup<br>5. Enters admin mobile and PIN (2468)<br>6. Adds new family member: name, mobile, role<br>7. Family member can now log in |
| **Postcondition** | New user stored in Room DB, can log in |

### UC-02: User Login

| Field | Description |
|-------|-------------|
| **Actor** | Any registered family member |
| **Precondition** | Mobile number exists in Room DB |
| **Flow** | 1. User enters mobile number on AuthScreen<br>2. `login()` → `UserDao.getByMobile()`<br>3. If found: set as currentUser, mark online in Firebase<br>4. Navigate to HomeScreen (Chat tab)<br>5. Start observing incoming calls and chat threads |
| **Alt Flow** | If mobile not found → show error "This mobile number is not allowed" |
| **Postcondition** | User logged in, online presence visible to others |

### UC-03: Send a Chat Message

| Field | Description |
|-------|-------------|
| **Actor** | Any logged-in user |
| **Precondition** | User is logged in, at least one other user registered |
| **Flow** | 1. User sees list of online/offline users on ChatScreen<br>2. Taps on a user → `openDirectMessage()`<br>3. Chat thread created/retrieved from Firebase<br>4. Types message in text field<br>5. Taps send → `sendChatMessageToThread()`<br>6. Message written to Firebase → recipient sees it in real-time<br>7. Unread badge incremented for recipient |
| **Postcondition** | Message persisted in Firebase, visible to both parties |

### UC-04: Send a Message with Media Attachment

| Field | Description |
|-------|-------------|
| **Actor** | Any logged-in user |
| **Precondition** | Active chat thread open |
| **Flow** | 1. User taps attachment icon (image/document/camera)<br>2. Android file picker / camera opens<br>3. User selects file → `uploadFileToStorage()`<br>4. File uploaded to Firebase Storage with progress tracking<br>5. Download URL obtained<br>6. Message sent with `mediaUri` = download URL<br>7. Recipient sees message with media link |
| **Postcondition** | File in Firebase Storage, message with link in chat |

### UC-05: Reply to a Message

| Field | Description |
|-------|-------------|
| **Actor** | Any logged-in user |
| **Precondition** | Viewing a chat thread with existing messages |
| **Flow** | 1. User long-presses on a message to select it for reply<br>2. Reply preview shows quoted sender name and body<br>3. User types reply text<br>4. Taps send → message sent with `replyToMessageId`, `replyToSenderName`, `replyToBody`<br>5. Message displayed with quote bubble |
| **Postcondition** | Reply message with reference to original stored in Firebase |

### UC-06: Make an Audio Call

| Field | Description |
|-------|-------------|
| **Actor** | Caller (any logged-in user) |
| **Precondition** | Active chat thread with another user, RECORD_AUDIO permission granted |
| **Flow** | 1. Caller taps phone icon in chat header<br>2. `initiateCallForSelectedThread()` resolves recipient<br>3. CallRequest written to Firebase (status: "pending")<br>4. Caller sees AudioCallScreen with "Calling..." state<br>5. Callee receives call notification + IncomingCallOverlay<br>6. Callee taps Accept → status updated to "accepted"<br>7. WebRTC initialized on both sides<br>8. SDP offer/answer + ICE candidates exchanged via Firebase<br>9. Audio connection established → CallStatus.ACTIVE<br>10. Call timer starts counting |
| **Postcondition** | Active P2P audio call between two devices |

### UC-07: Receive and Accept an Incoming Call

| Field | Description |
|-------|-------------|
| **Actor** | Callee (any logged-in user) |
| **Precondition** | User logged in, call observers active |
| **Flow** | 1. Caller initiates call (UC-06)<br>2. Callee's `observeCallRequests()` detects pending request<br>3. CallStatus → RINGING, IncomingCallOverlay displayed<br>4. System notification posted: "{name} is calling..."<br>5. Callee taps Accept → `acceptCall()`<br>6. Call status updated to "accepted" in Firebase<br>7. WebRTC session initialized, SDP negotiation begins<br>8. Call connects → AudioCallScreen shown with timer |
| **Postcondition** | Callee in active audio call |

### UC-08: Reject an Incoming Call

| Field | Description |
|-------|-------------|
| **Actor** | Callee |
| **Precondition** | Incoming call overlay visible |
| **Flow** | 1. Callee taps Reject → `rejectCall()`<br>2. Call status updated to "rejected" in Firebase<br>3. Caller's observer sees rejection → call state reset<br>4. Both devices return to CallStatus.IDLE |
| **Postcondition** | Call cancelled, no audio connection |

### UC-09: End an Active Call

| Field | Description |
|-------|-------------|
| **Actor** | Either party in an active call |
| **Precondition** | CallStatus is ACTIVE |
| **Flow** | 1. User taps End Call button → `endCall()`<br>2. Call status updated to "ended" in Firebase<br>3. Other party observes → resetCallState()<br>4. WebRTC peer connection disposed, audio stopped<br>5. Both devices show CallStatus.ENDED briefly, then IDLE |
| **Postcondition** | Call terminated, resources cleaned up |

### UC-10: Control Call Audio

| Field | Description |
|-------|-------------|
| **Actor** | Any participant in an active call |
| **Precondition** | CallStatus is ACTIVE |
| **Flow** | **Mute:** Tap mute button → `toggleLocalAudio(false)` → local audio track disabled, system mic muted<br>**Unmute:** Tap again → `toggleLocalAudio(true)`<br>**Speaker On:** Tap speaker → `toggleRemoteAudio(true)` → audio routed to speaker<br>**Speaker Off:** Tap again → `toggleRemoteAudio(false)` → audio routed to earpiece |
| **Postcondition** | Audio controls updated in real-time |

### UC-11: Create a Calendar Event

| Field | Description |
|-------|-------------|
| **Actor** | Any logged-in user |
| **Precondition** | User on CalendarScreen |
| **Flow** | 1. Tap "Add Event" or FAB button<br>2. Enter title, select date/time<br>3. Choose color tag, recurring flag, reminder duration<br>4. Tap Save → `addEvent()`<br>5. Event added to in-memory list<br>6. Local notification posted: "Event added: {title}" |
| **Postcondition** | Event visible in calendar, auto-expires after 3 days |

### UC-12: Create and Complete a Task

| Field | Description |
|-------|-------------|
| **Actor** | Any logged-in user |
| **Precondition** | User on TasksScreen |
| **Flow** | 1. Tap "Add Task"<br>2. Enter title, select assigned role, due date, optional reward points<br>3. Tap Save → `addTask()`<br>4. Task appears in pending list<br>5. Local notification: "Task assigned: {title}"<br>6. Later: tap checkbox → `toggleTask()` → task moves to completed |
| **Postcondition** | Task tracked with completion status |

### UC-13: Share Media

| Field | Description |
|-------|-------------|
| **Actor** | Any logged-in user |
| **Precondition** | User on MediaScreen |
| **Flow** | 1. Tap "Add Media"<br>2. Enter title, select type (Photo/Video)<br>3. Provide URI (local reference)<br>4. `addMedia()` → item added to in-memory list |
| **Postcondition** | Media item visible in gallery |

### UC-14: Create a Shared Note

| Field | Description |
|-------|-------------|
| **Actor** | Any logged-in user |
| **Precondition** | User on NotesScreen |
| **Flow** | 1. Tap "Add Note"<br>2. Enter title and content<br>3. `addNote()` → note stored with editor name and timestamp |
| **Postcondition** | Note visible to all users in NotesScreen |

### UC-15: Toggle Dark Mode

| Field | Description |
|-------|-------------|
| **Actor** | Any logged-in user |
| **Precondition** | User on SettingsScreen |
| **Flow** | 1. Toggle Dark Mode switch<br>2. `setDarkMode(enabled)` → DataStore updated<br>3. App theme changes immediately (Material3 DarkColorScheme applied) |
| **Postcondition** | Preference persisted; survives app restart |

### UC-16: Admin Changes Setup PIN

| Field | Description |
|-------|-------------|
| **Actor** | Admin user |
| **Precondition** | Logged in as ADMIN, on SettingsScreen |
| **Flow** | 1. Enter new PIN (min 4 digits)<br>2. Confirm PIN<br>3. `updateAdminSetupPin()` validates match and length<br>4. DataStore updated with new PIN |
| **Postcondition** | New PIN required for future admin operations |

### UC-17: User Logout

| Field | Description |
|-------|-------------|
| **Actor** | Any logged-in user |
| **Precondition** | User is logged in |
| **Flow** | 1. Tap Logout in Settings<br>2. `logout()` → `setUserOffline()` updates Firebase presence<br>3. Clear currentUser, all active threads, call state<br>4. Return to AuthScreen |
| **Postcondition** | User offline, other users see "Last seen..." |

### UC-18: Auto-Offline on App Exit/Crash

| Field | Description |
|-------|-------------|
| **Actor** | System |
| **Precondition** | User was online |
| **Flow** | 1. App killed / network drops / device crashes<br>2. Firebase `onDisconnect()` hooks fire automatically<br>3. `isOnline` set to `false`, `lastSeen` set to server timestamp<br>4. Other users see updated presence |
| **Postcondition** | Accurate online/offline status maintained |

---

## 9. Screen Flow & Navigation

```
┌──────────────┐
│  AuthScreen   │ ← Login with mobile number
│  (Login/Setup)│ ← Admin setup with PIN
└──────┬───────┘
       │ login success
       ▼
┌──────────────┐
│  HomeScreen   │ ← Tab-based: Chat | Settings
│  (Tabs)       │
└──────┬───────┘
       │
  ┌────┴────┐
  ▼         ▼
┌──────┐  ┌──────────┐
│ Chat │  │ Settings  │ ← Dark mode, language, admin, logout
│ Tab  │  │ Tab       │
└──┬───┘  └──────────┘
   │
   ├─► User List (online users + chat threads)
   │     │
   │     ├─► Tap user → openDirectMessage() → Chat Thread View
   │     │     │
   │     │     ├─► Send messages, attachments, emojis, replies
   │     │     ├─► Tap phone icon → Audio Call
   │     │     └─► Tap video icon → Video Call (placeholder)
   │     │
   │     └─► Tap existing thread → Chat Thread View
   │
   ├─► Dashboard tiles → Calendar, Tasks, Media, Notes
   │
   └─► Incoming Call Overlay (shown on top of any screen)
         ├─► Accept → AudioCallScreen
         └─► Reject → dismiss
```

**Screen Composables (all in FamilyConnectRoot.kt):**

| Screen | Composable | Purpose |
|--------|-----------|---------|
| Login/Register | `AuthScreen` | Mobile number login + admin setup |
| Home | `HomeScreen` | Tab container (Chat + Settings) |
| Dashboard | `DashboardScreen` | Feature tiles with badges |
| Chat | `ChatScreen` | User list + message thread |
| Audio Call | `AudioCallScreen` | Active call UI with controls + timer |
| Incoming Call | `IncomingCallOverlay` | Full-screen ring overlay + accept/reject |
| Video Call | `VideoCallScreen` | Placeholder UI (stub) |
| Calendar | `CalendarScreen` | Create/view events |
| Tasks | `TasksScreen` | Create/complete tasks |
| Media | `MediaScreen` | View shared media items |
| Notes | `NotesScreen` | Create/view shared notes |
| Settings | `SettingsScreen` | Preferences + admin controls |

---

## 10. Security & Permissions

### 10.1 Android Permissions

| Permission | Purpose | Runtime Request |
|-----------|---------|-----------------|
| `INTERNET` | Firebase, WebRTC | No (install-time) |
| `ACCESS_NETWORK_STATE` | Network detection | No (install-time) |
| `POST_NOTIFICATIONS` | Local notifications | Yes (Android 13+) |
| `RECORD_AUDIO` | WebRTC audio calls | Yes (Android 6+) |
| `MODIFY_AUDIO_SETTINGS` | Speaker/mic control | No (install-time) |
| `READ_EXTERNAL_STORAGE` | File picker | Yes (Android 6+) |
| `WRITE_EXTERNAL_STORAGE` | Legacy storage | Yes (pre-Android 11) |

### 10.2 Authentication Model

- **Mobile number whitelist:** Only pre-registered numbers can log in
- **No password:** Trust-based family model
- **Admin PIN:** Required for user management operations (default: 2468)
- **Firebase rules:** Currently open (read: true, write: true) — suitable for family-internal use

### 10.3 Data Privacy

- All chat messages stored in Firebase (cloud)
- Media files stored in Firebase Storage
- User whitelist stored locally in Room DB
- No PII transmitted to third parties beyond Firebase
- Presence data (online/offline + last seen) visible to all family members

---

## 11. Notification System

**Implementation:** `NotificationHelper.kt`

**Channel:** `family_connect_updates` ("Family Updates", IMPORTANCE_DEFAULT)

**Notification Triggers:**

| Trigger | Title | Body | ID |
|---------|-------|------|----|
| Event created | "Event added" | Event title | `event.id + 1000` |
| Task assigned | "Task assigned" | Task title | `task.id + 2000` |
| Legacy message | "New message" | "From {sender}" | `message.id + 3000` |
| Chat message received | "Message from {name}" | Message body (first 100 chars) | `message.messageId.hashCode()` |
| Incoming call | "Incoming Call" | "{name} is calling..." | `callData.hashCode()` |

All notifications are local — no Firebase Cloud Messaging (FCM) push notifications are configured.

---

## 12. Data Flow Diagrams

### 12.1 Chat Message Flow

```
┌──────────┐    ┌──────────────┐    ┌─────────────────┐    ┌──────────────────┐
│  Sender   │───►│ FamilyVM     │───►│ FamilyRepository│───►│ FirebaseService  │
│  (Compose │    │ .sendChat    │    │ .sendChatMessage│    │ .sendMessage()   │
│   UI)     │    │ MessageTo    │    │                 │    │                  │
│           │    │ Thread()     │    │                 │    │  ┌──────────────┐│
└──────────┘    └──────────────┘    └─────────────────┘    │  │messages/     ││
                                                            │  │{threadId}/   ││
                                                            │  │{messageId}   ││
                                                            │  └──────┬───────┘│
                                                            │         │        │
                                                            │  ┌──────▼───────┐│
                                                            │  │chats/        ││
┌──────────┐    ┌──────────────┐    ┌─────────────────┐    │  │{threadId}/   ││
│ Receiver  │◄───│ FamilyVM     │◄───│ Flow<List<      │◄───│  │lastMessage   ││
│ (Compose  │    │ currentThread│    │ ChatMessageData>│    │  │unread_{mob}  ││
│  UI)      │    │ Messages     │    │                 │    │  └──────────────┘│
└──────────┘    └──────────────┘    └─────────────────┘    └──────────────────┘
```

### 12.2 Audio Call Signaling Flow

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Firebase Realtime Database                      │
│                                                                        │
│  chats/{threadId}/callRequests/{callId}/                               │
│  ┌─────────────────────────────────────────────┐                       │
│  │ callId, fromUserId, toUserId, threadId      │                       │
│  │ status: pending → accepted → ended          │                       │
│  └─────────────────────────────────────────────┘                       │
│                                                                        │
│  chats/{threadId}/signaling/{callId}/                                  │
│  ┌─────────────────────────────────────────────┐                       │
│  │ offer_{ts}: {type: "offer", sdp, senderId}  │                       │
│  │ answer_{ts}: {type: "answer", sdp, senderId}│                       │
│  │ candidate_{ts}: {type: "candidate",         │                       │
│  │   candidate, sdpMLineIndex, sdpMid, senderId│                       │
│  └─────────────────────────────────────────────┘                       │
└────────────────────────┬───────────────┬───────────────────────────────┘
                         │               │
            ┌────────────▼──┐     ┌──────▼────────────┐
            │  CALLER       │     │  CALLEE            │
            │               │     │                    │
            │ 1. sendCall   │     │ 4. observeCall     │
            │    Request()  │     │    Requests()      │
            │               │     │                    │
            │ 2. observe    │     │ 5. acceptCall()    │
            │    ActiveCall │     │                    │
            │               │     │ 6. initialize      │
            │ 3. accepted → │     │    WebRtcSession   │
            │    initWebRTC │     │    (isCaller=false) │
            │    createOffer│     │                    │
            │               │     │ 7. handleRemote    │
            │ 8. handleRemot│     │    Offer()→Answer  │
            │    eAnswer()  │     │                    │
            │               │     │                    │
            │ ═══ ICE Candidates Exchange ═══          │
            │               │     │                    │
            │ P2P AUDIO ◄═══╪═════╪═══► P2P AUDIO     │
            └───────────────┘     └────────────────────┘
```

### 12.3 Presence Tracking Flow

```
┌───────────┐  login()   ┌───────────────────┐  setUserOnline()  ┌────────────────┐
│ User      │──────────►│ FamilyViewModel    │─────────────────►│ Firebase        │
│ Login     │            │                    │                   │ presence/{mob}/ │
└───────────┘            └───────────────────┘                   │ isOnline: true  │
                                                                  │ lastSeen: now   │
                                                                  │ onDisconnect:   │
                                                                  │  isOnline→false │
                                                                  │  lastSeen→SRVTS │
                                                                  └───────┬────────┘
                                                                          │
     ┌───────────┐  UI update   ┌───────────────────┐  Flow<List<>>     │
     │ Other     │◄────────────│ observeOnlineUsers │◄─────────────────┘
     │ Users     │              │ (ValueEventListener│
     │ See Status│              │  on presence/)     │
     └───────────┘              └───────────────────┘
```

---

## 13. Third-Party Dependencies

### 13.1 Production Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| `androidx.core:core-ktx` | 1.13.1 | Kotlin extensions for Android |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.8.4 | Lifecycle-aware coroutines |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.8.4 | ViewModel integration for Compose |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.8.4 | Compose lifecycle utilities |
| `androidx.activity:activity-compose` | 1.9.1 | ComponentActivity for Compose |
| `androidx.activity:activity-ktx` | 1.9.1 | Activity result APIs for file pickers |
| `androidx.navigation:navigation-compose` | 2.7.7 | Compose navigation |
| `androidx.compose:compose-bom` | 2024.06.00 | Compose Bill of Materials |
| `androidx.compose.ui:ui` | (BOM) | Compose UI core |
| `androidx.compose.material3:material3` | (BOM) | Material Design 3 |
| `androidx.compose.material:material-icons-extended` | (BOM) | Full Material icon set |
| `androidx.room:room-runtime` | 2.6.1 | Room database runtime |
| `androidx.room:room-ktx` | 2.6.1 | Room Kotlin extensions |
| `androidx.datastore:datastore-preferences` | 1.1.1 | Preferences DataStore |
| `com.google.firebase:firebase-bom` | 33.1.0 | Firebase Bill of Materials |
| `com.google.firebase:firebase-database-ktx` | (BOM) | Firebase Realtime Database |
| `com.google.firebase:firebase-auth-ktx` | (BOM) | Firebase Authentication |
| `com.google.firebase:firebase-storage-ktx` | (BOM) | Firebase Cloud Storage |
| `io.github.webrtc-sdk:android` | 125.6422.07 | WebRTC native library |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.8.1 | Kotlin coroutines |
| `com.google.android.material:material` | 1.12.0 | Material Components |
| `androidx.core:core-splashscreen` | 1.0.1 | Splash screen API |
| `com.android.tools:desugar_jdk_libs` | 2.1.2 | Java 8+ API desugaring |

### 13.2 Build Plugins

| Plugin | Purpose |
|--------|---------|
| `com.android.application` | Android application build |
| `org.jetbrains.kotlin.android` | Kotlin for Android |
| `com.google.devtools.ksp` | Kotlin Symbol Processing (Room compiler) |
| `com.google.gms.google-services` | Firebase google-services.json processing |

### 13.3 Test Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| `junit:junit` | 4.13.2 | Unit testing |
| `androidx.test.ext:junit` | 1.2.1 | Android JUnit extensions |
| `androidx.test.espresso:espresso-core` | 3.6.1 | UI testing |
| `androidx.compose.ui:ui-test-junit4` | (BOM) | Compose UI testing |

---

## 14. Firebase Database Schema

### 14.1 Complete Schema

```
Firebase Realtime Database Root
│
├── presence/
│   └── {mobile}/                          # e.g., "8888888888"
│       ├── name: String                   # "Mom"
│       ├── isOnline: Boolean              # true/false
│       └── lastSeen: Long                 # epoch millis (ServerValue.TIMESTAMP on disconnect)
│
├── chats/
│   └── {threadId}/                        # e.g., "7777777777_8888888888"
│       ├── threadId: String
│       ├── participant1Mobile: String
│       ├── participant1Name: String
│       ├── participant2Mobile: String
│       ├── participant2Name: String
│       ├── lastMessage: String
│       ├── lastMessageTime: Long
│       ├── createdAt: Long
│       ├── unread_{mobile}: Int           # e.g., "unread_7777777777": 3
│       │
│       ├── callRequests/
│       │   └── {callId}/                  # UUID
│       │       ├── callId: String
│       │       ├── fromUserId: String     # caller's mobile
│       │       ├── fromUserName: String
│       │       ├── toUserId: String       # callee's mobile
│       │       ├── threadId: String
│       │       ├── status: String         # "pending" | "accepted" | "rejected" | "ended"
│       │       └── createdAt: Long
│       │
│       └── signaling/
│           └── {callId}/
│               ├── offer_{timestamp}/
│               │   ├── type: "offer"
│               │   ├── sdp: String        # SDP description
│               │   ├── senderId: String
│               │   └── timestamp: Long
│               │
│               ├── answer_{timestamp}/
│               │   ├── type: "answer"
│               │   ├── sdp: String
│               │   ├── senderId: String
│               │   └── timestamp: Long
│               │
│               └── candidate_{timestamp}/
│                   ├── type: "candidate"
│                   ├── candidate: String  # ICE candidate SDP
│                   ├── sdpMLineIndex: Int
│                   ├── sdpMid: String
│                   ├── senderId: String
│                   └── timestamp: Long
│
├── messages/
│   └── {threadId}/
│       └── {messageId}/                   # UUID
│           ├── messageId: String
│           ├── senderMobile: String
│           ├── senderName: String
│           ├── body: String
│           ├── timestamp: Long
│           ├── read: Boolean
│           ├── mediaUri: String           # "" or Firebase Storage download URL
│           ├── replyToMessageId: String   # "" or original message ID
│           ├── replyToSenderName: String  # "" or original sender name
│           └── replyToBody: String        # "" or original message text
│
├── calls/                                 # Legacy mirror (backward compatibility)
│   └── {threadId}/
│       └── {callId}/
│           └── (same structure as chats/{threadId}/callRequests/{callId})
│
└── signaling/                             # Legacy mirror (backward compatibility)
    └── {threadId}/
        └── {callId}/
            └── (same structure as chats/{threadId}/signaling/{callId})
```

### 14.2 Firebase Storage Structure

```
Firebase Storage Root
│
└── chats/
    └── {threadId}/
        └── {timestamp}_{filename}         # e.g., "1711545600000_photo.jpg"
```

---

## 15. Testing Credentials

### Default Users (Auto-Seeded)

| Role | Name | Mobile Number |
|------|------|---------------|
| ADMIN | Family Admin | 9999999999 |
| PARENT | Mom | 8888888888 |
| CHILD | Kid | 7777777777 |

### Admin Setup PIN

| Purpose | Default Value |
|---------|---------------|
| Admin User Management | `2468` |

### Firebase Project

- **Project:** Configured via `google-services.json` in `app/` directory
- **Database Rules:** Open (read/write: true) — for family-internal testing
- **Storage Rules:** Default Firebase Storage rules

---

## 16. Known Limitations & Future Scope

### Current Limitations

| Area | Limitation |
|------|-----------|
| Events/Tasks/Media/Notes | Stored in-memory only — data lost on app restart |
| Video Calling | UI placeholder only — no actual video stream implementation |
| Push Notifications | Local notifications only — no FCM push when app is backgrounded |
| Firebase Auth | Included in dependencies but not actively used for authentication |
| Group Chat | Only 1:1 direct messaging supported |
| Message Search | No search functionality within chat history |
| Offline Messages | No offline message queue — requires active connection |
| Language i18n | Language preference stored but strings not fully translated |
| Admin presence | Admin (9999999999) excluded from online user list |

### Future Scope

| Feature | Description |
|---------|-------------|
| Firebase Firestore migration | Move events, tasks, media, notes to cloud for persistence |
| FCM Push Notifications | Background push notifications for messages and calls |
| Video Calling | Implement WebRTC video track alongside audio |
| Group Chat | Multi-participant chat rooms |
| Firebase Auth | Proper authentication with phone number OTP |
| Message Encryption | End-to-end encryption for chat messages |
| Message Search | Full-text search across chat history |
| Offline Support | Local message caching with sync-on-reconnect |
| File Preview | In-app image/document viewer for media attachments |
| Call History | Persist and display call logs |
| Typing Indicators | Show when the other user is typing |
| Message Reactions | Emoji reactions on individual messages |

---

*Document generated: March 27, 2026*  
*App Version: 1.0*  
*Package: com.familyconnect.app*
