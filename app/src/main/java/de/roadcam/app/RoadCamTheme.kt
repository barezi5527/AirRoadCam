package de.roadcam.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = Color(0xFF56E0B2),
    onPrimary = Color(0xFF002117),
    primaryContainer = Color(0xFF00513B),
    onPrimaryContainer = Color(0xFFC8F5E6),
    background = Color(0xFF0B1017),
    onBackground = Color(0xFFF4F7F9),
    surface = Color(0xFF151D27),
    onSurface = Color(0xFFF4F7F9),
    surfaceVariant = Color(0xFF202B37),
    onSurfaceVariant = Color(0xFF9AA7B5),
    error = Color(0xFFFF5B64),
    onError = Color(0xFF2B0004),
    errorContainer = Color(0xFF38232A),
    onErrorContainer = Color(0xFFFFB3B7),
    outline = Color(0xFF3A4857)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF087F5B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC8F5E6),
    onPrimaryContainer = Color(0xFF002117),
    background = Color(0xFFF3F7F9),
    onBackground = Color(0xFF0B1017),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0B1017),
    surfaceVariant = Color(0xFFE5ECF2),
    onSurfaceVariant = Color(0xFF5C6B7A),
    error = Color(0xFFC62828),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFFB6C2CE)
)

@Composable
fun RoadCamTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            view.context.findActivity()?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    MaterialTheme(colorScheme = colors, content = content)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
