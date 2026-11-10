package de.roadcam.app

data class DashcamSettings(
    val clipMinutes: Int = 3,
    val quality: RecordQuality = RecordQuality.AUSGEWOGEN,
    val autoProtect: Boolean = false,
    val autoStartRecording: Boolean = false,
    val eventRecordingEnabled: Boolean = false,
    val deleteOldClips: Boolean = false,
    val retentionDays: Int = 5,
    val storageLimitGb: Int = 10,
    val overlapSeconds: Int = 0,
    val hevcEnabled: Boolean = true,
    val audioEnabled: Boolean = false,
    val gpsSpeedEnabled: Boolean = false,
    val gSensorEnabled: Boolean = true,
    val gSensorSensitivity: GSensorSensitivity = GSensorSensitivity.MITTEL_2,
    val keepScreenOn: Boolean = true,
    val darkTheme: Boolean = true
) {
    val segmentLengthMs: Long get() = clipMinutes * 60L * 1000L
    val storageLimitBytes: Long get() = storageLimitGb * 1024L * 1024L * 1024L
    val retentionMillis: Long get() = retentionDays * 24L * 60L * 60L * 1000L

    companion object {
        val CLIP_MINUTES = listOf(1, 3, 5)
        val RETENTION_DAYS = listOf(1, 3, 5, 7, 14, 30)
        val STORAGE_LIMIT_GB = listOf(2, 5, 10, 20, 32, 64)
        val OVERLAP_SECONDS = listOf(0, 1, 2)
        const val FREE_SPACE_RESERVE_BYTES = 1024L * 1024L * 1024L
        const val PHOTO_SPACE_RESERVE_BYTES = 20L * 1024L * 1024L
    }
}
