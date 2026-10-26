package com.dre.lab1.helpers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

fun interface SensorReader {
    fun readings(): Flow<Acceleration>
}

class PhoneSensorReader(context: Context) : SensorReader {
    private val manager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    override fun readings(): Flow<Acceleration> = callbackFlow {
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) {
            close(IllegalStateException("Акселерометр недоступний"))
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(Acceleration(event.values[0], event.values[1], event.values[2]))
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (!manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)) {
            close(IllegalStateException("Не вдалося запустити акселерометр"))
        }
        awaitClose { manager.unregisterListener(listener) }
    }.conflate()
}
