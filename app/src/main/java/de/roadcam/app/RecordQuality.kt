package de.roadcam.app

import androidx.camera.video.Quality

enum class RecordQuality(
    val title: String,
    val badge: String,
    val quality: Quality,
    val bitrate: Int
) {
    SPAREND("Sparsam", "720p", Quality.HD, 4_000_000),
    AUSGEWOGEN("Ausgewogen", "1080p", Quality.FHD, 6_000_000),
    HOCH("Hoch", "1080p", Quality.FHD, 10_000_000)
}
