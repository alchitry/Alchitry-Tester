package com.alchitry.tester

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.alchitry.hardware.AlchitryFt
import com.alchitry.hardware.FtType
import com.alchitry.hardware.usb.ftdi.D3xx
import com.alchitry.labs2.ui.theme.AlchitryColors
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.TimeSource



@Composable
fun FtTesterScreen(onBack: () -> Unit) {
    val tester = remember { FtTester(false) }
    tester.statusUI(onBack)
}

@Composable
fun FtPlusTesterScreen(onBack: () -> Unit) {
    val tester = remember { FtTester(true) }
    tester.statusUI(onBack)
}

class FtTester(val isPlus: Boolean) {
    private enum class FtTesterStates {
        Searching,
        Testing,
        Error,
        Disconnect
    }

    private var state by mutableStateOf(FtTesterStates.Searching)
    private var superSpeed by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)
    private var totalBoards by mutableStateOf(0)
    private var failures by mutableStateOf(0)
    private var bytesRead by mutableStateOf(0)

    private var boardsPerHour by mutableStateOf(0.0)
    private var dataRate by mutableStateOf(0.0)

    private val minDataRateThreshold = if (isPlus) 315 else 170
    private val bufferLen = 1024 * 1024
    private val bufferCount = 2
    private val writeCount = if (isPlus) 600 else 300
    private val totalBytes = bufferLen * writeCount

    @Composable
    fun statusUI(onBack: () -> Unit) {
        LaunchedEffect(Unit) {
            withContext(Dispatchers.Default) {
                runTests()
            }
        }
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.padding(10.dp, 5.dp, 10.dp, 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()){
                    Image(
                        painter = painterResource("alchitry_main.svg"),
                        contentDescription = "Logo",
                        contentScale = ContentScale.FillHeight,
                        modifier = Modifier.height(100.dp)
                    )
                    Image(
                        painter = painterResource(if (isPlus) "ft_plus.svg" else "ft.svg"),
                        contentDescription = "Logo",
                        contentScale = ContentScale.FillHeight,
                        modifier =  Modifier.height(100.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                when (state) {
                    FtTesterStates.Searching -> Text("Waiting for board...", style = MaterialTheme.typography.headlineLarge)
                    FtTesterStates.Testing -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Testing: ", style = MaterialTheme.typography.headlineLarge)
                            LinearProgressIndicator(
                                progress = { bytesRead.toFloat() / totalBytes },
                                modifier = Modifier.fillMaxWidth().weight(1f).height(16.dp),
                            )
                        }
                    }

                    FtTesterStates.Error -> Text("Error: $errorMessage", color = AlchitryColors.current.Error, style = MaterialTheme.typography.headlineSmall)
                    FtTesterStates.Disconnect -> {
                        Text("Disconnect the current board.")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Result: ", style = MaterialTheme.typography.headlineLarge)
                            val passed = dataRate >= minDataRateThreshold && superSpeed && errorMessage == null
                            if (passed) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = "Success", tint = AlchitryColors.current.Success, modifier = Modifier.size(50.dp))
                                Text(" Passed", style = MaterialTheme.typography.headlineLarge)
                            } else {
                                Icon(Icons.Filled.Error, contentDescription = "Failure", tint = AlchitryColors.current.Error, modifier = Modifier.size(50.dp))
                                Text(" Failed", style = MaterialTheme.typography.headlineLarge)
                            }
                        }

                        errorMessage?.let{ error ->
                            Text("Error: $error", color = AlchitryColors.current.Error)
                        }
                    }
                }

                if (state != FtTesterStates.Searching) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                        val maxPossible = if (isPlus) 400 else 200
                        Text("Data Rate [MBPS]: ")
                        val color = if (dataRate < minDataRateThreshold) AlchitryColors.current.Error else AlchitryColors.current.Success
                        val thresholdFraction = minDataRateThreshold.toFloat() / maxPossible
                        LinearProgressIndicator(
                            progress = { (dataRate / maxPossible).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.weight(1f).height(16.dp).drawWithContent {
                                drawContent()
                                val x = size.width * thresholdFraction
                                drawLine(
                                    color = AlchitryColors.current.scheme.surface,
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = 4.dp.toPx()
                                )
                            },
                            color = color,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(dataRate.roundToInt().toString())
                        Text("/$minDataRateThreshold")
                    }
                    Row {
                        Text("SuperSpeed: ")
                        if (superSpeed)
                            Icon(Icons.Filled.CheckCircle, contentDescription = "Success", tint = AlchitryColors.current.Success)
                        else
                            Icon(Icons.Filled.Error, contentDescription = "Failed", tint = AlchitryColors.current.Error)
                    }
                }



                Spacer(Modifier.weight(1f))
                Text("Stats:")
                Column(Modifier.padding(start = 10.dp)) {
                    Text("Total Boards: $totalBoards")
                    Text("Successful Boards: ${totalBoards - failures}")
                    Text("Testing Rate (successful boards/hour): ${boardsPerHour.roundToInt()}")
                }
            }
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }
    }

    private suspend fun CoroutineScope.waitForBoard(): AlchitryFt {
        while (true) {
            ensureActive()
            val boards = AlchitryFt.find_boards()
            val idx = boards.indexOfFirst {
                if (isPlus) it is FtType.FtPlus else it is FtType.Ft
            }
            if (idx < 0) {
                delay(100.milliseconds)
                continue
            }
            val device = boards[idx]
            superSpeed = device.superSpeed
            return AlchitryFt.connect(idx)
        }
    }

    private fun hash_uint32(x: UInt): UInt {
        // MurmurHash3's fmix32 function
        var x: UInt = x
        x = x xor (x shr 16)
        x *= 0x85ebca6bu
        x = x xor (x shr 13)
        x *= 0xc2b2ae35u
        x = x xor (x shr 16)
        return x
    }

    suspend fun runTests() = coroutineScope {
        val testStartTime = TimeSource.Monotonic.markNow()

        while (isActive) {
            state = FtTesterStates.Searching
            val board = try {
                waitForBoard()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.message
                state = FtTesterStates.Error
                delay(1.seconds)
                continue
            }

            board.use {
                state = FtTesterStates.Testing
                errorMessage = null
                bytesRead = 0

                val boardStartTime = TimeSource.Monotonic.markNow()
                try {
                    coroutineScope mainScope@{
                        launch(Dispatchers.IO) { // write loop
                            var next = 0u
                            val contexts = MutableList<D3xx.DeviceConnection.OverlappedContext?>(bufferCount) { null }
                            for (k in 0 until writeCount / bufferCount) {
                                contexts.forEachIndexed { index, context ->
                                    ensureActive()
                                    if (context != null) {
                                        val (bytes, status) = context.getResult(true)
                                        context.close()
                                        if (bytes != bufferLen || status != D3xx.FtStatus.OK) {
                                            errorMessage = "Failed to write full buffer! Status: ${status.name}"
                                            state = FtTesterStates.Error
                                            this@mainScope.cancel()
                                            return@launch
                                        }
                                    }
                                    val data = ByteArray(bufferLen)
                                    val buffer = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder())
                                    for (i in 0 until bufferLen / 4) {
                                        buffer.putInt(hash_uint32(i.toUInt() + next).toInt())
                                    }
                                    next += (bufferLen / 4).toUInt()
                                    contexts[index] = board.asyncWriteData(data)
                                }
                            }
                        }
                        launch(Dispatchers.IO) { // read loop
                            var total = 0u
                            var next = 0u
                            val contexts = MutableList<D3xx.DeviceConnection.OverlappedContext?>(bufferCount) { null }
                            for (k in 0 until writeCount / bufferCount) {
                                contexts.forEachIndexed { index, context ->
                                    ensureActive()
                                    if (context != null) {
                                        val (data, status) = context.getResultBytes(true)
                                        context.close()
                                        if (data.size != bufferLen || status != D3xx.FtStatus.OK) {
                                            errorMessage = "Failed to read full buffer! Status: ${status.name}"
                                            state = FtTesterStates.Error
                                            this@mainScope.cancel()
                                            return@launch
                                        }
                                        val buffer = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder())
                                        for (i in 0 until bufferLen / 4) {
                                            val read = buffer.getInt().toUInt()
                                            val expected = hash_uint32(next + i.toUInt())
                                            if (read != expected) {
                                                errorMessage =
                                                    "Word value mismatch at ${total / bufferLen.toUInt()}[$i]! Got $read expected $expected."
                                                state = FtTesterStates.Error
                                                this@mainScope.cancel()
                                                return@launch
                                            }
                                        }
                                        next += (bufferLen / 4).toUInt()
                                        bytesRead += bufferLen
                                        val elapsed = boardStartTime.elapsedNow().toDouble(DurationUnit.SECONDS)
                                        val bytes = bytesRead * 2 // * 2 for read and write
                                        dataRate = bytes / elapsed / 1000000
                                    }
                                    contexts[index] = board.asyncReadData(bufferLen)
                                }
                            }
                        }
                    }
                } catch (_: CancellationException) {
                }
            }

            val hadError = state == FtTesterStates.Error

            if (hadError && !board.isConnected()) {
                delay(3.seconds)
            }

            state = FtTesterStates.Disconnect
            while (board.isConnected()) {
                delay(100.milliseconds)
            }

            totalBoards += 1
            if (hadError) {
                failures += 1
            }
            val successfulBoards = totalBoards - failures
            val totalTime = testStartTime.elapsedNow() / successfulBoards
            boardsPerHour = 1.0 / totalTime.toDouble(DurationUnit.HOURS)
        }
    }
}