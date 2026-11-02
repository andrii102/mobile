package com.dre.lab1.repository

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

data class SensorData(
    val deviceId: String,
    val timestamp: Long,
    val source: String,
    val x: Float,
    val y: Float,
    val z: Float,
    val sensorType: String = "accelerometer"
)

data class SensorPayload(
    val deviceId: String,
    val timestamp: Long,
    val source: String,
    val x: Float,
    val y: Float,
    val z: Float,
    val sensorType: String
)

fun SensorData.toPayload() = SensorPayload(deviceId, timestamp, source, x, y, z, sensorType)
enum class UploadPhase { Idle, Pending, Uploading, Synced, Error }
data class UploadStatus(val phase: UploadPhase = UploadPhase.Idle, val message: String? = null)

interface SensorRepository {
    val deviceId: String
    val status: StateFlow<UploadStatus>
    fun submit(data: SensorData)
}

/** One in-flight request and one replaceable pending sample. All access uses the main dispatcher. */
class LatestSensorRepository(
    override val deviceId: String,
    online: Flow<Boolean>,
    scope: CoroutineScope,
    private val upload: suspend (String, SensorData) -> Unit
) : SensorRepository {
    private val mutableStatus = MutableStateFlow(UploadStatus())
    override val status = mutableStatus.asStateFlow()
    private var pending: Pair<String, SensorData>? = null
    private var connected = false
    private var failed = false
    private val wake = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            online.distinctUntilChanged().collect {
                connected = it
                if (it) failed = false
                if (!it && pending != null) mutableStatus.value = UploadStatus(UploadPhase.Pending, "No internet. Latest reading is pending.")
                wake.trySend(Unit)
            }
        }
        scope.launch {
            for (signal in wake) {
                while (connected && !failed) {
                    val sample = pending ?: break
                    pending = null
                    mutableStatus.value = UploadStatus(UploadPhase.Uploading)
                    try {
                        upload(sample.first, sample.second)
                        mutableStatus.value = UploadStatus(if (pending == null) UploadPhase.Synced else UploadPhase.Pending)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        if (pending == null) pending = sample
                        failed = true
                        mutableStatus.value = UploadStatus(UploadPhase.Error, error.message ?: "Firebase upload failed")
                    }
                }
            }
        }
    }

    override fun submit(data: SensorData) {
        if (!data.x.isFinite() || !data.y.isFinite() || !data.z.isFinite()) return
        pending = java.util.UUID.randomUUID().toString() to data
        if (!failed) mutableStatus.value = UploadStatus(UploadPhase.Pending,
            if (!connected) "No internet. Latest reading is pending." else null)
        wake.trySend(Unit)
    }
}
