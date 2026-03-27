package com.familyconnect.app.webrtc

// Call status states
enum class CallStatus {
    IDLE,           // No active call
    REQUESTING,     // Call request sent
    RINGING,        // Incoming call ringing
    CONNECTING,     // Call being established
    ACTIVE,         // Call is active
    ENDED          // Call ended
}

// Call request model
data class CallRequest(
    val callId: String = "",
    val fromUserId: String = "",
    val fromUserName: String = "",
    val toUserId: String = "",
    val threadId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "pending"  // pending, accepted, rejected, ended
)

// Call signaling model for SDP and ICE candidates
data class CallSignaling(
    val callId: String = "",
    val type: String = "",  // offer, answer
    val sdp: String = "",
    val senderId: String = ""
)

data class IceCandidateData(
    val callId: String = "",
    val candidate: String = "",
    val sdpMLineIndex: Int = 0,
    val sdpMid: String = "",
    val senderId: String = ""
)

// Call state for UI
data class CallState(
    val status: CallStatus = CallStatus.IDLE,
    val incomingCallRequest: CallRequest? = null,
    val activeCallId: String? = null,
    val activeThreadId: String? = null,
    val activeCallPartyName: String? = null,
    val callDuration: Long = 0,
    val localAudioEnabled: Boolean = true,
    val remoteAudioEnabled: Boolean = true,
    val isCallConnected: Boolean = false
)
