# Family Connect Android App

Family Connect is a Kotlin + Jetpack Compose Android app scaffold that includes core family collaboration features.

## Implemented Features

- Mobile number based whitelist login only for stored family numbers
- Admin-managed allowed mobile numbers with roles: Parent, Child, Admin
- Shared family calendar (create/list, reminder metadata, recurring flag, color tag)
- Events auto-expire 3 days after creation
- Task and chore management (assign, due date, complete, optional reward points)
- Messaging/chat (group/direct target, optional media URI, read receipts)
- Photo/media sharing (upload metadata and list)
- Shared notes/lists (create and view)
- Dashboard after login with icon shortcuts and latest message/event summary
- Notifications/reminders (local notifications for added events/tasks/messages)
- Settings and personalization:
  - Dark/light mode
  - Language preference (English/Spanish/Hindi)
  - Accessibility large text toggle

## Default Login Mobile Numbers

These users are seeded on first launch:

- 9999999999 (ADMIN)
- 8888888888 (PARENT)
- 7777777777 (CHILD)

## Admin Setup Access

- Pre-login admin setup requires:
  - admin mobile number
  - admin setup PIN
- Default admin setup PIN: 2468
- Admin users can change this PIN later from the Settings screen

## Tech Stack

- Kotlin
- Jetpack Compose (Material3)
- Room
- DataStore Preferences
- MVVM

## Open & Run

1. Open the folder in Android Studio.
2. Let Android Studio sync Gradle and download dependencies.
3. Run the app on an emulator/device with min SDK 24+.
4. On Android 13+, allow notifications when prompted.

## Notes

- This is an end-to-end starter app with local/offline data.
- Media picker/camera and backend sync can be added next.
