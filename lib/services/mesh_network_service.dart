/// MeshNetworkService — Decentralized BLE/Wi-Fi Direct Mesh Layer
///
/// Uses Android's Nearby Connections API (via the `nearby_connections` plugin)
/// to create an offline, peer-to-peer communication mesh.
///
/// The service handles three concerns:
///   1. **Discovery & Advertising** — continuously advertise this device and
///      discover peers within BLE/Wi-Fi Direct range (~50-100 m).
///   2. **SOS Broadcast** — when the HITL timer expires or the user presses
///      "NO - I NEED HELP", serialise an [SosPayload] and broadcast it to
///      every connected peer.  Peers automatically re-broadcast (cascade).
///   3. **Chat Relay** — transmit and receive plain-text [ChatMessage] packets
///      for the offline Communication Mode.
///
/// Packet framing:  every payload is prefixed with a single ASCII byte:
///   - 'S' -> SOS payload (JSON)
///   - 'C' -> Chat message (JSON)
///   - 'A' -> Acknowledgement
///
/// All traffic is local-only; no internet or cell towers required.
library;

import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:nearby_connections/nearby_connections.dart';
import 'package:uuid/uuid.dart';

import '../models/sos_payload.dart';
import '../models/chat_message.dart';

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

/// Unique service identifier — all AllySong devices use this to find each other.
const String _kServiceId = 'com.allysong.mesh';

/// Prefix bytes for packet framing.
const String _kSosPrefix = 'S';
const String _kChatPrefix = 'C';

// ─────────────────────────────────────────────────────────────────────────────
// MeshNetworkService
// ─────────────────────────────────────────────────────────────────────────────

class MeshNetworkService extends ChangeNotifier {
  // ── Public observable state ──────────────────────────────────────────────

  /// This device's persistent mesh identity.
  String get localDeviceId => _localDeviceId;

  /// Human-readable alias broadcast to peers.
  String get localAlias => _localAlias;
  set localAlias(String value) {
    _localAlias = value;
    notifyListeners();
  }

  /// Currently connected peer endpoint IDs -> display names.
  Map<String, String> get connectedPeers => Map.unmodifiable(_connectedPeers);

  /// Incoming SOS payloads from other devices.
  List<SosPayload> get receivedSosAlerts => List.unmodifiable(_receivedSos);

  /// Chat message log (own + received).
  List<ChatMessage> get chatLog => List.unmodifiable(_chatLog);

  /// Whether we are actively advertising + discovering.
  bool get isMeshActive => _isMeshActive;

  // ── Private state ───────────────────────────────────────────────────────

  final String _localDeviceId = const Uuid().v4();
  String _localAlias = 'AllySong-User';

  final Map<String, String> _connectedPeers = {};
  final List<SosPayload> _receivedSos = [];
  final List<ChatMessage> _chatLog = [];

  bool _isMeshActive = false;

  /// Nearby Connections plugin instance.
  final Nearby _nearby = Nearby();

  /// Optional callback when an SOS is received from a remote peer.
  VoidCallback? onSosReceived;

  // ── Lifecycle ───────────────────────────────────────────────────────────

  /// Start advertising this device AND discovering nearby peers.
  ///
  /// Uses [Strategy.P2P_CLUSTER] which supports many-to-many connections
  /// — ideal for a mesh where every node is both sender and receiver.
  Future<void> startMesh() async {
    if (_isMeshActive) return;

    // ── Advertise ──────────────────────────────────────────────────────
    try {
      await _nearby.startAdvertising(
        _localAlias,
        _kServiceId,
        onConnectionInitiated: _onConnectionInitiated,
        onConnectionResult: _onConnectionResult,
        onDisconnected: _onDisconnected,
        strategy: Strategy.P2P_CLUSTER,
      );
    } catch (e) {
      debugPrint('[MeshService] Advertising error: $e');
    }

    // ── Discover ───────────────────────────────────────────────────────
    try {
      await _nearby.startDiscovery(
        _localAlias,
        _kServiceId,
        onEndpointFound: _onEndpointFound,
        onEndpointLost: _onEndpointLost,
        strategy: Strategy.P2P_CLUSTER,
      );
    } catch (e) {
      debugPrint('[MeshService] Discovery error: $e');
    }

    _isMeshActive = true;
    notifyListeners();
  }

  /// Tear down advertising, discovery, and all peer connections.
  Future<void> stopMesh() async {
    await _nearby.stopAdvertising();
    await _nearby.stopDiscovery();
    await _nearby.stopAllEndpoints();
    _connectedPeers.clear();
    _isMeshActive = false;
    notifyListeners();
  }

  @override
  void dispose() {
    stopMesh();
    super.dispose();
  }

  // ── SOS Broadcast ───────────────────────────────────────────────────────

  /// Broadcast an SOS payload to **every** connected peer.
  /// Each peer that receives it will re-broadcast (cascade) — see
  /// [_handleIncomingPayload].
  Future<void> broadcastSos(SosPayload sos) async {
    final String frame = _kSosPrefix + jsonEncode(sos.toJson());
    final bytes = Uint8List.fromList(utf8.encode(frame));

    for (final endpointId in _connectedPeers.keys) {
      try {
        await _nearby.sendBytesPayload(endpointId, bytes);
      } catch (e) {
        debugPrint('[MeshService] Failed to send SOS to $endpointId: $e');
      }
    }
  }

