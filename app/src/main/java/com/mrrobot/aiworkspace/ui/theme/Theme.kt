package com.mrrobot.aiworkspace.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CyberScheme = darkColorScheme(
    primary = Color(0xFF6200EE),
    secondary = Color(0xFF7257A8),
    tertiary = Color(0xFF0F70EE),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF24211F),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1)
)

@Composable
fun MrRobotAIWorkspaceTheme(content: @Composable () -> Unit) {
    // You can expand this wrapper later if you need dynamic schemes
    androidx.compose.material3.MaterialTheme(
        colorScheme = CyberScheme,
        content = content
    )
}
