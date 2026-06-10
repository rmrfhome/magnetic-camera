package com.example.magneticcamera.core.sensors

data class MagneticSample(
    val timestampNanos: Long,
    val sensorType: Int,
    val accuracy: Int,
    val xMicroTesla: Float,
    val yMicroTesla: Float,
    val zMicroTesla: Float,
    val biasXMicroTesla: Float? = null,
    val biasYMicroTesla: Float? = null,
    val biasZMicroTesla: Float? = null,
    val magnitudeMicroTesla: Float,
    val baselineDeltaMicroTesla: Float = 0f,
    val baselineVectorDeltaMicroTesla: Float = 0f
)
