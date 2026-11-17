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
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.selection.SelectionContainer
import com.dre.lab1.R
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
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Заголовок
        Text(
            text = "Rehabilitation Assistant",
            style = MaterialTheme.typography.headlineSmall,
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
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(state.status, style = MaterialTheme.typography.bodySmall)
            Text("Firebase: ${state.uploadStatus.phase}", style = MaterialTheme.typography.bodySmall)
            state.uploadStatus.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        ActuatorPanel(state, onAction)

        // Блок числових значень
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = "Поточні координати сенсора:", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = if (state.reading == null) "Вісь X: —" else "Вісь X: %.2f м/с²".format(sensorData.x))
                Text(text = if (state.reading == null) "Вісь Y: —" else "Вісь Y: %.2f м/с²".format(sensorData.y))
                Text(text = if (state.reading == null) "Вісь Z: —" else "Вісь Z: %.2f м/с²".format(sensorData.z))
            }
        }

        // Графічний індикатор нахилу (Canvas)
        Card(
            modifier = Modifier
                .fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Контроль амплітуди (Баланс)", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .size(128.dp)
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

@Composable
private fun ActuatorPanel(state: MonitorState, onAction: (MonitorAction) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.actuator_title), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ControlMode.entries.forEach { mode ->
                    FilterChip(selected = state.mode == mode,
                        onClick = { onAction(MonitorAction.SelectMode(mode)) },
                        label = { Text(stringResource(when (mode) {
                            ControlMode.AUTO -> R.string.mode_auto
                            ControlMode.REMOTE -> R.string.mode_remote
                        })) })
                }
            }
            if (state.mode == ControlMode.AUTO) {
                Text(stringResource(R.string.auto_thresholds,
                    DecisionEngine.TRIGGER_THRESHOLD, DecisionEngine.RESET_THRESHOLD))
                state.magnitude?.let { Text(stringResource(R.string.acceleration_magnitude, it)) }
                Text(stringResource(when {
                    state.alarmActive -> R.string.auto_alarm_active
                    state.autoArmed -> R.string.auto_armed
                    else -> R.string.auto_waiting
                }))
            }
            if (state.mode == ControlMode.REMOTE) {
                Text(stringResource(if (state.remote.connected) R.string.remote_connected else R.string.remote_waiting))
                if (state.remote.path.isNotEmpty()) SelectionContainer { Text(state.remote.path) }
                state.remote.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActuatorStatus(R.string.siren, state.actuators.siren, Modifier.weight(1f))
                ActuatorStatus(R.string.vibration, state.actuators.vibration, Modifier.weight(1f))
                ActuatorStatus(R.string.flashlight, state.actuators.flashlight, Modifier.weight(1f))
            }
            if (!state.actuators.hasVibrator) Text(stringResource(R.string.vibrator_unavailable))
            if (!state.actuators.hasFlash) Text(stringResource(R.string.flash_unavailable))
            if (!state.actuators.cameraPermission) {
                Button(onClick = { onAction(MonitorAction.RequestCameraPermission) },
                    enabled = state.active && !state.permissionRequestPending) {
                    Text(stringResource(R.string.allow_camera))
                }
            }
            state.actuators.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { onAction(MonitorAction.StopAlarm) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.stop_alarm))
            }
        }
    }
}

@Composable
private fun ActuatorStatus(label: Int, active: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
        Text(
            stringResource(if (active) R.string.actuator_on else R.string.actuator_off),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
