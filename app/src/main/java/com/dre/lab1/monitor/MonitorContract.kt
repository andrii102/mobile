package com.dre.lab1.monitor

import com.dre.lab1.helpers.Acceleration

enum class SensorSource { PHONE, BLE }

data class MonitorState(
    val source: SensorSource = SensorSource.PHONE,
    val reading: Acceleration? = null,
    val isRunning: Boolean = false,
    val status: String = "Призупинено"
)

sealed interface MonitorAction {
    data object Start : MonitorAction
    data object Stop : MonitorAction
    data class SelectSource(val source: SensorSource) : MonitorAction
}

sealed interface MonitorEvent {
    data class ShowMessage(val text: String) : MonitorEvent
}
