package com.dre.lab1.helpers

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.math.sin

/** In-memory notifications, confined to the main dispatcher. No Bluetooth API calls. */
class MockBleConnector : IBleConnector {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val connection = MutableStateFlow(false)
    override val connected = connection.asStateFlow()
    private val samples = MutableStateFlow<Acceleration?>(null)
    override val readings = samples.asStateFlow()
    private val mailbox = Channel<BleMessage>(Channel.BUFFERED)
    override val messages = mailbox.receiveAsFlow()
    private var active = false
    private var closed = false
    private var sampleIndex = 0L
    private var streamJob: Job? = null

    override suspend fun connect() {
        check(!closed) { "BLE session is closed" }
        if (connection.value) return
        sampleIndex = 0
        connection.value = true
        emit(BleDirection.SYSTEM, "Connected: ${BleProfile.device.name}")
        startStream()
    }

    override suspend fun disconnect() {
        if (!connection.value) return
        connection.value = false
        stopStream()
        emit(BleDirection.SYSTEM, "Disconnected")
    }

    override fun setActive(active: Boolean) {
        this.active = active
        if (active) startStream() else stopStream()
    }

    private fun startStream() {
        if (closed || !active || !connection.value || streamJob != null) return
        streamJob = scope.launch {
            while (isActive) {
                delay(100)
                val phase = sampleIndex++ * 0.1
                val value = Acceleration(
                    (4 * sin(phase)).toFloat(),
                    (3 * sin(phase * 0.7)).toFloat(),
                    (9.81 + 0.2 * sin(phase * 0.5)).toFloat()
                )
                samples.value = value
                emit(BleDirection.RX, "ACCELERATION: x=${value.x}, y=${value.y}, z=${value.z} m/s^2")
            }
        }
    }

    private fun stopStream() {
        streamJob?.cancel()
        streamJob = null
        samples.value = null
    }

    private fun emit(direction: BleDirection, text: String) {
        mailbox.trySend(BleMessage(direction, text))
    }

    override fun close() {
        closed = true
        connection.value = false
        stopStream()
        scope.cancel()
        mailbox.close()
    }
}
