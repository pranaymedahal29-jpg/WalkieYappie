package com.walkieyappie.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// User Requested Custom Matrix Theme Palette
val PitchBlack = Color(0xFF000000)                // Background: #000000
val MatrixGreenText = Color(0xFF00EE00)           // Text: #00ee00
val MatrixGreenOutline = Color(0xFF008E00)        // Outlines: #008e00
val MatrixGreenButtonFill = Color(0xB3005F00)     // Button fill (light transparency): #005f00
val MatrixGreenIncomingBlock = Color(0xFF002F00)   // Incoming request block: #002f00

// LED Unlit Dot Color (Dark warm green undertone)
val LedUnlitDot = Color(0xFF001F00)
val StatusRed = Color(0xFFFF3D00)
val StatusBlue = Color(0xFF00AAFF)

private val DarkColorScheme = darkColorScheme(
    primary = MatrixGreenText,
    secondary = MatrixGreenOutline,
    tertiary = MatrixGreenButtonFill,
    background = PitchBlack,
    surface = MatrixGreenIncomingBlock,
    onPrimary = PitchBlack,
    onSecondary = MatrixGreenText,
    onBackground = MatrixGreenText,
    onSurface = MatrixGreenText,
    error = StatusRed
)

@Composable
fun WalkieYappieTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
