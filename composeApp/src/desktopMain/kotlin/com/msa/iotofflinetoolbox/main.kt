package com.msa.iotofflinetoolbox

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension

fun main() = application {
    val windowState = rememberWindowState(width = 1_280.dp, height = 820.dp)
    Window(
        onCloseRequest = ::exitApplication,
        title = "IoT Offline Toolbox",
        state = windowState,
    ) {
        LaunchedEffect(window) {
            window.minimumSize = Dimension(560, 360)
        }
        App()
    }
}
