package com.dre.lab1.monitor

import com.dre.lab1.helpers.Acceleration
import org.junit.Assert.*
import org.junit.Test

class DecisionEngineTest {
    private fun reading(value: Float) = Acceleration(value, 0f, 0f)

    @Test fun gravityDoesNotTriggerAndMagnitudeUsesAllAxes() {
        val engine = DecisionEngine()
        assertFalse(engine.accept(Acceleration(0f, 0f, 9.81f)))
        assertEquals(13f, DecisionEngine.magnitude(Acceleration(3f, 4f, 12f))!!, 0.001f)
        assertTrue(engine.accept(Acceleration(-9f, -12f, 0f)))
    }

    @Test fun triggerIsInclusiveAndAlarmLatchesUntilStop() {
        val engine = DecisionEngine()
        assertFalse(engine.accept(reading(11.999f)))
        assertTrue(engine.accept(reading(12f)))
        assertTrue(engine.accept(reading(0f)))
        engine.stopAlarm()
        assertFalse(engine.accept(reading(16f)))
        assertFalse(engine.accept(reading(10.5f)))
        assertFalse(engine.armed)
        assertFalse(engine.accept(reading(16f)))
        assertFalse(engine.accept(reading(10.499f)))
        assertTrue(engine.armed)
        assertTrue(engine.accept(reading(12f)))
    }

    @Test fun stopRequiresANewLowReadingEvenIfAlarmPreviouslySawLowValues() {
        val engine = DecisionEngine()
        engine.accept(reading(20f))
        engine.accept(reading(0f))
        engine.stopAlarm()
        assertFalse(engine.accept(reading(20f)))
        engine.reset()
        assertTrue(engine.accept(reading(20f)))
    }

    @Test fun nonFiniteReadingsAreIgnored() {
        val engine = DecisionEngine()
        assertNull(DecisionEngine.magnitude(reading(Float.NaN)))
        assertFalse(engine.accept(reading(Float.POSITIVE_INFINITY)))
        engine.accept(reading(12f))
        assertTrue(engine.accept(reading(Float.NaN)))
    }
}
