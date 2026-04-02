package com.familyconnect.app

import android.app.Application
import android.util.Log
import com.familyconnect.app.data.local.AppDatabase
import com.familyconnect.app.data.repository.FamilyRepository
import com.familyconnect.app.notifications.NotificationHelper

data class PendingCallIntent(
    val callId: String,
    val threadId: String,
    val callerName: String
)

class FamilyConnectApp : Application() {
    lateinit var repository: FamilyRepository
        private set
    
    // Store pending call data from notification before UI renders
    var pendingCallIntent: PendingCallIntent? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        val database = AppDatabase.getInstance(this)
        repository = FamilyRepository(this, database.userDao())
        Log.d("FamilyConnectApp", "App initialized")
    }
}
