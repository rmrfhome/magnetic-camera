package com.example.magneticcamera.core.sensors

data class MagneticSensorInfo(
    val isAvailable: Boolean,
    val name: String?,
    val vendor: String?,
    val sensorType: Int,
    val typeLabel: String,
    val resolution: Float,
    val maximumRange: Float,
    val minimumDelayMicros: Int,
    val isUncalibrated: Boolean
) {
    companion object {
        val Unavailable = MagneticSensorInfo(
            isAvailable = false,
            name = null,
            vendor = null,
            sensorType = -1,
            typeLabel = "unavailable",
            resolution = 0f,
            maximumRange = 0f,
            minimumDelayMicros = 0,
            isUncalibrated = false
        )
    }
}
