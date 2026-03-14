package com.allysong.viewmodel

import com.allysong.domain.ai.DisasterDetectionEngine
import com.allysong.domain.ai.TyphoonDetectionEngine
import com.allysong.domain.mesh.MeshNetworkController
import com.allysong.domain.model.*
import com.allysong.domain.sensor.BarometerController
import com.allysong.domain.sensor.LocationController
import com.allysong.domain.sensor.SensorController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// ============================================================================
// AllySongViewModel.kt – commonMain
// ============================================================================
// Central ViewModel orchestrating AllySong's reactive pipeline:
//
//   Accelerometer Flow → Sliding Window → AI Inference → HITL Gate → SOS/Safe
//                                                                      ↓
//                                                              BLE Mesh Cascade
//
// This ViewModel is platform-agnostic. Platform implementations are injected
// via constructor parameters (interfaces defined in commonMain).
// ============================================================================

/**
 * The active screen/state of the application.
 */
enum class AppScreen {
    DASHBOARD,           // Normal monitoring mode
    HITL_VALIDATION,     // Disaster detected – awaiting user response
    COMMUNICATION_MODE,  // Offline mesh chat (post-SOS or received SOS)
    MANUAL_SOS           // Emergency type picker for manual SOS
}

/**
 * Immutable snapshot of the entire application state.
 * Observed by Compose via [StateFlow] to drive recomposition.
 */
data class AllySongUiState(
    val screen: AppScreen = AppScreen.DASHBOARD,
    val isSensorActive: Boolean = false,
    val isBarometerActive: Boolean = false,
    val isMeshActive: Boolean = false,
    val latestReading: AccelerometerReading? = null,
    val latestPressureHpa: Float? = null,
    val isBarometerAvailable: Boolean = false,
    val currentEvent: DisasterEvent? = null,
    val peerCount: Int = 0,
    val peers: List<PeerDevice> = emptyList(),
    val chatMessages: List<MeshMessage> = emptyList(),
    val aiStatus: String = "Idle – waiting for sensor data",
    val localAlias: String = "Device-${(1000..9999).random()}",
    val currentLatitude: Double = 0.0,
    val currentLongitude: Double = 0.0
)

/**
 * Commands the ViewModel sends to the platform-specific service layer.
 */
sealed class ServiceCommand {
    object StartService : ServiceCommand()
    object StopService : ServiceCommand()
    data class PostDisasterAlert(val event: DisasterEvent) : ServiceCommand()
}

/**
 * Primary ViewModel for the AllySong application.
 *
 * @param sensorController Platform-injected accelerometer abstraction.
 * @param barometerController Platform-injected barometric pressure abstraction.
 * @param meshController Platform-injected BLE mesh abstraction.
 * @param detectionEngine Platform-injected TFLite inference engine.
 * @param typhoonEngine Platform-injected typhoon detection engine.
 * @param scope CoroutineScope for launching reactive pipelines.
 *              On Android, this is typically viewModelScope.
 */
