package com.familyconnect.app.telecom

import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.util.Log
import android.os.Bundle

class MyConnectionService : ConnectionService() {

    private val TAG = "MyConnectionService"

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: android.telecom.PhoneAccountHandle,
        request: ConnectionRequest
    ): Connection {
        Log.d(TAG, "🎯 onCreateIncomingConnection() called")

        val connection = MyConnection()
        connection.setRinging()

        val extras = request.extras
        val callerName = extras?.getString("callerName") ?: "Unknown"
        val callId = extras?.getString("callId") ?: ""
        val threadId = extras?.getString("threadId") ?: ""
        val callType = extras?.getString("callType") ?: "audio"

        Log.d(TAG, "   📞 Call incoming from: $callerName (ID: $callId)")

        // Set caller display name (with presentation flags)
        connection.setCallerDisplayName(callerName, 0) // 0 = PRESENTATION_ALLOWED
        
        // Store call data in connection extras for later retrieval
        val connectionExtras = Bundle().apply {
            putString("callId", callId)
            putString("threadId", threadId)
            putString("callType", callType)
        }
        connection.putExtras(connectionExtras)

        return connection
    }
}
