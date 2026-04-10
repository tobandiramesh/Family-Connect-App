package com.familyconnect.app.utils

import android.util.Log
import android.widget.Toast
import android.content.Context
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Manages typing status indicators in Firebase.
 * Sends "user is typing" status and automatically clears it after inactivity.
 */
class TypingStatusManager(
    private val coroutineScope: CoroutineScope, 
    private val context: Context? = null,
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance("https://family-connect-app-a219b-default-rtdb.asia-southeast1.firebasedatabase.app")
) {
    companion object {
        private const val TAG = "TypingStatusManager"
        private const val DEBOUNCE_MS = 500L  // Wait 500ms before writing to Firebase
        private const val TYPING_PATH = "typing"
    }
    
    private var typingTimeoutJob: Job? = null
    private var debounceJob: Job? = null
    private var lastTypingTime = 0L

    /**
     * Called when user starts typing in chat input field.
     * Sets typing status in Firebase for the current thread (with debounce).
     * Auto-clears after 3 seconds of inactivity.
     */
    fun onTextChanged(threadId: String, userMobile: String, userName: String) {
        try {
            Log.d(TAG, "onTextChanged called for $threadId / $userMobile / $userName")
            
            // Cancel previous debounce and timeout
            debounceJob?.cancel()
            typingTimeoutJob?.cancel()

            // Debounce: wait before writing to Firebase to avoid excessive updates
            debounceJob = coroutineScope.launch {
                delay(DEBOUNCE_MS)
                Log.d(TAG, "Debounce expired - writing typing status")
                
                // Only write if we haven't written recently
                val now = System.currentTimeMillis()
                if (now - lastTypingTime > DEBOUNCE_MS) {
                    setTypingInFirebase(threadId, userMobile, userName)
                    lastTypingTime = now
                }
            }
            
            // Auto-clear typing after 1.5 seconds of inactivity
            typingTimeoutJob = coroutineScope.launch {
                delay(1500)
                Log.d(TAG, "⏱️ Typing timeout - clearing status")
                clearTypingInFirebase(threadId, userMobile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "onTextChanged error: ${e.message}", e)
        }
    }

    private fun setTypingInFirebase(threadId: String, userMobile: String, userName: String) {
        try {
            val path = "typing/$threadId/$userMobile"
            Log.d(TAG, "Writing typing to: $path")
            
            val typingRef = database.getReference(path)
            val data = mapOf(
                "name" to userName,
                "timestamp" to System.currentTimeMillis(),
                "isTyping" to true
            )
            
            typingRef.setValue(data) { error, _ ->
                if (error != null) {
                    Log.e(TAG, "Write failed: ${error.message}")
                } else {
                    Log.d(TAG, "Typing values set atomically")
                    // Set up auto-remove on disconnect
                    typingRef.onDisconnect().removeValue()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in setTypingInFirebase: ${e.message}", e)
        }
    }

    private fun clearTypingInFirebase(threadId: String, userMobile: String) {
        try {
            val typingRef = database.getReference("$TYPING_PATH/$threadId/$userMobile")
            typingRef.removeValue { error, _ ->
                if (error != null) {
                    Log.e(TAG, "❌ Error clearing typing status: ${error.message}")
                } else {
                    Log.d(TAG, "✅ Typing status CLEARED for $userMobile")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in clearTypingInFirebase: ${e.message}")
        }
    }

    /**
     * Called when user sends a message.
     * Immediately removes typing status from Firebase.
     */
    fun stopTyping(threadId: String, userMobile: String) {
        try {
            debounceJob?.cancel()
            typingTimeoutJob?.cancel()
            clearTypingInFirebase(threadId, userMobile)
            Log.d(TAG, "📝 Typing stopped for $userMobile - cleared from Firebase")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping typing: ${e.message}")
        }
    }

    /**
     * Called on app exit or thread close to ensure cleanup.
     */
    fun clearAll(threadId: String, userMobile: String) {
        debounceJob?.cancel()
        typingTimeoutJob?.cancel()
        clearTypingInFirebase(threadId, userMobile)
        Log.d(TAG, "🗑️ Cleared all typing state")
    }
}
