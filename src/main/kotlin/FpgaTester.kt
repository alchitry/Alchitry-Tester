package com.alchitry.tester

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.alchitry.hardware.Board
import com.alchitry.hardware.usb.BoardLoader
import com.alchitry.hardware.usb.SerialDevice
import com.alchitry.hardware.usb.UsbUtil
import com.alchitry.hardware.usb.ftdi.AU_EEPROM_DATA
import com.alchitry.hardware.usb.ftdi.CU_EEPROM_DATA
import com.alchitry.hardware.usb.ftdi.Ftdi
import com.alchitry.hardware.usb.ftdi.PT_EEPROM_DATA
import com.alchitry.labs2.ui.theme.AlchitryColors
import kotlinx.coroutines.*
import kotlin.experimental.and
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

class FpgaTester(val board: Board) {
    private enum class State {
        InitialConnect,
        ProgramEeprom,
        DisconnectEeprom,
        ConnectToProgram,
        ProgrammingTest,
        ShortTest,
        WaitConnectionTest,
        ConnectionTest,
        ProgrammingDemo,
        DisconnectFinal
    }

    private enum class ActionState {
        Pending,
        Running,
        Success,
        Failure
    }

    private var state by mutableStateOf(State.InitialConnect)
    private var errorMessage by mutableStateOf<String?>(null)
    private var totalBoards by mutableStateOf(0)
    private var failures by mutableStateOf(0)
    private var boardsPerHour by mutableStateOf(0.0)

    private var eepromProgrammed by mutableStateOf(ActionState.Pending)
    private var testBinProgrammed by mutableStateOf(ActionState.Pending)
    private var shortTest by mutableStateOf(ActionState.Pending)
    private var connectionTest by mutableStateOf(ActionState.Pending)
    private var demoBinProgrammed by mutableStateOf(ActionState.Pending)

