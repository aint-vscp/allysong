package com.allysong.domain.model

// ============================================================================
// DisasterEvent.kt
// ============================================================================
// Models a detected disaster event, including its type, confidence score,
// and the sensor window that triggered the detection.
// ============================================================================

/**
 * Enumeration of disaster / emergency types.
 *
 * AI-detectable types (sensor-based):
 *   EARTHQUAKE_P_WAVE, EARTHQUAKE_S_WAVE, EARTHQUAKE_COMBINED, TYPHOON
 *
 * Manual SOS types (user-selected):
 *   FIRE, FLOOD, KIDNAPPING, MEDICAL_EMERGENCY, OTHER
 */
enum class DisasterType {
    // ── AI-detectable (sensor-based) ─────────────────────────────────────
    EARTHQUAKE_P_WAVE,      // Primary (compressional) wave – arrives first
    EARTHQUAKE_S_WAVE,      // Secondary (shear) wave – arrives after P-wave
    EARTHQUAKE_COMBINED,    // Both P-wave and S-wave signatures detected
    TYPHOON,                // Rapid barometric pressure drop (cyclone)

    // ── Manual SOS (user-selected) ───────────────────────────────────────
    FIRE,
    FLOOD,
    KIDNAPPING,
    MEDICAL_EMERGENCY,
    OTHER;

    /** True if this type can only be triggered by manual user selection, not by AI detection. */
    val isManualOnly: Boolean
        get() = this in setOf(FIRE, FLOOD, KIDNAPPING, MEDICAL_EMERGENCY, OTHER)
}

/**
 * Represents a disaster event detected by the on-device AI pipeline.
 *
 * @property type The classified disaster type.
 * @property confidence Model output confidence in [0.0, 1.0].
 * @property detectedAtMs Timestamp when the detection was finalized.
 * @property sensorWindow The raw sensor readings that triggered detection.
 *                        Retained for HITL review and mesh payload encoding.
 */
data class DisasterEvent(
    val type: DisasterType,
    val confidence: Float,
    val detectedAtMs: Long,
    val sensorWindow: List<AccelerometerReading> = emptyList()
)
