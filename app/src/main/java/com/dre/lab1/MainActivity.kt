package com.dre.lab1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlin.math.min

class MainActivity : ComponentActivity() {
    private val viewModel: SensorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RehabilitationScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.startListening() // Відновлюємо зчитування даних при активації
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopListening() // Зупиняємо зчитування для енергозбереження
    }
}

@Composable
fun RehabilitationScreen(viewModel: SensorViewModel) {
    val sensorData by viewModel.sensorState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Заголовок
        Text(
            text = "Rehabilitation Assistant",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        // Блок числових значень
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Поточні координати сенсора:", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Вісь X: %.2f м/с²".format(sensorData.x))
                Text(text = "Вісь Y: %.2f м/с²".format(sensorData.y))
                Text(text = "Вісь Z: %.2f м/с²".format(sensorData.z))
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

                        drawCircle(
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
