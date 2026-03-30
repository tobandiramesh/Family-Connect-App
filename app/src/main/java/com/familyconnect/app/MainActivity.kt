package com.familyconnect.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.familyconnect.app.ui.FamilyConnectRoot
import com.familyconnect.app.ui.FamilyViewModel
import com.familyconnect.app.ui.FamilyViewModelFactory
import com.familyconnect.app.ui.theme.FamilyConnectTheme
import android.util.Log

class MainActivity : ComponentActivity() {
    private var viewModelInstance: FamilyViewModel? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeRequestNotificationPermission()
        maybeRequestFilePermissions()
        maybeRequestAudioPermissions()
        maybeRequestCameraPermission()
        val app = application as FamilyConnectApp
        setContent {
            FamilyConnectTheme {
                Root(app = app) { vm -> viewModelInstance = vm }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        viewModelInstance?.setUserOfflineOnExit()
    }
    
    override fun onPause() {
        super.onPause()
        // Can optionally set offline on pause, but logout explicitly handles it
        Log.d("MainActivity", "App paused, user will go offline if they don't return soon")
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
