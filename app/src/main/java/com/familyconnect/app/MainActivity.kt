package com.familyconnect.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.familyconnect.app.notifications.NotificationHelper
import com.familyconnect.app.ui.FamilyConnectRoot
import com.familyconnect.app.ui.FamilyViewModel
import com.familyconnect.app.ui.FamilyViewModelFactory
import com.familyconnect.app.ui.theme.FamilyConnectTheme
import android.util.Log

class MainActivity : ComponentActivity() {
    private var viewModelInstance: FamilyViewModel? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "=== onCreate called ===")
        Log.d("MainActivity", "Intent action: ${intent.action}")
        Log.d("MainActivity", "Intent extras keys: ${intent.extras?.keySet()}")
        
        // CRITICAL: Extract and store call data BEFORE rendering UI
        extractAndStorePendingCall(intent)
        
        maybeRequestNotificationPermission()
        maybeRequestFilePermissions()
        maybeRequestAudioPermissions()
        maybeRequestCameraPermission()
        
        // Ensure window is visible when launched from notification
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                       android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        
        val app = application as FamilyConnectApp
        
        Log.d("MainActivity", "Setting Compose content...")
        setContent {
            FamilyConnectTheme {
                Root(app = app) { vm ->
                    Log.d("MainActivity", "✅ ViewModel created and ready")
                    viewModelInstance = vm
                    
                    // Now process the pending call with the ready ViewModel
                    processPendingCall(vm)
                }
            }
        }
        Log.d("MainActivity", "Compose content set")
    }
    
    private fun extractAndStorePendingCall(intent: Intent?) {
        if (intent == null) return
        
        val callId = intent.getStringExtra(NotificationHelper.EXTRA_CALL_ID)
        val threadId = intent.getStringExtra(NotificationHelper.EXTRA_THREAD_ID)
        val callerName = intent.getStringExtra(NotificationHelper.EXTRA_CALLER_NAME)
        
        Log.d("MainActivity", "extractAndStorePendingCall: callId=$callId, threadId=$threadId, callerName=$callerName")
        
        if (callId.isNullOrBlank() || threadId.isNullOrBlank()) {
            Log.w("MainActivity", "❌ Missing call data in intent!")
            return
        }
        
        Log.d("MainActivity", "🔔 EXTRACT: Storing pending call for UI: $callId from $callerName")
        val app = application as FamilyConnectApp
        app.pendingCallIntent = PendingCallIntent(callId, threadId, callerName ?: "User")
        
        // Clear from intent so it doesn't re-trigger
        intent.removeExtra(NotificationHelper.EXTRA_CALL_ID)
        intent.removeExtra(NotificationHelper.EXTRA_THREAD_ID)
        intent.removeExtra(NotificationHelper.EXTRA_CALLER_NAME)
    }
    
    private fun processPendingCall(viewModel: FamilyViewModel) {
        val app = application as FamilyConnectApp
        val pendingCall = app.pendingCallIntent
        
        if (pendingCall != null) {
            Log.d("MainActivity", "✅ PROCESS: Found pending call: ${pendingCall.callId}")
            app.pendingCallIntent = null // Clear so we don't process it again
            
            NotificationHelper.cancelCallNotification(this)
            
            Log.d("MainActivity", "📞 Calling viewModel.acceptCall(${pendingCall.callId})")
            viewModel.acceptCall(pendingCall.callId, pendingCall.threadId, pendingCall.callerName)
            Log.d("MainActivity", "✅ acceptCall completed. callState.status = ${viewModel.callState.status}")
        } else {
            Log.d("MainActivity", "ℹ️ No pending call to process (might be coming from LaunchedEffect)")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.d("MainActivity", "🔄 onNewIntent called with action: ${intent.action}")
        Log.d("MainActivity", "   Extras: ${intent.extras?.keySet()}")
        
        // Always try to extract and process
        extractAndStorePendingCall(intent)
        
        // If we have ViewModel, process immediately
        val vm = viewModelInstance
        if (vm != null) {
            Log.d("MainActivity", "✅ ViewModel available, processing immediately")
            processPendingCall(vm)
        } else {
            Log.d("MainActivity", "⚠️ ViewModel not ready yet, will process in LaunchedEffect")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        viewModelInstance?.setUserOfflineOnExit()
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
}

@Composable
private fun Root(app: FamilyConnectApp, onViewModelReady: (FamilyViewModel) -> Unit = {}) {
    val viewModel: FamilyViewModel = viewModel(factory = FamilyViewModelFactory(app.repository, app))
    onViewModelReady(viewModel)
    FamilyConnectRoot(viewModel = viewModel)
}
