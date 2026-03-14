package com.allysong.domain.ai

import com.allysong.domain.model.AccelerometerReading
import com.allysong.domain.model.DisasterEvent

// ============================================================================
// DisasterDetectionEngine.kt – commonMain
// ============================================================================
// Platform-agnostic interface for the Edge AI inference pipeline.
//
// KMP Strategy: The interface is defined here; the Android implementation
// wraps TensorFlow Lite to run a quantized TinyML model that classifies
// accelerometer windows as seismic P-waves, S-waves, or background noise.
//
// The engine operates on a sliding window of accelerometer readings.
// When the model's confidence exceeds a configurable threshold, a
// DisasterEvent is emitted.
// ============================================================================

/**
 * Abstraction over the on-device ML inference engine for seismic detection.
 */
interface DisasterDetectionEngine {

    /**
     * Number of consecutive accelerometer readings the model expects as
     * input. For example, a 2-second window at 50 Hz = 100 samples.
     */
    val windowSize: Int

    /**
     * Minimum model confidence to trigger a disaster detection.
     * Values in [0.0, 1.0]. Default: 0.70 (70%).
     */
    val confidenceThreshold: Float

    /**
     * Runs inference on a window of accelerometer readings.
     *
     * @param window A list of [AccelerometerReading] with exactly [windowSize]
     *               elements, ordered chronologically.
     * @return A [DisasterEvent] if the model detects a seismic signature above
     *         [confidenceThreshold], or null if the window is classified as
     *         background noise.
     * @throws IllegalArgumentException if [window] size != [windowSize].
     */
    fun analyze(window: List<AccelerometerReading>): DisasterEvent?

    /**
     * Releases the ML model and any allocated native buffers.
     * Must be called when the engine is no longer needed.
     */
    fun release()
}
