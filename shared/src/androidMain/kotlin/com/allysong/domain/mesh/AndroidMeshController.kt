package com.allysong.domain.mesh

import android.content.Context
import android.util.Log
import com.allysong.domain.crypto.AesGcmPayloadEncryptor
import com.allysong.domain.crypto.PayloadEncryptor
import com.allysong.domain.model.*
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.flow.*
import android.os.Handler
import android.os.Looper
import java.security.SecureRandom

// ============================================================================
// AndroidMeshController.kt – androidMain
// ============================================================================
// Android implementation of the MeshNetworkController interface using
// Google's Nearby Connections API.
//
// Inspired by bitchat's BLE communication architecture:
//   - TTL-based message relay with hop counting (bitchat uses max 7 hops)
//   - Message deduplication via bounded LRU set (bitchat uses bloom filters)
//   - Auto-accept connections for emergency mesh formation
//   - Encrypted payloads (AES-256-GCM; bitchat uses Noise_XX_25519_ChaChaPoly)
//
// Nearby Connections provides:
//   - P2P_CLUSTER strategy: devices can simultaneously advertise and discover,
//     forming a decentralized mesh topology. No central coordinator needed.
//   - Automatic transport negotiation: BLE → Bluetooth Classic → Wi-Fi Direct,
//     selecting the highest bandwidth available.
//   - Works completely offline (no internet/cell towers required).
//
// Mesh Protocol:
//   1. startMesh() → startAdvertising() + startDiscovery() simultaneously.
//   2. On discovery, auto-request connection (no user confirmation for SOS).
//   3. On connection accepted, add to peers StateFlow.
//   4. Messages are serialized to JSON → encrypted → sent as BYTES payloads.
//   5. SOS messages are re-broadcast (cascade) with hopCount++ and max TTL.
// ============================================================================

private const val TAG = "AllySongMesh"

/** Maximum number of mesh hops for SOS re-broadcast to prevent storms. */
private const val MAX_HOP_COUNT = 5

/** Maximum number of remembered message IDs (bounded to prevent memory leak). */
private const val MAX_SEEN_MESSAGE_IDS = 2000

/** Service ID for Nearby Connections discovery (must match across devices). */
private const val SERVICE_ID = "com.allysong.mesh"

/** Handshake timeout in milliseconds (10 seconds). */
private const val HANDSHAKE_TIMEOUT_MS = 10_000L

/** XOR pattern for handshake response verification. */
private val HANDSHAKE_XOR_PATTERN = byteArrayOf(
    0x41.toByte(), 0x4C.toByte(), 0x4C.toByte(), 0x59.toByte(), // "ALLY"
    0x53.toByte(), 0x4F.toByte(), 0x4E.toByte(), 0x47.toByte()  // "SONG"
)

/**
 * Android BLE mesh controller using Nearby Connections.
 *
 * @param context Application context for Nearby Connections client binding.
 * @param encryptor Payload encryptor for securing mesh transmissions.
 */
