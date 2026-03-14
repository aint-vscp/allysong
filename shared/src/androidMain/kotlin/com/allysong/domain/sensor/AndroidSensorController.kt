package com.allysong.domain.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.allysong.domain.model.AccelerometerReading
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

// ============================================================================
// AndroidSensorController.kt – androidMain
// ============================================================================
// Android implementation of the SensorController interface.
//
// Uses android.hardware.SensorManager to register an accelerometer listener
// and emit AccelerometerReading objects through a Kotlin callbackFlow.
//
// callbackFlow is the idiomatic bridge between Android's callback-based
// sensor API and Kotlin's Flow-based reactive streams. It handles:
//   - Registering the listener when a collector subscribes.
//   - Unregistering when the collector cancels (structured concurrency).
//   - Back-pressure via the Channel buffer (CONFLATED strategy — drops
//     stale readings to keep the AI pipeline on the latest data).
// ============================================================================

/**
 * Android-specific accelerometer controller.
 *
 * @param context Application context (NOT Activity) to avoid memory leaks.
 */
class AndroidSensorController(
    private val context: Context
) : SensorController {

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // Track the current listener so stopMonitoring() can unregister it
    private var activeListener: SensorEventListener? = null

    override val isAccelerometerAvailable: Boolean
        get() = accelerometer != null

    /**
     * Streams accelerometer readings via a callbackFlow.
     *
     * The SensorEventListener is registered when the first collector
     * subscribes and automatically unregistered when the Flow is cancelled
     * (e.g., when the CoroutineScope is cancelled or the ViewModel clears).
     *
     * @param samplingPeriodUs Sensor delay in microseconds.
     *        - SensorManager.SENSOR_DELAY_FASTEST ≈ ~5ms
     *        - 20_000µs (20ms) = 50 Hz → good balance for seismic detection.
     */
    override fun startMonitoring(samplingPeriodUs: Int): Flow<AccelerometerReading> {
        return callbackFlow {
            val sensor = accelerometer
                ?: throw IllegalStateException("No accelerometer available on this device")

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    // event.values[0] = x-axis, [1] = y-axis, [2] = z-axis
                    // event.timestamp is nanoseconds from boot; convert to ms.
                    val reading = AccelerometerReading(
                        x = event.values[0],
                        y = event.values[1],
                        z = event.values[2],
                        timestampMs = event.timestamp / 1_000_000
                    )
                    // trySend is non-blocking. If the buffer is full (CONFLATED),
                    // the oldest undelivered reading is dropped — this is the
                    // correct behavior for real-time sensor streams.
                    trySend(reading)
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                    // Accuracy changes are not critical for this prototype.
                    // In production, degrade confidence if accuracy drops.
                }
            }

            // Register the listener with the requested sampling period
            sensorManager.registerListener(
                listener,
                sensor,
                samplingPeriodUs
            )
            activeListener = listener

            // awaitClose is invoked when the Flow collector cancels.
            // This ensures deterministic cleanup of the sensor listener.
            awaitClose {
                sensorManager.unregisterListener(listener)
                activeListener = null
            }
        }
    }

    /**
     * Imperatively stops sensor monitoring.
     * Safe to call even if no monitoring is active.
     */
    override fun stopMonitoring() {
        activeListener?.let { listener ->
            sensorManager.unregisterListener(listener)
            activeListener = null
        }
    }
}
