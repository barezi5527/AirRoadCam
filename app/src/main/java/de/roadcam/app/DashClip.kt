package de.roadcam.app

import java.io.File

data class DashClip(
    val file: File,
    val protected: Boolean,
    val shockProtected: Boolean = false
) {
    val isPhoto: Boolean get() = file.extension.equals("jpg", ignoreCase = true) ||
        file.extension.equals("jpeg", ignoreCase = true)
}
