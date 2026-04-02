package com.familyconnect.app.data.repository

import android.util.Log
import com.familyconnect.app.data.model.FamilyRole
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class AllowedUser(
    val mobile: String = "",
    val name: String = "",
    val role: String = FamilyRole.CHILD.name
)

object UserManagementService {
    private const val TAG = "UserManagementService"
    private const val ALLOWED_USERS_PATH = "allowedUsers"
    private val database by lazy {
        try {
            // Database URL from google-services.json
            val dbUrl = "https://family-connect-app-a219b-default-rtdb.asia-southeast1.firebasedatabase.app"
            FirebaseDatabase.getInstance(dbUrl).also {
                Log.d(TAG, "✅ Firebase Database initialized with URL: $dbUrl")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Firebase initialization error: ${e.message}", e)
            null
        }
    }

    /**
     * Verify Firebase connection - call on app startup to diagnose issues
     */
    suspend fun verifyFirebaseConnection(): Boolean {
        try {
            val db = database
            if (db == null) {
                Log.e(TAG, "❌ FIREBASE NOT INITIALIZED")
                return false
            }
            
            Log.d(TAG, "🔍 Verifying Firebase connection...")
            val testRef = db.getReference(".info/connected")
            
            // Try to read a test value to verify connectivity
            val snapshot = testRef.get().await()
            val isConnected = snapshot.value as? Boolean ?: false
            
            if (isConnected) {
                Log.d(TAG, "✅ FIREBASE CONNECTION VERIFIED - Real-time DB is accessible")
            } else {
                Log.w(TAG, "⚠️ Firebase connection offline mode")
            }
            
            // Also verify we can read the allowedUsers path
            Log.d(TAG, "🔍 Reading allowedUsers path to verify write access...")
            val allowedUsersSnapshot = db.getReference(ALLOWED_USERS_PATH).get().await()
            Log.d(TAG, "✅ Can read allowedUsers: exists=${allowedUsersSnapshot.exists()}, childCount=${allowedUsersSnapshot.childrenCount}")
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "❌ FIREBASE VERIFICATION FAILED: ${e.message}", e)
            return false
        }
    }

    /**
     * Observe all allowed users from Firebase in real-time
     */
    fun observeAllowedUsers(): Flow<List<AllowedUser>> = callbackFlow {
        try {
            val db = database
            if (db == null) {
                Log.w(TAG, "Firebase not initialized, returning empty list")
                trySend(emptyList())
                close()
                return@callbackFlow
            }

            val ref = db.getReference(ALLOWED_USERS_PATH)
            
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val users = mutableListOf<AllowedUser>()
                        for (child in snapshot.children) {
                            val mobile = child.key ?: continue
                            val name = child.child("name").value as? String ?: ""
                            val role = child.child("role").value as? String ?: FamilyRole.CHILD.name
                            users.add(AllowedUser(mobile, name, role))
                        }
                        Log.d(TAG, "Loaded ${users.size} allowed users")
                        trySend(users)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error observing users: ${e.message}", e)
                        trySend(emptyList())
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Database error: ${error.message}")
                    trySend(emptyList())
                }
            }

            ref.addValueEventListener(listener)
            
            awaitClose {
                try {
                    ref.removeEventListener(listener)
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing listener: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in observeAllowedUsers: ${e.message}", e)
            trySend(emptyList())
            close()
        }
    }

    /**
     * Add or update an allowed user - using proper async/await pattern
     */
    suspend fun addOrUpdateUser(
        mobile: String,
        name: String,
        role: FamilyRole,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        try {
            val db = database
            if (db == null) {
                Log.e(TAG, "❌ Firebase not initialized")
                onError("Firebase not initialized")
                return
            }

            val cleanMobile = mobile.filter { it.isDigit() }
            if (cleanMobile.length < 10) {
                Log.e(TAG, "❌ Mobile number too short: $cleanMobile")
                onError("Mobile number must be at least 10 digits")
                return
            }

            Log.d(TAG, "🔥 Writing to Firebase: allowedUsers/$cleanMobile")
            val ref = db.getReference("$ALLOWED_USERS_PATH/$cleanMobile")
            
            try {
                ref.setValue(mapOf(
                    "name" to name.trim(),
                    "role" to role.name
                )).addOnSuccessListener {
                    Log.d(TAG, "✅ User added/updated in Firebase: $cleanMobile")
                    onSuccess()
                }.addOnFailureListener { e ->
                    val errorMsg = "Firebase Write Failed:\n${e.message}\n\nCause: ${e.cause?.message ?: "Unknown"}"
                    Log.e(TAG, "❌ Firebase error adding user: $errorMsg")
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception setting Firebase value: ${e.message}", e)
                onError(e.message ?: "Unknown error")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception in addOrUpdateUser: ${e.message}", e)
            onError(e.message ?: "Unknown error")
        }
    }

    /**
     * Delete an allowed user
     */
    suspend fun deleteUser(
        mobile: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        try {
            val db = database
            if (db == null) {
                onError("Firebase not initialized")
                return
            }

            val cleanMobile = mobile.filter { it.isDigit() }
            val ref = db.getReference("$ALLOWED_USERS_PATH/$cleanMobile")
            ref.removeValue().addOnSuccessListener {
                Log.d(TAG, "User deleted: $cleanMobile")
                onSuccess()
            }.addOnFailureListener { e ->
                Log.e(TAG, "Error deleting user: ${e.message}")
                onError(e.message ?: "Failed to delete user")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception: ${e.message}", e)
            onError(e.message ?: "Unknown error")
        }
    }

    /**
     * Check if a mobile number is allowed to login
     */
    suspend fun isUserAllowed(mobile: String, onResult: (Boolean) -> Unit) {
        try {
            val db = database
            if (db == null) {
                Log.w(TAG, "Firebase not initialized, denying access")
                onResult(false)
                return
            }

            val cleanMobile = mobile.filter { it.isDigit() }
            val ref = db.getReference("$ALLOWED_USERS_PATH/$cleanMobile")
            ref.get().addOnSuccessListener { snapshot ->
                val isAllowed = snapshot.exists()
                Log.d(TAG, "User check: $cleanMobile -> $isAllowed")
                onResult(isAllowed)
            }.addOnFailureListener { e ->
                Log.e(TAG, "Error checking user: ${e.message}")
                onResult(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception: ${e.message}")
            onResult(false)
        }
    }
}
