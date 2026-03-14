package com.allysong.domain.model

// ============================================================================
// NotificationEvent.kt
// ============================================================================
// Sealed class for ViewModel → Foreground Service communication.
// The ViewModel emits these events; the Android Service collects them to
// manage the persistent notification and post high-priority disaster alerts.
// ============================================================================

/**
 * Events that the ViewModel sends to the platform-specific notification service.
 */
sealed class NotificationEvent {

    /**
     * A disaster was detected while the Activity is not visible.
     * The service should post a high-priority notification that opens the HITL screen.
     */
    data class DisasterAlert(
        val event: DisasterEvent
    ) : NotificationEvent()

    /**
     * Sensor or mesh monitoring has started; the service should show
     * the persistent low-priority "monitoring active" notification.
     */
    object MonitoringStarted : NotificationEvent()

    /**
     * All monitoring has stopped; the service should remove the persistent
     * notification and stop itself.
     */
    object MonitoringStopped : NotificationEvent()
}
