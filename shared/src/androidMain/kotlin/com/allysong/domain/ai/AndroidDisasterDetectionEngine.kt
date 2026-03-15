package com.allysong.domain.ai

import android.content.Context
import com.allysong.domain.model.AccelerometerReading
import com.allysong.domain.model.DisasterEvent
import com.allysong.domain.model.DisasterType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

// ============================================================================
// AndroidDisasterDetectionEngine.kt – androidMain
// ============================================================================
// Android implementation of the DisasterDetectionEngine using TensorFlow Lite.
//
// Multi-layered analysis inspired by MyShake (Kong et al., Science Advances,
// 2016) and standard USGS/IRIS seismological practices:
//
//   Layer 1: Rejection filters (fast, rules-based)
//      - Put-down: energy front-loads then goes silent
//      - Single-transient: door slams, drops, bumps (1 cluster < 5 samples)
//      - Periodicity: walking/running are periodic at 1.5-3.5 Hz,
//        earthquakes are broadband and aperiodic (autocorrelation check)
//
//   Layer 2: STA/LTA heuristic trigger (classic seismology)
//      - Demeaned characteristic function (removes gravity baseline)
//      - P-wave: sharp onset, high STA/LTA, high peak amplitude
//      - S-wave: sustained high variance, elevated STA/LTA
//      - Multi-axis correlation: earthquakes excite ≥2 axes simultaneously
//
//   Layer 3: TFLite CNN inference (4-class: bg, P, S, human)
//      - 1D CNN trained on synthetic data (11 human activity patterns)
//      - Rejects if human_activity class is dominant or > 25%
//
//   Layer 4: Consecutive-window confirmation
//      - Requires 3 consecutive positive windows (~1.5 seconds sustained)
//      - Eliminates any remaining isolated transient false positives
//
// Model Input:  [1, 50, 3] float tensor (1 second at 50 Hz, raw x/y/z)
// Model Output: [1, 4] float tensor (softmax: bg, P-wave, S-wave, human)
//
// FALLBACK: If no .tflite model file exists, the engine uses the STA/LTA
// heuristic with all rejection filters as a standalone detector.
// ============================================================================

/** Number of samples in the inference window (1 second at 50 Hz). */
private const val DEFAULT_WINDOW_SIZE = 50

/** Default minimum confidence to trigger a detection. */
private const val DEFAULT_CONFIDENCE_THRESHOLD = 0.80f

/** Expected model file name in the assets directory. */
private const val MODEL_FILENAME = "seismic_detector.tflite"

/**
 * TFLite-based seismic disaster detection engine for Android.
 *
 * @param context Application context for accessing assets.
 * @param modelOverride Optional override for the model filename.
 * @param threshold Confidence threshold override.
 */
