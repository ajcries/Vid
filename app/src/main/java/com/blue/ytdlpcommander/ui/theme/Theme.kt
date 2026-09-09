package com.blue.ytdlpcommander.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// A deliberately bolder, darker "commander" palette - electric violet accent
// on near-black, rather than a generic light Material blue/purple. Dark-only
// by design (matches the reference app and the terminal-log aesthetic).
val ElectricViolet = Color(0xFF7C5CFF)
val ElectricVioletDim = Color(0xFF5B3FCC)
val NeonPink = Color(0xFFFF4FA3)
val NeonGreen = Color(0xFF3DDC84)
val WarnAmber = Color(0xFFFFB74D)
val DangerRed = Color(0xFFFF5C5C)
val SurfaceDark = Color(0xFF14141C)
val SurfaceCard = Color(0xFF1E1E29)
val SurfaceCardAlt = Color(0xFF262635)
val BorderSubtle = Color(0xFF34344A)

private val DarkColors = darkColorScheme(
    primary = ElectricViolet,
    secondary = NeonPink,
    tertiary = NeonGreen,
    background = SurfaceDark,
    surface = SurfaceCard,
    surfaceVariant = SurfaceCardAlt,
    error = DangerRed,
    onPrimary = Color.White,
    onBackground = Color(0xFFEDEDF5),
    onSurface = Color(0xFFEDEDF5),
    onSurfaceVariant = Color(0xFFB0B0C8),
    outline = BorderSubtle
)

@Composable
fun YtDlpCommanderTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, typography = Typography, content = content)
}
