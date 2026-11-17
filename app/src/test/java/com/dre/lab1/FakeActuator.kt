package com.dre.lab1

import com.dre.lab1.helpers.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeActuator : Actuator {
    private val mutableState = MutableStateFlow(ActuatorState(hasFlash = true,
        hasVibrator = true, cameraPermission = true))
    override val state = mutableState.asStateFlow()
    val commands = mutableListOf<ActuatorCommand>()
    var released = false
    var failure: String? = null
    override fun refreshCapabilities() = Unit
    override fun apply(command: ActuatorCommand) {
        commands += command
        mutableState.value = state.value.copy(siren = command.siren && failure == null,
            vibration = command.vibration && failure == null,
            flashlight = command.flashlight && failure == null, error = failure)
    }
    override fun release() { apply(ActuatorCommand()); released = true }
}
