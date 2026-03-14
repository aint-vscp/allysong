package com.allysong.domain.sensor

import com.allysong.domain.model.AccelerometerReading
import kotlinx.coroutines.flow.Flow

// ============================================================================
// SensorController.kt – commonMain
// ============================================================================
// Platform-agnostic interface for streaming hardware accelerometer data.
//
// KMP Strategy: This interface lives in commonMain. Each platform provides
// an `actual` or injected implementation:
//   - androidMain → AndroidSensorController (android.hardware.SensorManager)
//   - iosMain    → IosSensorController (CoreMotion) [future]
//
// The Flow-based API integrates cleanly with Kotlin Coroutines and allows
// the disaster-detection pipeline to process readings reactively.
// ============================================================================

/**
 * Abstraction over the device's accelerometer hardware.
 *
 * Implementations must:
 * 1. Register a sensor listener on [startMonitoring].
 * 2. Emit [AccelerometerReading]s through the returned cold [Flow].
 * 3. Unregister the listener when the Flow collector cancels or
 *    [stopMonitoring] is called.
 */
interface SensorController {

    /**
     * Whether the device has a hardware accelerometer available.
     * Must be callable from any thread without suspension.
     */
    val isAccelerometerAvailable: Boolean

    /**
     * Begins streaming accelerometer data.
     *
     * @param samplingPeriodUs Requested sampling period in microseconds.
     *        Lower values (~20_000µs = 50 Hz) give higher fidelity for
     *        seismic detection but increase battery drain.
     * @return A cold [Flow] of [AccelerometerReading]. The sensor listener
     *         is registered when the first collector subscribes and
     *         unregistered when the last collector cancels.
     */
    fun startMonitoring(samplingPeriodUs: Int = 20_000): Flow<AccelerometerReading>

    /**
     * Immediately stops all sensor monitoring and releases hardware resources.
     * Safe to call multiple times.
     */
    fun stopMonitoring()
}
