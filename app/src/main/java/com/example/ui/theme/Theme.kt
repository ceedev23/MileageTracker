package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
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

@Composable
fun MyApplicationTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = LightColorScheme,
    typography = Typography,
    content = content
  )
}
