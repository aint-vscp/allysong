package com.allysong.di

import android.content.Context
import com.allysong.domain.ai.AndroidDisasterDetectionEngine
import com.allysong.domain.ai.DisasterDetectionEngine
import com.allysong.domain.ai.HeuristicTyphoonDetectionEngine
import com.allysong.domain.ai.TyphoonDetectionEngine
import com.allysong.domain.crypto.AesGcmPayloadEncryptor
import com.allysong.domain.crypto.PayloadEncryptor
import com.allysong.domain.mesh.AndroidMeshController
import com.allysong.domain.mesh.MeshNetworkController
import com.allysong.domain.sensor.AndroidBarometerController
import com.allysong.domain.sensor.AndroidSensorController
import com.allysong.domain.sensor.AndroidLocationController
import com.allysong.domain.sensor.BarometerController
import com.allysong.domain.sensor.LocationController
import com.allysong.domain.sensor.SensorController
import com.allysong.persistence.AllySongPreferences
import com.allysong.viewmodel.AllySongViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// ============================================================================
// AndroidServiceLocator.kt – androidMain
// ============================================================================
// Manual dependency injection / service locator for the Android prototype.
//
// This provides a simple, explicit wiring of platform implementations to
// commonMain interfaces. For production, replace with Koin or manual DI.
//
// Rationale for manual DI in the prototype:
//   - Zero additional dependencies.
//   - Clear visibility into what is injected where.
//   - Trivial to replace with Koin/Hilt when graduating from prototype.
// ============================================================================

/**
 * Creates and caches all platform dependencies for the Android target.
 *
 * @param applicationContext The application-scoped context (NOT Activity).
 */
class AndroidServiceLocator(
    private val applicationContext: Context
) {
    // ── Persistent preferences ───────────────────────────────────────────────
    val preferences: AllySongPreferences by lazy {
        AllySongPreferences(applicationContext)
    }

    // ── Encryption subsystem ─────────────────────────────────────────────────
    val payloadEncryptor: PayloadEncryptor by lazy {
        AesGcmPayloadEncryptor()
    }

    // ── Sensor subsystem ────────────────────────────────────────────────────
    val sensorController: SensorController by lazy {
        AndroidSensorController(applicationContext)
    }

    // ── Barometer subsystem ─────────────────────────────────────────────────
    val barometerController: BarometerController by lazy {
        AndroidBarometerController(applicationContext)
    }

    // ── Location subsystem ───────────────────────────────────────────────────
    val locationController: LocationController by lazy {
        AndroidLocationController(applicationContext)
    }

    // ── Mesh networking subsystem ───────────────────────────────────────────
    val meshController: MeshNetworkController by lazy {
        AndroidMeshController(applicationContext, payloadEncryptor)
    }

    // ── Edge AI subsystem ───────────────────────────────────────────────────
    val detectionEngine: DisasterDetectionEngine by lazy {
        AndroidDisasterDetectionEngine(applicationContext)
    }

    // ── Typhoon detection subsystem ─────────────────────────────────────────
    val typhoonEngine: TyphoonDetectionEngine by lazy {
        HeuristicTyphoonDetectionEngine()
    }

    // ── ViewModel ───────────────────────────────────────────────────────────
    // Uses a SupervisorJob so that a failure in one child coroutine (e.g.,
    // sensor read error) doesn't cancel the entire scope (mesh stays alive).
    val viewModel: AllySongViewModel by lazy {
        AllySongViewModel(
            sensorController = sensorController,
            barometerController = barometerController,
            meshController = meshController,
            detectionEngine = detectionEngine,
            typhoonEngine = typhoonEngine,
            locationController = locationController,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            initialAlias = preferences.localAlias
        )
    }
}
