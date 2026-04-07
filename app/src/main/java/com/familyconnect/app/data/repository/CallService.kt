package com.familyconnect.app.data.repository

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firebase Realtime Database call data model
 * Stored at: calls/{receiverUserId}/{callData}
 */
data class IncomingCallData(
    val callerId: String = "",
    val callerName: String = "",
    val type: String = "audio", // audio or video
    val timestamp: Long = System.currentTimeMillis(),
    val callRequest: Boolean = false // flag to track if it's a request
)

/**
 * Call Service - Handles simplified phone-to-phone call flow via Firebase Realtime DB
 * 
 * Architecture:
 * - Phone A calls Phone B → Write to calls/{phoneB_id}
 * - Phone B receives event → Show incoming call UI
 * - Phone B accepts/rejects → Remove calls/{phoneB_id} data
 * 
 * No backend needed - pure client-to-client via Firebase
 */
object CallService {
    private const val TAG = "CallService"
    private const val CALLS_PATH = "calls"
    
    private val database by lazy {
        try {
            val dbUrl = "https://family-connect-app-a219b-default-rtdb.asia-southeast1.firebasedatabase.app"
            FirebaseDatabase.getInstance(dbUrl).also {
                Log.d(TAG, "✅ Firebase Database initialized")
                it.setPersistenceEnabled(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Firebase initialization error: ${e.message}", e)
            null
        }
    }

    /**
     * PHONE A: Initiate a call to Phone B
     * 
     * Writes to: calls/{receiverUserId}/
     * Data structure:
     * {
     *   "callerId": "USER_A_ID",
     *   "callerName": "Ramesh",
     *   "type": "audio",
     *   "timestamp": 1712345678
     * }
     */
    suspend fun initiateCall(
        receiverUserId: String,
        callerId: String,
        callerName: String,
        callType: String = "audio"
    ): Boolean {
        return try {
            val db = database
            if (db == null) {
                Log.e(TAG, "❌ Firebase not initialized")
                return false
            }

            if (receiverUserId.isBlank() || callerId.isBlank()) {
                Log.e(TAG, "❌ Invalid receiver or caller ID")
                return false
            }

            val callData = mapOf(
                "callerId" to callerId,
                "callerName" to callerName.trim(),
                "type" to callType,
                "timestamp" to System.currentTimeMillis()
            )

            Log.d(TAG, "📞 Initiating call to $receiverUserId from $callerId")
            
            val callRef = db.getReference(CALLS_PATH).child(receiverUserId)
            callRef.setValue(callData).await()
            
            Log.d(TAG, "✅ Call initiated for $receiverUserId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initiating call: ${e.message}", e)
            false
        }
    }

    /**
     * PHONE B: Listen for incoming calls in real-time
     * 
     * Observes: calls/{currentUserId}/
     * Returns Flow of incoming call data
     */
    fun listenForIncomingCalls(currentUserId: String): Flow<IncomingCallData?> = callbackFlow {
        if (currentUserId.isBlank()) {
            Log.e(TAG, "❌ Empty user ID for incoming call listener")
            trySend(null)
            close()
            return@callbackFlow
        }

        try {
            val db = database
            if (db == null) {
                Log.w(TAG, "Firebase not initialized")
                trySend(null)
                close()
                return@callbackFlow
            }

            val callRef = db.getReference(CALLS_PATH).child(currentUserId)
            
            Log.d(TAG, "👂 Starting to listen for incoming calls on $currentUserId")

            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        if (snapshot.exists()) {
                            val callData = IncomingCallData(
                                callerId = snapshot.child("callerId").value as? String ?: "",
                                callerName = snapshot.child("callerName").value as? String ?: "Unknown",
                                type = snapshot.child("type").value as? String ?: "audio",
                                timestamp = snapshot.child("timestamp").value as? Long ?: System.currentTimeMillis()
                            )
                            
                            Log.d(TAG, "📳 Incoming call from: ${callData.callerName} (${callData.callerId})")
                            trySend(callData)
                        } else {
                            Log.d(TAG, "No incoming call")
                            trySend(null)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error parsing call data: ${e.message}", e)
                        trySend(null)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "❌ Database listener cancelled: ${error.message}")
                    trySend(null)
                }
            }

            callRef.addValueEventListener(listener)
            
            awaitClose {
                try {
                    callRef.removeEventListener(listener)
                    Log.d(TAG, "Removed incoming call listener")
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing listener: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in listenForIncomingCalls: ${e.message}", e)
            trySend(null)
            close()
        }
    }

    /**
     * PHONE B: Accept an incoming call
     * Removes the call data from Firebase (cleanup)
     */
    suspend fun acceptCall(currentUserId: String): Boolean {
        return try {
            val db = database
            if (db == null) {
                Log.e(TAG, "❌ Firebase not initialized")
                return false
            }

            Log.d(TAG, "✅ Accepting call - removing from $currentUserId")
            
            val callRef = db.getReference(CALLS_PATH).child(currentUserId)
            callRef.removeValue().await()
            
            Log.d(TAG, "✅ Call data removed (accepted)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error accepting call: ${e.message}", e)
            false
        }
    }

    /**
     * PHONE B: Reject an incoming call
     * Removes the call data from Firebase (cleanup)
     */
    suspend fun rejectCall(currentUserId: String): Boolean {
        return try {
            val db = database
            if (db == null) {
                Log.e(TAG, "❌ Firebase not initialized")
                return false
            }

            Log.d(TAG, "❌ Rejecting call - removing from $currentUserId")
            
            val callRef = db.getReference(CALLS_PATH).child(currentUserId)
            callRef.removeValue().await()
            
            Log.d(TAG, "✅ Call data removed (rejected)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error rejecting call: ${e.message}", e)
            false
        }
    }

    /**
     * PHONE A: Cancel/end outgoing call
     * Can be called by the caller to cancel before answer
     */
    suspend fun cancelOutgoingCall(receiverUserId: String): Boolean {
        return try {
            val db = database
            if (db == null) {
                Log.e(TAG, "❌ Firebase not initialized")
                return false
            }

            Log.d(TAG, "🔴 Cancelling outgoing call to $receiverUserId")
            
            val callRef = db.getReference(CALLS_PATH).child(receiverUserId)
            callRef.removeValue().await()
            
            Log.d(TAG, "✅ Outgoing call cancelled")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cancelling call: ${e.message}", e)
            false
        }
    }

    /**
     * Check if there's an active call for this user
     */
    suspend fun hasActiveCall(userId: String): Boolean {
        return try {
            val db = database
            if (db == null) return false

            val callRef = db.getReference(CALLS_PATH).child(userId)
            val snapshot = callRef.get().await()
            
            snapshot.exists()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking active call: ${e.message}", e)
            false
        }
    }

    /**
     * Get current incoming call data if it exists
     */
    suspend fun getCurrentIncomingCall(userId: String): IncomingCallData? {
        return try {
            val db = database
            if (db == null) return null

            val callRef = db.getReference(CALLS_PATH).child(userId)
            val snapshot = callRef.get().await()
            
            if (snapshot.exists()) {
                IncomingCallData(
                    callerId = snapshot.child("callerId").value as? String ?: "",
                    callerName = snapshot.child("callerName").value as? String ?: "Unknown",
                    type = snapshot.child("type").value as? String ?: "audio",
                    timestamp = snapshot.child("timestamp").value as? Long ?: System.currentTimeMillis()
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current call: ${e.message}", e)
            null
        }
    }
}
