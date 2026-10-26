package com.dre.lab1.helpers

import android.util.Log

fun interface BleLogger {
    fun log(message: BleMessage)
}

class LogcatBleLogger : BleLogger {
    override fun log(message: BleMessage) {
        Log.d("MockBLE", "${message.timestamp} ${message.direction}: ${message.text}")
    }
}
