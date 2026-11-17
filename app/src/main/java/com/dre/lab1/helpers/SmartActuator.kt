package com.dre.lab1.helpers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import com.dre.lab1.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Main-thread confined platform helper. It owns no Activity or background service. */
class SmartActuator(context: Context) : Actuator {
    private val app = context.applicationContext
    private val camera = app.getSystemService(CameraManager::class.java)
    private val vibrator = app.getSystemService(VibratorManager::class.java).defaultVibrator
    private val mutableState = MutableStateFlow(ActuatorState())
    override val state = mutableState.asStateFlow()
    private var player: MediaPlayer? = null
    private var requestedSiren = false
    private var cameraId: String? = null
    private var requestedTorch = false
    private var released = false
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (!released && id == cameraId) {
                // A queued ON callback must never undo a subsequent Stop.
                if (enabled && !requestedTorch) {
                    runCatching { camera.setTorchMode(id, false) }.onFailure(::report)
                }
                mutableState.update { it.copy(flashlight = enabled) }
            }
        }

        override fun onTorchModeUnavailable(id: String) {
            if (!released && id == cameraId) {
                mutableState.update { it.copy(flashlight = false) }
                if (requestedTorch) report(IllegalStateException("Ліхтарик тимчасово недоступний"))
                requestedTorch = false
            }
        }
    }

    init {
        refreshCapabilities()
        runCatching { camera.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper())) }
            .onFailure(::report)
    }

    override fun refreshCapabilities() {
        val permitted = ContextCompat.checkSelfPermission(app, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        runCatching {
            val ids = camera.cameraIdList.filter {
                camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            // Prefer the rear flash, falling back to any available flash.
            cameraId = ids.firstOrNull {
                camera.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK
            } ?: ids.firstOrNull()
        }.onFailure(::report)
        mutableState.update {
            it.copy(hasFlash = cameraId != null, hasVibrator = vibrator.hasVibrator(), cameraPermission = permitted)
        }
    }

    fun triggerAlarm() {
        setSiren(true)
        setVibration(true)
    }

    fun stopAlarm() {
        setSiren(false)
        setVibration(false)
    }

    fun setSiren(enabled: Boolean) {
        if (released || enabled == requestedSiren) return
        requestedSiren = enabled
        runCatching {
            if (enabled) {
                val media = MediaPlayer()
                player = media
                media.setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                app.resources.openRawResourceFd(R.raw.siren).use {
                    media.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
                media.isLooping = true
                media.setOnErrorListener { _, _, _ ->
                    requestedSiren = false
                    player?.release()
                    player = null
                    mutableState.update { it.copy(siren = false) }
                    report(IllegalStateException("Не вдалося відтворити сирену"))
                    true
                }
                media.setOnPreparedListener {
                    // Preparation may finish after Stop or after the player has been replaced.
                    if (player === it && requestedSiren && !released) {
                        runCatching { it.start() }
                            .onSuccess { mutableState.update { value -> value.copy(siren = true) } }
                            .onFailure { failure ->
                                setSiren(false)
                                report(failure)
                            }
                    }
                }
                media.prepareAsync()
            } else {
                player?.release()
                player = null
            }
            if (!enabled) mutableState.update { it.copy(siren = false) }
        }.onFailure {
            requestedSiren = false
            player?.release()
            player = null
            mutableState.update { it.copy(siren = false) }
            report(it)
        }
    }

    fun setVibration(enabled: Boolean) {
        if (released || enabled == state.value.vibration) return
        runCatching {
            check(!enabled || vibrator.hasVibrator()) { "Вібромотор недоступний" }
            if (enabled) vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0))
            else vibrator.cancel()
            mutableState.update { it.copy(vibration = enabled) }
        }.onFailure(::report)
    }

    fun toggleFlashlight(status: Boolean) {
        if (released || (!status && !requestedTorch && !state.value.flashlight)) return
        if (status && requestedTorch) return
        runCatching {
            check(!status || state.value.cameraPermission) { "Надайте дозвіл на камеру для ліхтарика" }
            val id = cameraId ?: if (status) error("Спалах відсутній") else return
            camera.setTorchMode(id, status)
            requestedTorch = status
        }.onFailure(::report)
    }

    override fun apply(command: ActuatorCommand) {
        mutableState.update { it.copy(error = null) }
        // Each output handles its own failure so other available outputs can still run.
        setSiren(command.siren)
        setVibration(command.vibration)
        toggleFlashlight(command.flashlight)
    }

    private fun report(error: Throwable) {
        mutableState.update { it.copy(error = error.message ?: "Помилка актуатора") }
    }

    override fun release() {
        if (released) return
        apply(ActuatorCommand())
        player?.release()
        player = null
        runCatching { camera.unregisterTorchCallback(torchCallback) }.onFailure(::report)
        released = true
    }
}
