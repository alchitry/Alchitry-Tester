package com.alchitry.tester

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() {
    application {
        MainWindow()
    }
}

@Composable
fun ApplicationScope.MainWindow() {
    val tester = remember{ FtTester(false) }
    Window(
        title="Alchitry Tester",
        state = rememberWindowState(placement = WindowPlacement.Fullscreen),
        onCloseRequest = {
            exitApplication()
        }
    ) {
        tester.FtTesterView()
    }
}