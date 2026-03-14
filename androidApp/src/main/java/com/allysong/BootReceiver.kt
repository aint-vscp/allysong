package com.allysong

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.allysong.persistence.AllySongPreferences

// ============================================================================
// BootReceiver.kt
// ============================================================================
// Listens for BOOT_COMPLETED and MY_PACKAGE_REPLACED broadcasts.
// If the user had monitoring enabled before reboot (or app update), this
// receiver restarts the foreground service so detection resumes automatically.
// ============================================================================

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val prefs = AllySongPreferences(context)

            // Only restart if the user had monitoring active before shutdown
            if (prefs.wasMonitoringActive) {
                AllySongService.start(context)
            }
        }
    }
}
