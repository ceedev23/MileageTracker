package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography =
  Typography(
    headlineLarge = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.Bold,
      fontSize = 32.sp,
      lineHeight = 40.sp,
      letterSpacing = (-0.5).sp,
      color = SlateTextPrimary
    ),
    headlineMedium = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.Bold,
      fontSize = 28.sp,
      lineHeight = 36.sp,
      letterSpacing = (-0.25).sp,
      color = SlateTextPrimary
    ),
    headlineSmall = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.SemiBold,
      fontSize = 24.sp,
      lineHeight = 32.sp,
      color = SlateTextPrimary
    ),
    titleLarge = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.Bold,
      fontSize = 20.sp,
      lineHeight = 28.sp,
      letterSpacing = (-0.2).sp,
      color = SlateTextPrimary
    ),
    titleMedium = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.SemiBold,
      fontSize = 16.sp,
      lineHeight = 24.sp,
      letterSpacing = 0.1.sp,
      color = SlateTextPrimary
    ),
    titleSmall = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.Medium,
      fontSize = 14.sp,
      lineHeight = 20.sp,
      letterSpacing = 0.1.sp,
      color = SlateTextPrimary
    ),
    bodyLarge = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.Normal,
      fontSize = 16.sp,
      lineHeight = 24.sp,
      letterSpacing = 0.25.sp,
      color = SlateTextPrimary
    ),
    bodyMedium = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.Normal,
      fontSize = 14.sp,
      lineHeight = 20.sp,
      letterSpacing = 0.25.sp,
      color = SlateTextSecondary
    ),
    bodySmall = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.Normal,
      fontSize = 12.sp,
      lineHeight = 16.sp,
      letterSpacing = 0.4.sp,
      color = SlateTextSecondary
    ),
    labelLarge = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.SemiBold,
      fontSize = 14.sp,
      lineHeight = 20.sp,
      letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.Medium,
      fontSize = 12.sp,
      lineHeight = 16.sp,
      letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.Medium,
      fontSize = 11.sp,
      lineHeight = 16.sp,
      letterSpacing = 0.5.sp
    )
  )
