package com.example.magneticcamera.core.sensors

import android.hardware.SensorManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class SensorSamplingMode(
    val label: String,
    val sensorDelay: Int,
    val description: String
) {
    Game(
        label = "Game",
        sensorDelay = SensorManager.SENSOR_DELAY_GAME,
        description = "Balanced sampling for live scanning"
    ),
    Fastest(
        label = "Fastest",
        sensorDelay = SensorManager.SENSOR_DELAY_FASTEST,
        description = "Requests the fastest sensor stream available"
    )
}

data class SensorReadConfig(
    val samplingMode: SensorSamplingMode = SensorSamplingMode.Game
) {
    val sensorDelay: Int get() = samplingMode.sensorDelay
}

interface MagneticSensorReader {
    val samples: Flow<MagneticSample>
    val sensorInfo: StateFlow<MagneticSensorInfo?>
    val latestAccuracy: StateFlow<Int?>

    fun start(config: SensorReadConfig = SensorReadConfig())
    fun stop()
}
