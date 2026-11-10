package de.roadcam.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GSensorSensitivityTest {

    @Test
    fun `legacy names map to same threshold`() {
        assertEquals(2.5f, GSensorSensitivity.fromStoredName("NIEDRIG")?.thresholdG)
        assertEquals(1.5f, GSensorSensitivity.fromStoredName("MITTEL")?.thresholdG)
        assertEquals(1.0f, GSensorSensitivity.fromStoredName("HOCH")?.thresholdG)
    }

    @Test
    fun `new names resolve directly`() {
        assertEquals(GSensorSensitivity.NIEDRIG_1, GSensorSensitivity.fromStoredName("NIEDRIG_1"))
        assertEquals(GSensorSensitivity.MITTEL_2, GSensorSensitivity.fromStoredName("MITTEL_2"))
        assertEquals(GSensorSensitivity.HOCH_2, GSensorSensitivity.fromStoredName("HOCH_2"))
    }

    @Test
    fun `unknown names return null`() {
        assertNull(GSensorSensitivity.fromStoredName("GAR NICHT"))
        assertNull(GSensorSensitivity.fromStoredName(null))
    }

    @Test
    fun `levels are ordered from least to most sensitive`() {
        val thresholds = GSensorSensitivity.entries.map { it.thresholdG }
        assertEquals(thresholds.sortedDescending(), thresholds)
    }
}
