package com.studyspark.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DisplayFont = FontFamily.Serif
private val BodyFont = FontFamily.SansSerif

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F6A5A),
    onPrimary = Color(0xFFF4FFF8),
    secondary = Color(0xFFD97706),
    background = Color(0xFFF3F7F4),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF14201C),
    onSurface = Color(0xFF14201C)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5EE0C4),
    onPrimary = Color(0xFF00382F),
    secondary = Color(0xFFFBBF24),
    background = Color(0xFF0B1411),
    surface = Color(0xFF13201B),
    onBackground = Color(0xFFE7F2EC),
    onSurface = Color(0xFFE7F2EC)
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 40.sp),
    headlineMedium = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
    titleLarge = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    bodyLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Medium, fontSize = 14.sp)
)

@Composable
fun StudySparkTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
