package com.veryschool.client.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// VerySchool Color Palette
val VSPrimary = Color(0xFF6C5CE7)
val VSPrimaryVariant = Color(0xFF5A4BD1)
val VSSecondary = Color(0xFF00CEC9)
val VSBackground = Color(0xFF0D0D1A)
val VSSurface = Color(0xFF1A1A2E)
val VSSurfaceVariant = Color(0xFF16213E)
val VSOnSurface = Color(0xFFE8E8F0)
val VSOnSurfaceMuted = Color(0xFF9090B0)
val VSMessageOwn = Color(0xFF6C5CE7)
val VSMessageOther = Color(0xFF1E1E3A)
val VSGreen = Color(0xFF00B894)
val VSRed = Color(0xFFD63031)
val VSBorder = Color(0xFF2D2D4E)

private val DarkColorScheme = darkColorScheme(
    primary = VSPrimary,
    primaryContainer = VSPrimaryVariant,
    secondary = VSSecondary,
    background = VSBackground,
    surface = VSSurface,
    surfaceVariant = VSSurfaceVariant,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = VSOnSurface,
    onSurface = VSOnSurface,
    onSurfaceVariant = VSOnSurfaceMuted,
    outline = VSBorder,
    error = VSRed
)

@Composable
fun VerySchoolTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}
