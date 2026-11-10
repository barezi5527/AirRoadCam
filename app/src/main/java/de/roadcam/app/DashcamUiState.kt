package de.roadcam.app

import androidx.camera.core.CameraSelector

data class DashcamUiState(
    val cameraReady: Boolean = false,
    val recording: Boolean = false,
    val currentProtected: Boolean = false,
    val status: UiText = UiText(R.string.status_preparing),
    val clips: List<DashClip> = emptyList(),
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val canSwitchCamera: Boolean = false,
    val hevcActive: Boolean = false,
    val snapshotAvailable: Boolean = false,
    val speedKmh: Float? = null,
    val settings: DashcamSettings = DashcamSettings()
)
