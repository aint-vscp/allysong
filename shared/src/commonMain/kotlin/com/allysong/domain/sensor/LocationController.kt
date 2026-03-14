package com.allysong.domain.sensor

import com.allysong.domain.model.LocationReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-agnostic interface for GPS location tracking.
 *
 * Implementations:
 *   - androidMain -> AndroidLocationController (FusedLocationProviderClient)
 */
interface LocationController {
    val isLocationAvailable: Boolean
    val lastKnownLocation: StateFlow<LocationReading?>
    fun startTracking(): Flow<LocationReading>
    fun stopTracking()
}
