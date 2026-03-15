package com.allysong

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.allysong.domain.model.NotificationEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

// ============================================================================
// AllySongService.kt
// ============================================================================
// Android Foreground Service that keeps the AllySong process alive for
// continuous sensor monitoring and mesh networking.
//
// Architecture:
//   - The sensor pipeline stays in the ViewModel (Application-scoped).
//   - This Service is purely a process-keepalive and notification poster.
//   - Communication: ViewModel → Service via companion MutableSharedFlow.
//   - Two notification channels:
//     1. Monitoring (LOW priority): persistent "sensors active" notification
//     2. Alerts (HIGH priority): disaster detected while app is backgrounded
//   - Acquires a partial WakeLock to keep the CPU active during screen-off.
//   - Returns START_STICKY so the system restarts the service if killed.
// ============================================================================

class AllySongService : Service() {

    companion object {
        const val CHANNEL_ID_MONITORING = "allysong_monitoring"
        const val CHANNEL_ID_ALERT = "allysong_alert"
        const val NOTIFICATION_ID_MONITORING = 1001
        const val NOTIFICATION_ID_ALERT = 1002

        /** ViewModel emits events here; Service collects them. */
        val notificationEvents = MutableSharedFlow<NotificationEvent>(extraBufferCapacity = 8)

        fun start(context: Context) {
            val intent = Intent(context, AllySongService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AllySongService::class.java))
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var overlayManager: DisasterOverlayManager

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        acquireWakeLock()
        overlayManager = DisasterOverlayManager(this)

        val notification = buildMonitoringNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID_MONITORING,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID_MONITORING, notification)
        }

        observeNotificationEvents()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        overlayManager.dismiss()
        releaseWakeLock()
        serviceScope.cancel()
    }

    // ── Notification event observer ──────────────────────────────────────────

    private fun observeNotificationEvents() {
        serviceScope.launch {
            notificationEvents.collect { event ->
                when (event) {
                    is NotificationEvent.DisasterAlert -> {
                        // Show floating overlay (chat-head style) if permission granted
                        if (canDrawOverlay()) {
                            overlayManager.showDisasterAlert(event.event)
                        }
                        // Also post a notification as fallback
                        postAlertNotification(event)
                    }
                    is NotificationEvent.MonitoringStarted -> { /* already showing */ }
                    is NotificationEvent.MonitoringStopped -> stopSelf()
                }
            }
        }
    }

    private fun canDrawOverlay(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.provider.Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    // ── Alert notification (high priority) ───────────────────────────────────

    private fun postAlertNotification(alert: NotificationEvent.DisasterAlert) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "HITL_VALIDATION")
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_ALERT)
            .setContentTitle("DISASTER DETECTED")
            .setContentText("${alert.event.type.name} – Tap to respond")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pendingIntent, true)
            .build()
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID_ALERT, notification)
    }

    // ── Persistent monitoring notification (low priority) ────────────────────

    private fun buildMonitoringNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID_MONITORING)
            .setContentTitle("AllySong Active")
            .setContentText("Monitoring sensors for disaster detection")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    // ── Notification channels ────────────────────────────────────────────────

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_MONITORING,
                    "Sensor Monitoring",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Persistent notification while sensors are active"
                }
            )

            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_ALERT,
                    "Disaster Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "High-priority alerts when disaster is detected"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                }
            )
        }
    }

    // ── Wake lock management ─────────────────────────────────────────────────

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        @Suppress("DEPRECATION")
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AllySong::SensorMonitoring"
        ).apply { acquire() }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
}
