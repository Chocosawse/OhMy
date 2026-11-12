package com.ohmy.zfsync

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.ohmy.zfsync.ui.CameraViewModel
import com.ohmy.zfsync.ui.NikonSyncApp
import com.ohmy.zfsync.ui.theme.OhMyTheme

class MainActivity : ComponentActivity() {

    private val viewModel: CameraViewModel by viewModels()

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* no-op: user can retry Connect */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val neededPermissions = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (neededPermissions.isNotEmpty()) {
            requestPermissions.launch(neededPermissions.toTypedArray())
        }

        setContent {
            OhMyTheme {
                NikonSyncApp(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The camera's Wi-Fi AP can get torn down by the system while backgrounded (see
        // CameraSyncRepository.handleNetworkLost) -- coming back to the foreground is the right
        // moment to retry, since a WifiNetworkSpecifier join may need a system dialog that can't
        // show while backgrounded.
        viewModel.retryConnectionIfNeeded()
    }

    private fun requiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.NEARBY_WIFI_DEVICES
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        return permissions
    }
}
