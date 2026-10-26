package com.dre.lab1.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dre.lab1.helpers.IBleConnector
import com.dre.lab1.helpers.SensorReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MonitorViewModel(
    private val phone: SensorReader,
    private val ble: IBleConnector
) : ViewModel() {
    private val _state = MutableStateFlow(MonitorState())
    val state = _state.asStateFlow()
    private val _events = Channel<MonitorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private var collectionJob: Job? = null
    private var active = false

    fun onAction(action: MonitorAction) {
        when (action) {
            MonitorAction.Start -> {
                active = true
                start()
            }
            MonitorAction.Stop -> {
                active = false
                stop()
                _state.update { it.copy(status = "Призупинено") }
            }
            is MonitorAction.SelectSource -> {
                if (action.source == _state.value.source) return
                stop()
                _state.update { it.copy(source = action.source) }
                if (active) start()
            }
        }
    }

    private fun start() {
        if (collectionJob?.isActive == true) return
        _state.update {
            it.copy(status = if (it.source == SensorSource.PHONE) "Очікування даних телефону"
                else "Підключіть BLE-акселерометр")
        }
        collectionJob = viewModelScope.launch {
            try {
                val source = _state.value.source
                val readings = if (source == SensorSource.PHONE) phone.readings() else ble.readings
                readings.collect { reading ->
                    _state.update {
                        it.copy(
                            reading = reading,
                            isRunning = reading != null,
                            status = if (reading != null) "Отримання даних"
                            else "Підключіть BLE-акселерометр"
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = error.message ?: "Помилка акселерометра"
                _state.update { it.copy(reading = null, isRunning = false, status = message) }
                _events.send(MonitorEvent.ShowMessage(message))
            }
        }
    }

    private fun stop() {
        collectionJob?.cancel()
        collectionJob = null
        _state.update { it.copy(reading = null, isRunning = false) }
    }

    override fun onCleared() {
        stop()
        _events.close()
        super.onCleared()
    }
}
