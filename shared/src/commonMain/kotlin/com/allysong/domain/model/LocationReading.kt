package com.allysong.domain.model

/**
 * A single GPS location reading.
 *
 * @property latitude WGS-84 latitude in degrees.
 * @property longitude WGS-84 longitude in degrees.
 * @property accuracyMeters Estimated horizontal accuracy radius in meters.
 * @property timestampMs Epoch timestamp when the fix was obtained.
 */
data class LocationReading(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timestampMs: Long
)
