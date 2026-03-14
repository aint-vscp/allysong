package com.allysong.persistence

import android.content.Context
import android.content.SharedPreferences

// ============================================================================
// AllySongPreferences.kt – androidMain
// ============================================================================
// Persistent storage for user settings via SharedPreferences. This file is
// included in Android Auto Backup (Google Drive) so settings survive:
//   1. App restarts / process death
//   2. Device reboots
//   3. Factory resets (restored from Google Drive backup on re-setup)
//
// Stored state:
//   - Sensor monitoring toggle (accelerometer)
//   - Barometer monitoring toggle
//   - BLE mesh networking toggle
//   - Device alias (persistent identity across mesh sessions)
//   - Whether any monitoring was active (for boot receiver)
// ============================================================================

class AllySongPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Sensor toggle ────────────────────────────────────────────────────────

    var isSensorEnabled: Boolean
        get() = prefs.getBoolean(KEY_SENSOR_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SENSOR_ENABLED, value).apply()

    // ── Barometer toggle ─────────────────────────────────────────────────────

    var isBarometerEnabled: Boolean
        get() = prefs.getBoolean(KEY_BAROMETER_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BAROMETER_ENABLED, value).apply()

    // ── Mesh toggle ──────────────────────────────────────────────────────────

    var isMeshEnabled: Boolean
        get() = prefs.getBoolean(KEY_MESH_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_MESH_ENABLED, value).apply()

    // ── Device alias ─────────────────────────────────────────────────────────

    var localAlias: String
        get() = prefs.getString(KEY_LOCAL_ALIAS, null)
            ?: "Device-${(1000..9999).random()}".also { localAlias = it }
        set(value) = prefs.edit().putString(KEY_LOCAL_ALIAS, value).apply()

    // ── Service was running (for boot receiver) ──────────────────────────────

    /** True if any monitoring was active when the app last ran. */
    var wasMonitoringActive: Boolean
        get() = prefs.getBoolean(KEY_WAS_MONITORING, false)
        set(value) = prefs.edit().putBoolean(KEY_WAS_MONITORING, value).apply()

    companion object {
        private const val PREFS_NAME = "allysong_settings"
        private const val KEY_SENSOR_ENABLED = "sensor_enabled"
        private const val KEY_BAROMETER_ENABLED = "barometer_enabled"
        private const val KEY_MESH_ENABLED = "mesh_enabled"
        private const val KEY_LOCAL_ALIAS = "local_alias"
        private const val KEY_WAS_MONITORING = "was_monitoring_active"
    }
}
