/// Immutable data class representing an SOS beacon payload.
///
/// This is the structured packet broadcast over the BLE mesh when
/// a user confirms "NO - I NEED HELP" or the HITL timer expires.
class SosPayload {
  /// Unique device identifier (UUID v4, generated on first launch).
  final String deviceId;

  /// GPS latitude at time of trigger (0.0 if unavailable offline).
  final double latitude;

  /// GPS longitude at time of trigger (0.0 if unavailable offline).
  final double longitude;

  /// Unix-epoch milliseconds when the SOS was triggered.
  final int timestampMs;

  /// Human-readable message attached to the SOS.
  final String message;

  /// Severity level: 0 = unknown, 1 = minor, 2 = moderate, 3 = critical.
  final int severity;

  const SosPayload({
    required this.deviceId,
    required this.latitude,
    required this.longitude,
    required this.timestampMs,
    required this.message,
    this.severity = 3,
  });

  /// Serialise to a Map for JSON encoding before BLE transmission.
  Map<String, dynamic> toJson() => {
        'deviceId': deviceId,
        'lat': latitude,
        'lng': longitude,
        'ts': timestampMs,
        'msg': message,
        'sev': severity,
      };

  /// Deserialise from a received JSON map.
  factory SosPayload.fromJson(Map<String, dynamic> json) => SosPayload(
        deviceId: json['deviceId'] as String,
        latitude: (json['lat'] as num).toDouble(),
        longitude: (json['lng'] as num).toDouble(),
        timestampMs: json['ts'] as int,
        message: json['msg'] as String,
        severity: json['sev'] as int? ?? 3,
      );
}
