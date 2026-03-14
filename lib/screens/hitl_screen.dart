/// HITLScreen — Human-in-the-Loop Disaster Validation UI
///
/// Displayed as a full-screen, high-priority interrupt whenever the
/// [SensorService] detects seismic S-wave signatures.
///
/// Behaviour:
///   - Shows a 60-second countdown timer.
///   - "YES — I'M SAFE" → dismiss, reset sensor, return to home.
///   - "NO — I NEED HELP" → immediately broadcast SOS over mesh.
///   - Timer expires (user incapacitated) → auto-trigger SOS.
///
/// Design: maximum contrast, large tap targets, minimal cognitive load.
library;

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:vibration/vibration.dart';

import '../services/sensor_service.dart';
import '../services/mesh_network_service.dart';
import '../models/sos_payload.dart';
import 'chat_screen.dart';

class HITLScreen extends StatefulWidget {
  static const String routeName = '/hitl';

  const HITLScreen({super.key});

  @override
  State<HITLScreen> createState() => _HITLScreenState();
}

class _HITLScreenState extends State<HITLScreen>
    with SingleTickerProviderStateMixin {
  // ── Constants ───────────────────────────────────────────────────────────

  static const int _kCountdownSeconds = 60;

  // ── State ───────────────────────────────────────────────────────────────

  late int _secondsRemaining;
  Timer? _timer;
  bool _resolved = false; // true once the user tapped or timer expired

  // Animation controller for the pulsing warning effect.
  late AnimationController _pulseController;
  late Animation<double> _pulseAnimation;

  // ── Lifecycle ───────────────────────────────────────────────────────────

  @override
  void initState() {
    super.initState();

    _secondsRemaining = _kCountdownSeconds;

    // Start haptic pulse pattern to grab attention.
    _startHaptics();

    // Countdown timer — ticks once per second.
    _timer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (_resolved) return;

      setState(() {
        _secondsRemaining--;
      });

      if (_secondsRemaining <= 0) {
        // Timer expired — user is incapacitated.  Auto-trigger SOS.
        _onNeedHelp(auto: true);
      }
    });

    // Pulsing animation (opacity throb 0.6 ↔ 1.0).
    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 800),
    )..repeat(reverse: true);

    _pulseAnimation = Tween<double>(begin: 0.6, end: 1.0).animate(
      CurvedAnimation(parent: _pulseController, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _timer?.cancel();
    _pulseController.dispose();
    super.dispose();
  }

  // ── Actions ─────────────────────────────────────────────────────────────

  /// User confirms they are safe.
  void _onSafe() {
    if (_resolved) return;
    _resolved = true;
    _timer?.cancel();

    // Reset the sensor service so it can re-arm for future events.
    context.read<SensorService>().resetHitl();

    Navigator.of(context).pop(); // return to HomeScreen
  }

  /// User needs help — or the timer ran out (incapacitated).
  void _onNeedHelp({bool auto = false}) {
    if (_resolved) return;
    _resolved = true;
    _timer?.cancel();

    final mesh = context.read<MeshNetworkService>();

    // Build the SOS payload.
    final sos = SosPayload(
      deviceId: mesh.localDeviceId,
      latitude: 0.0, // GPS not implemented in prototype
      longitude: 0.0,
      timestampMs: DateTime.now().millisecondsSinceEpoch,
      message: auto
          ? 'AUTO-SOS: User did not respond within $_kCountdownSeconds seconds.'
          : 'SOS: User reported they need help.',
    );

    // Broadcast to every peer in the mesh.
    mesh.broadcastSos(sos);

    // Reset sensor for future events.
    context.read<SensorService>().resetHitl();

    // Navigate to the offline chat screen.
    Navigator.of(context).pushReplacementNamed(ChatScreen.routeName);
  }

  /// Trigger vibration bursts to alert the user.
  Future<void> _startHaptics() async {
    final bool? hasVibrator = await Vibration.hasVibrator();
    if (hasVibrator != true) return;

    // Three strong pulses.
    Vibration.vibrate(pattern: [0, 500, 200, 500, 200, 500]);
  }

  // ── Build ───────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    // Colour shifts from amber → red as time runs out.
    final double urgency = 1.0 - (_secondsRemaining / _kCountdownSeconds);
    final Color bgColor =
        Color.lerp(Colors.orange.shade900, Colors.red.shade900, urgency)!;

    return PopScope(
      // Prevent back-button dismissal — the user MUST respond.
      canPop: false,
      child: Scaffold(
        backgroundColor: bgColor,
        body: SafeArea(
          child: AnimatedBuilder(
            animation: _pulseAnimation,
            builder: (context, child) => Opacity(
              opacity: _pulseAnimation.value,
              child: child,
            ),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 32),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  // ── Warning icon ────────────────────────────────────
                  const Icon(
                    Icons.warning_amber_rounded,
                    size: 96,
                    color: Colors.white,
                  ),
                  const SizedBox(height: 24),

                  // ── Title ───────────────────────────────────────────
                  const Text(
                    'DISASTER SIGNATURES\nDETECTED',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontSize: 28,
                      fontWeight: FontWeight.w900,
                      color: Colors.white,
                      height: 1.2,
                    ),
                  ),
                  const SizedBox(height: 16),

                  // ── Subtitle ────────────────────────────────────────
                  const Text(
                    'Are you safe?',
                    style: TextStyle(
                      fontSize: 22,
                      color: Colors.white70,
                    ),
                  ),
                  const SizedBox(height: 32),

                  // ── Countdown timer ─────────────────────────────────
                  _CountdownRing(
                    secondsRemaining: _secondsRemaining,
                    totalSeconds: _kCountdownSeconds,
                  ),
                  const SizedBox(height: 12),

                  Text(
                    'SOS auto-sends in $_secondsRemaining s',
                    style: const TextStyle(
                      fontSize: 14,
                      color: Colors.white54,
                    ),
                  ),
                  const SizedBox(height: 40),

                  // ── YES — I'M SAFE button ───────────────────────────
                  SizedBox(
                    width: double.infinity,
                    height: 64,
                    child: ElevatedButton(
                      onPressed: _onSafe,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.green.shade700,
                        foregroundColor: Colors.white,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(16),
                        ),
                      ),
                      child: const Text(
                        'YES — I\'M SAFE',
                        style: TextStyle(
                          fontSize: 20,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),

                  // ── NO — I NEED HELP button ─────────────────────────
                  SizedBox(
                    width: double.infinity,
                    height: 64,
                    child: ElevatedButton(
                      onPressed: () => _onNeedHelp(),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.red.shade800,
                        foregroundColor: Colors.white,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(16),
                        ),
                      ),
                      child: const Text(
                        'NO — I NEED HELP',
                        style: TextStyle(
                          fontSize: 20,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// _CountdownRing — circular progress indicator for the timer
// ─────────────────────────────────────────────────────────────────────────────

class _CountdownRing extends StatelessWidget {
  final int secondsRemaining;
  final int totalSeconds;

  const _CountdownRing({
    required this.secondsRemaining,
    required this.totalSeconds,
  });

  @override
  Widget build(BuildContext context) {
    final double progress = secondsRemaining / totalSeconds;

    return SizedBox(
      width: 120,
      height: 120,
      child: Stack(
        fit: StackFit.expand,
        children: [
          CircularProgressIndicator(
            value: progress,
            strokeWidth: 8,
            backgroundColor: Colors.white24,
            valueColor: AlwaysStoppedAnimation<Color>(
              progress > 0.3 ? Colors.white : Colors.yellowAccent,
            ),
          ),
          Center(
            child: Text(
              '$secondsRemaining',
              style: const TextStyle(
                fontSize: 42,
                fontWeight: FontWeight.bold,
                color: Colors.white,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
