/// Immutable data class for a single peer-to-peer chat message
/// transmitted over the Nearby Connections mesh.
class ChatMessage {
  /// Sender's device UUID.
  final String senderId;

  /// Human-readable sender alias (e.g. "Rescuer-A").
  final String senderAlias;

  /// Plain-text message body.
  final String body;

  /// Unix-epoch milliseconds.
  final int timestampMs;

  const ChatMessage({
    required this.senderId,
    required this.senderAlias,
    required this.body,
    required this.timestampMs,
  });

  Map<String, dynamic> toJson() => {
        'sid': senderId,
        'alias': senderAlias,
        'body': body,
        'ts': timestampMs,
      };

  factory ChatMessage.fromJson(Map<String, dynamic> json) => ChatMessage(
        senderId: json['sid'] as String,
        senderAlias: json['alias'] as String,
        body: json['body'] as String,
        timestampMs: json['ts'] as int,
      );
}
