package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme =
  lightColorScheme(
    primary = MileageBlue,
    onPrimary = Color.White,
    primaryContainer = MileageBlueContainer,
    onPrimaryContainer = MileageBlue,
    secondary = BusinessBlue,
    onSecondary = Color.White,
    secondaryContainer = BusinessBg,
    onSecondaryContainer = BusinessBlue,
    tertiary = PersonalOrange,
    onTertiary = Color.White,
    tertiaryContainer = PersonalBg,
    onTertiaryContainer = PersonalOrange,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = LightBorder,
    outlineVariant = LightBorderSubtle,
  )

private val DarkColorScheme =
  darkColorScheme(
    primary = MileageBlueLight,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color.White,
    secondary = BusinessBlue,
    onSecondary = Color.White,
    secondaryContainer = BusinessBg,
    onSecondaryContainer = BusinessBlue,
    tertiary = PersonalOrange,
    onTertiary = Color.White,
    tertiaryContainer = PersonalBg,
    onTertiaryContainer = PersonalOrange,
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFF162032),
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF1E293B),
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false, // Light theme is default as requested
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
    typography = Typography,
    content = content
  )
}
