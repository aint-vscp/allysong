/// HomeScreen — AllySong primary dashboard
///
/// Shows real-time sensor telemetry, mesh status, and provides controls
/// to start/stop monitoring and manually trigger the HITL flow for testing.
library;

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:permission_handler/permission_handler.dart';

import '../services/sensor_service.dart';
import '../services/mesh_network_service.dart';
import '../services/ai_classifier_service.dart';
import 'hitl_screen.dart';
import 'chat_screen.dart';

class HomeScreen extends StatefulWidget {
  static const String routeName = '/';

  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  bool _permissionsGranted = false;

  @override
  void initState() {
    super.initState();
    _requestPermissions();
    _initServices();
  }

  // ── Permissions ─────────────────────────────────────────────────────────

  /// Request the runtime permissions required by Nearby Connections and
  /// the accelerometer on Android 12+.
  Future<void> _requestPermissions() async {
    final statuses = await [
      Permission.bluetooth,
      Permission.bluetoothAdvertise,
      Permission.bluetoothConnect,
      Permission.bluetoothScan,
      Permission.nearbyWifiDevices,
      Permission.location,
    ].request();

    final allGranted = statuses.values.every(
      (s) => s.isGranted || s.isLimited,
    );

    setState(() {
      _permissionsGranted = allGranted;
    });
  }

  // ── Service init ────────────────────────────────────────────────────────

  Future<void> _initServices() async {
    // Try to load the TFLite model (gracefully no-ops if asset is missing).
    await context.read<AiClassifierService>().loadModel();

    if (!mounted) return;

    // Wire the sensor service's HITL callback to push the HITL screen.
    final sensor = context.read<SensorService>();
    sensor.onHitlTrigger = () {
      if (!mounted) return;
      Navigator.of(context).pushNamed(HITLScreen.routeName);
    };

    // Wire mesh service's SOS-received callback.
    final mesh = context.read<MeshNetworkService>();
    mesh.onSosReceived = () {
      if (!mounted) return;
      _showSosReceivedDialog();
    };
  }

  // ── SOS-received dialog ─────────────────────────────────────────────────

