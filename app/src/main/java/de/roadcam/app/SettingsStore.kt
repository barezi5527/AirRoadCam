package de.roadcam.app

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): DashcamSettings {
        val defaults = DashcamSettings()
        return DashcamSettings(
            clipMinutes = prefs.getInt("clipMinutes", defaults.clipMinutes)
                .takeIf { it in DashcamSettings.CLIP_MINUTES } ?: defaults.clipMinutes,
            quality = RecordQuality.entries.firstOrNull { it.name == prefs.getString("quality", null) }
                ?: defaults.quality,
            autoProtect = prefs.getBoolean("autoProtect", defaults.autoProtect),
            autoStartRecording = prefs.getBoolean("autoStartRecording", defaults.autoStartRecording),
            eventRecordingEnabled = prefs.getBoolean("eventRecordingEnabled", defaults.eventRecordingEnabled),
            deleteOldClips = prefs.getBoolean("deleteOldClips", defaults.deleteOldClips),
            retentionDays = prefs.getInt("retentionDays", defaults.retentionDays)
                .takeIf { it in DashcamSettings.RETENTION_DAYS } ?: defaults.retentionDays,
            storageLimitGb = prefs.getInt("storageLimitGb", defaults.storageLimitGb)
                .takeIf { it in DashcamSettings.STORAGE_LIMIT_GB } ?: defaults.storageLimitGb,
            overlapSeconds = prefs.getInt("overlapSeconds", defaults.overlapSeconds)
                .takeIf { it in DashcamSettings.OVERLAP_SECONDS } ?: defaults.overlapSeconds,
            hevcEnabled = prefs.getBoolean("hevcEnabled", defaults.hevcEnabled),
            audioEnabled = prefs.getBoolean("audioEnabled", defaults.audioEnabled),
            gpsSpeedEnabled = prefs.getBoolean("gpsSpeedEnabled", defaults.gpsSpeedEnabled),
            watermarkEnabled = prefs.getBoolean("watermarkEnabled", defaults.watermarkEnabled),
            gSensorEnabled = prefs.getBoolean("gSensorEnabled", defaults.gSensorEnabled),
            gSensorSensitivity = GSensorSensitivity.fromStoredName(prefs.getString("gSensorSensitivity", null))
                ?: defaults.gSensorSensitivity,
            keepScreenOn = prefs.getBoolean("keepScreenOn", defaults.keepScreenOn),
            darkTheme = prefs.getBoolean("darkTheme", defaults.darkTheme)
        )
    }

    fun save(settings: DashcamSettings) {
        prefs.edit()
            .putInt("clipMinutes", settings.clipMinutes)
            .putString("quality", settings.quality.name)
            .putBoolean("autoProtect", settings.autoProtect)
            .putBoolean("autoStartRecording", settings.autoStartRecording)
            .putBoolean("eventRecordingEnabled", settings.eventRecordingEnabled)
            .putBoolean("deleteOldClips", settings.deleteOldClips)
            .putInt("retentionDays", settings.retentionDays)
            .putInt("storageLimitGb", settings.storageLimitGb)
            .putInt("overlapSeconds", settings.overlapSeconds)
            .putBoolean("hevcEnabled", settings.hevcEnabled)
            .putBoolean("audioEnabled", settings.audioEnabled)
            .putBoolean("gpsSpeedEnabled", settings.gpsSpeedEnabled)
            .putBoolean("watermarkEnabled", settings.watermarkEnabled)
            .putBoolean("gSensorEnabled", settings.gSensorEnabled)
            .putString("gSensorSensitivity", settings.gSensorSensitivity.name)
            .putBoolean("keepScreenOn", settings.keepScreenOn)
            .putBoolean("darkTheme", settings.darkTheme)
            .apply()
    }
}
