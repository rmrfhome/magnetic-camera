package com.example.magneticcamera.ui.live

import com.example.magneticcamera.core.sensors.MagneticSample
import com.example.magneticcamera.core.sensors.MagneticSensorInfo
import com.example.magneticcamera.domain.model.MagneticBaseline

data class LiveMeterUiState(
    val sensorAvailable: Boolean = true,
    val sensorInfo: MagneticSensorInfo? = null,
    val latestSample: MagneticSample? = null,
    val snapshotSample: MagneticSample? = null,
    val snapshotCreatedAtMillis: Long? = null,
    val baseline: MagneticBaseline? = null,
    val recentSamples: List<MagneticSample> = emptyList(),
    val samplingRateHz: Float = 0f,
    val accuracyLabel: String = "Unknown",
    val isFrozen: Boolean = false,
    val isCalibrating: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null
)
