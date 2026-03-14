package com.allysong.domain.mesh

import com.allysong.domain.model.MeshMessage
import com.allysong.domain.model.PeerDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

// ============================================================================
// MeshNetworkController.kt – commonMain
// ============================================================================
// Platform-agnostic interface defining the BLE mesh networking contract.
//
// KMP Strategy: This interface lives in commonMain. The Android implementation
// (AndroidMeshController) uses Google's Nearby Connections API to perform
// device discovery, connection negotiation, and payload transfer over
// BLE + Wi-Fi Direct.
//
// The mesh protocol supports:
//   1. SOS broadcast cascade (multi-hop emergency relay)
//   2. Peer-to-peer chat messaging in Communication Mode
//   3. Heartbeat-based mesh topology maintenance
// ============================================================================

/**
 * Abstraction over the platform's BLE / Nearby Connections mesh layer.
 *
 * All methods are safe to call from the main thread; implementations must
 * dispatch heavy I/O work onto appropriate coroutine dispatchers.
 */
interface MeshNetworkController {

    // ── Observable state ────────────────────────────────────────────────────

    /**
     * Live set of discovered and connected peer devices.
     * The UI observes this to render the mesh topology view.
     */
    val peers: StateFlow<List<PeerDevice>>

    /**
     * Stream of inbound messages from the mesh.
     * Includes SOS broadcasts, chat messages, and heartbeats.
     */
    val incomingMessages: Flow<MeshMessage>

    /**
     * True when the controller is actively advertising + discovering.
     */
    val isActive: StateFlow<Boolean>

    // ── Lifecycle ───────────────────────────────────────────────────────────

    /**
     * Starts BLE advertising and scanning simultaneously.
     * This makes the device both discoverable and able to discover others.
     *
     * @param deviceAlias Human-readable name advertised to nearby peers
     *                    (e.g., "Survivor-7A3F").
     */
    suspend fun startMesh(deviceAlias: String)

    /**
     * Stops all advertising, scanning, and disconnects all peers.
     * Releases all platform resources.
     */
    suspend fun stopMesh()

    // ── Messaging ───────────────────────────────────────────────────────────

    /**
     * Broadcasts an SOS payload to ALL connected peers.
     * Each peer re-broadcasts to its own connections (cascade), respecting
     * a maximum hop count to prevent broadcast storms.
     *
     * @param message The SOS [MeshMessage] to broadcast. Must have
     *                [MeshMessage.type] == [MeshMessageType.SOS_BROADCAST].
     */
    suspend fun broadcastSos(message: MeshMessage)

    /**
     * Sends a point-to-point chat message to a specific peer.
     *
     * @param endpointId The target peer's endpoint identifier.
     * @param message The chat [MeshMessage] to send.
     */
    suspend fun sendMessage(endpointId: String, message: MeshMessage)

    /**
     * Sends a message to ALL connected peers (used for chat broadcast
     * in Communication Mode when no specific target is selected).
     * Messages are also relayed by peers to form a full mesh flood.
     *
     * @param message The [MeshMessage] to broadcast.
     */
    suspend fun broadcastMessage(message: MeshMessage)

    // ── History sync ────────────────────────────────────────────────────────

    /**
     * Registers a callback invoked when a new peer completes handshake
     * verification. The callback receives the endpoint ID and should return
     * the list of chat messages to send as history sync.
     */
    fun setOnPeerVerified(callback: ((endpointId: String) -> List<MeshMessage>)?)
}
