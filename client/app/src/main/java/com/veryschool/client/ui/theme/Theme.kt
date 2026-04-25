package com.veryschool.client.ui.theme

import android.content.res.Configuration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.veryschool.client.data.prefs.AppTheme

val VSPrimary     = Color(0xFF8B5CF6)
val VSPrimaryDark = Color(0xFF7C3AED)
val VSPrimaryLite = Color(0xFFA78BFA)
val VSSecondary   = Color(0xFF06B6D4)
val VSGreen       = Color(0xFF10B981)
val VSRed         = Color(0xFFEF4444)
val VSYellow      = Color(0xFFF59E0B)
val VSFrozen      = Color(0xFF67E8F9)
val VSBubbleOwn   = Color(0xFF6D28D9)
val VSBubbleOther = Color(0xFF1E1E3A)
val VSBubbleBot   = Color(0xFF064E3B)
val VSBgDark      = Color(0xFF0F0F17)
val VSSurfDark    = Color(0xFF1A1A2E)
val VSCardDark    = Color(0xFF1E1E32)
val VSBorderDark  = Color(0xFF2D2D50)
val VSOnDark      = Color(0xFFE8E8F5)
val VSMutedDark   = Color(0xFF8888AA)
val VSBgLight     = Color(0xFFF8F7FF)
val VSSurfLight   = Color(0xFFFFFFFF)
val VSCardLight   = Color(0xFFF3F2FF)
val VSBorderLight = Color(0xFFDDDDFF)
val VSOnLight     = Color(0xFF1A1A2E)
val VSMutedLight  = Color(0xFF666688)

val LocalDark = compositionLocalOf { true }

data class TC(val dark: Boolean) {
    val bg = if (dark) VSBgDark else VSBgLight
    val surf = if (dark) VSSurfDark else VSSurfLight
    val card = if (dark) VSCardDark else VSCardLight
    val border = if (dark) VSBorderDark else VSBorderLight
    val on = if (dark) VSOnDark else VSOnLight
    val muted = if (dark) VSMutedDark else VSMutedLight
}
val LocalTC = compositionLocalOf { TC(true) }

private val DarkCS = darkColorScheme(
    primary = VSPrimary, primaryContainer = VSPrimaryDark, secondary = VSSecondary,
    background = VSBgDark, surface = VSSurfDark, surfaceVariant = VSCardDark,
    onPrimary = Color.White, onSecondary = Color.White,
    onBackground = VSOnDark, onSurface = VSOnDark, onSurfaceVariant = VSMutedDark,
    outline = VSBorderDark, error = VSRed
)
private val LightCS = lightColorScheme(
    primary = VSPrimary, primaryContainer = VSPrimaryLite, secondary = VSSecondary,
    background = VSBgLight, surface = VSSurfLight, surfaceVariant = VSCardLight,
    onPrimary = Color.White, onSecondary = Color.White,
    onBackground = VSOnLight, onSurface = VSOnLight, onSurfaceVariant = VSMutedLight,
    outline = VSBorderLight, error = VSRed
)

@Composable
fun VerySchoolTheme(appTheme: AppTheme = AppTheme.DARK, content: @Composable () -> Unit) {
    val dark = when (appTheme) {
        AppTheme.DARK -> true; AppTheme.LIGHT -> false
        AppTheme.SYSTEM -> (LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
    CompositionLocalProvider(LocalDark provides dark, LocalTC provides TC(dark)) {
        MaterialTheme(
            colorScheme = if (dark) DarkCS else LightCS,
            typography = Typography(
                titleLarge  = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                bodyLarge   = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
                bodyMedium  = TextStyle(fontSize = 13.sp), labelSmall = TextStyle(fontSize = 11.sp)
            ), content = content
        )
    }
}
