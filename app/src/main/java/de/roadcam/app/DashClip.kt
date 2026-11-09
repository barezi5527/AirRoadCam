package de.roadcam.app

import java.io.File

data class DashClip(
    val file: File,
    val protected: Boolean,
    val shockProtected: Boolean = false
)
