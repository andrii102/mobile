package com.dre.lab1.monitor

import com.dre.lab1.helpers.Acceleration
import com.dre.lab1.helpers.ActuatorState
import com.dre.lab1.repository.RemoteUpdate

enum class SensorSource { PHONE, BLE }
enum class ControlMode { AUTO, REMOTE }

data class MonitorState(
    val mode: ControlMode = ControlMode.AUTO,
    val actuators: ActuatorState = ActuatorState(),
    val remote: RemoteUpdate = RemoteUpdate(),
    val magnitude: Float? = null,
    val alarmActive: Boolean = false,
    val autoArmed: Boolean = true,
    val active: Boolean = false,
    val permissionRequestPending: Boolean = false,
    val uploadStatus: com.dre.lab1.repository.UploadStatus = com.dre.lab1.repository.UploadStatus(),
    val source: SensorSource = SensorSource.PHONE,
    val reading: Acceleration? = null,
    val isRunning: Boolean = false,
    val status: String = "Призупинено"
)

sealed interface MonitorAction {
    data object Start : MonitorAction
    data object Stop : MonitorAction
    data class SelectSource(val source: SensorSource) : MonitorAction
    data class SelectMode(val mode: ControlMode) : MonitorAction
    data object StopAlarm : MonitorAction
    data object RequestCameraPermission : MonitorAction
    data class CameraPermissionResult(val granted: Boolean) : MonitorAction
}

sealed interface MonitorEvent {
    data class ShowMessage(val text: String) : MonitorEvent
}
