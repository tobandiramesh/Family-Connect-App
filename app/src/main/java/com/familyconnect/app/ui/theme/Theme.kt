package com.familyconnect.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = PrimaryLight,
    onPrimaryContainer = Primary,
    
    secondary = Secondary,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = SecondaryLight,
    onSecondaryContainer = Secondary,
    
    tertiary = Tertiary,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Tertiary,
    
    error = Error,
    onError = Color(0xFFFFFFFF),
    errorContainer = ErrorLight,
    onErrorContainer = Error,
    
    background = Color(0xFFFBF9F3),
    onBackground = Color(0xFF1A1D23),
    
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1D23),
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF5A5A5A),
    
    outline = Color(0xFFD0D0D0)
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Primary,
    primaryContainer = Primary,
    onPrimaryContainer = Color(0xFFFFFFFF),
    
    secondary = SecondaryLight,
    onSecondary = Secondary,
    secondaryContainer = Secondary,
    onSecondaryContainer = Color(0xFFFFFFFF),
    
    tertiary = Tertiary,
    onTertiary = Color(0xFF1A1F2E),
    tertiaryContainer = Color(0xFF7C2D12),
    onTertiaryContainer = Color(0xFFFEF3C7),
    
    error = ErrorLight,
    onError = Error,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = ErrorLight,
    
    background = DarkSurface,
    onBackground = Color(0xFFF3F4F6),
    
    surface = DarkSurfaceVariant,
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = Color(0xFF3A4251),
    onSurfaceVariant = Color(0xFFD1D5DB),
    
    outline = Color(0xFF5A6271)
)

@Composable
fun FamilyConnectTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
