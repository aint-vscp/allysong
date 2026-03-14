package com.allysong

import android.app.Application
import com.allysong.di.AndroidServiceLocator
import com.allysong.domain.model.NotificationEvent
import com.allysong.viewmodel.ServiceCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// ============================================================================
// AllySongApplication.kt
// ============================================================================
// Custom Application class that initializes the dependency graph at app start.
// The AndroidServiceLocator is created once here and accessed by MainActivity.
//
// Also wires the ViewModel → Foreground Service bridge so that the ViewModel
// (which lives in commonMain and cannot import Android classes) can trigger
// service start/stop and post disaster alert notifications.
//
// Persistence: Observes ViewModel state and saves toggle preferences to
// SharedPreferences (backed up to Google Drive via Auto Backup). On launch,
// restores previously enabled toggles so monitoring resumes automatically.
// ============================================================================

class AllySongApplication : Application() {

    /**
     * Global service locator instance. Activities and composables access
     * this to obtain the shared ViewModel and platform implementations.
     */
    lateinit var serviceLocator: AndroidServiceLocator
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        serviceLocator = AndroidServiceLocator(applicationContext)
        wireServiceBridge()
        observeAndPersistState()
        restoreSavedToggles()
    }

    /**
     * Connects the platform-agnostic ViewModel to the Android Foreground Service.
     * The ViewModel emits [ServiceCommand]s; this bridge translates them into
     * concrete Android service lifecycle calls.
     */
    private fun wireServiceBridge() {
        serviceLocator.viewModel.onServiceCommand = { command ->
            when (command) {
                is ServiceCommand.StartService -> {
                    AllySongService.start(this@AllySongApplication)
                }
                is ServiceCommand.StopService -> {
                    AllySongService.stop(this@AllySongApplication)
                }
                is ServiceCommand.PostDisasterAlert -> {
                    AllySongService.notificationEvents.tryEmit(
                        NotificationEvent.DisasterAlert(command.event)
                    )
                }
            }
        }
    }

    /**
     * Observes the ViewModel's UI state and persists toggle changes to
     * SharedPreferences. Uses distinctUntilChanged to avoid redundant writes.
     */
    private fun observeAndPersistState() {
        val prefs = serviceLocator.preferences
        val vm = serviceLocator.viewModel

        // Persist each toggle independently when it changes
        appScope.launch {
            vm.uiState
                .map { Triple(it.isSensorActive, it.isBarometerActive, it.isMeshActive) }
                .distinctUntilChanged()
                .collect { (sensor, barometer, mesh) ->
                    prefs.isSensorEnabled = sensor
                    prefs.isBarometerEnabled = barometer
                    prefs.isMeshEnabled = mesh
                    prefs.wasMonitoringActive = sensor || barometer || mesh
                }
        }
    }

    /**
     * Restores previously saved toggle states. If the user had monitoring
     * enabled before the app was killed / device rebooted, this re-enables
     * those pipelines automatically.
     */
    private fun restoreSavedToggles() {
        val prefs = serviceLocator.preferences
        val vm = serviceLocator.viewModel

        if (prefs.isSensorEnabled) vm.toggleSensor()
        if (prefs.isBarometerEnabled) vm.toggleBarometer()
        if (prefs.isMeshEnabled) vm.toggleMesh()
    }
}
