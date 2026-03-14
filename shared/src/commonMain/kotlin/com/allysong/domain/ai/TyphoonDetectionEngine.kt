package com.allysong.domain.ai

import com.allysong.domain.model.BarometerReading
import com.allysong.domain.model.DisasterEvent

// ============================================================================
// TyphoonDetectionEngine.kt – commonMain
// ============================================================================
// Platform-agnostic interface for typhoon/cyclone detection using barometric
// pressure trends.
//
// KMP Strategy: The interface is defined here; the Android implementation
// analyzes sliding windows of pressure readings to detect rapid drops
// characteristic of approaching typhoons/cyclones.
//
// Meteorological basis:
//   - Normal pressure at sea level: ~1013 hPa
//   - Typhoon approach: pressure drops >6 hPa/hour
//   - Typhoon eye: pressure drops to 900–950 hPa
// ============================================================================

/**
 * Abstraction over the typhoon/cyclone detection pipeline.
 */
interface TyphoonDetectionEngine {

    /**
     * Number of consecutive barometer readings the engine expects as input.
     */
    val windowSize: Int

    /**
     * Minimum pressure drop (hPa) within the window to trigger detection.
     */
    val pressureDropThresholdHpa: Float

    /**
     * Analyzes a window of barometric pressure readings for typhoon signatures.
     *
     * @param window A list of [BarometerReading] with at least [windowSize]
     *               elements, ordered chronologically.
     * @return A [DisasterEvent] of type TYPHOON if storm signatures are
     *         detected, or null if pressure trends are normal.
     */
    fun analyze(window: List<BarometerReading>): DisasterEvent?

    /**
     * Releases any resources held by this engine.
     */
    fun release()
}