class AllySongViewModel(
    private val sensorController: SensorController,
    private val barometerController: BarometerController?,
    private val meshController: MeshNetworkController,
    private val detectionEngine: DisasterDetectionEngine,
    private val typhoonEngine: TyphoonDetectionEngine?,
    private val locationController: LocationController?,
    private val scope: CoroutineScope,
    initialAlias: String? = null
) {
    // ── Mutable backing state ───────────────────────────────────────────────
    private val _uiState = MutableStateFlow(
        AllySongUiState(
            localAlias = initialAlias ?: "Device-${(1000..9999).random()}"
        )
    )
    val uiState: StateFlow<AllySongUiState> = _uiState.asStateFlow()

    // Job references for cancellable pipelines
    private var sensorJob: Job? = null
    private var barometerJob: Job? = null
    private var meshListenerJob: Job? = null
    private var locationJob: Job? = null

    // Sliding window buffer for AI inference
    private val sensorWindow = mutableListOf<AccelerometerReading>()

    // Sliding window buffer for typhoon detection
    private val pressureWindow = mutableListOf<BarometerReading>()

    init {
        // Set barometer availability in initial state
        _uiState.update {
            it.copy(isBarometerAvailable = barometerController?.isBarometerAvailable == true)
        }
    }

    // ── Service bridge ───────────────────────────────────────────────────────
    /** Set by the platform layer (AllySongApplication) to bridge ViewModel → Service. */
    var onServiceCommand: ((ServiceCommand) -> Unit)? = null

    /** Tracks whether the Activity is currently visible (foreground). */
    private val _isActivityVisible = MutableStateFlow(true)

    /** Called by Activity in onStart/onStop to track visibility. */
    fun setActivityVisible(visible: Boolean) {
        _isActivityVisible.value = visible
    }

    // ── Sensor pipeline ─────────────────────────────────────────────────────

    /**
     * Toggles accelerometer monitoring on/off.
     * When active, readings flow through the AI detection pipeline.
     */
    fun toggleSensor() {
        if (_uiState.value.isSensorActive) {
            stopSensor()
        } else {
            startSensor()
        }
    }

    private fun startSensor() {
        if (!sensorController.isAccelerometerAvailable) {
            _uiState.update { it.copy(aiStatus = "ERROR: No accelerometer found") }
            return
        }

        _uiState.update {
            it.copy(
                isSensorActive = true,
                aiStatus = "Monitoring – collecting sensor data"
            )
        }

        // Start the foreground service to keep the process alive
        onServiceCommand?.invoke(ServiceCommand.StartService)

        // Launch the sensor → AI inference pipeline
        sensorJob = scope.launch {
            sensorController.startMonitoring(samplingPeriodUs = 20_000)
                .collect { reading ->
                    // Update UI with the latest reading
                    _uiState.update { it.copy(latestReading = reading) }

                    // Append to sliding window
                    sensorWindow.add(reading)

                    // When window is full, run inference and slide
                    if (sensorWindow.size >= detectionEngine.windowSize) {
                        val windowSnapshot = sensorWindow.toList()

                        // Run inference (potentially blocking; OK in this
                        // coroutine context since it's a fast TFLite call)
                        val event = detectionEngine.analyze(windowSnapshot)

                        if (event != null) {
                            // Disaster detected → transition to HITL screen
                            _uiState.update {
                                it.copy(
                                    screen = AppScreen.HITL_VALIDATION,
                                    currentEvent = event,
                                    aiStatus = "ALERT: ${event.type} detected " +
                                               "(${(event.confidence * 100).toInt()}%)"
                                )
                            }
                            // If the Activity is backgrounded, post a notification
                            if (!_isActivityVisible.value) {
                                onServiceCommand?.invoke(
                                    ServiceCommand.PostDisasterAlert(event)
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(aiStatus = "Monitoring – no anomalies")
                            }
                        }

                        // Slide window by half (50% overlap for continuity)
                        val slideAmount = detectionEngine.windowSize / 2
                        if (sensorWindow.size > slideAmount) {
                            sensorWindow.subList(0, slideAmount).clear()
                        }
                    }
                }
        }
    }

    private fun stopSensor() {
        sensorJob?.cancel()
        sensorJob = null
        sensorController.stopMonitoring()
        sensorWindow.clear()
        _uiState.update {
            it.copy(
                isSensorActive = false,
                latestReading = null,
                aiStatus = "Idle – sensor stopped"
            )
        }
        // Stop the foreground service if all monitoring is now inactive
        if (!_uiState.value.isMeshActive && !_uiState.value.isBarometerActive) {
            onServiceCommand?.invoke(ServiceCommand.StopService)
        }
    }

    // ── Mesh network ────────────────────────────────────────────────────────

    // ── Barometer pipeline ───────────────────────────────────────────────────

    /**
     * Toggles barometric pressure monitoring on/off.
     * When active, readings flow through the typhoon detection pipeline.
     */
    fun toggleBarometer() {
        if (_uiState.value.isBarometerActive) {
            stopBarometer()
        } else {
            startBarometer()
        }
    }

    private fun startBarometer() {
        val controller = barometerController ?: return
        val engine = typhoonEngine ?: return

        if (!controller.isBarometerAvailable) {
            _uiState.update { it.copy(aiStatus = "ERROR: No barometer found") }
            return
        }

        _uiState.update { it.copy(isBarometerActive = true) }

        // Start the foreground service if not already running
        if (!_uiState.value.isSensorActive && !_uiState.value.isMeshActive) {
            onServiceCommand?.invoke(ServiceCommand.StartService)
        }

        barometerJob = scope.launch {
            controller.startMonitoring(samplingPeriodUs = 200_000) // 5 Hz
                .collect { reading ->
                    _uiState.update { it.copy(latestPressureHpa = reading.pressureHpa) }

                    pressureWindow.add(reading)

                    // When window is full, run typhoon analysis and slide
                    if (pressureWindow.size >= engine.windowSize) {
                        val windowSnapshot = pressureWindow.toList()
                        val event = engine.analyze(windowSnapshot)

                        if (event != null) {
                            _uiState.update {
                                it.copy(
                                    screen = AppScreen.HITL_VALIDATION,
                                    currentEvent = event,
                                    aiStatus = "ALERT: ${event.type} detected " +
                                               "(${(event.confidence * 100).toInt()}%)"
                                )
                            }
                            if (!_isActivityVisible.value) {
                                onServiceCommand?.invoke(
                                    ServiceCommand.PostDisasterAlert(event)
                                )
                            }
                        }

                        // Slide window by half
                        val slideAmount = engine.windowSize / 2
                        if (pressureWindow.size > slideAmount) {
                            pressureWindow.subList(0, slideAmount).clear()
                        }
                    }
                }
        }
    }

    private fun stopBarometer() {
        barometerJob?.cancel()
        barometerJob = null
        barometerController?.stopMonitoring()
        pressureWindow.clear()
        _uiState.update {
            it.copy(isBarometerActive = false, latestPressureHpa = null)
        }
        // Stop service if nothing else is active
        if (!_uiState.value.isSensorActive && !_uiState.value.isMeshActive) {
            onServiceCommand?.invoke(ServiceCommand.StopService)
        }
    }

    // ── Mesh network (continued) ─────────────────────────────────────────────

    /**
     * Toggles the BLE mesh network on/off.
     */
    fun toggleMesh() {
        scope.launch {
            if (_uiState.value.isMeshActive) {
                stopMesh()
            } else {
                startMesh()
            }
        }
    }

    private suspend fun startMesh() {
        val alias = _uiState.value.localAlias
        meshController.startMesh(alias)

        _uiState.update { it.copy(isMeshActive = true) }

        // Register history sync callback — when a new peer joins, send them
        // all existing chat messages so they see the full conversation
        meshController.setOnPeerVerified { _ ->
            _uiState.value.chatMessages
        }

        // Start the foreground service to keep the process alive
        onServiceCommand?.invoke(ServiceCommand.StartService)

        // Start GPS location tracking when mesh is active
        locationController?.let { locCtrl ->
            locationJob = scope.launch {
                locCtrl.startTracking().collect { reading ->
                    _uiState.update {
                        it.copy(
                            currentLatitude = reading.latitude,
                            currentLongitude = reading.longitude
                        )
                    }
                }
            }
        }

        // Observe peer list changes
        scope.launch {
            meshController.peers.collect { peers ->
                _uiState.update {
                    it.copy(peers = peers, peerCount = peers.count { p ->
                        p.state == PeerConnectionState.CONNECTED
                    })
                }
            }
        }

        // Observe incoming messages
        meshListenerJob = scope.launch {
            meshController.incomingMessages.collect { message ->
                handleIncomingMessage(message)
            }
        }
    }

    private suspend fun stopMesh() {
        meshListenerJob?.cancel()
        meshListenerJob = null
        locationJob?.cancel()
        locationJob = null
        locationController?.stopTracking()
        meshController.setOnPeerVerified(null)
        meshController.stopMesh()
        _uiState.update { it.copy(isMeshActive = false, peerCount = 0) }
        // Stop the foreground service if all monitoring is now inactive
        if (!_uiState.value.isSensorActive && !_uiState.value.isBarometerActive) {
            onServiceCommand?.invoke(ServiceCommand.StopService)
        }
    }

    /**
     * Processes an inbound mesh message. SOS broadcasts trigger automatic
     * transition to Communication Mode so the user can coordinate a response.
     */
    private fun handleIncomingMessage(message: MeshMessage) {
        when (message.type) {
            MeshMessageType.SOS_BROADCAST -> {
                // Received an SOS from another device → unlock Comms Mode
                _uiState.update {
                    it.copy(
                        screen = AppScreen.COMMUNICATION_MODE,
                        chatMessages = it.chatMessages + message
                    )
                }
            }

            MeshMessageType.CHAT_TEXT, MeshMessageType.LOCATION_SHARE,
            MeshMessageType.IMAGE_SHARE -> {
                // Append chat message to the conversation
                _uiState.update {
                    it.copy(chatMessages = it.chatMessages + message)
                }
            }

            MeshMessageType.SOS_ACK, MeshMessageType.HEARTBEAT,
            MeshMessageType.HANDSHAKE_CHALLENGE, MeshMessageType.HANDSHAKE_RESPONSE -> {
                // Heartbeats, ACKs, and handshakes are handled at the controller level
            }
        }
    }

    // ── HITL response handlers ──────────────────────────────────────────────

    /**
     * User confirmed they are safe. Dismiss the alert and return to dashboard.
     */
    fun onUserSafe() {
        _uiState.update {
            it.copy(
                screen = AppScreen.DASHBOARD,
                currentEvent = null,
                aiStatus = "Monitoring – user confirmed safe"
            )
        }
    }

    /**
     * User needs help OR timer expired. Trigger SOS broadcast cascade.
     */
    fun onUserSos() {
        val event = _uiState.value.currentEvent ?: return
        val alias = _uiState.value.localAlias
        val state = _uiState.value

        scope.launch {
            // Build the SOS mesh message
            val sosMessage = MeshMessage(
                id = generateMessageId(),
                type = MeshMessageType.SOS_BROADCAST,
                senderId = alias,
                senderAlias = alias,
                payload = "SOS from $alias – ${event.type} detected " +
                          "with ${(event.confidence * 100).toInt()}% confidence. " +
                          "Immediate assistance needed.",
                timestampMs = currentTimeMs(),
                hopCount = 0,
                emergencyType = event.type.name,
                latitude = state.currentLatitude,
                longitude = state.currentLongitude
            )

            // Broadcast to all connected mesh peers
            meshController.broadcastSos(sosMessage)

            // Transition to Communication Mode
            _uiState.update {
                it.copy(
                    screen = AppScreen.COMMUNICATION_MODE,
                    chatMessages = it.chatMessages + sosMessage,
                    aiStatus = "SOS ACTIVE – broadcasting to mesh"
                )
            }
        }
    }

    /**
     * Manual SOS trigger from the dashboard → opens emergency type picker.
     */
    fun onManualSos() {
        _uiState.update { it.copy(screen = AppScreen.MANUAL_SOS) }
    }

    /**
     * User confirmed manual SOS with a selected emergency type.
     * Skips HITL countdown (user is actively requesting help) and broadcasts immediately.
     */
    fun onManualSosConfirm(type: DisasterType, description: String) {
        val alias = _uiState.value.localAlias
        val state = _uiState.value

        scope.launch {
            val event = DisasterEvent(
                type = type,
                confidence = 1.0f,
                detectedAtMs = currentTimeMs()
            )

            val sosMessage = MeshMessage(
                id = generateMessageId(),
                type = MeshMessageType.SOS_BROADCAST,
                senderId = alias,
                senderAlias = alias,
                payload = "MANUAL SOS from $alias – ${type.name}" +
                          if (description.isNotBlank()) ": $description" else "",
                timestampMs = currentTimeMs(),
                hopCount = 0,
                emergencyType = type.name,
                latitude = state.currentLatitude,
                longitude = state.currentLongitude
            )

            meshController.broadcastSos(sosMessage)

            _uiState.update {
                it.copy(
                    screen = AppScreen.COMMUNICATION_MODE,
                    currentEvent = event,
                    chatMessages = it.chatMessages + sosMessage,
                    aiStatus = "MANUAL SOS ACTIVE – ${type.name}"
                )
            }
        }
    }

    /**
     * User cancelled the manual SOS type picker → return to dashboard.
     */
    fun onManualSosCancel() {
        _uiState.update { it.copy(screen = AppScreen.DASHBOARD) }
    }

    // ── Chat messaging ──────────────────────────────────────────────────────

    /**
     * Sends a chat message to all connected mesh peers.
     */
    fun sendChatMessage(text: String) {
        val alias = _uiState.value.localAlias
        val msg = MeshMessage(
            id = generateMessageId(),
            type = MeshMessageType.CHAT_TEXT,
            senderId = alias,
            senderAlias = alias,
            payload = text,
            timestampMs = currentTimeMs(),
            hopCount = 0
        )

        // Add to local chat immediately (optimistic update)
        _uiState.update { it.copy(chatMessages = it.chatMessages + msg) }

        // Broadcast through mesh
        scope.launch {
            meshController.broadcastMessage(msg)
        }
    }

    /**
     * Sends the user's current GPS location as a dedicated location-share message.
     * Recipients can tap to open the coordinates in their phone's map app.
     */
    fun sendLocation() {
        val state = _uiState.value
        if (state.currentLatitude == 0.0 && state.currentLongitude == 0.0) return

        val alias = state.localAlias
        val msg = MeshMessage(
            id = generateMessageId(),
            type = MeshMessageType.LOCATION_SHARE,
            senderId = alias,
            senderAlias = alias,
            payload = "Shared location",
            timestampMs = currentTimeMs(),
            hopCount = 0,
            latitude = state.currentLatitude,
            longitude = state.currentLongitude
        )

        _uiState.update { it.copy(chatMessages = it.chatMessages + msg) }

        scope.launch {
            meshController.broadcastMessage(msg)
        }
    }

    /**
     * Sends a compressed image (base64-encoded JPEG) to all mesh peers.
     * The base64 string goes into MeshMessage.payload and is rendered
     * as an inline image in the chat.
     */
    fun sendImage(base64: String) {
        val alias = _uiState.value.localAlias
        val msg = MeshMessage(
            id = generateMessageId(),
            type = MeshMessageType.IMAGE_SHARE,
            senderId = alias,
            senderAlias = alias,
            payload = base64,
            timestampMs = currentTimeMs(),
            hopCount = 0
        )

        _uiState.update { it.copy(chatMessages = it.chatMessages + msg) }

        scope.launch {
            meshController.broadcastMessage(msg)
        }
    }

    // ── Navigation ──────────────────────────────────────────────────────────

    fun navigateTo(screen: AppScreen) {
        _uiState.update { it.copy(screen = screen) }
    }

    // ── Cleanup ─────────────────────────────────────────────────────────────

    fun onCleared() {
        stopSensor()
        stopBarometer()
        detectionEngine.release()
        typhoonEngine?.release()
        scope.cancel()
    }

    // ── Utilities ───────────────────────────────────────────────────────────

    private fun generateMessageId(): String {
        // Simple unique ID; in production use kotlin-uuid or platform UUID
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..16).map { chars.random() }.joinToString("")
    }

    private fun currentTimeMs(): Long {
        // Using kotlinx-datetime would be cleaner but this avoids the import
        // for a simple timestamp. Platform implementations can override.
        return kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
    }
}
