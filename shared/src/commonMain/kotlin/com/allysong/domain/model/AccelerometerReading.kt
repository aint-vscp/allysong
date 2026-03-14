package com.allysong.domain.model

// ============================================================================
// AccelerometerReading.kt
// ============================================================================
// Immutable data class representing a single 3-axis accelerometer sample.
// Used by the sensor fusion pipeline to detect seismic wave signatures.
// ============================================================================

/**
 * A single accelerometer reading from the device's IMU.
 *
 * @property x Lateral acceleration (m/s²)
 * @property y Longitudinal acceleration (m/s²)
 * @property z Vertical acceleration (m/s²)
 * @property timestampMs Monotonic timestamp in milliseconds when the sample
 *                       was captured. Uses the sensor's own clock domain.
 */
data class AccelerometerReading(
    val x: Float,
    val y: Float,
    val z: Float,
    val timestampMs: Long
) {
    /**
     * Computes the Euclidean magnitude of the acceleration vector.
     * This is the primary input feature for the seismic detection model:
     *   magnitude = √(x² + y² + z²)
     *
     * At rest, magnitude ≈ 9.81 m/s² (Earth gravity).
     * Seismic P-waves produce sharp spikes above this baseline.
     */
    val magnitude: Float
        get() = kotlin.math.sqrt(x * x + y * y + z * z)
}
