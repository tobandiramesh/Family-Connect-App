# Chat Feature Implementation - Firebase Setup Guide

## Overview
The chat feature has been implemented with the following capabilities:
- **Login-based identity**: Chat is tied to mobile number login
- **Online/offline presence tracking**: See which family members are online
- **1:1 direct messaging**: Real-time chat with online family members
- **Message history**: All messages are stored in Firebase Realtime Database
- **Auto-cleanup**: User presence automatically updates to "offline" when disconnected

## Firebase Setup Instructions

### Step 1: Create a Firebase Project
1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Click "Create a new project"
3. Name it "family-connect-app"
4. Disable Google Analytics (or enable if you prefer)
5. Click "Create project"

### Step 2: Register Android App with Firebase
1. In Firebase Console, click "Add app" → "Android"
2. Enter package name: `com.familyconnect.app`
3. Enter app nickname: `Family Connect`
4. Click "Register app"
5. Download the `google-services.json` file
6. **IMPORTANT**: Replace the placeholder `app/google-services.json` in your project with the downloaded file

### Step 3: Enable Realtime Database
1. In Firebase Console, go to "Build" → "Realtime Database"
2. Click "Create Database"
3. Choose location closest to you
4. **START IN TEST MODE** for development (required for the app to work without auth setup)
5. Click "Enable"

### Step 4: Set Firebase Realtime Database Rules
1. In Realtime Database, go to the "Rules" tab
2. Replace the default rules with:

```json
{
  "rules": {
    ".read": true,
    ".write": true,
    "presence": {
      ".indexOn": ["mobile", "status", "lastSeen"]
    },
    "chats": {
      ".indexOn": ["threadId", "participant1Mobile", "participant2Mobile", "lastMessageTime"]
    },
    "messages": {
      ".indexOn": ["timestamp", "senderMobile", "read"]
    },
    "typing": {
      ".indexOn": ["timestamp"]
    }
  }
}
```

3. Click "Publish"

⚠️ **Security Note**: These rules allow all read/write access. For production:
- Implement proper Firebase Authentication
- Add user-specific security rules that verify user identity before allowing read/write
- Use `.uid` from Firebase Auth to restrict data access

### Step 5: Enable Firebase Storage
1. In Firebase Console, go to "Build" → "Storage"
2. Click "Get Started"
3. Accept the default bucket location (same as your Realtime Database)
4. Click "Done"
5. Go to the "Rules" tab
6. Replace the default rules with:

```
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /chats/{threadId}/{fileName} {
      // Allow anyone to read and write to chat files
      allow read: if true;
      allow write: if true;
    }
  }
}
```

7. Click "Publish"

⚠️ **Security Note**: These Storage rules allow all access. For production:
- Implement Firebase Authentication first
- Restrict uploads to authenticated users
- Validate file sizes and types before allowing uploads
- Set storage quotas per user/thread

### Step 6: Encode Project ID in Code
The Firebase SDK reads your configuration from `google-services.json`. The SDK automatically initializes Firebase when the app starts.

## How the Chat Feature Works

### Architecture
- **Presence System**: `/presence/{mobile}` stores online/offline status
- **Chat Threads**: `/chats/{threadId}` stores conversation metadata
- **Messages**: `/messages/{threadId}/{messageId}` stores individual messages

### User Flow
1. User logs in with mobile number → `setUserOnline(mobile, name)` called
2. App observes `/presence` to show online family members
3. User selects an online member → creates/gets chat thread
4. Chat thread displays real-time messages
5. User types and sends message → stored in `/messages/{threadId}`
6. User logs out → `setUserOffline(mobile, name)` called

### Key Files Modified/Created

| File | Changes |
|------|---------|
| `build.gradle.kts` | Added Firebase dependencies |
| `FamilyModels.kt` | Added ChatThread, ChatMessageData, OnlineUser models |
| `FirebaseService.kt` | Created - handles all Firebase operations |
| `FamilyRepository.kt` | Added chat-related methods |
| `FamilyViewModel.kt` | Added chat flows and methods |
| `FamilyConnectRoot.kt` | Updated ChatScreen UI with new interface |
| `AndroidManifest.xml` | Added INTERNET permissions |
| `google-services.json` | Firebase configuration (placeholder) |

## Testing the Chat Feature

### Local Emulator (Recommended for Development)
1. Download [Firebase Emulator Suite](https://firebase.google.com/docs/emulator-suite)
2. Run Emulator: `firebase emulators:start --only database`
3. Emulator runs on `http://localhost:9000`
4. Point your app to local emulator (modify FirebaseService.kt for development)

### With Real Firebase
1. Ensure `app/google-services.json` is replaced with your actual credentials
2. Run app on two devices/emulators
3. Log in with different mobile numbers (use defaults: 9999999999, 8888888888, 7777777777)
4. User 1 will be online, User 2 can see them and start a chat

## Default Test Users
- **9999999999** - ADMIN (Mom)
- **8888888888** - PARENT (Dad)
- **7777777777** - CHILD (Kid)

Use these mobile numbers to log in and test chat between different roles.

## Troubleshooting

### Firebase Connection Issues
**Symptom**: App crashes or doesn't show online users
**Solution**: 
- Verify internet permission in manifest
- Confirm google-services.json is in correct location
- Check Firebase console for rule errors

### Media Upload Not Working
**Symptom**: Photos or documents fail to send
**Solution**:
- Verify Firebase Storage is enabled (Step 5)
- Check Storage Rules are configured correctly (allow read/write)
- Ensure app has READ_EXTERNAL_STORAGE and WRITE_EXTERNAL_STORAGE permissions
- Grant file access permissions when prompted by the app
- Check device has enough storage space
- Verify file is accessible from the file picker

### No Online Users Showing
**Symptom**: Online users list is empty
**Solution**:
- Confirm `setUserOnline()` is called after login
- Check that another user is logged in
- Verify database rules allow read access

### Messages Not Sending
**Symptom**: Message input shows but doesn't appear
**Solution**:
- Confirm both users are in the same chat thread
- Check database rules allow write access
- Verify message thread ID is created correctly

### Database Rules Errors
**Error**: "Permission denied" in Firebase Console logs
**Solution**:
- Use the TEST MODE rules provided above
- For production, implement proper authentication before deploying

## Next Steps

1. **Authentication**: Implement Firebase Authentication for phone number verification in production
2. **Read Receipts**: Enhance UI to show when messages are read
3. **Notifications**: Add Firebase Cloud Messaging (FCM) for push notifications
4. **Image Preview**: Display thumbnail previews of images in chat instead of just clickable text
5. **File Sharing Limits**: Add file size limits and better error handling for large files
5. **Encryption**: Add end-to-end encryption for messages
6. **Backup**: Implement database backup strategy

## Production Checklist

- [ ] Replace google-services.json with real Firebase project credentials
- [ ] Implement proper Firebase security rules with user authentication
- [ ] Add Firebase Authentication for phone number verification
- [ ] Enable Firebase Realtime Database backups
- [ ] Set up Firebase monitoring and crash reporting
- [ ] Implement proper error handling and user feedback
- [ ] Add database indexing for performance
- [ ] Test with real devices
- [ ] Set up CI/CD pipeline for deployment

