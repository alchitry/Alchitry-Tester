package com.alchitry.tester

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.alchitry.hardware.Board
import com.alchitry.labs2.ui.theme.AlchitryTheme

fun main() {
//    runBlocking {
//        println(dumpEEPROM())
//    }
    application {
        MainWindow()
    }
}

@Composable
fun ApplicationScope.MainWindow() {
    Window(
        title = "Alchitry Tester",
        state = rememberWindowState(placement = WindowPlacement.Fullscreen),
        //state = rememberWindowState(/*placement = WindowPlacement.Fullscreen*/ size = DpSize(800.dp, 480.dp)),
        onCloseRequest = {
            exitApplication()
        }
    ) {
        AlchitryTheme {
            Surface(Modifier.fillMaxSize()) {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
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
                                ElementButton("cu.svg") {
                                    navController.navigate("cu_tester")
                                }
                                ElementButton("au.svg", "35T") {
                                    navController.navigate("au_tester_35t")
                                }
                                ElementButton("au.svg", "50T") {
                                    navController.navigate("au_tester_50t")
                                }
                                ElementButton("pt.svg", "100T") {
                                    navController.navigate("pt_tester_100t")
                                }
                                ElementButton("pt.svg", "200T") {
                                    navController.navigate("pt_tester_200t")
                                }
                                ElementButton("ft.svg") {
                                    navController.navigate("ft_tester")
                                }
                                ElementButton("ft_plus.svg") {
                                    navController.navigate("ft_plus_tester")
                                }
                            }
                        }
                    }
                    composable("au_tester_35t") {
                        val tester = remember { FpgaTester(Board.AlchitryAuV2T35) }
                        tester.statusUI{ navController.popBackStack() }
                    }
                    composable("cu_tester") {
                        val tester = remember { FpgaTester(Board.AlchitryCuV2) }
                        tester.statusUI{ navController.popBackStack() }
                    }
                    composable("pt_tester_100t") {
                        val tester = remember { FpgaTester(Board.AlchitryPtV2T100) }
                        tester.statusUI{ navController.popBackStack() }
                    }
                    composable("pt_tester_200t") {
                        val tester = remember { FpgaTester(Board.AlchitryPtV2T200) }
                        tester.statusUI{ navController.popBackStack() }
                    }
                    composable("ft_tester") {
                        val tester = remember { FtTester(false) }
                        tester.statusUI{ navController.popBackStack() }
                    }
                    composable("ft_plus_tester") {
                        val tester = remember { FtTester(true) }
                        tester.statusUI{ navController.popBackStack() }
                    }
                }
            }
        }
    }
}

@Composable
fun ElementButton(icon: String, size: String? = null, onClick: () -> Unit) {
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
            if (size != null) {
                Text(size, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.BottomEnd))
            }
        }
    }
}