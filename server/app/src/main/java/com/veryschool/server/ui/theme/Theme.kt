package com.veryschool.server.ui.theme

import android.content.res.Configuration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AdminPrimary   = Color(0xFF00B894)
val AdminSecondary = Color(0xFF8B5CF6)
val AdminBg        = Color(0xFF0A0A0F)
val AdminSurf      = Color(0xFF131320)
val AdminCard      = Color(0xFF1A1A2E)
val AdminBorder    = Color(0xFF2D2D50)
val AdminOn        = Color(0xFFE8E8F5)
val AdminMuted     = Color(0xFF8888AA)
val AdminGreen     = Color(0xFF10B981)
val AdminRed       = Color(0xFFEF4444)
val AdminYellow    = Color(0xFFF59E0B)

val LocalAdminTC = compositionLocalOf { AdminTC() }
data class AdminTC(val x: Int = 0) {
    val bg = AdminBg; val surf = AdminSurf; val card = AdminCard
    val border = AdminBorder; val on = AdminOn; val muted = AdminMuted
}

private val CS = darkColorScheme(
    primary = AdminPrimary, secondary = AdminSecondary, background = AdminBg,
    surface = AdminSurf, surfaceVariant = AdminCard, onPrimary = Color.White,
    onBackground = AdminOn, onSurface = AdminOn, onSurfaceVariant = AdminMuted,
    outline = AdminBorder, error = AdminRed
)

@Composable
fun VSAdminTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAdminTC provides AdminTC()) {
        MaterialTheme(colorScheme = CS,
            typography = Typography(
                titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                bodyLarge = TextStyle(fontSize = 15.sp), bodyMedium = TextStyle(fontSize = 13.sp)
            ), content = content)
    }
}
