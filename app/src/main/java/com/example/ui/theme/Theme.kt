package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

fun getAccentColor(name: String): Color {
    return when (name.uppercase()) {
        "AMBER" -> AccentAmber
        "CYAN" -> AccentCyan
        "EMERALD" -> AccentEmerald
        "PURPLE" -> AccentPurple
        "BLUE" -> AccentBlue
        else -> AccentCoral
    }
}

@Composable
fun SalimTheme(
    themeMode: String = "SYSTEM",
    accentName: String = "CORAL",
    content: @Composable () -> Unit
) {
    val systemInDark = isSystemInDarkTheme()
    val isDark = when (themeMode.uppercase()) {
        "DARK", "AMOLED" -> true
        "LIGHT" -> false
        else -> systemInDark
    }
    val isAmoled = themeMode.uppercase() == "AMOLED"
    val accent = getAccentColor(accentName)

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = accent.copy(alpha = 0.25f),
            onPrimaryContainer = Color(0xFFFFDBCF),
            secondary = FunctionCyan,
            onSecondary = Color.White,
            tertiary = FunctionPurple,
            onTertiary = Color.White,
            background = if (isAmoled) AmoledBackground else DarkBackground,
            onBackground = DarkTextPrimary,
            surface = if (isAmoled) AmoledSurface else DarkSurface,
            onSurface = DarkTextPrimary,
            surfaceVariant = if (isAmoled) AmoledSurfaceVariant else DarkSurfaceVariant,
            onSurfaceVariant = DarkTextSecondary,
            outline = if (isAmoled) AmoledSurfaceElevated else DarkSurfaceElevated,
            error = ErrorRed,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = accent.copy(alpha = 0.15f),
            onPrimaryContainer = Color(0xFF7A2500),
            secondary = FunctionCyan,
            onSecondary = Color.White,
            tertiary = FunctionPurple,
            onTertiary = Color.White,
            background = LightBackground,
            onBackground = LightTextPrimary,
            surface = LightSurface,
            onSurface = LightTextPrimary,
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = LightTextSecondary,
            outline = LightSurfaceElevated,
            error = ErrorRed,
            onError = Color.White
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
