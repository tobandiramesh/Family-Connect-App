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
        
        Log.d(TAG, "🚀 onCreate() called")
        Log.d(TAG, "   Intent: ${intent?.action}")
        Log.d(TAG, "   Extras: ${intent?.extras?.keySet()}")
        
        // Extract call data
        val callId = intent?.getStringExtra(NotificationHelper.EXTRA_CALL_ID)
        val threadId = intent?.getStringExtra(NotificationHelper.EXTRA_THREAD_ID)
        val callerName = intent?.getStringExtra(NotificationHelper.EXTRA_CALLER_NAME)
        val callType = intent?.getStringExtra(NotificationHelper.EXTRA_CALL_TYPE) ?: "audio"
        
        Log.d(TAG, "   📞 Call Data:")
        Log.d(TAG, "      callId: $callId")
        Log.d(TAG, "      threadId: $threadId")
        Log.d(TAG, "      callerName: $callerName")
        Log.d(TAG, "      callType: $callType")
        
        // Set window flags FIRST
        try {
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
            Log.d(TAG, "   ✅ Window flags set (legacy)")
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
                Log.d(TAG, "   ✅ Window flags set (modern API)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting window flags: ${e.message}")
        }
        
        // Cancel notification
        if (!callId.isNullOrBlank()) {
            try {
                NotificationHelper.cancelCallNotification(this, callId)
                Log.d(TAG, "   ✅ Notification cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error cancelling notification: ${e.message}")
            }
        }
        
        // Now render UI with call state set BEFORE composition
        if (!callId.isNullOrBlank() && !threadId.isNullOrBlank()) {
            Log.d(TAG, "✅ Valid call data, rendering UI...")
            
            try {
                val app = application as FamilyConnectApp
                val viewModel = ViewModelProvider(
                    this,
                    FamilyViewModelFactory(app.repository, this)
                )[FamilyViewModel::class.java]
                
                Log.d(TAG, "   🔧 Setting call state to RINGING...")
                viewModel.setIncomingCallRinging(callId, threadId, callerName ?: "Unknown", callType)
                Log.d(TAG, "   ✅ Call state set to RINGING")
                
                Log.d(TAG, "   📄 Setting pending call...")
                app.setPendingCall(PendingCallIntent(callId, threadId, callerName ?: "Unknown", callType))
                Log.d(TAG, "   ✅ Pending call set")
                
                Log.d(TAG, "   🎨 Rendering FamilyConnectRoot with call UI...")
                setContent {
                    FamilyConnectTheme {
                        FamilyConnectRoot(viewModel)
                    }
                }
                Log.d(TAG, "✅ UI rendered successfully")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in onCreate: ${e.message}", e)
                // Show error UI as fallback
                showErrorUI("Error: ${e.message?.take(50)}")
            }
        } else {
            Log.e(TAG, "❌ Missing critical call data!")
            Log.e(TAG, "   callId=$callId, threadId=$threadId")
            showErrorUI("Invalid call data")
        }
    }
    
    private fun showErrorUI(errorMessage: String) {
        setContent {
            FamilyConnectTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        errorMessage,
                        color = Color.Red,
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)  // CRITICAL: Update intent FIRST
        
        val TAG = "IncomingCallActivity"
        Log.d(TAG, "📞 onNewIntent() called - new call arrived")
        Log.d(TAG, "   Intent: ${intent?.action}")
        Log.d(TAG, "   Extras: ${intent?.extras?.keySet()}")
        
        // Extract NEW call data from the fresh intent
        val callId = intent.getStringExtra(NotificationHelper.EXTRA_CALL_ID)
        val threadId = intent.getStringExtra(NotificationHelper.EXTRA_THREAD_ID)
        val callerName = intent.getStringExtra(NotificationHelper.EXTRA_CALLER_NAME)
        val callType = intent.getStringExtra(NotificationHelper.EXTRA_CALL_TYPE) ?: "audio"
        
        Log.d(TAG, "   📞 New Call Data:")
        Log.d(TAG, "      callId: $callId")
        Log.d(TAG, "      threadId: $threadId")
        Log.d(TAG, "      callerName: $callerName")
        Log.d(TAG, "      callType: $callType")
        
        if (!callId.isNullOrBlank() && !threadId.isNullOrBlank()) {
            try {
                val app = application as FamilyConnectApp
                val viewModel = ViewModelProvider(
                    this,
                    FamilyViewModelFactory(app.repository, this)
                )[FamilyViewModel::class.java]
                
                Log.d(TAG, "   🔄 Updating call state to RINGING with new call...")
                viewModel.setIncomingCallRinging(callId, threadId, callerName ?: "Unknown", callType)
                Log.d(TAG, "   ✅ Call state updated")
                
                Log.d(TAG, "   📄 Updating pending call...")
                app.setPendingCall(PendingCallIntent(callId, threadId, callerName ?: "Unknown", callType))
                Log.d(TAG, "   ✅ Pending call updated")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in onNewIntent: ${e.message}", e)
            }
        } else {
            Log.w(TAG, "⚠️ Invalid call data in new intent")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        val TAG = "IncomingCallActivity"
        Log.d(TAG, "🔴 IncomingCallActivity.onDestroy()")
    }
}

