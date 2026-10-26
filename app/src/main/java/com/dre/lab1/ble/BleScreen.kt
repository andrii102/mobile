package com.dre.lab1.ble

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BleScreen(viewModel: BleViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is BleEvent.ShowMessage -> snackbar.showSnackbar(event.text)
                }
            }
        }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.padding(padding)) {
            BleContent(state, viewModel::onAction)
        }
    }
}

@Composable
fun BleContent(state: BleState, onAction: (BleAction) -> Unit) {
    val terminalScroll = rememberLazyListState()
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    LaunchedEffect(state.terminal.lastOrNull()) {
        if (state.terminal.isNotEmpty()) terminalScroll.scrollToItem(state.terminal.lastIndex)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Віртуальний BLE-акселерометр", style = MaterialTheme.typography.headlineSmall)
            Text("Імітація пристрою без Bluetooth-обладнання")
        }
        item {
            Text(
                if (state.isConnected) "Connected" else "Disconnected",
                color = if (state.isConnected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(state.reading?.let { "X: %.2f  Y: %.2f  Z: %.2f м/с²".format(it.x, it.y, it.z) }
                ?: "Очікування даних акселерометра")
        }
        item {
            Button(
                onClick = { onAction(BleAction.Scan) },
                enabled = !state.isScanning && !state.isConnected
            ) { Text("Пошук пристроїв") }
            if (state.isScanning) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Сканування…")
            }
        }
        items(state.devices, key = { it.id }) { device ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(device.name, style = MaterialTheme.typography.titleMedium)
                    Text(device.id)
                    if (state.isConnected) {
                        OutlinedButton(onClick = { onAction(BleAction.Disconnect) }) {
                            Text("Відключити")
                        }
                    } else {
                        Button(onClick = { onAction(BleAction.Connect(device.id)) }) {
                            Text("Підключити")
                        }
                    }
                }
            }
        }
        item {
            Text("Термінал • останні 200 записів", style = MaterialTheme.typography.titleMedium)
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                LazyColumn(
                    state = terminalScroll,
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    if (state.terminal.isEmpty()) item { Text("Журнал порожній") }
                    items(state.terminal) { entry ->
                        Text(
                            "${timeFormat.format(Date(entry.timestamp))} ${entry.direction}: ${entry.text}",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
