package de.roadcam.app

enum class GSensorSensitivity(val thresholdG: Float, val level: Int) {
    NIEDRIG_1(4.0f, 1),
    NIEDRIG_2(3.0f, 2),
    MITTEL_1(2.5f, 1),
    MITTEL_2(2.0f, 2),
    HOCH_1(1.5f, 1),
    HOCH_2(1.0f, 2);

    companion object {
        private val legacyNames = mapOf(
            "NIEDRIG" to MITTEL_1,
            "MITTEL" to HOCH_1,
            "HOCH" to HOCH_2
        )

        fun fromStoredName(name: String?): GSensorSensitivity? =
            entries.firstOrNull { it.name == name } ?: name?.let(legacyNames::get)
    }
}
