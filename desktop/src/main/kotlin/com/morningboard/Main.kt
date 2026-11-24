package com.morningboard

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    val prefs = remember { Prefs() }
    var alwaysOnTop by remember { mutableStateOf(prefs.alwaysOnTop) }
    val windowState = rememberWindowState(width = 1120.dp, height = 720.dp)

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "아침보드",
        undecorated = false,
        transparent = false,
        alwaysOnTop = alwaysOnTop,
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = C.BLUE, background = C.BG, surface = C.CARD, onSurface = C.TEXT,
            )
        ) {
            App(
                prefs = prefs,
                alwaysOnTop = alwaysOnTop,
                onToggleTop = { alwaysOnTop = !alwaysOnTop; prefs.alwaysOnTop = alwaysOnTop },
                onMinimize = { windowState.isMinimized = true },
                onClose = ::exitApplication,
                dragArea = { content -> WindowDraggableArea { content() } },
            )
        }
    }
}
