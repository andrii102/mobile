package com.dre.lab1.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dre.lab1.helpers.IBleConnector
import com.dre.lab1.helpers.SensorReader
import com.dre.lab1.helpers.Actuator
import com.dre.lab1.helpers.ActuatorCommand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.dre.lab1.repository.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MonitorViewModel(
    private val phone: SensorReader,
    private val ble: IBleConnector,
    private val repository: SensorRepository,
    private val actuator: Actuator,
    private val remoteRepository: RemoteCommandRepository
) : ViewModel() {
    private val _state = MutableStateFlow(MonitorState())
    val state = _state.asStateFlow()
    private val _events = Channel<MonitorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private var collectionJob: Job? = null
    private var sensorGeneration = 0
    private var active = false
    private var samplingJob: Job? = null
    private var latest: SensorData? = null
    private val engine = DecisionEngine()
    private var desired = ActuatorCommand()
    private var remoteJob: Job? = null
    private var remoteGeneration = 0

    init {
        viewModelScope.launch {
            var previousError: String? = null
            actuator.state.collect { value ->
                _state.update { it.copy(actuators = value) }
                if (value.error != null && value.error != previousError) {
                    _events.trySend(MonitorEvent.ShowMessage(value.error))
                }
                previousError = value.error
            }
        }
        viewModelScope.launch {
            var previousMessage: String? = null
            repository.status.collect { status ->
                _state.update { it.copy(uploadStatus = status) }
                if (status.message != null && status.message != previousMessage) {
                    _events.send(MonitorEvent.ShowMessage(status.message))
                }
                previousMessage = status.message
            }
        }
    }

    fun onAction(action: MonitorAction) {
        when (action) {
            MonitorAction.Start -> {
                if (active) return
                active = true
                actuator.refreshCapabilities()
                _state.update { it.copy(active = true) }
                start()
                startRemote()
            }
            MonitorAction.Stop -> {
                active = false
                stop()
                stopRemote()
                engine.reset()
                applyActuators(ActuatorCommand())
                _state.update { it.copy(status = "Призупинено", active = false,
                    alarmActive = false, autoArmed = true) }
            }
            is MonitorAction.SelectSource -> {
                if (action.source == _state.value.source) return
                stop()
                engine.reset()
                if (_state.value.mode == ControlMode.AUTO) applyActuators(ActuatorCommand())
                _state.update { it.copy(source = action.source, alarmActive = false, autoArmed = true) }
                if (active) start()
            }
            is MonitorAction.SelectMode -> {
                if (_state.value.mode == action.mode) return
                stopRemote()
                applyActuators(ActuatorCommand())
                engine.reset()
                _state.update { it.copy(mode = action.mode, alarmActive = false, autoArmed = true) }
                startRemote()
            }
            MonitorAction.StopAlarm -> {
                if (_state.value.mode == ControlMode.REMOTE) {
                    // Stop receiving remote commands without changing the value stored in Firebase.
                    stopRemote()
                    _state.update { it.copy(mode = ControlMode.AUTO) }
                }
                engine.stopAlarm()
                applyActuators(ActuatorCommand())
                _state.update { it.copy(alarmActive = false, autoArmed = false) }
            }
            MonitorAction.RequestCameraPermission -> {
                if (active) _state.update { it.copy(permissionRequestPending = true) }
            }
            is MonitorAction.CameraPermissionResult -> {
                actuator.refreshCapabilities()
                _state.update { it.copy(permissionRequestPending = false) }
                if (!action.granted) _events.trySend(MonitorEvent.ShowMessage("Дозвіл на камеру не надано"))
                // Permission dialogs can pause the Activity. Only apply current, active intent.
                if (active && action.granted) actuator.apply(desired)
            }
        }
    }

    private fun applyActuators(command: ActuatorCommand) {
        desired = command
        actuator.apply(command)
    }

    private fun stopRemote() {
        // Invalidate queued updates from the previous subscription before cancelling it.
        remoteGeneration++
        remoteJob?.cancel()
        remoteJob = null
        _state.update { it.copy(remote = it.remote.copy(connected = false, command = ActuatorCommand())) }
    }

    private fun startRemote() {
        if (!active || _state.value.mode != ControlMode.REMOTE || remoteJob?.isActive == true) return
        val generation = ++remoteGeneration
        _state.update { it.copy(remote = RemoteUpdate()) }
        remoteJob = viewModelScope.launch {
            try {
                remoteRepository.commands().collect { update ->
                    if (!active || generation != remoteGeneration || _state.value.mode != ControlMode.REMOTE) return@collect
                    _state.update { it.copy(remote = update) }
                    applyActuators(if (update.connected && update.error == null) update.command else ActuatorCommand())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == remoteGeneration && active && _state.value.mode == ControlMode.REMOTE) {
                    applyActuators(ActuatorCommand())
                    val message = error.message ?: "Помилка Firebase-команд"
                    _state.update { it.copy(remote = it.remote.copy(connected = false, error = message)) }
                    _events.trySend(MonitorEvent.ShowMessage(message))
                }
            }
        }
    }

    private fun start() {
        if (collectionJob?.isActive == true) return
        val generation = ++sensorGeneration
        _state.update {
            it.copy(status = if (it.source == SensorSource.PHONE) "Очікування даних телефону"
                else "Підключіть BLE-акселерометр")
        }
        samplingJob = viewModelScope.launch {
            // Upload only the latest reading every two seconds; evaluate alarms on every reading.
            while (isActive) {
                delay(2_000)
                latest?.let(repository::submit)
                latest = null
            }
        }
        collectionJob = viewModelScope.launch {
            try {
                val source = _state.value.source
                val readings = if (source == SensorSource.PHONE) phone.readings() else ble.readings
                readings.collect { reading ->
                    if (!active || generation != sensorGeneration || source != _state.value.source) return@collect
                    if (reading != null && DecisionEngine.magnitude(reading) == null) return@collect
                    if (_state.value.mode == ControlMode.AUTO) {
                        if (reading == null) {
                            engine.reset()
                            applyActuators(ActuatorCommand())
                        } else {
                            val wasActive = engine.alarmActive
                            // Start outputs once per alarm instead of restarting them on each sample.
                            if (engine.accept(reading) && !wasActive) {
                                applyActuators(ActuatorCommand(true, true, true))
                            }
                        }
                    }
                    latest = reading?.let {
                        SensorData(repository.deviceId, System.currentTimeMillis(), source.name, it.x, it.y, it.z)
                    }
                    _state.update {
                        it.copy(
                            reading = reading,
                            magnitude = reading?.let(DecisionEngine::magnitude),
                            alarmActive = engine.alarmActive,
                            autoArmed = engine.armed,
                            isRunning = reading != null,
                            status = if (reading != null) "Отримання даних"
                            else "Підключіть BLE-акселерометр"
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                samplingJob?.cancel()
                latest = null
                if (_state.value.mode == ControlMode.AUTO) {
                    engine.reset()
                    applyActuators(ActuatorCommand())
                }
                val message = error.message ?: "Помилка акселерометра"
                _state.update { it.copy(reading = null, magnitude = null, isRunning = false,
                    alarmActive = engine.alarmActive, autoArmed = engine.armed, status = message) }
                _events.send(MonitorEvent.ShowMessage(message))
            }
        }
    }

    private fun stop() {
        sensorGeneration++
        samplingJob?.cancel()
        samplingJob = null
        latest = null
        collectionJob?.cancel()
        collectionJob = null
        _state.update { it.copy(reading = null, magnitude = null, isRunning = false) }
    }

    override fun onCleared() {
        active = false
        stop()
        stopRemote()
        actuator.release()
        _events.close()
        super.onCleared()
    }
}
