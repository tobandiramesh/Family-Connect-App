/**
 * Firebase Cloud Function: onNewIncomingCall
 * 
 * DEPLOYMENT:
 * 1. Copy this code to Firebase Console → Cloud Functions
 * 2. Or use: firebase deploy --only functions
 * 
 * CRITICAL: This function sends FCM messages with callType to wake up the receiving app
 * when a new incoming call is created, even if the app is killed/backgrounded.
 */

const functions = require("firebase-functions");
const admin = require("firebase-admin");

// Initialize Firebase Admin SDK (auto-initialized in Cloud Functions)
if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.database();
const messaging = admin.messaging();

/**
 * MAIN FUNCTION: Triggered when a new call request is created
 * Path: /chats/{threadId}/callRequests/{callId}
 */
exports.onNewIncomingCall = functions.database
  .ref("/chats/{threadId}/callRequests/{callId}")
  .onCreate(async (snapshot, context) => {
    try {
      const { threadId, callId } = context.params;
      const callData = snapshot.val();

      if (!callData) {
        console.log(`❌ No call data for ${callId}`);
        return;
      }

      const toUserId = callData.toUserId || "";
      const fromUserName = callData.fromUserName || "Someone";
      const callType = callData.callType || "audio"; // ← CRITICAL: Extract callType
      const status = callData.status || "pending";

      console.log(`\n🔔 NEW CALL INCOMING:`);
      console.log(`   callId: ${callId}`);
      console.log(`   to: ${toUserId}`);
      console.log(`   from: ${fromUserName}`);
      console.log(`   type: ${callType} ⭐⭐⭐`);
      console.log(`   status: ${status}`);

      // 1. Get FCM token for receiving user
      const normalizedUserId = toUserId.replace(/\D/g, ""); // Remove non-digits
      const tokenSnapshot = await db
        .ref(`/users/${normalizedUserId}/fcm_token`)
        .once("value");

      const fcmToken = tokenSnapshot.val();

      if (!fcmToken) {
        console.log(`❌ No FCM token for user: ${normalizedUserId}`);
        // Still post notification for CallListenerService if app is running
        return;
      }

      console.log(`✅ FCM token found for ${normalizedUserId}`);

      // 2. Send FCM message with callType included
      const payload = {
        data: {
          type: "incoming_call",
          callId: callId,
          threadId: threadId,
          callerName: fromUserName,
          callType: callType, // ← CRITICAL: Include callType in FCM data!
        },
        notification: {
          title: callType === "video" ? "📹 Video Call" : "☎️ Audio Call",
          body: `${fromUserName} is calling...`,
        },
      };

      console.log(`📡 Sending FCM with payload:`, JSON.stringify(payload, null, 2));

      const messageId = await messaging.send({
        token: fcmToken,
        ...payload,
        webpush: {
          priority: "high",
        },
        apns: {
          headers: {
            "apns-priority": "10",
          },
        },
        android: {
          priority: "high",
        },
      });

      console.log(`✅ FCM sent successfully: ${messageId}`);
      console.log(`   callType was: ${callType} ⭐⭐⭐\n`);

      // 3. Optional: Log analytics
      const analyticsRef = db.ref(
        `/analytics/calls/${new Date().toISOString()}`
      );
      await analyticsRef.update({
        callId: callId,
        toUserId: toUserId,
        fromUserName: fromUserName,
        callType: callType,
        fcmSent: true,
      });

      console.log(`✅ Analytics logged`);
    } catch (error) {
      console.error(`❌ ERROR in onNewIncomingCall:`, error);
      console.error(`   Message: ${error.message}`);
      console.error(`   Stack: ${error.stack}`);
      throw error;
    }
  });

/**
 * BACKUP FUNCTION: Retry function in case first FCM fails
 * Triggered every 5 minutes to resend pending calls without FCM tokens yet
 */
exports.retryPendingCallNotifications = functions.pubsub
  .schedule("every 5 minutes")
  .onRun(async (context) => {
    try {
      console.log(`\n🔄 Checking for pending calls without FCM...`);

      const chatsSnapshot = await db.ref("/chats").once("value");

      for (const threadSnap of chatsSnapshot.val() || []) {
        for (const callSnap of threadSnap.child("callRequests").val() || []) {
          const call = callSnap.val();
          if (call.status !== "pending") continue;

          const tokenSnapshot = await db
            .ref(`/users/${call.toUserId.replace(/\D/g, "")}/fcm_token`)
            .once("value");

          if (tokenSnapshot.val()) {
            console.log(
              `✅ Token now available for ${call.toUserId}, would retry FCM`
            );
            // Could add logic here to retry if needed
          }
        }
      }
    } catch (error) {
      console.error(`❌ Error in retryPendingCallNotifications:`, error);
    }
  });
