package com.dre.lab1.helpers

import kotlinx.coroutines.flow.StateFlow

data class ActuatorCommand(
    val siren: Boolean = false,
    val vibration: Boolean = false,
    val flashlight: Boolean = false
)

data class ActuatorState(
    val siren: Boolean = false,
    val vibration: Boolean = false,
    val flashlight: Boolean = false,
    val hasFlash: Boolean = false,
    val hasVibrator: Boolean = false,
    val cameraPermission: Boolean = false,
    val error: String? = null
)

interface Actuator {
    val state: StateFlow<ActuatorState>
    fun apply(command: ActuatorCommand)
    fun refreshCapabilities()
    fun release()
}