  // ── Chat ────────────────────────────────────────────────────────────────

  /// Send a text chat message to all connected peers.
  Future<void> sendChatMessage(String body) async {
    final msg = ChatMessage(
      senderId: _localDeviceId,
      senderAlias: _localAlias,
      body: body,
      timestampMs: DateTime.now().millisecondsSinceEpoch,
    );

    // Add to local log first.
    _chatLog.add(msg);
    notifyListeners();

    final String frame = _kChatPrefix + jsonEncode(msg.toJson());
    final bytes = Uint8List.fromList(utf8.encode(frame));

    for (final endpointId in _connectedPeers.keys) {
      try {
        await _nearby.sendBytesPayload(endpointId, bytes);
      } catch (e) {
        debugPrint('[MeshService] Failed to send chat to $endpointId: $e');
      }
    }
  }

  // ── Nearby Connections Callbacks ────────────────────────────────────────

  /// Called when a nearby endpoint is discovered.
  void _onEndpointFound(String endpointId, String endpointName, String serviceId) {
    debugPrint('[MeshService] Found peer: $endpointName ($endpointId)');

    // Automatically request a connection — no manual pairing required.
    _nearby.requestConnection(
      _localAlias,
      endpointId,
      onConnectionInitiated: _onConnectionInitiated,
      onConnectionResult: _onConnectionResult,
      onDisconnected: _onDisconnected,
    );
  }

  /// Called when a discovered endpoint goes out of range.
  void _onEndpointLost(String? endpointId) {
    debugPrint('[MeshService] Lost endpoint: $endpointId');
  }

  /// Both sides receive this when a connection is being negotiated.
  /// We auto-accept every connection for the disaster-response use case.
  void _onConnectionInitiated(String endpointId, ConnectionInfo info) {
    debugPrint('[MeshService] Connection initiated with ${info.endpointName}');

    // Auto-accept — in a production build you would verify an auth token.
    _nearby.acceptConnection(
      endpointId,
      onPayLoadRecieved: (endpointId, payload) {
        _handleIncomingPayload(endpointId, payload);
      },
    );
  }

  /// Called after the connection handshake completes.
  void _onConnectionResult(String endpointId, Status status) {
    if (status == Status.CONNECTED) {
      debugPrint('[MeshService] Connected to $endpointId');
      _connectedPeers[endpointId] = endpointId; // name resolved later
      notifyListeners();
    } else {
      debugPrint('[MeshService] Connection failed to $endpointId: $status');
    }
  }

  /// Called when a peer disconnects.
  void _onDisconnected(String endpointId) {
    debugPrint('[MeshService] Disconnected: $endpointId');
    _connectedPeers.remove(endpointId);
    notifyListeners();
  }

  // ── Payload handling ────────────────────────────────────────────────────

  /// Decode incoming bytes, route by prefix, and trigger UI callbacks.
  void _handleIncomingPayload(String endpointId, Payload payload) {
    if (payload.type != PayloadType.BYTES || payload.bytes == null) return;

    final String raw = utf8.decode(payload.bytes!);
    if (raw.isEmpty) return;

    final String prefix = raw[0];
    final String jsonBody = raw.substring(1);

    switch (prefix) {
      case _kSosPrefix:
        _handleIncomingSos(jsonBody, endpointId);
        break;
      case _kChatPrefix:
        _handleIncomingChat(jsonBody);
        break;
      default:
        debugPrint('[MeshService] Unknown payload prefix: $prefix');
    }
  }

  /// Process a received SOS and cascade it to other peers.
  void _handleIncomingSos(String jsonBody, String sourceEndpoint) {
    try {
      final sos = SosPayload.fromJson(
        jsonDecode(jsonBody) as Map<String, dynamic>,
      );

      // De-duplicate: ignore if we've already seen this device+timestamp combo.
      final bool duplicate = _receivedSos.any(
        (existing) =>
            existing.deviceId == sos.deviceId &&
            existing.timestampMs == sos.timestampMs,
      );
      if (duplicate) return;

      _receivedSos.add(sos);
      notifyListeners();

      // Notify UI.
      onSosReceived?.call();

      // ── Cascade: re-broadcast to every peer EXCEPT the source ────────
      final String frame = _kSosPrefix + jsonBody;
      final bytes = Uint8List.fromList(utf8.encode(frame));

      for (final peerId in _connectedPeers.keys) {
        if (peerId == sourceEndpoint) continue; // don't echo back
        _nearby.sendBytesPayload(peerId, bytes).catchError((e) {
          debugPrint('[MeshService] Cascade send error: $e');
        });
      }
    } catch (e) {
      debugPrint('[MeshService] SOS parse error: $e');
    }
  }

  /// Process a received chat message and add to the log.
  void _handleIncomingChat(String jsonBody) {
    try {
      final msg = ChatMessage.fromJson(
        jsonDecode(jsonBody) as Map<String, dynamic>,
      );
      _chatLog.add(msg);
      notifyListeners();
    } catch (e) {
      debugPrint('[MeshService] Chat parse error: $e');
    }
  }
}
