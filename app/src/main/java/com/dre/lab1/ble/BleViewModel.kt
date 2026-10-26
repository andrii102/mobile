package com.dre.lab1.ble

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dre.lab1.helpers.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BleViewModel(
    private val scanner: MockBleScanner = MockBleScanner(),
    private val connector: IBleConnector,
    private val logger: BleLogger = LogcatBleLogger()
) : ViewModel() {
    private val _state = MutableStateFlow(BleState())
    val state = _state.asStateFlow()
    private val _events = Channel<BleEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            connector.connected.collect { connected ->
                _state.update { it.copy(isConnected = connected) }
            }
        }
        viewModelScope.launch {
            connector.messages.collect { message ->
                record(message)
            }
        }
        viewModelScope.launch {
            connector.readings.collect { reading ->
                _state.update { it.copy(reading = reading) }
            }
        }
    }

    fun onAction(action: BleAction) {
        when (action) {
            BleAction.Scan -> scan()
            is BleAction.Connect -> {
                if (!_state.value.isScanning && _state.value.devices.any { it.id == action.deviceId }) {
                    operate { connector.connect() }
                }
            }
            BleAction.Disconnect -> operate { connector.disconnect() }
            BleAction.Start -> connector.setActive(true)
            BleAction.Pause -> connector.setActive(false)
            BleAction.Stop -> {
                connector.setActive(false)
                scanJob?.cancel()
                scanJob = null
                if (_state.value.isScanning) recordSystem("Сканування скасовано")
                _state.update { it.copy(isScanning = false) }
                operate { connector.disconnect() }
            }
        }
    }

    private fun scan() {
        if (_state.value.isScanning || connector.connected.value) return
        _state.update { it.copy(isScanning = true, devices = emptyList()) }
        recordSystem("Пошук пристроїв…")
        scanJob = viewModelScope.launch {
            try {
                val devices = scanner.scan()
                _state.update { it.copy(devices = devices, isScanning = false) }
                recordSystem("Знайдено пристроїв: ${devices.size}")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(isScanning = false) }
                reportError(error)
            }
        }
    }

    private fun operate(operation: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                operation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                reportError(error)
            }
        }
    }

    private suspend fun reportError(error: Exception) {
        val text = "Помилка BLE: ${error.message ?: "невідома помилка"}"
        recordSystem(text)
        _events.send(BleEvent.ShowMessage(text))
    }

    private fun recordSystem(text: String) = record(BleMessage(BleDirection.SYSTEM, text))

    private fun record(message: BleMessage) {
        logger.log(message)
        _state.update { it.copy(terminal = (it.terminal + message).takeLast(200)) }
    }

    override fun onCleared() {
        scanJob?.cancel()
        _events.close()
        super.onCleared()
    }
}
