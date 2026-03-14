package com.allysong.domain.sensor

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.allysong.domain.model.LocationReading
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Android GPS location controller using FusedLocationProviderClient.
 *
 * Battery-efficient: requests updates every 30 seconds with balanced accuracy.
 * Only active when the mesh is running (privacy: location shared only when
 * the user explicitly opts in).
 *
 * @param context Application context for location services.
 */
class AndroidLocationController(
    private val context: Context
) : LocationController {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _lastKnownLocation = MutableStateFlow<LocationReading?>(null)
    override val lastKnownLocation: StateFlow<LocationReading?> = _lastKnownLocation.asStateFlow()

    override val isLocationAvailable: Boolean
        get() = true // FusedLocationProvider handles availability internally

    private var activeCallback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    override fun startTracking(): Flow<LocationReading> {
        return callbackFlow {
            val request = LocationRequest.Builder(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                30_000L // 30 seconds
            ).setMinUpdateIntervalMillis(10_000L).build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location = result.lastLocation ?: return
                    val reading = LocationReading(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyMeters = location.accuracy,
                        timestampMs = location.time
                    )
                    _lastKnownLocation.value = reading
                    trySend(reading)
                }
            }

            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            activeCallback = callback

            awaitClose {
                fusedClient.removeLocationUpdates(callback)
                activeCallback = null
            }
        }
    }

    override fun stopTracking() {
        activeCallback?.let { callback ->
            fusedClient.removeLocationUpdates(callback)
            activeCallback = null
        }
    }
}
