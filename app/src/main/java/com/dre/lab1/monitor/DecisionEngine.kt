package com.dre.lab1.monitor

import com.dre.lab1.helpers.Acceleration
import kotlin.math.sqrt

/** Latches the alarm until Stop; a new trigger then requires movement to settle first. */
class DecisionEngine {
    var alarmActive = false
        private set
    var armed = true
        private set

    fun accept(reading: Acceleration): Boolean {
        val magnitude = magnitude(reading) ?: return alarmActive
        if (!alarmActive) {
            // Separate thresholds prevent repeated triggers around the activation boundary.
            if (magnitude < RESET_THRESHOLD) armed = true
            if (armed && magnitude >= TRIGGER_THRESHOLD) {
                alarmActive = true
                armed = false
            }
        }
        return alarmActive
    }

    fun stopAlarm() {
        alarmActive = false
        // Ignore the current movement until a fresh reading falls below the reset threshold.
        armed = false
    }

    fun reset() {
        alarmActive = false
        armed = true
    }

    companion object {
        const val TRIGGER_THRESHOLD = 12f
        const val RESET_THRESHOLD = 10.5f

        fun magnitude(reading: Acceleration): Float? {
            // Total acceleration includes gravity (about 9.81 m/s² at rest).
            if (!reading.x.isFinite() || !reading.y.isFinite() || !reading.z.isFinite()) return null
            return sqrt(reading.x.toDouble() * reading.x + reading.y.toDouble() * reading.y +
                reading.z.toDouble() * reading.z).toFloat().takeIf { it.isFinite() }
        }
    }
}
