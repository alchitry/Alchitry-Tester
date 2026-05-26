package com.alchitry.tester

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.*
import com.alchitry.hardware.AlchitryFt
import com.alchitry.hardware.FtType
import com.alchitry.hardware.usb.ftdi.D3xx
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

private const val BUFFER_LEN = 1024 * 1024
private const val BUFFER_COUNT = 2
private const val WRITE_COUNT = 1000

class FtTester(val isPlus: Boolean) {
    enum class FtTesterStates {
        Searching,
        Testing,
        Error,
        Disconnect
    }

    var state by mutableStateOf(FtTesterStates.Searching)
    var superSpeed by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var totalBoards by mutableStateOf(0)
    var failures by mutableStateOf(0)

    var boardsPerHour by mutableStateOf(0.0)
    var dataRate by mutableStateOf(0.0)

    @Composable
    fun FtTesterView() {
        LaunchedEffect(Unit) {
            runTests()
        }
        Column {
            Text(state.name)
            Text("Error: $errorMessage")
            Text("SuperSpeed: $superSpeed")
            Text("Total Boards: $totalBoards")
            Text("Testing Rate: $boardsPerHour")
            Text("Data Rate (MBPS): $dataRate")
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
            val board = waitForBoard()
            state = FtTesterStates.Testing
            errorMessage = null

            val boardStartTime = TimeSource.Monotonic.markNow()
            try {
                coroutineScope mainScope@{
                    launch(Dispatchers.IO) { // write loop
                        var next = 0u
                        val contexts = MutableList<D3xx.DeviceConnection.OverlappedContext?>(BUFFER_COUNT) { null }
                        for (k in 0 until WRITE_COUNT/BUFFER_COUNT) {
                            contexts.forEachIndexed { index, context ->
                                ensureActive()
                                if (context != null) {
                                    val (bytes, status) = context.getResult(true)
                                    if (bytes != BUFFER_LEN || status != D3xx.FtStatus.OK) {
                                        errorMessage = "Failed to write full buffer! Status: ${status.name}"
                                        state = FtTesterStates.Error
                                        this@mainScope.cancel()
                                        return@launch
                                    }
                                }
                                val data = ByteArray(BUFFER_LEN)
                                val buffer = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder())
                                for (i in 0 until BUFFER_LEN / 4) {
                                    buffer.putInt(hash_uint32(i.toUInt() + next).toInt())
                                }
                                next += (BUFFER_LEN / 4).toUInt()
                                contexts[index] = board.asyncWriteData(data)
                            }
                        }
                    }
                    launch(Dispatchers.IO) { // read loop
                        var total = 0u
                        var next = 0u
                        val contexts = MutableList<D3xx.DeviceConnection.OverlappedContext?>(BUFFER_COUNT) { null }
                        for (k in 0 until WRITE_COUNT/BUFFER_COUNT) {
                            contexts.forEachIndexed { index, context ->
                                ensureActive()
                                if (context != null) {
                                    val (data, status) = context.getResultBytes(true)
                                    if (data.size != BUFFER_LEN || status != D3xx.FtStatus.OK) {
                                        errorMessage = "Failed to read full buffer! Status: ${status.name}"
                                        state = FtTesterStates.Error
                                        this@mainScope.cancel()
                                        return@launch
                                    }
                                    val buffer = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder())
                                    for (i in 0 until BUFFER_LEN / 4) {
                                        val read = buffer.getInt().toUInt()
                                        val expected = hash_uint32(next + i.toUInt())
                                        if (read != expected) {
                                            errorMessage =
                                                "Word value mismatch at ${total / BUFFER_LEN.toUInt()}[$i]! Got $read expected $expected."
                                            state = FtTesterStates.Error
                                            this@mainScope.cancel()
                                            return@launch
                                        }
                                    }
                                    next += (BUFFER_LEN / 4).toUInt()
                                }
                                contexts[index] = board.asyncReadData(BUFFER_LEN)
                            }
                        }
                    }
                }
            } catch (_: CancellationException) {

            }
            if (state == FtTesterStates.Error) {
                failures += 1
            } else {
                val elapsed = boardStartTime.elapsedNow().toDouble(DurationUnit.SECONDS)
                val bytes = WRITE_COUNT * BUFFER_LEN * 2 // * 2 for read and write
                dataRate = bytes / elapsed / 1000000
            }
            state = FtTesterStates.Disconnect
            while (board.isConnected()) {
                delay(100.milliseconds)
            }
            totalBoards += 1
            val successfulBoards = totalBoards - failures
            val totalTime = testStartTime.elapsedNow() / successfulBoards
            boardsPerHour = 1.0 / totalTime.toDouble(DurationUnit.HOURS)
        }
    }
}