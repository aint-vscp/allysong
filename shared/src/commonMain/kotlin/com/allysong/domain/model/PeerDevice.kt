package com.allysong.domain.model

// ============================================================================
// PeerDevice.kt
// ============================================================================
// Represents a discovered peer device in the BLE mesh network.
// ============================================================================

/**
 * Connection state of a peer device in the mesh.
 */
enum class PeerConnectionState {
    DISCOVERED,  // Device found via BLE scan / Nearby discovery
    CONNECTING,  // Handshake in progress
    CONNECTED,   // Active bidirectional link
    DISCONNECTED // Previously connected, now unreachable
}

/**
 * A peer device participating in the AllySong mesh network.
 *
 * @property endpointId The platform-level endpoint identifier
 *                      (Nearby Connections endpoint ID or BLE address).
 * @property alias Human-readable device alias shown in the UI.
 * @property state Current connection state.
 * @property lastSeenMs Timestamp of the last received message or heartbeat.
 * @property isSos True if this peer has an active SOS broadcast.
 */
data class PeerDevice(
    val endpointId: String,
    val alias: String,
    val state: PeerConnectionState,
    val lastSeenMs: Long,
    val isSos: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)
