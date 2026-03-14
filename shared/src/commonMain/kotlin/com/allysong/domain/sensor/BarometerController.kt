package com.allysong.domain.sensor

import com.allysong.domain.model.BarometerReading
import kotlinx.coroutines.flow.Flow

// ============================================================================
// BarometerController.kt – commonMain
// ============================================================================
// Platform-agnostic interface for streaming barometric pressure data.
//
// KMP Strategy: This interface lives in commonMain. Each platform provides
// an injected implementation:
//   - androidMain → AndroidBarometerController (android.hardware.SensorManager)
//
// Barometric pressure monitoring enables typhoon/cyclone detection by
// identifying rapid pressure drops characteristic of approaching storms.
// ============================================================================

/**
 * Abstraction over the device's barometric pressure sensor.
 *
 * Implementations must:
 * 1. Register a sensor listener on [startMonitoring].
 * 2. Emit [BarometerReading]s through the returned cold [Flow].
 * 3. Unregister the listener when the Flow collector cancels or
 *    [stopMonitoring] is called.
 */
interface BarometerController {

    /**
     * Whether the device has a barometric pressure sensor available.
     * Must be callable from any thread without suspension.
     */
    val isBarometerAvailable: Boolean

    /**
     * Begins streaming barometric pressure data.
     *
     * @param samplingPeriodUs Requested sampling period in microseconds.
     *        Default: 200,000µs = 5 Hz. Barometric pressure changes slowly
     *        so a lower rate conserves battery while capturing storm fronts.
     * @return A cold [Flow] of [BarometerReading]. The sensor listener
     *         is registered when the first collector subscribes and
     *         unregistered when the last collector cancels.
     */
    fun startMonitoring(samplingPeriodUs: Int = 200_000): Flow<BarometerReading>

    /**
     * Immediately stops all barometer monitoring and releases hardware resources.
     * Safe to call multiple times.
     */
    fun stopMonitoring()
}
