package com.familyconnect.app.telecom

import android.telecom.Connection
import android.util.Log

class MyConnection : Connection() {

    private val TAG = "MyConnection"

    override fun onAnswer() {
        Log.d(TAG, "✅ onAnswer() called - User answered call")
        setActive()
    }

    override fun onDisconnect() {
        Log.d(TAG, "❌ onDisconnect() called - Call ended")
        destroy()
    }

    override fun onReject() {
        Log.d(TAG, "🚫 onReject() called - User rejected call")
        destroy()
    }
}
