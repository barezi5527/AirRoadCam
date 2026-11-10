package de.roadcam.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DashClipTest {

    private fun clip(name: String) = DashClip(File(name), protected = false)

    @Test
    fun `jpg and jpeg files are photos`() {
        assertTrue(clip("AirRoadCam_Photo_1.jpg").isPhoto)
        assertTrue(clip("AirRoadCam_Photo_1.jpeg").isPhoto)
    }

    @Test
    fun `photo detection is case insensitive`() {
        assertTrue(clip("AirRoadCam_Photo_1.JPG").isPhoto)
        assertTrue(clip("AirRoadCam_Photo_1.JPEG").isPhoto)
    }

    @Test
    fun `videos are not photos`() {
        assertFalse(clip("RoadCam_1.mp4").isPhoto)
    }
}
