package com.gamegear.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF82AAFF),
    onPrimary = Color(0xFF00286E),
    primaryContainer = Color(0xFF003F9B),
    onPrimaryContainer = Color(0xFFD8E2FF),
    secondary = Color(0xFFBBC5E8),
    onSecondary = Color(0xFF252E4B),
    secondaryContainer = Color(0xFF3B4563),
    onSecondaryContainer = Color(0xFFD8E2FF),
    tertiary = Color(0xFFE2BBFF),
    onTertiary = Color(0xFF3E1F62),
    tertiaryContainer = Color(0xFF563579),
    onTertiaryContainer = Color(0xFFF0DBFF),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099),
    error = Color(0xFFFFB4AB),
)

@Composable
fun GameGearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
