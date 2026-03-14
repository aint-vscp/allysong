/// ChatScreen — Offline Decentralized Communication Mode
///
/// Unlocked when the HITL SOS is triggered or when a remote SOS is received.
/// Provides a basic peer-to-peer text messaging UI that operates entirely
/// over the Nearby Connections mesh — no cell towers or internet required.
library;

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../services/mesh_network_service.dart';
import '../models/chat_message.dart';

class ChatScreen extends StatefulWidget {
  static const String routeName = '/chat';

  const ChatScreen({super.key});

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  final TextEditingController _msgController = TextEditingController();
  final ScrollController _scrollController = ScrollController();

  @override
  void dispose() {
    _msgController.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  /// Send the composed message over the mesh.
  void _send() {
    final String text = _msgController.text.trim();
    if (text.isEmpty) return;

    context.read<MeshNetworkService>().sendChatMessage(text);
    _msgController.clear();

    // Scroll to bottom after the new message is added.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollController.hasClients) {
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 200),
          curve: Curves.easeOut,
        );
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final mesh = context.watch<MeshNetworkService>();
    final messages = mesh.chatLog;
    final localId = mesh.localDeviceId;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Communication Mode'),
        actions: [
          Padding(
            padding: const EdgeInsets.only(right: 16),
            child: Chip(
              avatar: const Icon(Icons.people, size: 18),
              label: Text('${mesh.connectedPeers.length} peers'),
            ),
          ),
        ],
      ),
      body: Column(
        children: [
          // ── Mesh status banner ──────────────────────────────────────
          if (!mesh.isMeshActive)
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(12),
              color: Colors.orange.shade900,
              child: const Row(
                children: [
                  Icon(Icons.warning, color: Colors.white, size: 20),
                  SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'Mesh is offline. Go back and start the mesh to '
                      'connect with nearby devices.',
                      style: TextStyle(color: Colors.white, fontSize: 13),
                    ),
                  ),
                ],
              ),
            ),

          // ── Message list ────────────────────────────────────────────
          Expanded(
            child: messages.isEmpty
                ? const Center(
                    child: Text(
                      'No messages yet.\nConnect with nearby devices to chat.',
                      textAlign: TextAlign.center,
                      style: TextStyle(color: Colors.white38, fontSize: 16),
                    ),
                  )
                : ListView.builder(
                    controller: _scrollController,
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 8,
                    ),
                    itemCount: messages.length,
                    itemBuilder: (_, i) => _MessageBubble(
                      message: messages[i],
                      isLocal: messages[i].senderId == localId,
                    ),
                  ),
          ),

          // ── Input bar ───────────────────────────────────────────────
          Container(
            color: const Color(0xFF1E1E1E),
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            child: SafeArea(
              top: false,
              child: Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _msgController,
                      textInputAction: TextInputAction.send,
                      onSubmitted: (_) => _send(),
                      style: const TextStyle(color: Colors.white),
                      decoration: InputDecoration(
                        hintText: 'Type a message...',
                        hintStyle: const TextStyle(color: Colors.white38),
                        filled: true,
                        fillColor: const Color(0xFF2C2C2C),
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(24),
                          borderSide: BorderSide.none,
                        ),
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 10,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  FloatingActionButton.small(
                    onPressed: _send,
                    backgroundColor: Colors.teal,
                    child: const Icon(Icons.send, color: Colors.white),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// _MessageBubble
// ─────────────────────────────────────────────────────────────────────────────

class _MessageBubble extends StatelessWidget {
  final ChatMessage message;
  final bool isLocal;

  const _MessageBubble({
    required this.message,
    required this.isLocal,
  });

  @override
  Widget build(BuildContext context) {
    final time = DateTime.fromMillisecondsSinceEpoch(message.timestampMs);
    final timeStr =
        '${time.hour.toString().padLeft(2, '0')}:'
        '${time.minute.toString().padLeft(2, '0')}';

    return Align(
      alignment: isLocal ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        margin: const EdgeInsets.symmetric(vertical: 4),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        constraints: BoxConstraints(
          maxWidth: MediaQuery.of(context).size.width * 0.75,
        ),
        decoration: BoxDecoration(
          color: isLocal ? Colors.teal.shade800 : const Color(0xFF2C2C2C),
          borderRadius: BorderRadius.only(
            topLeft: const Radius.circular(16),
            topRight: const Radius.circular(16),
            bottomLeft: Radius.circular(isLocal ? 16 : 4),
            bottomRight: Radius.circular(isLocal ? 4 : 16),
          ),
        ),
        child: Column(
          crossAxisAlignment:
              isLocal ? CrossAxisAlignment.end : CrossAxisAlignment.start,
          children: [
            if (!isLocal)
              Padding(
                padding: const EdgeInsets.only(bottom: 2),
                child: Text(
                  message.senderAlias,
                  style: const TextStyle(
                    color: Colors.tealAccent,
                    fontSize: 12,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            Text(
              message.body,
              style: const TextStyle(color: Colors.white, fontSize: 15),
            ),
            const SizedBox(height: 4),
            Text(
              timeStr,
              style: const TextStyle(color: Colors.white38, fontSize: 11),
            ),
          ],
        ),
      ),
    );
  }
}
