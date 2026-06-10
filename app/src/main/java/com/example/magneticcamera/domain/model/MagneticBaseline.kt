package com.example.magneticcamera.domain.model

data class MagneticBaseline(
    val createdAtMillis: Long,
    val sampleCount: Int,
    val xMean: Float,
    val yMean: Float,
    val zMean: Float,
    val magnitudeMean: Float,
    val xStdDev: Float,
    val yStdDev: Float,
    val zStdDev: Float,
    val magnitudeStdDev: Float
)
