package com.familyconnect.app.activities

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.familyconnect.app.notifications.NotificationHelper

class IncomingCallActivity : ComponentActivity() {
    
    private val TAG = "IncomingCallActivity"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "🔥 IncomingCallActivity created")
        
        // 🔥 CRITICAL: Show over lock screen + wake device
        // Android 8.1+ way (modern)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            // Fallback for older versions
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Extract call data from intent
        val callId = intent.getStringExtra(NotificationHelper.EXTRA_CALL_ID) ?: ""
        val threadId = intent.getStringExtra(NotificationHelper.EXTRA_THREAD_ID) ?: ""
        val callerName = intent.getStringExtra(NotificationHelper.EXTRA_CALLER_NAME) ?: "Unknown"
        val callType = intent.getStringExtra(NotificationHelper.EXTRA_CALL_TYPE) ?: "audio"
        
        Log.d(TAG, "📞 Incoming call from: $callerName (ID: $callId, Type: $callType)")
        
        // 🔥 Launch Compose UI
        setContent {
            IncomingCallScreen(
                callerName = callerName,
                onAccept = {
                    Log.d(TAG, "✅ Accept clicked - handling call connection")
                    // TODO: Connect call, navigate to active call screen
                    finish()
                },
                onReject = {
                    Log.d(TAG, "❌ Reject clicked - rejecting call")
                    // TODO: Reject call (update Firebase, send ICE disconnect, close service)
                    finish()
                }
            )
        }
    }
    
    override fun onBackPressed() {
        // Prevent accidental dismissal via back button
        Log.d(TAG, "Back pressed - ignoring (use Reject button)")
        // Don't call super - user must use Reject button
    }
}

        // 🎨 JETPACK COMPOSE UI
@Composable
fun IncomingCallScreen(
    callerName: String,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            // Caller name
            Text(
                text = callerName,
                color = Color.White,
                fontSize = 32.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Call status
            Text(
                text = "Incoming Call...",
                color = Color.Gray,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 80.dp)
            )
            
            // Buttons row
            Row(
                horizontalArrangement = Arrangement.spacedBy(40.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 64.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reject button
                Button(
                    onClick = onReject,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30)),
                    shape = CircleShape,
                    modifier = Modifier.size(80.dp)
                ) {
                    Text("Decline", color = Color.White, fontSize = 12.sp)
                }
                
                // Accept button
                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)),
                    shape = CircleShape,
                    modifier = Modifier.size(80.dp)
                ) {
                    Text("Accept", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}
