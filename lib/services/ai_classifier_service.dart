/// AiClassifierService — Edge AI Earthquake Inference Module
///
/// Wraps a quantized TFLite model that classifies a window of accelerometer
/// data into one of three categories:
///   0 = no seismic activity
///   1 = P-wave (primary / compressive)
///   2 = S-wave (secondary / shear)
///
/// The model file (earthquake_classifier.tflite) is expected in
/// assets/models/.  If absent the service gracefully degrades — the
/// STA/LTA heuristic in [SensorService] remains the primary detector.
///
/// In a production build you would train the model on labelled seismometer
/// data (e.g. from USGS ShakeAlert) and quantize to INT8 for sub-5 ms
/// inference on low-spec Android SoCs.
library;

import 'dart:typed_data';
import 'package:flutter/foundation.dart';
import 'package:tflite_flutter/tflite_flutter.dart';

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

/// Path to the quantized model asset.
const String _kModelAsset = 'assets/models/earthquake_classifier.tflite';

/// Number of samples in a single inference window (must match model input).
const int _kWindowSize = 150; // 3 seconds at 50 Hz

/// Number of axes per sample.
const int _kAxes = 3; // x, y, z

// ─────────────────────────────────────────────────────────────────────────────
// SeismicClass
// ─────────────────────────────────────────────────────────────────────────────

enum SeismicClass { none, pWave, sWave }

// ─────────────────────────────────────────────────────────────────────────────
// AiClassifierService
// ─────────────────────────────────────────────────────────────────────────────

class AiClassifierService extends ChangeNotifier {
  // ── Public state ─────────────────────────────────────────────────────────

  /// Whether the TFLite model loaded successfully.
  bool get isModelLoaded => _interpreter != null;

  /// Latest classification result.
  SeismicClass get lastResult => _lastResult;

  /// Confidence of the latest prediction (0.0 - 1.0).
  double get lastConfidence => _lastConfidence;

  // ── Private state ───────────────────────────────────────────────────────

  Interpreter? _interpreter;
  SeismicClass _lastResult = SeismicClass.none;
  double _lastConfidence = 0.0;

  // ── Lifecycle ───────────────────────────────────────────────────────────

  /// Attempt to load the TFLite model from assets.
  ///
  /// Call once at app start.  Safe to call multiple times — will no-op
  /// if already loaded.
  Future<void> loadModel() async {
    if (_interpreter != null) return;

    try {
      _interpreter = await Interpreter.fromAsset(_kModelAsset);
      debugPrint('[AiClassifier] Model loaded successfully.');
      notifyListeners();
    } catch (e) {
      // Model file missing or incompatible — this is expected during
      // early prototyping.  The STA/LTA heuristic still provides
      // earthquake detection without the model.
      debugPrint('[AiClassifier] Model load failed (expected if no .tflite '
          'asset is present): $e');
    }
  }

  @override
  void dispose() {
    _interpreter?.close();
    super.dispose();
  }

  // ── Inference ───────────────────────────────────────────────────────────

  /// Run inference on a window of accelerometer samples.
  ///
  /// [samples] must be a flat list of length [_kWindowSize * _kAxes],
  /// ordered as [x0, y0, z0, x1, y1, z1, ...].
  ///
  /// Returns the predicted [SeismicClass].  If the model is not loaded
  /// the method returns [SeismicClass.none] without error.
  SeismicClass classify(List<double> samples) {
    if (_interpreter == null) return SeismicClass.none;

    if (samples.length != _kWindowSize * _kAxes) {
      debugPrint('[AiClassifier] Invalid sample count: ${samples.length}, '
          'expected ${_kWindowSize * _kAxes}');
      return SeismicClass.none;
    }

    // Reshape into [1, _kWindowSize, _kAxes] for the model.
    final input = List.generate(
      1,
      (_) => List.generate(
        _kWindowSize,
        (i) => Float32List.fromList(
          samples.sublist(i * _kAxes, (i + 1) * _kAxes),
        ),
      ),
    );

    // Output shape: [1, 3] — softmax probabilities for [none, pWave, sWave].
    final output = List.generate(1, (_) => Float32List(_kAxes));

    _interpreter!.run(input, output);

    // Argmax over the 3 classes.
    final probs = output[0];
    int bestIdx = 0;
    double bestVal = probs[0];
    for (int i = 1; i < probs.length; i++) {
      if (probs[i] > bestVal) {
        bestVal = probs[i];
        bestIdx = i;
      }
    }

    _lastResult = SeismicClass.values[bestIdx];
    _lastConfidence = bestVal;
    notifyListeners();

    return _lastResult;
  }
}
