package com.familyconnect.app

import android.app.Application
import android.util.Log
import com.familyconnect.app.data.local.AppDatabase
import com.familyconnect.app.data.repository.FamilyRepository
import com.familyconnect.app.notifications.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class PendingCallIntent(
    val callId: String,
    val threadId: String,
    val callerName: String,
    val callType: String = "audio"  // ← ADD CALL TYPE
)

class FamilyConnectApp : Application() {
    lateinit var repository: FamilyRepository
        private set
    
    // Store pending call data from notification - StateFlow so UI reacts automatically
    private val _pendingCallIntent = MutableStateFlow<PendingCallIntent?>(null)
    val pendingCallIntent: StateFlow<PendingCallIntent?> = _pendingCallIntent
    
    fun setPendingCall(call: PendingCallIntent?) {
        _pendingCallIntent.value = call
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        val database = AppDatabase.getInstance(this)
        repository = FamilyRepository(this, database.userDao())
        Log.d("FamilyConnectApp", "App initialized")
    }
}
