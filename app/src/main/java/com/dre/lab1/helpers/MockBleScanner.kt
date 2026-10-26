package com.dre.lab1.helpers

import kotlinx.coroutines.delay

class MockBleScanner {
    suspend fun scan(): List<BleDevice> {
        delay(2_000)
        return listOf(BleProfile.device)
    }
}
