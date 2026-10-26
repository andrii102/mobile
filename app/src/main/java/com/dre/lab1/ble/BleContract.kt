package com.dre.lab1.ble

import com.dre.lab1.helpers.Acceleration
import com.dre.lab1.helpers.BleDevice
import com.dre.lab1.helpers.BleMessage

data class BleState(
    val isScanning: Boolean = false,
    val devices: List<BleDevice> = emptyList(),
    val isConnected: Boolean = false,
    val reading: Acceleration? = null,
    val terminal: List<BleMessage> = emptyList()
)

sealed interface BleAction {
    data object Scan : BleAction
    data class Connect(val deviceId: String) : BleAction
    data object Disconnect : BleAction
    data object Start : BleAction
    data object Pause : BleAction
    data object Stop : BleAction
}

sealed interface BleEvent {
    data class ShowMessage(val text: String) : BleEvent
}
