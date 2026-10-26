package com.dre.lab1

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.dre.lab1.ble.BleViewModel
import com.dre.lab1.helpers.MockBleConnector
import com.dre.lab1.helpers.PhoneSensorReader
import com.dre.lab1.monitor.MonitorViewModel

/** Activity-retained dependency owner; screen ViewModels never reference each other. */
class AppSessionViewModel : ViewModel() {
    private val connector = MockBleConnector()

    fun screenFactory(context: Context): ViewModelProvider.Factory {
        val phone = PhoneSensorReader(context.applicationContext)
        return object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val model = when (modelClass) {
                    BleViewModel::class.java -> BleViewModel(connector = connector)
                    MonitorViewModel::class.java -> MonitorViewModel(phone, connector)
                    else -> error("Unknown ViewModel: $modelClass")
                }
                return modelClass.cast(model)!!
            }
        }
    }

    override fun onCleared() {
        connector.close()
        super.onCleared()
    }
}
