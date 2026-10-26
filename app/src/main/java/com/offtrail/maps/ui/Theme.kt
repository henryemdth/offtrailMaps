package com.offtrail.maps.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Forest = Color(0xFF1B4D3E)
private val ForestLight = Color(0xFF2E7D64)
private val Sand = Color(0xFFE8F5E9)

private val LightColors = lightColorScheme(
    primary = Forest,
    secondary = ForestLight,
    background = Color(0xFFFAFDFB),
    surface = Color(0xFFFAFDFB),
)

private val DarkColors = darkColorScheme(
    primary = ForestLight,
    secondary = Sand,
    background = Color(0xFF101613),
    surface = Color(0xFF101613),
)

@Composable
fun OfftrailTheme(themeMode: String = "system", content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