class AndroidMeshController(
    private val context: Context,
    private val encryptor: PayloadEncryptor = AesGcmPayloadEncryptor()
) : MeshNetworkController {

    private val connectionsClient = Nearby.getConnectionsClient(context)

    // ── State flows ─────────────────────────────────────────────────────────
    private val _peers = MutableStateFlow<List<PeerDevice>>(emptyList())
    override val peers: StateFlow<List<PeerDevice>> = _peers.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<MeshMessage>(
        extraBufferCapacity = 64
    )
    override val incomingMessages: Flow<MeshMessage> = _incomingMessages

    private val _isActive = MutableStateFlow(false)
    override val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    // Track connected endpoint IDs for message routing
    private val connectedEndpoints = mutableSetOf<String>()
    private var localAlias = "Device"

    // Peer verification: only verified endpoints receive/send app messages
    private val verifiedEndpoints = mutableSetOf<String>()
    private val pendingHandshakes = mutableMapOf<String, String>() // endpointId -> expected nonce hex
    private val handler = Handler(Looper.getMainLooper())

    // Callback for history sync when a new peer joins
    private var onPeerVerifiedCallback: ((endpointId: String) -> List<MeshMessage>)? = null

    // Bounded LRU set of already-seen message IDs to prevent duplicate
    // processing while avoiding unbounded memory growth.
    // Modeled after bitchat's bloom-filter deduplication approach.
    private val seenMessageIds = LinkedHashSet<String>()

    // ── Connection lifecycle callback ───────────────────────────────────────
    // Handles the connection handshake when a remote device requests connection.
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {

        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Auto-accept all connections (appropriate for emergency scenarios;
            // in production, add authentication via shared secret or PKI).
            Log.d(TAG, "Connection initiated with $endpointId (${info.endpointName})")
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                // Connection established → start handshake verification
                Log.d(TAG, "Connected to $endpointId, initiating handshake")
                connectedEndpoints.add(endpointId)

                // Add as CONNECTING (not CONNECTED until handshake completes)
                val peer = PeerDevice(
                    endpointId = endpointId,
                    alias = "Peer-${endpointId.takeLast(4)}",
                    state = PeerConnectionState.CONNECTING,
                    lastSeenMs = System.currentTimeMillis()
                )
                _peers.update { current ->
                    if (current.any { it.endpointId == endpointId }) {
                        current.map {
                            if (it.endpointId == endpointId) peer else it
                        }
                    } else {
                        current + peer
                    }
                }

                // Send handshake challenge with random nonce
                sendHandshakeChallenge(endpointId)

                // Set timeout: disconnect peers that don't complete handshake
                handler.postDelayed({
                    if (endpointId !in verifiedEndpoints && endpointId in connectedEndpoints) {
                        Log.w(TAG, "Handshake timeout for $endpointId, disconnecting")
                        pendingHandshakes.remove(endpointId)
                        connectedEndpoints.remove(endpointId)
                        connectionsClient.disconnectFromEndpoint(endpointId)
                        _peers.update { current ->
                            current.filter { it.endpointId != endpointId }
                        }
                    }
                }, HANDSHAKE_TIMEOUT_MS)
            } else {
                Log.w(TAG, "Connection failed to $endpointId: ${result.status}")
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from $endpointId")
            connectedEndpoints.remove(endpointId)
            verifiedEndpoints.remove(endpointId)
            pendingHandshakes.remove(endpointId)
            _peers.update { current ->
                current.map {
                    if (it.endpointId == endpointId)
                        it.copy(state = PeerConnectionState.DISCONNECTED)
                    else it
                }
            }
        }
    }

    // ── Payload receive callback ────────────────────────────────────────────
    private val payloadCallback = object : PayloadCallback() {

        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val encryptedBytes = payload.asBytes() ?: return

            try {
                // Decrypt the payload
                val decryptedBytes = encryptor.decrypt(encryptedBytes)
                if (decryptedBytes == null) {
                    Log.w(TAG, "Payload decryption failed from $endpointId (tampered?)")
                    return
                }

                val jsonString = decryptedBytes.toString(Charsets.UTF_8)
                val message = deserializeMessage(jsonString)

                // Handle handshake messages before verification check
                when (message.type) {
                    MeshMessageType.HANDSHAKE_CHALLENGE -> {
                        handleHandshakeChallenge(endpointId, message)
                        return
                    }
                    MeshMessageType.HANDSHAKE_RESPONSE -> {
                        handleHandshakeResponse(endpointId, message)
                        return
                    }
                    else -> { /* continue to verification check */ }
                }

                // Drop all non-handshake messages from unverified endpoints
                if (endpointId !in verifiedEndpoints) {
                    Log.w(TAG, "Dropped ${message.type} from unverified $endpointId")
                    return
                }

                // Dedup: ignore messages we've already seen (prevents relay loops)
                if (!markSeen(message.id)) return

                Log.d(TAG, "Received ${message.type} from ${message.senderAlias} " +
                        "(hop ${message.hopCount})")

                // Update lastSeenMs and location for the sending peer
                _peers.update { current ->
                    current.map {
                        if (it.endpointId == endpointId)
                            it.copy(
                                lastSeenMs = System.currentTimeMillis(),
                                isSos = message.type == MeshMessageType.SOS_BROADCAST || it.isSos,
                                latitude = if (message.latitude != 0.0) message.latitude else it.latitude,
                                longitude = if (message.longitude != 0.0) message.longitude else it.longitude
                            )
                        else it
                    }
                }

                // Emit to observers
                _incomingMessages.tryEmit(message)

                // Relay cascade: re-broadcast to other verified peers if under TTL.
                // All user-visible messages are relayed so the entire mesh sees them,
                // forming a true flood-fill group chat over Bluetooth.
                val isRelayable = message.type in setOf(
                    MeshMessageType.SOS_BROADCAST,
                    MeshMessageType.CHAT_TEXT,
                    MeshMessageType.LOCATION_SHARE,
                    MeshMessageType.IMAGE_SHARE
                )

                if (isRelayable && message.hopCount < MAX_HOP_COUNT) {
                    val forwarded = message.copy(hopCount = message.hopCount + 1)
                    val forwardBytes = encryptPayload(serializeMessage(forwarded))

                    // Forward to all verified peers EXCEPT the sender
                    verifiedEndpoints
                        .filter { it != endpointId }
                        .forEach { peerId ->
                            connectionsClient.sendPayload(
                                peerId,
                                Payload.fromBytes(forwardBytes)
                            )
                        }

                    Log.d(TAG, "Relayed ${message.type} from ${message.senderAlias} " +
                            "to ${verifiedEndpoints.size - 1} verified peers " +
                            "(hop ${forwarded.hopCount})")
                }
            } catch (e: Exception) {
                // Malformed payload – log and drop to avoid crash cascades
                Log.e(TAG, "Failed to process payload from $endpointId", e)
            }
        }

        override fun onPayloadTransferUpdate(
            endpointId: String,
            update: PayloadTransferUpdate
        ) {
            // Transfer progress tracking; not critical for prototype
        }
    }

    // ── Discovery callback ──────────────────────────────────────────────────
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {

        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Discovered endpoint: $endpointId (${info.endpointName})")

            // Add as CONNECTING before connection attempt
            val peer = PeerDevice(
                endpointId = endpointId,
                alias = info.endpointName.ifBlank { "Peer-${endpointId.takeLast(4)}" },
                state = PeerConnectionState.CONNECTING,
                lastSeenMs = System.currentTimeMillis()
            )
            _peers.update { current ->
                // Avoid duplicate entries
                if (current.any { it.endpointId == endpointId }) current
                else current + peer
            }

            // Auto-request connection to discovered AllySong devices
            connectionsClient.requestConnection(
                localAlias,
                endpointId,
                connectionLifecycleCallback
            )
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Lost endpoint: $endpointId")
            connectedEndpoints.remove(endpointId)
            _peers.update { current ->
                current.filter { it.endpointId != endpointId }
            }
        }
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────

    override suspend fun startMesh(deviceAlias: String) {
        localAlias = deviceAlias
        Log.d(TAG, "Starting mesh as '$deviceAlias'")

        // Start advertising this device to nearby peers
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER) // Decentralized mesh topology
            .build()

        connectionsClient.startAdvertising(
            deviceAlias,
            SERVICE_ID,
            connectionLifecycleCallback,
            advertisingOptions
        )

        // Simultaneously start discovering nearby peers
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            discoveryOptions
        )

        _isActive.value = true
    }

    override suspend fun stopMesh() {
        Log.d(TAG, "Stopping mesh")
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        connectedEndpoints.clear()
        verifiedEndpoints.clear()
        pendingHandshakes.clear()
        seenMessageIds.clear()
        _peers.value = emptyList()
        _isActive.value = false
    }

    // ── Messaging ───────────────────────────────────────────────────────────

    override suspend fun broadcastSos(message: MeshMessage) {
        markSeen(message.id)
        val bytes = encryptPayload(serializeMessage(message))
        verifiedEndpoints.forEach { endpointId ->
            connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes))
        }
        Log.d(TAG, "Broadcast SOS to ${verifiedEndpoints.size} verified peers")
    }

    override suspend fun sendMessage(endpointId: String, message: MeshMessage) {
        if (endpointId !in verifiedEndpoints) {
            Log.w(TAG, "Cannot send to unverified endpoint $endpointId")
            return
        }
        markSeen(message.id)
        val bytes = encryptPayload(serializeMessage(message))
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes))
    }

    override suspend fun broadcastMessage(message: MeshMessage) {
        markSeen(message.id)
        val bytes = encryptPayload(serializeMessage(message))
        verifiedEndpoints.forEach { endpointId ->
            connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes))
        }
    }

    // ── Encryption helpers ──────────────────────────────────────────────────

    private fun encryptPayload(json: String): ByteArray {
        return encryptor.encrypt(json.toByteArray(Charsets.UTF_8))
    }

    // ── Message deduplication ───────────────────────────────────────────────
    // Returns true if the message is NEW (not seen before).
    // Returns false if it's a duplicate.
    // Bounded to MAX_SEEN_MESSAGE_IDS to prevent unbounded memory growth;
    // evicts oldest entries when full (LRU via LinkedHashSet iteration order).

    @Synchronized
    private fun markSeen(messageId: String): Boolean {
        if (messageId in seenMessageIds) return false

        // Evict oldest entries if at capacity
        if (seenMessageIds.size >= MAX_SEEN_MESSAGE_IDS) {
            val iterator = seenMessageIds.iterator()
            // Remove the oldest 10% to amortize eviction cost
            val removeCount = MAX_SEEN_MESSAGE_IDS / 10
            repeat(removeCount) {
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
        }

        seenMessageIds.add(messageId)
        return true
    }

    // ── Serialization helpers ───────────────────────────────────────────────
    // Safe JSON encode/decode with proper escaping for all string fields.
    // Uses manual construction to avoid requiring @Serializable annotation
    // on MeshMessage (which is defined in commonMain without the serialization
    // compiler plugin).

    private fun serializeMessage(message: MeshMessage): String {
        return buildString {
            append('{')
            append("\"id\":").append(jsonEscape(message.id)).append(',')
            append("\"type\":").append(jsonEscape(message.type.name)).append(',')
            append("\"senderId\":").append(jsonEscape(message.senderId)).append(',')
            append("\"senderAlias\":").append(jsonEscape(message.senderAlias)).append(',')
            append("\"payload\":").append(jsonEscape(message.payload)).append(',')
            append("\"timestampMs\":").append(message.timestampMs).append(',')
            append("\"hopCount\":").append(message.hopCount).append(',')
            append("\"emergencyType\":").append(jsonEscape(message.emergencyType)).append(',')
            append("\"latitude\":").append(message.latitude).append(',')
            append("\"longitude\":").append(message.longitude)
            append('}')
        }
    }

    private fun deserializeMessage(jsonString: String): MeshMessage {
        // Hand-rolled JSON parser that correctly handles escape sequences.
        // Avoids kotlinx.serialization plugin dependency in the shared module.
        val map = parseJsonObject(jsonString)

        return MeshMessage(
            id = map["id"] ?: error("Missing 'id' in mesh message"),
            type = MeshMessageType.valueOf(
                map["type"] ?: error("Missing 'type' in mesh message")
            ),
            senderId = map["senderId"] ?: error("Missing 'senderId'"),
            senderAlias = map["senderAlias"] ?: error("Missing 'senderAlias'"),
            payload = map["payload"] ?: "",
            timestampMs = (map["timestampMs"] ?: "0").toLong(),
            hopCount = (map["hopCount"] ?: "0").toInt(),
            emergencyType = map["emergencyType"] ?: "",
            latitude = (map["latitude"] ?: "0.0").toDouble(),
            longitude = (map["longitude"] ?: "0.0").toDouble()
        )
    }

    /**
     * Escapes a string value for safe JSON embedding.
     * Handles: backslash, double-quote, newline, carriage return, tab.
     */
    private fun jsonEscape(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    /**
     * Parses a flat JSON object string into a key-value map.
     * Handles string values (with escape sequences) and numeric values.
     * Does NOT handle nested objects or arrays (not needed for MeshMessage).
     */
    private fun parseJsonObject(json: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val trimmed = json.trim()
        if (!trimmed.startsWith('{') || !trimmed.endsWith('}')) return result

        val content = trimmed.substring(1, trimmed.length - 1)
        var i = 0

        while (i < content.length) {
            // Skip whitespace and commas
            while (i < content.length && content[i] in " ,\n\r\t") i++
            if (i >= content.length) break

            // Parse key (must be a quoted string)
            if (content[i] != '"') { i++; continue }
            val keyEnd = findClosingQuote(content, i)
            if (keyEnd == -1) break
            val key = unescapeJsonString(content.substring(i + 1, keyEnd))
            i = keyEnd + 1

            // Skip colon and whitespace
            while (i < content.length && content[i] in " :") i++

            // Parse value
            if (i >= content.length) break

            if (content[i] == '"') {
                // String value
                val valEnd = findClosingQuote(content, i)
                if (valEnd == -1) break
                val value = unescapeJsonString(content.substring(i + 1, valEnd))
                result[key] = value
                i = valEnd + 1
            } else {
                // Numeric or boolean value
                val start = i
                while (i < content.length && content[i] !in ",} \n") i++
                result[key] = content.substring(start, i).trim()
            }
        }

        return result
    }

    /** Finds the closing quote index, respecting backslash escapes. */
    private fun findClosingQuote(s: String, openIndex: Int): Int {
        var i = openIndex + 1
        while (i < s.length) {
            if (s[i] == '\\') { i += 2; continue }
            if (s[i] == '"') return i
            i++
        }
        return -1
    }

    /** Unescapes a JSON string value. */
    private fun unescapeJsonString(s: String): String {
        return s
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
    }

    // ── Handshake verification ──────────────────────────────────────────────
    // Challenge-response protocol to verify that peers are running AllySong.
    // 1. Both sides send HANDSHAKE_CHALLENGE with a random 16-byte nonce.
    // 2. Receiver XORs nonce with HANDSHAKE_XOR_PATTERN and replies HANDSHAKE_RESPONSE.
    // 3. Challenger verifies the response matches expected value.

    private fun sendHandshakeChallenge(endpointId: String) {
        val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val nonceHex = nonce.joinToString("") { "%02x".format(it) }

        // Compute expected response: XOR nonce with repeating pattern
        val expectedResponse = ByteArray(nonce.size) { i ->
            (nonce[i].toInt() xor HANDSHAKE_XOR_PATTERN[i % HANDSHAKE_XOR_PATTERN.size].toInt()).toByte()
        }
        val expectedHex = expectedResponse.joinToString("") { "%02x".format(it) }
        pendingHandshakes[endpointId] = expectedHex

        val challengeMsg = MeshMessage(
            id = "hs-${System.nanoTime()}",
            type = MeshMessageType.HANDSHAKE_CHALLENGE,
            senderId = localAlias,
            senderAlias = localAlias,
            payload = nonceHex,
            timestampMs = System.currentTimeMillis(),
            hopCount = 0
        )

        val bytes = encryptPayload(serializeMessage(challengeMsg))
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes))
        Log.d(TAG, "Sent handshake challenge to $endpointId")
    }

    private fun handleHandshakeChallenge(endpointId: String, message: MeshMessage) {
        // Received a challenge nonce — compute response by XORing with pattern
        val nonceHex = message.payload
        val nonce = nonceHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val response = ByteArray(nonce.size) { i ->
            (nonce[i].toInt() xor HANDSHAKE_XOR_PATTERN[i % HANDSHAKE_XOR_PATTERN.size].toInt()).toByte()
        }
        val responseHex = response.joinToString("") { "%02x".format(it) }

        val responseMsg = MeshMessage(
            id = "hs-resp-${System.nanoTime()}",
            type = MeshMessageType.HANDSHAKE_RESPONSE,
            senderId = localAlias,
            senderAlias = localAlias,
            payload = responseHex,
            timestampMs = System.currentTimeMillis(),
            hopCount = 0
        )

        val bytes = encryptPayload(serializeMessage(responseMsg))
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes))
        Log.d(TAG, "Sent handshake response to $endpointId")
    }

    private fun handleHandshakeResponse(endpointId: String, message: MeshMessage) {
        val expected = pendingHandshakes.remove(endpointId)
        if (expected == null) {
            Log.w(TAG, "Unexpected handshake response from $endpointId (no pending challenge)")
            return
        }

        if (message.payload == expected) {
            verifyEndpoint(endpointId, message.senderAlias)
        } else {
            Log.w(TAG, "Handshake verification FAILED for $endpointId — disconnecting")
            connectedEndpoints.remove(endpointId)
            connectionsClient.disconnectFromEndpoint(endpointId)
            _peers.update { current -> current.filter { it.endpointId != endpointId } }
        }
    }

    override fun setOnPeerVerified(callback: ((endpointId: String) -> List<MeshMessage>)?) {
        onPeerVerifiedCallback = callback
    }

    private fun verifyEndpoint(endpointId: String, alias: String) {
        verifiedEndpoints.add(endpointId)
        _peers.update { current ->
            current.map {
                if (it.endpointId == endpointId)
                    it.copy(
                        state = PeerConnectionState.CONNECTED,
                        alias = alias.ifBlank { it.alias }
                    )
                else it
            }
        }
        Log.d(TAG, "Peer $endpointId ($alias) verified and CONNECTED")

        // Send chat history to the newly verified peer so they see
        // all messages from before they joined the mesh
        onPeerVerifiedCallback?.let { getHistory ->
            val history = getHistory(endpointId)
            if (history.isNotEmpty()) {
                history.forEach { msg ->
                    val bytes = encryptPayload(serializeMessage(msg))
                    connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes))
                }
                Log.d(TAG, "Sent ${history.size} history messages to $endpointId")
            }
        }
    }
}
