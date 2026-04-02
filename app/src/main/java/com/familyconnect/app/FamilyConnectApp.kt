package com.familyconnect.app

import android.app.Application
import android.util.Log
import com.familyconnect.app.data.local.AppDatabase
import com.familyconnect.app.data.repository.FamilyRepository
import com.familyconnect.app.notifications.NotificationHelper

class FamilyConnectApp : Application() {
    lateinit var repository: FamilyRepository
        private set

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        val database = AppDatabase.getInstance(this)
        repository = FamilyRepository(this, database.userDao())
        Log.d("FamilyConnectApp", "App initialized")
    }
}
