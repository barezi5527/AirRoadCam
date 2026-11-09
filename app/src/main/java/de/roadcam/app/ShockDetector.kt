package de.roadcam.app

import kotlin.math.sqrt

/**
 * Detects strong impacts from acceleration samples. Values are expected in m/s².
 * When [removeGravity] is true the gravity component is removed with a high-pass
 * filter, which is needed for raw accelerometer samples. Linear acceleration
 * sensors already exclude gravity.
 */
class ShockDetector(
    private val thresholdG: Float,
    private val removeGravity: Boolean = true,
    private val cooldownMs: Long = 3_000L,
    private val gravityFilterAlpha: Float = 0.95f,
    private val onSample: (Float) -> Unit = {},
    private val onShock: (Float) -> Unit
) {
    private val gravity = FloatArray(3)
    private var initialized = false
    private var lastShockMs: Long? = null

    fun onAcceleration(x: Float, y: Float, z: Float, timestampMs: Long) {
        val linear = if (removeGravity) {
            if (!initialized) {
                gravity[0] = x
                gravity[1] = y
                gravity[2] = z
                initialized = true
                return
            }
            gravity[0] = gravityFilterAlpha * gravity[0] + (1f - gravityFilterAlpha) * x
            gravity[1] = gravityFilterAlpha * gravity[1] + (1f - gravityFilterAlpha) * y
            gravity[2] = gravityFilterAlpha * gravity[2] + (1f - gravityFilterAlpha) * z
            floatArrayOf(x - gravity[0], y - gravity[1], z - gravity[2])
        } else {
            floatArrayOf(x, y, z)
        }

        val magnitude = sqrt(
            linear[0] * linear[0] + linear[1] * linear[1] + linear[2] * linear[2]
        )
        val forceG = magnitude / GRAVITY
        onSample(forceG)
        if (forceG < thresholdG) return

        val previous = lastShockMs
        if (previous != null && timestampMs - previous < cooldownMs) return
        lastShockMs = timestampMs
        onShock(forceG)
    }

    companion object {
        const val GRAVITY = 9.81f
    }
}