class AndroidDisasterDetectionEngine(
    private val context: Context,
    modelOverride: String = MODEL_FILENAME,
    threshold: Float = DEFAULT_CONFIDENCE_THRESHOLD
) : DisasterDetectionEngine {

    override val windowSize: Int = DEFAULT_WINDOW_SIZE
    override val confidenceThreshold: Float = threshold

    // TFLite interpreter – nullable because the model may not exist yet
    private var interpreter: Interpreter? = null
    private var useFallbackHeuristic = false

    // Consecutive-window confirmation: require 2 consecutive positive windows
    // to trigger a detection. This eliminates single-window spikes from
    // phone drops, door slams, etc.
    private var consecutiveDetections = 0
    private val requiredConsecutive = 3

    init {
        try {
            val modelBuffer = loadModelFromAssets(modelOverride)
            interpreter = Interpreter(modelBuffer)
        } catch (e: Exception) {
            // Model file not found → fall back to heuristic detection.
            useFallbackHeuristic = true
        }
    }

    /**
     * Runs multi-layered analysis on the accelerometer window.
     *
     * @param window Exactly [windowSize] chronologically-ordered readings.
     * @return [DisasterEvent] if seismic signature detected, null otherwise.
     */
    override fun analyze(window: List<AccelerometerReading>): DisasterEvent? {
        require(window.size == windowSize) {
            "Expected window of $windowSize samples, got ${window.size}"
        }

        val candidate = if (useFallbackHeuristic) {
            analyzeWithHeuristic(window)
        } else {
            analyzeWithTFLite(window)
        }

        // Layer 4: consecutive-window confirmation
        return if (candidate != null) {
            consecutiveDetections++
            if (consecutiveDetections >= requiredConsecutive) {
                consecutiveDetections = 0
                candidate
            } else {
                null // Waiting for confirmation from next window
            }
        } else {
            consecutiveDetections = 0
            null
        }
    }

    // ── TFLite inference path ───────────────────────────────────────────────

    private fun analyzeWithTFLite(window: List<AccelerometerReading>): DisasterEvent? {
        val interp = interpreter ?: return null

        // Layer 1: rejection filters run BEFORE model inference (cheap, fast)
        if (isTransientImpulse(window)) return null
        if (isPeriodicHumanMotion(window)) return null
        if (isPutDown(window)) return null

        // Prepare input tensor: [1, windowSize, 3] float buffer
        val inputBuffer = ByteBuffer
            .allocateDirect(1 * windowSize * 3 * 4)
            .order(ByteOrder.nativeOrder())

        window.forEach { reading ->
            inputBuffer.putFloat(reading.x)
            inputBuffer.putFloat(reading.y)
            inputBuffer.putFloat(reading.z)
        }
        inputBuffer.rewind()

        // Prepare output tensor: [1, 4] float buffer
        // Classes: [background, p_wave, s_wave, human_activity]
        val outputBuffer = ByteBuffer
            .allocateDirect(1 * 4 * 4)
            .order(ByteOrder.nativeOrder())

        interp.run(inputBuffer, outputBuffer)
        outputBuffer.rewind()

        outputBuffer.float // skip background probability
        val pWaveProb = outputBuffer.float
        val sWaveProb = outputBuffer.float
        val humanActivityProb = outputBuffer.float

        // If human activity is the dominant class, reject entirely
        if (humanActivityProb > pWaveProb && humanActivityProb > sWaveProb) {
            return null
        }

        // Even if seismic class is highest, reject if human_activity
        // probability is substantial (model is uncertain between classes)
        if (humanActivityProb > 0.25f) {
            return null
        }

        return when {
            pWaveProb >= confidenceThreshold && sWaveProb >= confidenceThreshold -> {
                DisasterEvent(
                    type = DisasterType.EARTHQUAKE_COMBINED,
                    confidence = (pWaveProb + sWaveProb) / 2f,
                    detectedAtMs = System.currentTimeMillis(),
                    sensorWindow = window
                )
            }
            pWaveProb >= confidenceThreshold -> {
                DisasterEvent(
                    type = DisasterType.EARTHQUAKE_P_WAVE,
                    confidence = pWaveProb,
                    detectedAtMs = System.currentTimeMillis(),
                    sensorWindow = window
                )
            }
            sWaveProb >= confidenceThreshold -> {
                DisasterEvent(
                    type = DisasterType.EARTHQUAKE_S_WAVE,
                    confidence = sWaveProb,
                    detectedAtMs = System.currentTimeMillis(),
                    sensorWindow = window
                )
            }
            else -> null
        }
    }

    // ── Heuristic fallback ──────────────────────────────────────────────────
    // STA/LTA (Short-Term Average / Long-Term Average) algorithm — the
    // industry-standard seismological trigger used by USGS, IRIS, and other
    // seismic networks worldwide.
    //
    // Phone adaptation:
    //   - Raw accelerometer includes gravity (~9.8 m/s²), so we DEMEAN the
    //     signal first (subtract window mean from each sample).
    //   - The characteristic function is the SQUARED deviation from mean,
    //     which amplifies transients while suppressing steady-state noise.
    //   - LTA = average of characteristic function over the full window.
    //   - STA = average of characteristic function over the recent sub-window.
    //   - When STA/LTA exceeds the threshold, seismic activity is occurring.

    /** STA window = last 20% of samples. */
    private val staLength get() = (windowSize * 0.2).toInt().coerceAtLeast(3)

    /** STA/LTA ratio threshold — raised for phone accelerometer. */
    private val staLtaThreshold = 5.0f

    private fun analyzeWithHeuristic(window: List<AccelerometerReading>): DisasterEvent? {
        // Layer 1: rejection filters
        if (isTransientImpulse(window)) return null
        if (isPeriodicHumanMotion(window)) return null
        if (isPutDown(window)) return null

        // Layer 2: STA/LTA analysis
        val magnitudes = window.map { it.magnitude }
        val mean = magnitudes.average().toFloat()

        val cf = magnitudes.map { m ->
            val deviation = m - mean
            deviation * deviation
        }

        val lta = cf.average().toFloat()
        if (lta < 0.001f) return null // dead-flat signal

        val sta = cf.takeLast(staLength).average().toFloat()
        val staLtaRatio = sta / lta

        val maxDeviation = magnitudes.maxOf { kotlin.math.abs(it - mean) }
        val stdDev = magnitudes.standardDeviation()

        // P-wave signature: sharp onset spike with high STA/LTA ratio
        val spikeCount = magnitudes.count { it > mean + 3 * stdDev }
        val hasPWaveSignature = staLtaRatio > staLtaThreshold &&
                spikeCount >= 3 && maxDeviation > 8.0f

        // S-wave signature: sustained high variance (lateral shaking)
        val sustainedHighCount = magnitudes.count { it > mean + 2 * stdDev }
        val hasSWaveSignature = staLtaRatio > (staLtaThreshold * 0.7f) &&
                stdDev > 3.5f && sustainedHighCount > 12

        // Multi-axis correlation: earthquakes excite all axes simultaneously.
        // Human activities (walking, typing) often dominate a single axis.
        if (hasPWaveSignature || hasSWaveSignature) {
            val axisStdDevs = listOf(
                window.map { it.x }.standardDeviation(),
                window.map { it.y }.standardDeviation(),
                window.map { it.z }.standardDeviation()
            )
            val maxAxisStd = axisStdDevs.max()
            val activeAxes = axisStdDevs.count { it > maxAxisStd * 0.25f }
            if (activeAxes < 2) return null
        }

        // Sustained energy density filter: earthquakes sustain energy across
        // most of the window. Human activity energy comes in bursts/steps.
        // Reject if fewer than 50% of samples exceed mean + 1.5 * stdDev.
        if (hasPWaveSignature || hasSWaveSignature) {
            val energyThreshold = mean + 1.5f * stdDev
            val aboveCount = magnitudes.count { it > energyThreshold }
            val fraction = aboveCount.toFloat() / magnitudes.size
            if (fraction < 0.50f) return null
        }

        val baseConfidence = ((staLtaRatio / staLtaThreshold) * 0.75f)
            .coerceIn(0.80f, 0.95f)

        return when {
            hasPWaveSignature && hasSWaveSignature -> {
                DisasterEvent(
                    type = DisasterType.EARTHQUAKE_COMBINED,
                    confidence = (baseConfidence + 0.10f).coerceAtMost(0.95f),
                    detectedAtMs = System.currentTimeMillis(),
                    sensorWindow = window
                )
            }
            hasPWaveSignature -> {
                DisasterEvent(
                    type = DisasterType.EARTHQUAKE_P_WAVE,
                    confidence = baseConfidence,
                    detectedAtMs = System.currentTimeMillis(),
                    sensorWindow = window
                )
            }
            hasSWaveSignature -> {
                DisasterEvent(
                    type = DisasterType.EARTHQUAKE_S_WAVE,
                    confidence = baseConfidence,
                    detectedAtMs = System.currentTimeMillis(),
                    sensorWindow = window
                )
            }
            else -> null
        }
    }

    // ── Rejection Filters ───────────────────────────────────────────────────
    // These run BEFORE STA/LTA or TFLite to cheaply eliminate common
    // false positive sources. Each filter targets a specific pattern.

    /**
     * Put-down detection: tilt/rotation → single impact → dead quiet tail.
     *
     * The "front-loaded energy + silent tail" pattern is unique to placing
     * a phone on a surface. Earthquakes never go silent mid-shaking.
     */
    private fun isPutDown(window: List<AccelerometerReading>): Boolean {
        val magnitudes = window.map { it.magnitude }
        val mean = magnitudes.average().toFloat()
        val cf = magnitudes.map { m -> val d = m - mean; d * d }
        val maxDeviation = magnitudes.maxOf { kotlin.math.abs(it - mean) }

        if (maxDeviation < 3.0f) return false

        val tailLength = (windowSize * 0.3).toInt().coerceAtLeast(5)
        val tailEnergy = cf.takeLast(tailLength).average().toFloat()
        val frontEnergy = cf.dropLast(tailLength).average().toFloat()

        return frontEnergy > 0.01f && tailEnergy < frontEnergy * 0.1f
    }

    /**
     * Single-transient impulse detection: door slams, drops, bumps.
     *
     * These produce a single sharp spike that decays in < 5 samples (0.1 s).
     * Earthquakes produce sustained energy over many samples.
     *
     * Counts consecutive above-3σ sample clusters. A single short cluster
     * is a transient, not an earthquake.
     */
    private fun isTransientImpulse(window: List<AccelerometerReading>): Boolean {
        val magnitudes = window.map { it.magnitude }
        val mean = magnitudes.average().toFloat()
        val stdDev = magnitudes.standardDeviation()
        val maxDeviation = magnitudes.maxOf { kotlin.math.abs(it - mean) }

        if (maxDeviation < 3.0f || stdDev < 0.5f) return false

        val threshold = mean + 3 * stdDev
        val aboveThreshold = magnitudes.map { it > threshold }

        var clusterCount = 0
        var maxClusterLen = 0
        var currentLen = 0
        for (above in aboveThreshold) {
            if (above) {
                currentLen++
            } else {
                if (currentLen > 0) {
                    clusterCount++
                    maxClusterLen = maxOf(maxClusterLen, currentLen)
                }
                currentLen = 0
            }
        }
        if (currentLen > 0) {
            clusterCount++
            maxClusterLen = maxOf(maxClusterLen, currentLen)
        }

        // Single short cluster → transient impulse
        return clusterCount == 1 && maxClusterLen < 5
    }

    /**
     * Periodicity detection for walking, running, stairs, cycling.
     *
     * Human locomotion produces regular periodic signals at 1.5-3.5 Hz.
     * Earthquakes are aperiodic — each oscillation cycle varies in
     * amplitude, frequency, and duration.
     *
     * Uses autocorrelation of the demeaned magnitude signal at locomotion
     * lags. High autocorrelation (> 0.55) at these frequencies indicates
     * periodic human motion, not an earthquake.
     */
    private fun isPeriodicHumanMotion(window: List<AccelerometerReading>): Boolean {
        val magnitudes = window.map { it.magnitude }
        val mean = magnitudes.average().toFloat()
        val demeaned = magnitudes.map { it - mean }
        val stdDev = magnitudes.standardDeviation()

        if (stdDev < 0.5f) return false

        // At 50 Hz: lag 12 = ~4 Hz (fast running), lag 50 = 1 Hz (slow fidgeting/sway)
        val minLag = 12
        val maxLag = (windowSize * 0.7).toInt().coerceAtMost(50)

        var bestCorrelation = 0f

        for (lag in minLag..maxLag) {
            var sumProduct = 0f
            var sumSquare1 = 0f
            var sumSquare2 = 0f
            val overlapLen = windowSize - lag
            if (overlapLen < 10) continue

            for (i in 0 until overlapLen) {
                sumProduct += demeaned[i] * demeaned[i + lag]
                sumSquare1 += demeaned[i] * demeaned[i]
                sumSquare2 += demeaned[i + lag] * demeaned[i + lag]
            }

            val denom = kotlin.math.sqrt(sumSquare1 * sumSquare2)
            if (denom > 0.001f) {
                val correlation = sumProduct / denom
                bestCorrelation = maxOf(bestCorrelation, correlation)
            }
        }

        return bestCorrelation > 0.40f
    }

    // ── Cleanup ─────────────────────────────────────────────────────────────

    override fun release() {
        interpreter?.close()
        interpreter = null
    }

    // ── Utility: load model from APK assets ─────────────────────────────────

    private fun loadModelFromAssets(filename: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(filename)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    // ── Extension: standard deviation ───────────────────────────────────────

    private fun List<Float>.standardDeviation(): Float {
        val mean = this.average()
        val variance = this.map { (it - mean) * (it - mean) }.average()
        return kotlin.math.sqrt(variance).toFloat()
    }
}
