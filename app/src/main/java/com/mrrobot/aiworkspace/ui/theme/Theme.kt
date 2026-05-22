package com.mrrobot.aiworkspace.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrrobot.aiworkspace.data.AppThemeMode

private val WarmLightScheme = lightColorScheme(
    primary = Color(0xFF8A4B2A),
    secondary = Color(0xFF6F5E53),
    tertiary = Color(0xFF0F766E),
    background = Color(0xFFFAF7F2),
    surface = Color(0xFFFFFCF7),
    surfaceVariant = Color(0xFFF1E9DE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF201A16),
    onSurface = Color(0xFF201A16),
    onSurfaceVariant = Color(0xFF665B52),
    outline = Color(0xFFD8C8B8)
)

private val WarmDarkScheme = darkColorScheme(
    primary = Color(0xFFFFB88C),
    secondary = Color(0xFFE8C8B8),
    tertiary = Color(0xFF7DD3C7),
    background = Color(0xFF11100F),
    surface = Color(0xFF1A1816),
    surfaceVariant = Color(0xFF24211F),
    onPrimary = Color(0xFF3B1605),
    onSecondary = Color(0xFF321A10),
    onTertiary = Color(0xFF052D2A),
    onBackground = Color(0xFFF6EFE8),
    onSurface = Color(0xFFF6EFE8),
    onSurfaceVariant = Color(0xFFCFC3BA),
    outline = Color(0xFF4E4540)
)

private val CyberScheme = lightColorScheme(
    primary = Color(0xFF007D89),
    secondary = Color(0xFF7257A8),
    tertiary = Color(0xFF0F766E),
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEFF6FF),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1)
)

private val HackerScheme = darkColorScheme(
    primary = Color(0xFFFFB3B6),
    secondary = Color(0xFFFFB4AC),
    tertiary = Color(0xFFFFB95F),
    background = Color(0xFF131313),
    surface = Color(0xFF1C1B1B),
    surfaceVariant = Color(0xFF353534),
    primaryContainer = Color(0xFFE11D48),
    secondaryContainer = Color(0xFF921517),
    tertiaryContainer = Color(0xFFA36700),
    onPrimary = Color(0xFF68001A),
    onSecondary = Color(0xFF690007),
    onTertiary = Color(0xFF472A00),
    onBackground = Color(0xFFE5E2E1),
    onSurface = Color(0xFFE5E2E1),
    onSurfaceVariant = Color(0xFFE5BDBE),
    outline = Color(0xFFAC8889),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val RoundedShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val HackerShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(10.dp)
)

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 46.sp,
        lineHeight = 50.sp,
        letterSpacing = (-1.1).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.6).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 25.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.25).sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.0.sp
    )
)

@Composable
fun MrRobotTheme(
    themeMode: AppThemeMode = AppThemeMode.Auto,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()

    val scheme = when (themeMode) {
        AppThemeMode.Auto -> if (systemDark) WarmDarkScheme else WarmLightScheme
        AppThemeMode.Dark -> WarmDarkScheme
        AppThemeMode.Light -> WarmLightScheme
        AppThemeMode.Cyberpunk -> CyberScheme
        AppThemeMode.Hacker -> HackerScheme
    }

    val shapes = when (themeMode) {
        AppThemeMode.Hacker -> HackerShapes
        else -> RoundedShapes
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        shapes = shapes,
        content = content
    )
}
