package com.familyconnect.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.lifecycle.viewmodel.compose.viewModel
import com.familyconnect.app.notifications.NotificationHelper
import com.familyconnect.app.ui.FamilyConnectRoot
import com.familyconnect.app.ui.FamilyViewModel
import com.familyconnect.app.ui.FamilyViewModelFactory
import com.familyconnect.app.ui.theme.FamilyConnectTheme
import android.util.Log
import com.familyconnect.app.notifications.CallListenerService
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import android.os.Handler
import android.os.Looper
import android.app.PendingIntent
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import android.os.PowerManager

class MainActivity : ComponentActivity() {
    private var viewModelInstance: FamilyViewModel? = null
    private var pendingCallHandled = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Auto-start CallListenerService if user is already logged in
        ensureCallListenerServiceRunning()
        
        // Extract and store call data BEFORE rendering UI
        extractAndStorePendingCall(intent)
        
        maybeRequestNotificationPermission()
        maybeRequestFilePermissions()
        maybeRequestAudioPermissions()
        maybeRequestCameraPermission()
        maybeRequestFullScreenIntentPermission()
        
        // Ensure window is visible when launched from notification
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                       android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        
        val app = application as FamilyConnectApp
        
        setContent {
            FamilyConnectTheme {
                Root(app = app, mainActivity = this) { vm ->
                    viewModelInstance = vm
                    
                    // Now process the pending call with the ready ViewModel
                    processPendingCall(vm)
                    
                    // 📩 Handle notification tap navigation
                    handleNotificationNavigation(intent)
                }
            }
        }
    }
    
    private fun extractAndStorePendingCall(intent: Intent?) {
        if (intent == null) return
        
        val callId = intent.getStringExtra(NotificationHelper.EXTRA_CALL_ID)
        val threadId = intent.getStringExtra(NotificationHelper.EXTRA_THREAD_ID)
        val callerName = intent.getStringExtra(NotificationHelper.EXTRA_CALLER_NAME)
        val callType = intent.getStringExtra(NotificationHelper.EXTRA_CALL_TYPE) ?: "audio"
        val action = intent.getStringExtra("action")
        
        Log.d("MainActivity", "🔍 extractAndStorePendingCall:")
        Log.d("MainActivity", "   callId=$callId")
        Log.d("MainActivity", "   threadId=$threadId")
        Log.d("MainActivity", "   callerName=$callerName")

        if (callId.isNullOrBlank() || threadId.isNullOrBlank()) {
            return
        }
        
        val app = application as FamilyConnectApp
        app.setPendingCall(PendingCallIntent(callId, threadId, callerName ?: "User", callType))
    }
    
    private fun handleNotificationNavigation(intent: Intent?) {
        val fromNotification = intent?.getBooleanExtra("fromNotification", false) ?: false
        
        if (fromNotification) {
            val threadId = intent?.getStringExtra(NotificationHelper.EXTRA_THREAD_ID)
            val senderName = intent?.getStringExtra("senderName")
            
            Log.d("MainActivity", "📩 Opened from message notification")
            Log.d("MainActivity", "   threadId=$threadId")
            Log.d("MainActivity", "   senderName=$senderName")
            
            if (!threadId.isNullOrBlank() && viewModelInstance != null) {
                viewModelInstance?.openChatByThreadId(threadId)
                Log.d("MainActivity", "✅ Navigating to chat: $threadId")
            } else {
                Log.w("MainActivity", "⚠️ Missing threadId or ViewModel not ready")
            }
        }
    }
    
    private fun processPendingCall(viewModel: FamilyViewModel) {
        if (pendingCallHandled) {
            return
        }
        
        val app = application as FamilyConnectApp
        val pendingCall = app.pendingCallIntent.value
        val action = intent.getStringExtra("action")
        val shouldAutoAccept = action == "accept_call"
        
        if (pendingCall != null) {
            pendingCallHandled = true
            
            NotificationHelper.cancelCallNotification(this, pendingCall.callId)
            
            // ✅ FIX: If accept button was tapped on notification, auto-accept the call
            if (shouldAutoAccept) {
                try {
                    viewModel.acceptCall(
                        callId = pendingCall.callId,
                        threadId = pendingCall.threadId,
                        fromUserNameOverride = pendingCall.callerName
                    )
                } catch (e: Exception) {
                    Log.e("MainActivity", "   ❌ acceptCall() threw exception: ${e.message}", e)
                }
            } else {
                // Normal case: just show ringing UI
                Log.d("MainActivity", "   📞 Showing ringing UI...")
                Log.d("MainActivity", "   Calling setIncomingCallRinging with:")
                Log.d("MainActivity", "     callId=${pendingCall.callId}")
                Log.d("MainActivity", "     threadId=${pendingCall.threadId}")
                Log.d("MainActivity", "     callerName=${pendingCall.callerName}")
                Log.d("MainActivity", "     callType=${pendingCall.callType} ⭐")
                
                viewModel.setIncomingCallRinging(
                    callId = pendingCall.callId,
                    threadId = pendingCall.threadId,
                    callerName = pendingCall.callerName,
                    callType = pendingCall.callType
                )
                Log.d("MainActivity", "   ✅ setIncomingCallRinging() completed with type=${pendingCall.callType}")
            }
        } else {
            Log.d("MainActivity", "   ℹ️ No pending call found")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)  // CRITICAL: Update the intent FIRST
        val action = intent.getStringExtra("action")
        
        // Always extract and store the pending call
        extractAndStorePendingCall(intent)
        
        // If we have ViewModel, process immediately with this fresh intent
        val vm = viewModelInstance
        if (vm != null) {
            processPendingCall(vm)
            // 📩 Handle notification tap navigation
            handleNotificationNavigation(intent)
        } else {
            Log.d("MainActivity", "⚠️ ViewModel not ready, will process in LaunchedEffect")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        viewModelInstance?.setUserOfflineOnExit()
    }
    
    private fun ensureCallListenerServiceRunning() {
        try {
            Log.d("MainActivity", "🔍 Checking if user is already logged in...")
            val app = application as FamilyConnectApp
            
            val loggedInMobile = runBlocking {
                try {
                    app.repository.loggedInMobileFlow.first()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error reading loggedInMobileFlow: ${e.message}")
                    null
                }
            }
            
            if (!loggedInMobile.isNullOrBlank()) {
                Log.d("MainActivity", "✅ User already logged in: $loggedInMobile")
                Log.d("MainActivity", "🚀 Auto-starting CallListenerService...")
                
                try {
                    CallListenerService.start(this, loggedInMobile, "")
                    Log.d("MainActivity", "✅ CallListenerService started successfully!")
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Failed to start CallListenerService: ${e.message}", e)
                }
            } else {
                Log.d("MainActivity", "ℹ️ No logged-in user found, service will start after login")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in ensureCallListenerServiceRunning: ${e.message}", e)
        }
    }
    
    override fun onPause() {
        super.onPause()
        Log.d("MainActivity", "App paused, background service continues listening")
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(permission), 1001)
            }
        }
    }

    private fun maybeRequestFilePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissions = mutableListOf<String>()
            
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            
            if (permissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1002)
            }
        }
    }

    private fun maybeRequestAudioPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permission = Manifest.permission.RECORD_AUDIO
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(permission), 1003)
            }
        }
    }

    private fun maybeRequestCameraPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permission = Manifest.permission.CAMERA
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(permission), 1004)
            }
        }
    }

    private fun maybeRequestFullScreenIntentPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {  // Android 12+
            val permission = Manifest.permission.USE_FULL_SCREEN_INTENT
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "Requesting USE_FULL_SCREEN_INTENT permission")
                ActivityCompat.requestPermissions(this, arrayOf(permission), 1005)
            } else {
                Log.d("MainActivity", "✅ USE_FULL_SCREEN_INTENT permission already granted")
            }
        }
    }
    

}

@Composable
private fun Root(app: FamilyConnectApp, mainActivity: MainActivity, onViewModelReady: (FamilyViewModel) -> Unit = {}) {
    val viewModel: FamilyViewModel = viewModel(factory = FamilyViewModelFactory(app.repository, app))
    onViewModelReady(viewModel)
    
    Column(modifier = Modifier.fillMaxWidth()) {
        // UI content goes here
        FamilyConnectRoot(viewModel = viewModel)
    }
}
