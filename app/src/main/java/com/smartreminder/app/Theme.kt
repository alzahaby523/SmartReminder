package com.smartreminder.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/** تدرج بطاقة «التالي» في أعلى الشاشة. */
val HeroStart = Color(0xFF0E3B3A)
val HeroEnd = Color(0xFF1F7A6D)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F7A6D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3F1EE),
    onPrimaryContainer = Color(0xFF0E3B3A),
    secondaryContainer = Color(0xFFE3F1EE),
    background = Color(0xFFF6F8F7),
    onBackground = Color(0xFF10201F),
    surface = Color.White,
    onSurface = Color(0xFF10201F),
    surfaceVariant = Color(0xFFE3F1EE),
    onSurfaceVariant = Color(0xFF6B7C7A),
    outline = Color(0xFF9AAAA8),
    outlineVariant = Color(0xFFE3EAE8)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4FD1B0),
    onPrimary = Color(0xFF04201B),
    primaryContainer = Color(0xFF143330),
    onPrimaryContainer = Color(0xFFCDEFE7),
    secondaryContainer = Color(0xFF143330),
    background = Color(0xFF0B1313),
    onBackground = Color(0xFFE3EEEC),
    surface = Color(0xFF121D1D),
    onSurface = Color(0xFFE3EEEC),
    surfaceVariant = Color(0xFF143330),
    onSurfaceVariant = Color(0xFF8FA3A0),
    outline = Color(0xFF5E716F),
    outlineVariant = Color(0xFF1F2E2D)
)

/** الواجهة عربية بالكامل، لذلك نفرض اتجاه RTL مهما كانت لغة النظام. */
@Composable
fun SmartReminderTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
