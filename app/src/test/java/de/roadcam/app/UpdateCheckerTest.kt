package de.roadcam.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    private val releaseJson = """
        {
          "tag_name": "v1.1-release",
          "name": "Air-RoadCam 1.1 – Release",
          "body": "Neue Version",
          "assets": [
            {
              "name": "AirRoadCam-1.1.apk",
              "browser_download_url": "https://github.com/barezi5527/AirRoadCam/releases/download/v1.1-release/AirRoadCam-1.1.apk"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun parsesNewerReleaseWithApkAsset() {
        val release = UpdateChecker.parseRelease(releaseJson, "1.0")

        assertEquals("1.1", release?.version)
        assertEquals(
            "https://github.com/barezi5527/AirRoadCam/releases/download/v1.1-release/AirRoadCam-1.1.apk",
            release?.apkUrl
        )
    }

    @Test
    fun ignoresCurrentOrOlderRelease() {
        assertNull(UpdateChecker.parseRelease(releaseJson, "1.1"))
        assertNull(UpdateChecker.parseRelease(releaseJson, "1.2"))
    }

    @Test
    fun comparesVersionSegmentsNumerically() {
        assertTrue(UpdateChecker.isNewer("1.0", "v1.1-release"))
        assertTrue(UpdateChecker.isNewer("1.1", "v1.1.1-release"))
        assertTrue(UpdateChecker.isNewer("1.9", "1.10"))
        assertFalse(UpdateChecker.isNewer("1.1", "v1.1-release"))
        assertFalse(UpdateChecker.isNewer("1.2", "v1.1-release"))
    }
}
