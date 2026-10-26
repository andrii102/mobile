package com.dre.lab1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.dre.lab1.ble.BleAction
import com.dre.lab1.ble.BleScreen
import com.dre.lab1.ble.BleViewModel
import androidx.compose.ui.Modifier
import com.dre.lab1.monitor.*

class MainActivity : ComponentActivity() {
    private val session: AppSessionViewModel by viewModels()
    private val screenFactory by lazy { session.screenFactory(applicationContext) }
    private val viewModel: MonitorViewModel by viewModels { screenFactory }
    private val bleViewModel: BleViewModel by viewModels { screenFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
                    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                        PrimaryTabRow(selectedTabIndex = selectedTab) {
                            listOf("Реабілітація", "BLE-пристрій").forEachIndexed { index, title ->
                                Tab(
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    text = { Text(title) }
                                )
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            if (selectedTab == 0) MonitorScreen(viewModel)
                            else BleScreen(bleViewModel)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        bleViewModel.onAction(BleAction.Start)
        viewModel.onAction(MonitorAction.Start)
    }

    override fun onPause() {
        viewModel.onAction(MonitorAction.Stop)
        bleViewModel.onAction(BleAction.Pause)
        super.onPause()
    }

    override fun onStop() {
        if (!isChangingConfigurations) bleViewModel.onAction(BleAction.Stop)
        super.onStop()
    }
}
