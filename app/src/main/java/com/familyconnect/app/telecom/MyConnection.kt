package com.familyconnect.app.telecom

import android.telecom.Connection
import android.util.Log

class MyConnection(
    private val callId: String?,
    private val callerName: String
) : Connection() {

    override fun onAnswer() {
        Log.e("CALL_UI", "✅ Call answered: $callerName")
        setActive()
    }

    override fun onDisconnect() {
        Log.e("CALL_UI", "❌ Call ended: $callId")
        destroy()
    }

    override fun onReject() {
        Log.e("CALL_UI", "🚫 Call rejected: $callId")
        destroy()
    }
}
