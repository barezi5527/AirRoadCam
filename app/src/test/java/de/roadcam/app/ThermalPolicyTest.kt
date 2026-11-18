package de.roadcam.app

import android.os.PowerManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalPolicyTest {
    @Test
    fun mapsThermalStatusToLevels() {
        assertEquals(0, ThermalPolicy.levelForStatus(PowerManager.THERMAL_STATUS_NONE))
        assertEquals(0, ThermalPolicy.levelForStatus(PowerManager.THERMAL_STATUS_LIGHT))
        assertEquals(1, ThermalPolicy.levelForStatus(PowerManager.THERMAL_STATUS_MODERATE))
        assertEquals(2, ThermalPolicy.levelForStatus(PowerManager.THERMAL_STATUS_SEVERE))
    }

    @Test
    fun reducesFpsAndBitrateOnlyWhenHot() {
        assertEquals(30, ThermalPolicy.fps(0))
        assertEquals(30, ThermalPolicy.fps(1))
        assertEquals(25, ThermalPolicy.fps(2))
        assertEquals(6_000_000, ThermalPolicy.bitrate(0, 6_000_000))
        assertEquals(4_500_000, ThermalPolicy.bitrate(1, 6_000_000))
        assertEquals(2_000_000, ThermalPolicy.bitrate(2, 6_000_000))
    }

    @Test
    fun disablesOverlapWhenWarm() {
        assertTrue(ThermalPolicy.overlapAllowed(0))
        assertFalse(ThermalPolicy.overlapAllowed(1))
        assertFalse(ThermalPolicy.overlapAllowed(2))
    }
}
