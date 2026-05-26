package com.alchitry.tester

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.alchitry.labs2.ui.theme.AlchitryTheme

fun main() {
    application {
        MainWindow()
    }
}

@Composable
fun ApplicationScope.MainWindow() {
    val tester = remember { FtTester(false) }
    Window(
        title = "Alchitry Tester",
        state = rememberWindowState(/*placement = WindowPlacement.Fullscreen*/ size = DpSize(800.dp, 480.dp)),
        onCloseRequest = {
            exitApplication()
        }
    ) {
        AlchitryTheme {
            Surface(Modifier.fillMaxSize()) {
                Column(
                    Modifier.padding(10.dp, 5.dp, 10.dp, 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource("alchitry_tester.svg"),
                        contentDescription = "Logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.width(600.dp)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        ElementButton("ft.svg") {

                        }
                        ElementButton("ft_plus.svg") {

                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ElementButton(icon: String, onClick: () -> Unit) {
    Surface(
        tonalElevation = 100.dp,
        shadowElevation = 10.dp,
        shape = RoundedCornerShape(10.dp)
    ) {
        Box(
            Modifier.clickable(onClick = onClick)
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = "Element Icon",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(100.dp)
            )
        }
    }
}