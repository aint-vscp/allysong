package com.allysong

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.allysong.ui.navigation.AllySongApp
import com.allysong.viewmodel.AppScreen

// ============================================================================
// MainActivity.kt
// ============================================================================
// Single-Activity entry point for the AllySong Android app.
//
// Responsibilities:
//   1. Requests runtime permissions required for BLE mesh networking.
//   2. Retrieves the ViewModel from the Application-scoped service locator.
//   3. Sets the Compose content to AllySongApp (the shared navigation host).
//   4. Tracks Activity visibility for background notification decisions.
//   5. Handles notification tap intents (navigates to HITL_VALIDATION).
//
// Note: onDestroy does NOT call viewModel.onCleared() because the ViewModel
// is Application-scoped and must survive Activity recreation. The foreground
// service keeps the process alive for background monitoring.
// ============================================================================

class MainActivity : ComponentActivity() {

    // Runtime permission launcher – requests all BLE/location permissions
    // upfront so the mesh controller can operate without interruption.
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        // Log denied permissions for debugging; the app will degrade gracefully
        // (mesh toggle will show an error if BLE permissions are missing).
        grants.entries.filter { !it.value }.forEach { (perm, _) ->
            android.util.Log.w("AllySong", "Permission denied: $perm")
        }
    }

    private lateinit var viewModelRef: com.allysong.viewmodel.AllySongViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request permissions before setting content
        requestRequiredPermissions()
        requestOverlayPermission()

        // Obtain the ViewModel from the Application-scoped service locator
        val app = application as AllySongApplication
        viewModelRef = app.serviceLocator.viewModel

        // Handle notification tap intent (if launched from a disaster alert)
        handleNotificationIntent(intent)

        setContent {
            AllySongApp(viewModel = viewModelRef)
        }
    }

    override fun onStart() {
        super.onStart()
        // Tell the ViewModel the Activity is visible so it shows HITL screen
        // directly instead of posting a notification
        viewModelRef.setActivityVisible(true)
    }

    override fun onStop() {
        super.onStop()
        // Activity backgrounded → ViewModel will post notifications for alerts
        viewModelRef.setActivityVisible(false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    /**
     * If the Activity was launched from a disaster alert notification,
     * navigate to the HITL validation screen.
     */
    private fun handleNotificationIntent(intent: Intent?) {
        if (intent?.getStringExtra("navigate_to") == "HITL_VALIDATION") {
            if (::viewModelRef.isInitialized) {
                viewModelRef.navigateTo(AppScreen.HITL_VALIDATION)
            }
        }
    }

    /**
     * Collects all runtime permissions needed for BLE/Nearby and requests
     * any that haven't already been granted.
     *
     * Android 12+ (API 31+) uses the new BLUETOOTH_* permissions.
     * Older versions use ACCESS_FINE_LOCATION for BLE scanning.
     */
    private fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ BLE permissions
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ nearby Wi-Fi devices + notification permission
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Location is required by Nearby Connections for BLE scanning
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        // Filter to only permissions not yet granted
        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) !=
                PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    /**
     * Requests the "Display over other apps" permission needed for the
     * floating disaster alert overlay. This opens the system Settings page
     * if the permission hasn't been granted yet.
     */
    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }
    }
}
