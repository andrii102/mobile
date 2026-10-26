package com.dre.lab1.helpers

import java.io.Closeable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class BleDirection { SYSTEM, RX }

data class BleMessage(
    val direction: BleDirection,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

interface IBleConnector : Closeable {
    val connected: StateFlow<Boolean>
    val messages: Flow<BleMessage>
    /** Latest notification in m/s²; null when disconnected or acquisition is paused. */
    val readings: StateFlow<Acceleration?>
    suspend fun connect()
    suspend fun disconnect()
    fun setActive(active: Boolean)
}
