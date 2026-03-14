package com.allysong.domain.model

// ============================================================================
// MeshMessage.kt
// ============================================================================
// Data classes representing messages transmitted over the BLE mesh network.
// These payloads are serialized, encrypted, and broadcast to nearby peers.
// ============================================================================

/**
 * The type of message flowing through the mesh network.
 */
enum class MeshMessageType {
    SOS_BROADCAST,       // Emergency: device owner needs help
    SOS_ACK,             // Acknowledgement that SOS was received
    CHAT_TEXT,           // Peer-to-peer text message in Communication Mode
    HEARTBEAT,           // Periodic keep-alive for mesh topology maintenance
    HANDSHAKE_CHALLENGE, // Peer verification: nonce challenge sent on connect
    HANDSHAKE_RESPONSE,  // Peer verification: response proving AllySong identity
    LOCATION_SHARE,      // User-initiated GPS location share (opens in map app)
    IMAGE_SHARE          // User-initiated image share (compressed JPEG over mesh)
}

/**
 * A single message in the BLE mesh network.
 *
 * @property id Unique message identifier (UUID string).
 * @property type The semantic type of this message.
 * @property senderId Identifier of the originating device.
 * @property senderAlias Human-readable alias (e.g., "Survivor-7A3F").
 * @property payload The message body. For SOS_BROADCAST this contains
 *                   serialized [DisasterEvent] data. For CHAT_TEXT, the
 *                   plaintext message content.
 * @property timestampMs Epoch timestamp when the message was created.
 * @property hopCount Number of mesh hops this message has traversed.
 *                    Used to implement TTL and prevent broadcast storms.
 */
data class MeshMessage(
    val id: String,
    val type: MeshMessageType,
    val senderId: String,
    val senderAlias: String,
    val payload: String,
    val timestampMs: Long,
    val hopCount: Int = 0,
    val emergencyType: String = "",  // DisasterType.name for SOS messages; empty for chat/heartbeat
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)
