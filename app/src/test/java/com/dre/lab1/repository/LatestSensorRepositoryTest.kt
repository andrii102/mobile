package com.dre.lab1.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LatestSensorRepositoryTest {
    private fun sample(x: Float) = SensorData("device", 123L, "BLE", x, 2f, 3f)

    @Test fun offlineRetainsOnlyLatestAndReconnects() = runTest {
        val online = MutableStateFlow(false)
        val saved = mutableListOf<SensorData>()
        val repository = LatestSensorRepository("device", online, backgroundScope) { _, data -> saved.add(data) }
        runCurrent()
        repository.submit(sample(1f))
        repository.submit(sample(2f))
        runCurrent()
        assertTrue(saved.isEmpty())
        assertEquals(UploadPhase.Pending, repository.status.value.phase)
        online.value = true
        runCurrent()
        assertEquals(listOf(sample(2f)), saved)
        assertEquals(UploadPhase.Synced, repository.status.value.phase)
    }

    @Test fun slowWriteDoesNotLoseNewerPendingReading() = runTest {
        val gate = CompletableDeferred<Unit>()
        val saved = mutableListOf<SensorData>()
        val repository = LatestSensorRepository("device", MutableStateFlow(true), backgroundScope) { _, data ->
            gate.await()
            saved.add(data)
        }
        runCurrent()
        repository.submit(sample(1f))
        runCurrent()
        repository.submit(sample(2f))
        repository.submit(sample(3f))
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(sample(1f), sample(3f)), saved)
    }

    @Test fun failuresDoNotRetryOnEverySample() = runTest {
        var calls = 0
        val repository = LatestSensorRepository("device", MutableStateFlow(true), backgroundScope) { _, _ ->
            calls++
            error("Permission denied")
        }
        runCurrent()
        repository.submit(sample(1f))
        runCurrent()
        repository.submit(sample(2f))
        runCurrent()
        assertEquals(1, calls)
        assertEquals(UploadPhase.Error, repository.status.value.phase)
    }
}
