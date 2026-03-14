package com.allysong.domain.ai

import com.allysong.domain.model.BarometerReading
import com.allysong.domain.model.DisasterEvent
import com.allysong.domain.model.DisasterType

// ============================================================================
// HeuristicTyphoonDetectionEngine.kt – androidMain
// ============================================================================
// Heuristic implementation of the TyphoonDetectionEngine interface.
//
// Meteorological basis:
//   - Normal atmospheric pressure at sea level: ~1013.25 hPa
//   - Typical daily variation: ±1-2 hPa
//   - Approaching typhoon: pressure drops >6 hPa/hour
//   - Typhoon eye: pressure drops to 900–950 hPa
//   - Super typhoon (Philippines category): <920 hPa
//
// Algorithm:
//   1. Compute linear pressure trend across the window using least-squares.
//   2. Extrapolate the hourly rate of change.
//   3. If the rate exceeds the threshold, classify as TYPHOON.
//   4. Confidence is derived from how far the rate exceeds the threshold.
//
// This heuristic serves as a development-time fallback. In production,
// a TFLite model trained on PAGASA (Philippine Atmospheric, Geophysical
// and Astronomical Services Administration) typhoon data would replace it.
// ============================================================================

/**
 * Heuristic typhoon detection using barometric pressure trend analysis.
 *
 * @param windowSizeOverride Number of readings in the analysis window.
 *        Default: 30 samples at 5 Hz = 6 seconds of data.
 *        For meaningful trends, the ViewModel accumulates a larger window.
 * @param thresholdOverride Minimum pressure drop (hPa/hour) to trigger.
 *        Default: 6.0 hPa/hour (PAGASA tropical cyclone threshold).
 */
class HeuristicTyphoonDetectionEngine(
    windowSizeOverride: Int = DEFAULT_WINDOW_SIZE,
    thresholdOverride: Float = DEFAULT_PRESSURE_DROP_THRESHOLD
) : TyphoonDetectionEngine {

    override val windowSize: Int = windowSizeOverride
    override val pressureDropThresholdHpa: Float = thresholdOverride

    override fun analyze(window: List<BarometerReading>): DisasterEvent? {
        if (window.size < 2) return null

        // Compute time span of the window in hours
        val timeSpanMs = window.last().timestampMs - window.first().timestampMs
        if (timeSpanMs <= 0) return null
        val timeSpanHours = timeSpanMs / 3_600_000.0f

        // Compute pressure change using linear regression slope
        val pressureDelta = computeLinearTrend(window)

        // Rate of change in hPa/hour (negative = dropping)
        val ratePerHour = pressureDelta / timeSpanHours

        // Typhoon signature: rapid pressure DROP (negative rate, large magnitude)
        if (ratePerHour < -pressureDropThresholdHpa) {
            // Confidence scales from 0.70 at threshold to 0.95 at 2x threshold
            val exceedance = (-ratePerHour) / pressureDropThresholdHpa
            val confidence = (0.70f + 0.25f * (exceedance - 1.0f).coerceIn(0f, 1f))
                .coerceIn(0.70f, 0.95f)

            return DisasterEvent(
                type = DisasterType.TYPHOON,
                confidence = confidence,
                detectedAtMs = window.last().timestampMs
            )
        }

        return null
    }

    override fun release() {
        // No resources to release for the heuristic engine
    }

    /**
     * Computes the linear regression slope of pressure over the window.
     * Returns the total pressure change (hPa) predicted by the linear fit.
     *
     * Uses the standard least-squares formula:
     *   slope = (n*Σxy - Σx*Σy) / (n*Σx² - (Σx)²)
     *
     * Where x = time offset from first sample (ms), y = pressure (hPa).
     */
    private fun computeLinearTrend(window: List<BarometerReading>): Float {
        val n = window.size
        val t0 = window.first().timestampMs

        var sumX = 0.0
        var sumY = 0.0
        var sumXY = 0.0
        var sumX2 = 0.0

        for (reading in window) {
            val x = (reading.timestampMs - t0).toDouble()
            val y = reading.pressureHpa.toDouble()
            sumX += x
            sumY += y
            sumXY += x * y
            sumX2 += x * x
        }

        val denominator = n * sumX2 - sumX * sumX
        if (denominator == 0.0) return 0f

        val slope = (n * sumXY - sumX * sumY) / denominator

        // Total pressure change over the window timespan
        val timeSpan = (window.last().timestampMs - t0).toDouble()
        return (slope * timeSpan).toFloat()
    }

    companion object {
        /** 30 readings at 5 Hz = 6 seconds minimum window. */
        private const val DEFAULT_WINDOW_SIZE = 30

        /** PAGASA threshold: 6 hPa drop per hour signals tropical cyclone. */
        private const val DEFAULT_PRESSURE_DROP_THRESHOLD = 6.0f
    }
}
