package com.veryschool.server.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AdminPrimary = Color(0xFF00B894)
val AdminSecondary = Color(0xFF6C5CE7)
val AdminBackground = Color(0xFF0A0A0F)
val AdminSurface = Color(0xFF14141F)
val AdminSurfaceVariant = Color(0xFF1E1E2E)
val AdminOnSurface = Color(0xFFE8E8F0)
val AdminOnSurfaceMuted = Color(0xFF7070A0)
val AdminBorder = Color(0xFF2A2A3E)
val AdminGreen = Color(0xFF00B894)
val AdminRed = Color(0xFFFF4757)
val AdminYellow = Color(0xFFFFA502)

private val AdminColorScheme = darkColorScheme(
    primary = AdminPrimary, secondary = AdminSecondary,
    background = AdminBackground, surface = AdminSurface,
    surfaceVariant = AdminSurfaceVariant, onPrimary = Color.White,
    onBackground = AdminOnSurface, onSurface = AdminOnSurface,
    onSurfaceVariant = AdminOnSurfaceMuted, outline = AdminBorder, error = AdminRed
)

@Composable
fun VSAdminTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AdminColorScheme, content = content)
}
