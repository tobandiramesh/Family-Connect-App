package com.familyconnect.app

import android.content.Intent
import android.media.RingtoneManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import com.familyconnect.app.notifications.NotificationHelper
import com.familyconnect.app.ui.FamilyConnectRoot
import com.familyconnect.app.ui.FamilyViewModel
import com.familyconnect.app.ui.FamilyViewModelFactory
import com.familyconnect.app.ui.theme.FamilyConnectTheme

/**
 * Dedicated Activity for displaying incoming calls.
 * This ensures the call UI shows immediately on all Android versions.
 */
class IncomingCallActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val TAG = "IncomingCallActivity"
        
        // Extract call data
        val callId = intent.getStringExtra(NotificationHelper.EXTRA_CALL_ID)
        val threadId = intent.getStringExtra(NotificationHelper.EXTRA_THREAD_ID)
        val callerName = intent.getStringExtra(NotificationHelper.EXTRA_CALLER_NAME)
        val callType = intent.getStringExtra(NotificationHelper.EXTRA_CALL_TYPE) ?: "audio"
        
        // Ensure window is visible - use proper API level checks
        try {
            // Old API (deprecated but still needed for older devices)
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
            
            // New API (Android 8.1+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting window flags: ${e.message}")
        }
        
        // Cancel notification immediately
        try {
            NotificationHelper.cancelCallNotification(this, callId ?: "")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling notification: ${e.message}")
        }
        
        // ✅ Create ViewModel BEFORE setContent to set state early
        if (!callId.isNullOrBlank() && !threadId.isNullOrBlank()) {
            val app = application as FamilyConnectApp
            val viewModel = ViewModelProvider(
                this,
                FamilyViewModelFactory(app.repository, this)
            )[FamilyViewModel::class.java]
            
            // ✅ Set call state to RINGING BEFORE rendering UI
            viewModel.setIncomingCallRinging(callId, threadId, callerName ?: "Unknown", callType)
            
            // ✅ Also set pending call
            app.setPendingCall(PendingCallIntent(callId, threadId, callerName ?: "Unknown", callType))
            
            // Now render - state is already RINGING
            setContent {
                FamilyConnectTheme {
                    FamilyConnectRoot(viewModel)
                }
            }
        } else {
            // Fallback: Show error
            Log.e(TAG, "   ❌ Missing call data - showing error")
            
            setContent {
                FamilyConnectTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("❌ Missing call information", color = Color.White)
                    }
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        val TAG = "IncomingCallActivity"
        Log.d(TAG, "🔴 IncomingCallActivity.onDestroy()")
    }
}

