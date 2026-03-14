package com.allysong.domain.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.allysong.domain.model.BarometerReading
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

// ============================================================================
// AndroidBarometerController.kt – androidMain
// ============================================================================
// Android implementation of the BarometerController interface.
//
// Uses android.hardware.SensorManager to register a TYPE_PRESSURE listener
// and emit BarometerReading objects through a Kotlin callbackFlow.
//
// Barometric pressure monitoring enables typhoon/cyclone detection by
// identifying rapid pressure drops characteristic of approaching storms.
// Sampling at 5 Hz (200,000µs) is sufficient since pressure changes slowly.
// ============================================================================

/**
 * Android-specific barometric pressure controller.
 *
 * @param context Application context (NOT Activity) to avoid memory leaks.
 */
class AndroidBarometerController(
    private val context: Context
) : BarometerController {

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val barometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

    private var activeListener: SensorEventListener? = null

    override val isBarometerAvailable: Boolean
        get() = barometer != null

    /**
     * Streams barometric pressure readings via a callbackFlow.
     *
     * @param samplingPeriodUs Sensor delay in microseconds.
     *        Default: 200,000µs = 5 Hz. Barometric pressure changes slowly
     *        so a lower rate conserves battery while still capturing storm fronts.
     */
    override fun startMonitoring(samplingPeriodUs: Int): Flow<BarometerReading> {
        return callbackFlow {
            val sensor = barometer
                ?: throw IllegalStateException("No barometer available on this device")

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    // event.values[0] = atmospheric pressure in hPa (millibars)
                    val reading = BarometerReading(
                        pressureHpa = event.values[0],
                        timestampMs = event.timestamp / 1_000_000
                    )
                    trySend(reading)
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                    // Accuracy changes are not critical for pressure monitoring
                }
            }

            sensorManager.registerListener(listener, sensor, samplingPeriodUs)
            activeListener = listener

            awaitClose {
                sensorManager.unregisterListener(listener)
                activeListener = null
            }
        }
    }

    override fun stopMonitoring() {
        activeListener?.let { listener ->
            sensorManager.unregisterListener(listener)
            activeListener = null
        }
    }
}
