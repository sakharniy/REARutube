package com.matik.reatube.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val Background = Color(0xFF0F1115)
val Panel = Color(0xFF1A1D24)
val PanelFocused = Color(0xFF262B35)
val Accent = Color(0xFF00A3FF)
val Muted = Color(0xFF9AA3B2)
val LiveRed = Color(0xFFE53935)

@Composable
fun ReaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Accent, background = Background, surface = Background),
        content = content,
    )
}
