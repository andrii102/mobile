package com.dre.lab1.helpers

import java.util.UUID

data class BleDevice(val id: String, val name: String)

/** Virtual GATT profile: status and acceleration support Read/Notify. */
object BleProfile {
    val serviceUuid: UUID = UUID.fromString("7a910001-6d27-4f83-a285-19bc7e20d641")
    val statusUuid: UUID = UUID.fromString("7a910002-6d27-4f83-a285-19bc7e20d641")
    val accelerationUuid: UUID = UUID.fromString("7a910003-6d27-4f83-a285-19bc7e20d641")
    val device = BleDevice("virtual-accelerometer-001", "Virtual Rehab Accelerometer")
}
