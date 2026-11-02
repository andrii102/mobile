package com.dre.lab1.monitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.*
import androidx.lifecycle.repeatOnLifecycle
import com.dre.lab1.helpers.Acceleration
import kotlin.math.min

@Composable
fun MonitorScreen(viewModel: MonitorViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is MonitorEvent.ShowMessage -> snackbar.showSnackbar(event.text)
                }
            }
        }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.padding(padding)) { MonitorContent(state, viewModel::onAction) }
    }
}

@Composable
fun MonitorContent(state: MonitorState, onAction: (MonitorAction) -> Unit) {
    val sensorData = state.reading ?: Acceleration(0f, 0f, 0f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Заголовок
        Text(
            text = "Rehabilitation Assistant",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SensorSource.entries.forEach { source ->
                FilterChip(
                    selected = state.source == source,
                    onClick = { onAction(MonitorAction.SelectSource(source)) },
                    label = { Text(if (source == SensorSource.PHONE) "Телефон" else "BLE mock") }
                )
            }
        }
        Text(state.status)
        Text("Firebase: ${state.uploadStatus.phase}")
        state.uploadStatus.message?.let { Text(it) }

        // Блок числових значень
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Поточні координати сенсора:", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = if (state.reading == null) "Вісь X: —" else "Вісь X: %.2f м/с²".format(sensorData.x))
                Text(text = if (state.reading == null) "Вісь Y: —" else "Вісь Y: %.2f м/с²".format(sensorData.y))
                Text(text = if (state.reading == null) "Вісь Z: —" else "Вісь Z: %.2f м/с²".format(sensorData.z))
            }
        }

        // Графічний індикатор нахилу (Canvas)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Контроль амплітуди (Баланс)", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val centerX = size.width / 2
                        val centerY = size.height / 2
                        val radius = size.minDimension / 2

                        // Зовнішнє коло (межі амплітуди)
                        drawCircle(
                            color = surfaceVariant,
                            radius = radius,
                            center = Offset(centerX, centerY)
                        )

                        // Зміщення кульки на основі даних акселерометра (X та Y)
                        // Обмежуємо зміщення радіусом кола
                        val maxAccel = 10f
                        val offsetX = (sensorData.x / maxAccel) * (radius * 0.7f)
                        val offsetY = (sensorData.y / maxAccel) * (radius * 0.7f)

                        if (state.reading != null) drawCircle(
                            color = primaryColor,
                            radius = 16.dp.toPx(),
                            center = Offset(
                                centerX + offsetX,
                                centerY - offsetY
                            ) // Інвертуємо Y для природного руху
                        )
                    }
                }
            }
        }

        // Лінійний індикатор навантаження по осі X
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Інтенсивність руху (Вісь X):")
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { min(kotlin.math.abs(sensorData.x) / 10f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
            )
        }
    }
}
