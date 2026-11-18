package de.roadcam.app

import kotlin.math.abs

enum class GSensorSensitivity(val thresholdG: Float, val level: Int) {
    NIEDRIG_1(4.0f, 1),
    NIEDRIG_2(3.0f, 2),
    MITTEL_1(2.5f, 1),
    MITTEL_2(2.0f, 2),
    HOCH_1(1.5f, 1),
    HOCH_2(1.0f, 2);

    companion object {
        private val legacyNames = mapOf(
            "NIEDRIG" to NIEDRIG_1,
            "MITTEL" to MITTEL_1,
            "HOCH" to HOCH_1
        )

        fun fromStoredName(name: String?): GSensorSensitivity? {
            if (name == null) return null
            entries.firstOrNull { it.name == name }?.let { return it }
            legacyNames[name]?.let { return it }
            val threshold = name.toFloatOrNull() ?: return null
            if (!threshold.isFinite() || threshold <= 0f) return null
            return entries.minByOrNull { abs(it.thresholdG - threshold) }
        }
    }
}
