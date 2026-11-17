package com.dre.lab1.monitor

import androidx.lifecycle.ViewModelStore
import com.dre.lab1.FakeActuator
import com.dre.lab1.helpers.*
import com.dre.lab1.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActuatorMonitorTest {
    private val dispatcher = StandardTestDispatcher()
    private val readings = MutableSharedFlow<Acceleration>()
    private val remote = MutableSharedFlow<RemoteUpdate>()
    private val actuator = FakeActuator()
    private lateinit var vm: MonitorViewModel
    private lateinit var connector: MockBleConnector
    private val store = ViewModelStore()
    private var remoteSubscriptions = 0
    private var failRemote = false

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        connector = MockBleConnector()
        val repository = object : SensorRepository {
            override val deviceId = "device"
            override val status = MutableStateFlow(UploadStatus())
            override fun submit(data: SensorData) = Unit
        }
        vm = MonitorViewModel(SensorReader { readings }, connector, repository, actuator,
            RemoteCommandRepository {
                flow {
                    remoteSubscriptions++
                    try {
                        if (failRemote) error("Permission denied")
                        emitAll(remote)
                    } finally { remoteSubscriptions-- }
                }
            })
        store.put("monitor", vm)
    }

    @After fun tearDown() {
        store.clear()
        connector.close()
        Dispatchers.resetMain()
    }

    private fun monitorTest(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try { block() } finally {
            store.clear()
            runCurrent()
        }
    }

    @Test fun autoTriggersOnceLatchesAndRequiresLowAfterStop() = monitorTest {
        vm.onAction(MonitorAction.Start)
        vm.onAction(MonitorAction.Start)
        runCurrent()
        readings.emit(Acceleration(12f, 0f, 0f)); runCurrent()
        readings.emit(Acceleration(20f, 0f, 0f)); runCurrent()
        readings.emit(Acceleration(0f, 0f, 9.81f)); runCurrent()
        assertEquals(listOf(ActuatorCommand(true, true, true)), actuator.commands)
        assertTrue(vm.state.value.alarmActive)
        vm.onAction(MonitorAction.StopAlarm)
        readings.emit(Acceleration(20f, 0f, 0f)); runCurrent()
        assertFalse(vm.state.value.alarmActive)
        readings.emit(Acceleration(10.49f, 0f, 0f)); runCurrent()
        assertTrue(vm.state.value.autoArmed)
        readings.emit(Acceleration(12f, 0f, 0f)); runCurrent()
        assertTrue(vm.state.value.alarmActive)
        vm.onAction(MonitorAction.Stop)
        runCurrent()
        assertEquals(ActuatorCommand(), actuator.commands.last())
        assertEquals(0, readings.subscriptionCount.value)
    }

    @Test fun remoteStopReturnsToAutoAndRequiresANewMovement() = monitorTest {
        vm.onAction(MonitorAction.Start); runCurrent()
        readings.emit(Acceleration(12f, 0f, 0f)); runCurrent()
        assertTrue(vm.state.value.alarmActive)
        vm.onAction(MonitorAction.SelectMode(ControlMode.REMOTE)); runCurrent()
        assertEquals(ActuatorCommand(), actuator.commands.last())
        assertFalse(vm.state.value.alarmActive)
        assertEquals(1, remoteSubscriptions)
        readings.emit(Acceleration(30f, 0f, 0f)); runCurrent()
        assertEquals(ActuatorCommand(), actuator.commands.last())
        remote.emit(RemoteUpdate(ActuatorCommand(vibration = true), connected = true)); runCurrent()
        assertEquals(ActuatorCommand(vibration = true), actuator.commands.last())
        vm.onAction(MonitorAction.StopAlarm); runCurrent()
        assertEquals(ControlMode.AUTO, vm.state.value.mode)
        assertFalse(vm.state.value.autoArmed)
        assertEquals(0, remoteSubscriptions)
        remote.emit(RemoteUpdate(ActuatorCommand(true, true, true), connected = true)); runCurrent()
        readings.emit(Acceleration(12f, 0f, 0f)); runCurrent()
        assertEquals(ActuatorCommand(), actuator.commands.last())
        assertFalse(vm.state.value.alarmActive)
        readings.emit(Acceleration(10.5f, 0f, 0f)); runCurrent()
        assertFalse(vm.state.value.autoArmed)
        readings.emit(Acceleration(9.81f, 0f, 0f)); runCurrent()
        assertTrue(vm.state.value.autoArmed)
        readings.emit(Acceleration(11.999f, 0f, 0f)); runCurrent()
        assertFalse(vm.state.value.alarmActive)
        readings.emit(Acceleration(12f, 0f, 0f)); runCurrent()
        assertTrue(vm.state.value.alarmActive)
        assertEquals(ActuatorCommand(true, true, true), actuator.commands.last())
    }

    @Test fun remotePauseResumeAndDisconnectTurnOffAndDoNotDuplicateSubscriptions() = monitorTest {
        vm.onAction(MonitorAction.SelectMode(ControlMode.REMOTE))
        assertEquals(0, remoteSubscriptions)
        vm.onAction(MonitorAction.Start)
        vm.onAction(MonitorAction.Start); runCurrent()
        assertEquals(1, remoteSubscriptions)
        remote.emit(RemoteUpdate(ActuatorCommand(true, true, true), true)); runCurrent()
        assertTrue(vm.state.value.actuators.siren)
        remote.emit(RemoteUpdate(ActuatorCommand(true, true, true), false)); runCurrent()
        assertEquals(ActuatorCommand(), actuator.commands.last())
        remote.emit(RemoteUpdate(ActuatorCommand(true, true, true), true)); runCurrent()
        assertTrue(vm.state.value.actuators.flashlight)
        remote.emit(RemoteUpdate(ActuatorCommand(true, true, true), true, error = "Invalid payload")); runCurrent()
        assertEquals(ActuatorCommand(), actuator.commands.last())
        vm.onAction(MonitorAction.Stop); runCurrent()
        assertEquals(0, remoteSubscriptions)
        vm.onAction(MonitorAction.CameraPermissionResult(true)); runCurrent()
        assertEquals(ActuatorCommand(), actuator.commands.last())
        vm.onAction(MonitorAction.Start); runCurrent()
        assertEquals(1, remoteSubscriptions)
        store.clear(); runCurrent()
        assertEquals(0, remoteSubscriptions)
        assertTrue(actuator.released)
    }

    @Test fun remoteFailureAndActuatorFailureAreVisibleAndOff() = monitorTest {
        failRemote = true
        vm.onAction(MonitorAction.Start)
        vm.onAction(MonitorAction.SelectMode(ControlMode.REMOTE)); runCurrent()
        assertEquals("Permission denied", vm.state.value.remote.error)
        assertEquals(ActuatorCommand(), actuator.commands.last())
        actuator.failure = "Camera unavailable"
        vm.onAction(MonitorAction.SelectMode(ControlMode.AUTO)); runCurrent()
        readings.emit(Acceleration(12f, 0f, 0f)); runCurrent()
        assertFalse(vm.state.value.actuators.flashlight)
        assertEquals("Camera unavailable", vm.state.value.actuators.error)
    }

    @Test fun changingSourceResetsAutoAndModeSwitchCancelsPendingRemoteSubscription() = monitorTest {
        vm.onAction(MonitorAction.Start); runCurrent()
        readings.emit(Acceleration(12f, 0f, 0f)); runCurrent()
        vm.onAction(MonitorAction.SelectSource(SensorSource.BLE)); runCurrent()
        assertFalse(vm.state.value.alarmActive)
        assertEquals(ActuatorCommand(), actuator.commands.last())
        vm.onAction(MonitorAction.SelectMode(ControlMode.REMOTE))
        vm.onAction(MonitorAction.SelectMode(ControlMode.AUTO)); runCurrent()
        assertEquals(0, remoteSubscriptions)
        remote.emit(RemoteUpdate(ActuatorCommand(true, true, true), true)); runCurrent()
        assertEquals(ActuatorCommand(), actuator.commands.last())
    }
}
