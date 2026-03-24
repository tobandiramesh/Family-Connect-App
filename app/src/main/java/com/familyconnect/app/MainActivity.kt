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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeRequestNotificationPermission()
        val app = application as FamilyConnectApp
        setContent {
            FamilyConnectTheme {
                Root(app = app)
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(permission), 1001)
            }
        }
    }
}

@Composable
private fun Root(app: FamilyConnectApp) {
    val viewModel: FamilyViewModel = viewModel(factory = FamilyViewModelFactory(app.repository))
    FamilyConnectRoot(viewModel = viewModel)
}
