package de.roadcam.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShockDetectorTest {

    @Test
    fun `impact above threshold triggers once`() {
        val forces = mutableListOf<Float>()
        val detector = ShockDetector(
            thresholdG = 1.5f,
            removeGravity = false,
            onShock = { forces += it }
        )

        detector.onAcceleration(0f, 0f, 0f, 0L)
        detector.onAcceleration(20f, 0f, 0f, 100L)

        assertEquals(1, forces.size)
        assertTrue(forces.first() > 1.5f)
    }

    @Test
    fun `movement below threshold is ignored`() {
        val forces = mutableListOf<Float>()
        val detector = ShockDetector(
            thresholdG = 1.5f,
            removeGravity = false,
            onShock = { forces += it }
        )

        detector.onAcceleration(5f, 0f, 0f, 0L)
        detector.onAcceleration(10f, 0f, 0f, 100L)

        assertTrue(forces.isEmpty())
    }

    @Test
    fun `repeated impact is suppressed during cooldown`() {
        val forces = mutableListOf<Float>()
        val detector = ShockDetector(
            thresholdG = 1.5f,
            removeGravity = false,
            cooldownMs = 3_000L,
            onShock = { forces += it }
        )

        detector.onAcceleration(20f, 0f, 0f, 0L)
        detector.onAcceleration(20f, 0f, 0f, 500L)
        detector.onAcceleration(20f, 0f, 0f, 3_500L)

        assertEquals(2, forces.size)
    }

    @Test
    fun `steady gravity does not trigger when gravity is removed`() {
        val forces = mutableListOf<Float>()
        val detector = ShockDetector(
            thresholdG = 1.5f,
            removeGravity = true,
            onShock = { forces += it }
        )

        repeat(20) { index ->
            detector.onAcceleration(0f, 0f, ShockDetector.GRAVITY, index * 20L)
        }

        assertTrue(forces.isEmpty())
    }

    @Test
    fun `impact triggers when gravity is removed`() {
        val forces = mutableListOf<Float>()
        val detector = ShockDetector(
            thresholdG = 1.5f,
            removeGravity = true,
            onShock = { forces += it }
        )

        repeat(20) { index ->
            detector.onAcceleration(0f, 0f, ShockDetector.GRAVITY, index * 20L)
        }
        detector.onAcceleration(25f, 0f, ShockDetector.GRAVITY, 500L)

        assertEquals(1, forces.size)
    }
}
