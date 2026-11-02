package com.dre.lab1.ble

import androidx.lifecycle.ViewModelStore
import com.dre.lab1.helpers.*
import com.dre.lab1.monitor.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BleTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun discoveryTakesTwoSecondsAndCanBeCancelled() = runTest(dispatcher) {
        val scanner = MockBleScanner()
        var found = emptyList<BleDevice>()
        val cancelled = launch { found = scanner.scan() }
        advanceTimeBy(1_999)
        assertTrue(found.isEmpty())
        cancelled.cancel()
        advanceUntilIdle()
        assertTrue(found.isEmpty())
        launch { found = scanner.scan() }
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(listOf(BleProfile.device), found)
    }

    @Test
    fun notificationsRequireConnectionAndActiveLifecycleAndRestartOnReconnect() = runTest(dispatcher) {
        val connector = MockBleConnector()
        val messages = mutableListOf<BleMessage>()
        backgroundScope.launch { connector.messages.collect { messages += it } }
        try {
            connector.setActive(true)
            advanceTimeBy(500)
            assertNull(connector.readings.value)
            connector.connect()
            connector.connect()
            runCurrent()
            advanceTimeBy(99)
            assertNull(connector.readings.value)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(Acceleration(0f, 0f, 9.81f), connector.readings.value)
            advanceTimeBy(100)
            runCurrent()
            val second = connector.readings.value!!
            assertTrue(second.x > 0)
            assertTrue(second.y > 0)
            assertEquals(2, messages.count { it.direction == BleDirection.RX })
            connector.setActive(false)
            assertNull(connector.readings.value)
            val pausedCount = messages.size
            advanceTimeBy(500)
            runCurrent()
            assertEquals(pausedCount, messages.size)
            assertTrue(connector.connected.value)
            connector.setActive(true)
            connector.setActive(true)
            runCurrent()
            advanceTimeBy(100)
            runCurrent()
            assertEquals(3, messages.count { it.direction == BleDirection.RX })
            connector.disconnect()
            connector.disconnect()
            runCurrent()
            val disconnectedCount = messages.size
            advanceTimeBy(500)
            runCurrent()
            assertNull(connector.readings.value)
            assertEquals(disconnectedCount, messages.size)
            connector.connect()
            runCurrent()
            advanceTimeBy(100)
            runCurrent()
            assertEquals(Acceleration(0f, 0f, 9.81f), connector.readings.value)
        } finally { connector.close() }
        advanceTimeBy(500)
        assertFalse(connector.connected.value)
        assertNull(connector.readings.value)
    }

    @Test
    fun bleStateTracksReadingsCapsTerminalAndCancelsScan() = runTest(dispatcher) {
        val connector = MockBleConnector()
        val logged = mutableListOf<BleMessage>()
        val vm = BleViewModel(connector = connector, logger = BleLogger { logged += it })
        val store = ViewModelStore().apply { put("ble", vm) }
        try {
            vm.onAction(BleAction.Scan)
            vm.onAction(BleAction.Scan)
            advanceTimeBy(1000)
            vm.onAction(BleAction.Stop)
            advanceUntilIdle()
            assertTrue(vm.state.value.devices.isEmpty())
            assertFalse(vm.state.value.isScanning)
            vm.onAction(BleAction.Connect("unknown"))
            runCurrent()
            assertFalse(vm.state.value.isConnected)
            vm.onAction(BleAction.Scan)
            advanceUntilIdle()
            assertEquals(listOf(BleProfile.device), vm.state.value.devices)
            vm.onAction(BleAction.Start)
            vm.onAction(BleAction.Connect(BleProfile.device.id))
            runCurrent()
            vm.onAction(BleAction.Scan)
            assertFalse(vm.state.value.isScanning)
            advanceTimeBy(21_000)
            runCurrent()
            assertTrue(vm.state.value.isConnected)
            assertNotNull(vm.state.value.reading)
            assertEquals(200, vm.state.value.terminal.size)
            assertEquals(logged.takeLast(200), vm.state.value.terminal)
            vm.onAction(BleAction.Pause)
            runCurrent()
            assertNull(vm.state.value.reading)
            assertTrue(vm.state.value.isConnected)
            vm.onAction(BleAction.Stop)
            runCurrent()
            assertFalse(vm.state.value.isConnected)
        } finally { store.clear(); connector.close() }
    }

    @Test
    fun monitorUsesOnlySelectedSourceAndCancelsPhoneCollection() = runTest(dispatcher) {
        val connector = MockBleConnector()
        val phoneValues = MutableSharedFlow<Acceleration>()
        var subscriptions = 0
        val phone = SensorReader {
            flow {
                subscriptions++
                try { emitAll(phoneValues) } finally { subscriptions-- }
            }
        }
        val vm = MonitorViewModel(phone, connector, testRepository)
        val store = ViewModelStore().apply { put("monitor", vm) }
        val phoneReading = Acceleration(1f, 2f, 3f)
        try {
            vm.onAction(MonitorAction.Start)
            vm.onAction(MonitorAction.Start)
            runCurrent()
            assertEquals(1, subscriptions)
            phoneValues.emit(phoneReading)
            runCurrent()
            assertEquals(phoneReading, vm.state.value.reading)
            vm.onAction(MonitorAction.SelectSource(SensorSource.BLE))
            runCurrent()
            assertEquals(0, subscriptions)
            assertNull(vm.state.value.reading)
            assertFalse(vm.state.value.isRunning)
            connector.setActive(true)
            connector.connect()
            runCurrent()
            advanceTimeBy(100)
            runCurrent()
            assertEquals(connector.readings.value, vm.state.value.reading)
            phoneValues.emit(phoneReading)
            runCurrent()
            assertNotEquals(phoneReading, vm.state.value.reading)
            connector.disconnect()
            runCurrent()
            assertNull(vm.state.value.reading)
            vm.onAction(MonitorAction.SelectSource(SensorSource.PHONE))
            runCurrent()
            assertEquals(1, subscriptions)
            phoneValues.emit(phoneReading)
            runCurrent()
            assertEquals(phoneReading, vm.state.value.reading)
            vm.onAction(MonitorAction.Stop)
            runCurrent()
            assertEquals(0, subscriptions)
            assertNull(vm.state.value.reading)
            vm.onAction(MonitorAction.Start)
            vm.onAction(MonitorAction.Start)
            runCurrent()
            assertEquals(1, subscriptions)
        } finally { store.clear(); connector.close() }
        runCurrent()
        assertEquals(0, subscriptions)
    }

    @Test
    fun sensorFailureIsDurableStateAndTransientEvent() = runTest(dispatcher) {
        val connector = MockBleConnector()
        val vm = MonitorViewModel(SensorReader { flow { error("Sensor unavailable") } }, connector, testRepository)
        val store = ViewModelStore().apply { put("monitor", vm) }
        val events = mutableListOf<MonitorEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }
        try {
            vm.onAction(MonitorAction.Start)
            runCurrent()
            assertFalse(vm.state.value.isRunning)
            assertNull(vm.state.value.reading)
            assertEquals("Sensor unavailable", vm.state.value.status)
            assertEquals(listOf(MonitorEvent.ShowMessage("Sensor unavailable")), events)
        } finally { store.clear(); connector.close() }
    }
}

private val testRepository = object : com.dre.lab1.repository.SensorRepository {
    override val deviceId = "test"
    override val status = kotlinx.coroutines.flow.MutableStateFlow(com.dre.lab1.repository.UploadStatus())
    override fun submit(data: com.dre.lab1.repository.SensorData) = Unit
}