    @Composable
    private fun ActionStateIcon(state: ActionState) {
        val modifier = Modifier.size(25.dp)
        when (state) {
            ActionState.Pending -> Icon(
                Icons.Filled.Pending,
                contentDescription = "Pending",
                tint = AlchitryColors.current.Info,
                modifier = modifier
            )

            ActionState.Running -> CircularProgressIndicator(modifier, color = AlchitryColors.current.Accent)
            ActionState.Success -> Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "Success",
                tint = AlchitryColors.current.Success,
                modifier = modifier
            )

            ActionState.Failure -> Icon(
                Icons.Filled.Error,
                contentDescription = "Failure",
                tint = AlchitryColors.current.Error,
                modifier = modifier
            )
        }
    }

    @Composable
    fun statusUI(onBack: () -> Unit) {
        LaunchedEffect(Unit) {
            withContext(Dispatchers.Default) {
                programBoards()
            }
        }
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.padding(10.dp, 5.dp, 10.dp, 10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        painter = painterResource("alchitry_main.svg"),
                        contentDescription = "Logo",
                        contentScale = ContentScale.FillHeight,
                        modifier = Modifier.height(75.dp)
                    )
                    val boardImage = when (board) {
                        Board.AlchitryAu, Board.AlchitryAuPlus, Board.AlchitryAuV2 -> "au"
                        Board.AlchitryCu, Board.AlchitryCuV2 -> "cu"
                        Board.AlchitryPtV2 -> "pt"
                    }
                    Image(
                        painter = painterResource("$boardImage.svg"),
                        contentDescription = "Logo",
                        contentScale = ContentScale.FillHeight,
                        modifier = Modifier.height(75.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))

                CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.headlineMedium) {
                    when (state) {
                        State.InitialConnect -> Text("Connect new board.", color = AlchitryColors.current.Accent)
                        State.ProgramEeprom -> Text("Programming EEPROM...")
                        State.DisconnectEeprom -> Text("Disconnect board.", color = AlchitryColors.current.Accent)
                        State.ConnectToProgram -> Text("Reconnect the board.", color = AlchitryColors.current.Accent)
                        State.ProgrammingTest -> Text("Programming test bin...")
                        State.ShortTest -> Text("Running short test...")
                        State.WaitConnectionTest -> Text(
                            "Attach Qc then press the button.",
                            color = AlchitryColors.current.Accent
                        )

                        State.ConnectionTest -> Text("Running connection test...")
                        State.ProgrammingDemo -> Text("Programming demo bin...")
                        State.DisconnectFinal -> Text("Disconnect board.", color = AlchitryColors.current.Accent)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("EEPROM programmed: ")
                        ActionStateIcon(eepromProgrammed)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Test bin programmed: ")
                        ActionStateIcon(testBinProgrammed)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Short test: ")
                        ActionStateIcon(shortTest)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Connection test: ")
                        ActionStateIcon(connectionTest)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Demo bin programmed: ")
                        ActionStateIcon(demoBinProgrammed)
                    }
                }

                if (state == State.DisconnectFinal) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Result: ", style = MaterialTheme.typography.headlineLarge)
                        val passed = errorMessage == null
                        if (passed) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = "Success",
                                tint = AlchitryColors.current.Success,
                                modifier = Modifier.size(50.dp)
                            )
                            Text(" Passed", style = MaterialTheme.typography.headlineLarge)
                        } else {
                            Icon(
                                Icons.Filled.Error,
                                contentDescription = "Failure",
                                tint = AlchitryColors.current.Error,
                                modifier = Modifier.size(50.dp)
                            )
                            Text(" Failed", style = MaterialTheme.typography.headlineLarge)
                        }
                    }
                }

                errorMessage?.let { error ->
                    Text("Error: $error", color = AlchitryColors.current.Error)
                }

                Spacer(Modifier.weight(1f))
                Text("Stats:")
                Column(Modifier.padding(start = 10.dp)) {
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


    private suspend fun programBoards() = coroutineScope {
        val testStartTime = TimeSource.Monotonic.markNow()

        while (isActive) {
            try {
                errorMessage = null
                eepromProgrammed = ActionState.Pending
                testBinProgrammed = ActionState.Pending
                shortTest = ActionState.Pending
                connectionTest = ActionState.Pending
                demoBinProgrammed = ActionState.Pending
                state = State.InitialConnect
                openFirstOfType(null).use { ftdi ->
                    state = State.ProgramEeprom
                    eepromProgrammed = ActionState.Running
                    try {
                        ftdi.programEEPROM(
                            when (board) {
                                Board.AlchitryAuV2 -> AU_EEPROM_DATA
                                Board.AlchitryCuV2 -> CU_EEPROM_DATA
                                Board.AlchitryPtV2 -> PT_EEPROM_DATA
                                else -> error("Unsupported board type: ${board.name}")
                            }
                        )
                    } catch (e: Exception) {
                        eepromProgrammed = ActionState.Failure
                        throw e
                    }
                    eepromProgrammed = ActionState.Success
                }
                state = State.DisconnectEeprom
                waitForDisconnect()
                state = State.ConnectToProgram
                waitForDevice(board)
                val boardPrefix = when (board) {
                    Board.AlchitryAuV2 -> "au_v2"
                    Board.AlchitryCuV2 -> "cu_v2"
                    Board.AlchitryPtV2 -> "pt_v2"
                    else -> error("Unsupported board: ${board.name}")
                }

                state = State.ProgrammingTest
                testBinProgrammed = ActionState.Running
                try {
                    if (!BoardLoader.load(
                            board,
                            0,
                            loadResourceBin("alchitry_${boardPrefix}_test.bin"),
                            !board.supportsRamLoading
                        )
                    ) {
                        error("Failed to find board!")
                    }
                    delay(500.milliseconds)
                } catch (e: Exception) {
                    testBinProgrammed = ActionState.Failure
                    throw e
                }
                testBinProgrammed = ActionState.Success

                state = State.ShortTest
                shortTest = ActionState.Running

                UsbUtil.openSerial(board, 0).use { device ->
                    if (device == null) {
                        error("Failed to open serial device!")
                    }
                    device.setTimeouts(1000, 1000)
                    device.setBaudrate(1000000)

                    delay(1.seconds)

                    val shortStatus = device.getDeviceStatus()
                    if (shortStatus.mode == DeviceMode.ShortTest) {
                        if (shortStatus.errors != 0) {
                            shortTest = ActionState.Failure
                            error("Errors detected during short test: $shortStatus")
                        }
                    } else {
                        shortTest = ActionState.Failure
                        error("Device is not in short test mode!")
                    }

                    shortTest = ActionState.Success

                    state = State.WaitConnectionTest

                    while (device.getDeviceStatus().mode != DeviceMode.ConnectionTest) {
                        delay(100.milliseconds)
                    }

                    state = State.ConnectionTest
                    connectionTest = ActionState.Running

                    delay(1.seconds)

                    val connectionStatus = device.getDeviceStatus()
                    if (connectionStatus.mode == DeviceMode.ConnectionTest) {
                        if (connectionStatus.errors != 0) {
                            connectionTest = ActionState.Failure
                            error("Errors detected during connection test: $shortStatus")
                        }
                    } else {
                        connectionTest = ActionState.Failure
                        error("Device is not in connection test mode!")
                    }
                }

                connectionTest = ActionState.Success

                state = State.ProgrammingDemo
                demoBinProgrammed = ActionState.Running
                try {
                    if (!BoardLoader.load(board, 0, loadResourceBin("alchitry_${boardPrefix}_wave.bin"), true)) {
                        error("Failed to find board!")
                    }
                } catch (e: Exception) {
                    demoBinProgrammed = ActionState.Failure
                    throw e
                }
                demoBinProgrammed = ActionState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.message
            }

            val hadError = errorMessage != null

            state = State.DisconnectFinal

            if (hadError && UsbUtil.detectAttachedBoards().isEmpty())
                delay(3.seconds)

            waitForDisconnect()

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

private suspend fun waitForDevice(type: Board) {
    while (!UsbUtil.detectAttachedBoards().contains(type)) {
        delay(100.milliseconds)
    }
}

private suspend fun openFirstOfType(type: Board?): Ftdi {
    while (true) {
        delay(100.milliseconds)
        return UsbUtil.detectAttachedBoards().firstNotNullOfOrNull { (board, _) ->
            if (type == null || board == type)
                UsbUtil.openFtdiDevice(board, 0)
            else null
        } ?: continue
    }
}

private suspend fun waitForDisconnect() {
    while (UsbUtil.detectAttachedBoards().isNotEmpty()) {
        delay(100.milliseconds)
    }
}

private fun loadResourceBin(name: String): ByteArray {
    val classLoader = Thread.currentThread().contextClassLoader
    classLoader.getResourceAsStream("bins/${name}")?.use { inputStream ->
        return inputStream.readAllBytes()
    } ?: throw IllegalArgumentException("Resource not found: bins/${name}")
}

private enum class DeviceMode {
    ShortTest,
    ConnectionTest
}

private data class DeviceStatus(val mode: DeviceMode, val errors: Int, val errorPinCount: Int, val errorIndex: Int)

private fun SerialDevice.getDeviceStatus(): DeviceStatus {
    flushReadBuffer()
    val buffer = ByteArray(5)
    buffer[0] = 0x42.toByte()
    check(writeData(buffer) == buffer.size) { "Failed to send read request!" }
    val readBuffer = ByteArray(12)
    val bytesRead = readDataWithTimeout(readBuffer)
    check(bytesRead == readBuffer.size) {
        if (readBuffer.isEmpty())
            "Reading ${readBuffer.size} bytes took too long!"
        else
            "Read $bytesRead but expected ${readBuffer.size} bytes!"
    }
    val mode =
        if (readBuffer[0] and 0x80.toByte() != 0.toByte()) DeviceMode.ConnectionTest else DeviceMode.ShortTest
    val errors = readBuffer[0] and 0x7F.toByte()
    return DeviceStatus(mode, errors.toInt(), readBuffer[8].toUByte().toInt(), readBuffer[4].toUByte().toInt())
}