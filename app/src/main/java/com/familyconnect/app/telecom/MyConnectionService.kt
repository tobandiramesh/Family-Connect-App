package com.familyconnect.app.telecom

import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.util.Log

class MyConnectionService : ConnectionService() {

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: android.telecom.PhoneAccountHandle,
        request: ConnectionRequest
    ): Connection {
        Log.e("CALL_UI", "🎉 onCreateIncomingConnection() - System will show call UI now")

        val extras = request.extras
        val callerName = extras?.getString("callerName") ?: "Unknown"
        val callId = extras?.getString("callId") ?: ""

        Log.e("CALL_UI", "   📞 From: $callerName | Call ID: $callId")

        // Create connection with call data
        val connection = MyConnection(callId, callerName)
        
        // Set caller display name
        connection.setCallerDisplayName(callerName, 0)
        
        // Set to ringing state (system shows call UI)
        connection.setRinging()
        
        Log.e("CALL_UI", "✅ Call UI will appear on lock screen/background")
        return connection
    }
}
