package com.dre.lab1.repository

import com.dre.lab1.helpers.ActuatorCommand
import org.junit.Assert.*
import org.junit.Test

class RemoteCommandTest {
    @Test fun deletedAndMissingFieldsMeanOff() {
        assertEquals(ActuatorCommand(), parseRemoteCommand(null))
        assertEquals(ActuatorCommand(flashlight = true), parseRemoteCommand(mapOf("flashlight" to true)))
    }

    @Test fun parsesIndependentBooleans() {
        assertEquals(ActuatorCommand(true, false, true), parseRemoteCommand(
            mapOf("siren" to true, "vibration" to false, "flashlight" to true)))
    }

    @Test fun rejectsStringsNumbersAndNonObjects() {
        listOf("true", true, 1, listOf(true), mapOf("siren" to "true"),
            mapOf("flashlight" to 1)).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { parseRemoteCommand(value) }
        }
    }
}
