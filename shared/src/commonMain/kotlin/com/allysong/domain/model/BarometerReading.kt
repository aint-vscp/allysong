package com.allysong.domain.model

// ============================================================================
// BarometerReading.kt
// ============================================================================
// Immutable data class representing a single barometric pressure sample.
// Used by the typhoon detection pipeline to identify rapid pressure drops
// consistent with approaching cyclone activity.
// ============================================================================

/**
 * A single barometric pressure reading from the device's pressure sensor.
 *
 * @property pressureHpa Atmospheric pressure in hectopascals (hPa / millibars).
 *                       Sea-level standard: ~1013.25 hPa.
 *                       Typhoon eye: ~900–950 hPa.
 * @property timestampMs Monotonic timestamp in milliseconds when the sample
 *                       was captured.
 */
data class BarometerReading(
    val pressureHpa: Float,
    val timestampMs: Long
)