  void _showSosReceivedDialog() {
    final mesh = context.read<MeshNetworkService>();
    final latest = mesh.receivedSosAlerts.last;

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (_) => AlertDialog(
        backgroundColor: Colors.red.shade900,
        title: const Row(
          children: [
            Icon(Icons.sos, color: Colors.white, size: 32),
            SizedBox(width: 12),
            Text('SOS RECEIVED', style: TextStyle(color: Colors.white)),
          ],
        ),
        content: Text(
          '${latest.message}\n\nDevice: ${latest.deviceId.substring(0, 8)}...'
          '\nTime: ${DateTime.fromMillisecondsSinceEpoch(latest.timestampMs)}',
          style: const TextStyle(color: Colors.white70),
        ),
        actions: [
          TextButton(
            onPressed: () {
              Navigator.of(context).pop();
              Navigator.of(context).pushNamed(ChatScreen.routeName);
            },
            child: const Text(
              'OPEN COMMUNICATION MODE',
              style: TextStyle(color: Colors.yellowAccent),
            ),
          ),
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text(
              'DISMISS',
              style: TextStyle(color: Colors.white54),
            ),
          ),
        ],
      ),
    );
  }

  // ── Build ───────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    final sensor = context.watch<SensorService>();
    final mesh = context.watch<MeshNetworkService>();
    final ai = context.watch<AiClassifierService>();

    return Scaffold(
      appBar: AppBar(
        title: const Text('AllySong'),
        actions: [
          // Peer count badge.
          Padding(
            padding: const EdgeInsets.only(right: 16),
            child: Chip(
              avatar: Icon(
                Icons.bluetooth_connected,
                size: 18,
                color: mesh.isMeshActive ? Colors.greenAccent : Colors.grey,
              ),
              label: Text('${mesh.connectedPeers.length} peers'),
            ),
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // ── Status card ───────────────────────────────────────────
            _StatusCard(
              permissionsGranted: _permissionsGranted,
              isMonitoring: sensor.isMonitoring,
              isMeshActive: mesh.isMeshActive,
              isModelLoaded: ai.isModelLoaded,
            ),
            const SizedBox(height: 20),

            // ── Sensor telemetry ──────────────────────────────────────
            _TelemetryCard(
              magnitude: sensor.currentMagnitude,
              staLtaRatio: sensor.staLtaRatio,
              phase: sensor.phase,
            ),
            const SizedBox(height: 20),

            // ── Control buttons ───────────────────────────────────────
            Row(
              children: [
                Expanded(
                  child: ElevatedButton.icon(
                    onPressed: () {
                      if (sensor.isMonitoring) {
                        sensor.stopMonitoring();
                      } else {
                        sensor.startMonitoring();
                      }
                    },
                    icon: Icon(
                      sensor.isMonitoring ? Icons.stop : Icons.sensors,
                    ),
                    label: Text(
                      sensor.isMonitoring ? 'Stop Sensors' : 'Start Sensors',
                    ),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: sensor.isMonitoring
                          ? Colors.red.shade700
                          : Colors.teal.shade700,
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: ElevatedButton.icon(
                    onPressed: () {
                      if (mesh.isMeshActive) {
                        mesh.stopMesh();
                      } else {
                        mesh.startMesh();
                      }
                    },
                    icon: Icon(
                      mesh.isMeshActive
                          ? Icons.bluetooth_disabled
                          : Icons.bluetooth_searching,
                    ),
                    label: Text(
                      mesh.isMeshActive ? 'Stop Mesh' : 'Start Mesh',
                    ),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: mesh.isMeshActive
                          ? Colors.red.shade700
                          : Colors.blue.shade700,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),

            // ── Debug / demo trigger ──────────────────────────────────
            OutlinedButton.icon(
              onPressed: () => sensor.debugTriggerHitl(),
              icon: const Icon(Icons.bug_report, color: Colors.amber),
              label: const Text('DEBUG: Trigger HITL Alert'),
              style: OutlinedButton.styleFrom(
                side: const BorderSide(color: Colors.amber),
                foregroundColor: Colors.amber,
              ),
            ),
            const SizedBox(height: 12),

            // ── Open chat manually ────────────────────────────────────
            OutlinedButton.icon(
              onPressed: () {
                Navigator.of(context).pushNamed(ChatScreen.routeName);
              },
              icon: const Icon(Icons.chat, color: Colors.white54),
              label: const Text('Open Communication Mode'),
              style: OutlinedButton.styleFrom(
                foregroundColor: Colors.white54,
              ),
            ),

            // ── Received SOS alerts list ──────────────────────────────
            if (mesh.receivedSosAlerts.isNotEmpty) ...[
              const SizedBox(height: 24),
              Text(
                'Received SOS Alerts (${mesh.receivedSosAlerts.length})',
                style: Theme.of(context).textTheme.headlineLarge?.copyWith(
                      fontSize: 18,
                    ),
              ),
              const SizedBox(height: 8),
              ...mesh.receivedSosAlerts.reversed.take(10).map(
                    (sos) => Card(
                      color: Colors.red.shade900.withOpacity(0.5),
                      child: ListTile(
                        leading:
                            const Icon(Icons.sos, color: Colors.redAccent),
                        title: Text(
                          sos.message,
                          style: const TextStyle(color: Colors.white),
                        ),
                        subtitle: Text(
                          'Device: ${sos.deviceId.substring(0, 8)}...',
                          style: const TextStyle(color: Colors.white54),
                        ),
                      ),
                    ),
                  ),
            ],
          ],
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// _StatusCard
// ─────────────────────────────────────────────────────────────────────────────

class _StatusCard extends StatelessWidget {
  final bool permissionsGranted;
  final bool isMonitoring;
  final bool isMeshActive;
  final bool isModelLoaded;

  const _StatusCard({
    required this.permissionsGranted,
    required this.isMonitoring,
    required this.isMeshActive,
    required this.isModelLoaded,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      color: const Color(0xFF1E1E1E),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'System Status',
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.bold,
                color: Colors.white,
              ),
            ),
            const Divider(color: Colors.white24),
            _StatusRow('Permissions', permissionsGranted),
            _StatusRow('Sensor Monitoring', isMonitoring),
            _StatusRow('BLE Mesh Active', isMeshActive),
            _StatusRow('AI Model Loaded', isModelLoaded),
          ],
        ),
      ),
    );
  }
}

class _StatusRow extends StatelessWidget {
  final String label;
  final bool active;

  const _StatusRow(this.label, this.active);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Icon(
            active ? Icons.check_circle : Icons.cancel,
            color: active ? Colors.greenAccent : Colors.red.shade400,
            size: 20,
          ),
          const SizedBox(width: 10),
          Text(
            label,
            style: const TextStyle(color: Colors.white70, fontSize: 14),
          ),
        ],
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// _TelemetryCard
// ─────────────────────────────────────────────────────────────────────────────

class _TelemetryCard extends StatelessWidget {
  final double magnitude;
  final double staLtaRatio;
  final SeismicPhase phase;

  const _TelemetryCard({
    required this.magnitude,
    required this.staLtaRatio,
    required this.phase,
  });

  String get _phaseLabel {
    switch (phase) {
      case SeismicPhase.idle:
        return 'IDLE';
      case SeismicPhase.pWaveDetected:
        return 'P-WAVE DETECTED';
      case SeismicPhase.sWaveDetected:
        return 'S-WAVE DETECTED';
    }
  }

  Color get _phaseColor {
    switch (phase) {
      case SeismicPhase.idle:
        return Colors.greenAccent;
      case SeismicPhase.pWaveDetected:
        return Colors.amber;
      case SeismicPhase.sWaveDetected:
        return Colors.redAccent;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Card(
      color: const Color(0xFF1E1E1E),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Sensor Telemetry',
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.bold,
                color: Colors.white,
              ),
            ),
            const Divider(color: Colors.white24),
            Text(
              'Accel Magnitude: ${magnitude.toStringAsFixed(4)} m/s\u00B2',
              style: const TextStyle(color: Colors.white70, fontSize: 14),
            ),
            const SizedBox(height: 4),
            Text(
              'STA/LTA Ratio: ${staLtaRatio.toStringAsFixed(3)}',
              style: const TextStyle(color: Colors.white70, fontSize: 14),
            ),
            const SizedBox(height: 8),

            // Phase indicator chip.
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
              decoration: BoxDecoration(
                color: _phaseColor.withOpacity(0.2),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: _phaseColor),
              ),
              child: Text(
                _phaseLabel,
                style: TextStyle(
                  color: _phaseColor,
                  fontWeight: FontWeight.bold,
                  fontSize: 14,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
