package com.walkieyappie.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val DeepObsidian = Color(0xFF0D0E15)
val DarkSurface = Color(0xFF161824)
val SurfaceBorder = Color(0xFF2B2F45)

val NeonCyan = Color(0xFF00E5FF)
val ElectricBlue = Color(0xFF2979FF)
val TransmitRed = Color(0xFFFF3D00)
val ReceiveGreen = Color(0xFF00E676)
val TextPrimary = Color(0xFFF0F4F8)
val TextSecondary = Color(0xFF8C9BAE)

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    secondary = ElectricBlue,
    tertiary = ReceiveGreen,
    background = DeepObsidian,
    surface = DarkSurface,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = TransmitRed
)

@Composable
fun WalkieYappieTheme(
    darkTheme: Boolean = true, // Cyber tactical UI defaults to dark
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
