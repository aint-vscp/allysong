/// SensorService — Offline Sensor Fusion Module
///
/// Continuously streams 3-axis accelerometer data from the device IMU
/// and applies a lightweight STA/LTA (Short-Term Average / Long-Term Average)
/// algorithm to detect the physical signatures of seismic P-waves and S-waves.
///
/// When the ratio exceeds configurable thresholds the service moves through
/// two phases:
///   1. P-wave detection  → "possible quake" (early warning)
///   2. S-wave detection  → "confirmed quake" → triggers HITL validation
///
/// This runs entirely on-device with zero network dependency.
library;

import 'dart:async';
import 'dart:collection';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:sensors_plus/sensors_plus.dart';

// ─────────────────────────────────────────────────────────────────────────────
// Tuning constants — adjust for real-world sensitivity calibration
// ─────────────────────────────────────────────────────────────────────────────

/// Length of the short-term averaging window (samples).
const int _kStaWindowSize = 30; // ~0.6 s at 50 Hz

/// Length of the long-term averaging window (samples).
const int _kLtaWindowSize = 300; // ~6 s at 50 Hz

/// STA/LTA ratio threshold for P-wave detection (lower energy, faster).
const double _kPWaveThreshold = 3.0;

/// STA/LTA ratio threshold for S-wave detection (higher energy, shearing).
const double _kSWaveThreshold = 5.0;

/// Minimum time (ms) between successive HITL triggers to avoid spam.
const int _kCooldownMs = 30000; // 30 seconds

/// Target accelerometer sampling interval.
const Duration _kSamplingInterval = Duration(milliseconds: 20); // 50 Hz

// ─────────────────────────────────────────────────────────────────────────────
// SeismicPhase enum
// ─────────────────────────────────────────────────────────────────────────────

enum SeismicPhase { idle, pWaveDetected, sWaveDetected }

// ─────────────────────────────────────────────────────────────────────────────
// SensorService
// ─────────────────────────────────────────────────────────────────────────────

class SensorService extends ChangeNotifier {
  // ── Public observable state ──────────────────────────────────────────────

  /// Current vector magnitude of the accelerometer reading.
  double get currentMagnitude => _currentMagnitude;

  /// Current STA/LTA ratio (useful for debug graphs).
  double get staLtaRatio => _staLtaRatio;

  /// Detected seismic phase.
  SeismicPhase get phase => _phase;

  /// Whether the sensor stream is actively running.
  bool get isMonitoring => _subscription != null;

  /// True once the HITL alert has been triggered and not yet resolved.
  bool get hitlTriggered => _hitlTriggered;

  // ── Private state ───────────────────────────────────────────────────────

  double _currentMagnitude = 0.0;
  double _staLtaRatio = 0.0;
  SeismicPhase _phase = SeismicPhase.idle;
  bool _hitlTriggered = false;

  StreamSubscription<AccelerometerEvent>? _subscription;

  /// Circular buffers for STA/LTA computation.
  final Queue<double> _staBuffer = Queue<double>();
  final Queue<double> _ltaBuffer = Queue<double>();

  /// Cooldown tracking.
  int _lastTriggerTimestamp = 0;

  /// Optional external callback invoked when HITL should be shown.
  /// Set by the HomeScreen so navigation can be triggered from the service.
  VoidCallback? onHitlTrigger;

  // ── Lifecycle ───────────────────────────────────────────────────────────

  /// Begin streaming accelerometer data and analysing for seismic events.
  void startMonitoring() {
    if (_subscription != null) return; // already running

    _subscription = accelerometerEventStream(
      samplingPeriod: _kSamplingInterval,
    ).listen(_onAccelerometerEvent);

    notifyListeners();
  }

  /// Stop the accelerometer stream and reset detection state.
  void stopMonitoring() {
    _subscription?.cancel();
    _subscription = null;
    _staBuffer.clear();
    _ltaBuffer.clear();
    _phase = SeismicPhase.idle;
    _hitlTriggered = false;
    notifyListeners();
  }

  /// Call after the HITL dialog is dismissed (user responded or timed out)
  /// so the system can re-arm for future events.
  void resetHitl() {
    _hitlTriggered = false;
    _phase = SeismicPhase.idle;
    notifyListeners();
  }

  @override
  void dispose() {
    stopMonitoring();
    super.dispose();
  }

  // ── Core processing pipeline ────────────────────────────────────────────

  /// Called for every raw accelerometer sample (~50 Hz).
  void _onAccelerometerEvent(AccelerometerEvent event) {
    // 1. Compute the vector magnitude (sqrt(x^2 + y^2 + z^2)).
    //    Subtract gravity (~9.81) so a resting phone reads ~0.
    final double rawMag = sqrt(
      event.x * event.x + event.y * event.y + event.z * event.z,
    );
    _currentMagnitude = (rawMag - 9.81).abs();

    // 2. Push into STA and LTA circular buffers.
    _staBuffer.addLast(_currentMagnitude);
    _ltaBuffer.addLast(_currentMagnitude);

    if (_staBuffer.length > _kStaWindowSize) _staBuffer.removeFirst();
    if (_ltaBuffer.length > _kLtaWindowSize) _ltaBuffer.removeFirst();

    // 3. Need enough samples before we can compute a meaningful ratio.
    if (_ltaBuffer.length < _kLtaWindowSize) return;

    // 4. Compute STA and LTA means.
    final double staMean =
        _staBuffer.fold<double>(0.0, (a, b) => a + b) / _staBuffer.length;
    final double ltaMean =
        _ltaBuffer.fold<double>(0.0, (a, b) => a + b) / _ltaBuffer.length;

    // Avoid division by zero when the device is perfectly still.
    _staLtaRatio = ltaMean > 0.001 ? staMean / ltaMean : 0.0;

    // 5. Phase-state machine.
    if (_staLtaRatio >= _kSWaveThreshold) {
      _phase = SeismicPhase.sWaveDetected;
      _tryTriggerHitl();
    } else if (_staLtaRatio >= _kPWaveThreshold) {
      _phase = SeismicPhase.pWaveDetected;
    } else {
      // Only reset to idle if we haven't already triggered.
      if (!_hitlTriggered) _phase = SeismicPhase.idle;
    }

    notifyListeners();
  }

  /// Fire the HITL prompt if cooldown has elapsed.
  void _tryTriggerHitl() {
    final int now = DateTime.now().millisecondsSinceEpoch;
    if (_hitlTriggered) return;
    if (now - _lastTriggerTimestamp < _kCooldownMs) return;

    _hitlTriggered = true;
    _lastTriggerTimestamp = now;

    // Invoke the navigation callback so the UI can push the HITL screen.
    onHitlTrigger?.call();

    notifyListeners();
  }

  // ── Debug helper ────────────────────────────────────────────────────────

  /// Force-trigger the HITL for development / demo purposes.
  void debugTriggerHitl() {
    _phase = SeismicPhase.sWaveDetected;
    _tryTriggerHitl();
  }
}
