package com.beatsync.cut.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ObsidianBg = Color(0xFF0A0A0D)
val SurfaceDark = Color(0xFF13141A)
val SurfaceElevated = Color(0xFF1B1D26)
val PantoneBlue = Color(0xFF0057B8)
val PantoneBlueLight = Color(0xFF2B7DE9)
val BeatAmber = Color(0xFFF59E0B)
val BorderSubtle = Color(0xFF252834)
val TextPrimary = Color(0xFFF4F5F8)
val TextSecondary = Color(0xFF9499AD)
val DangerRed = Color(0xFFEF4444)
val SuccessGreen = Color(0xFF10B981)

private val BeatSyncDarkScheme = darkColorScheme(
    primary = PantoneBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF003B80),
    onPrimaryContainer = Color.White,
    secondary = PantoneBlueLight,
    onSecondary = Color.White,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    error = DangerRed
)

@Composable
fun BeatSyncTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BeatSyncDarkScheme,
        content = content
    )
}
