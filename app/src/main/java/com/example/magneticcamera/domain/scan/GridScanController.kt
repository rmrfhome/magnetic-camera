package com.example.magneticcamera.domain.scan

import android.hardware.SensorManager
import com.example.magneticcamera.core.math.MagneticMath
import com.example.magneticcamera.core.sensors.MagneticSample
import java.util.UUID

class GridScanController(
    private val captureWindowMs: Long = 700,
    private val minimumSamples: Int = 15
) {
    fun shouldStopCapture(startMillis: Long, nowMillis: Long, sampleCount: Int): Boolean {
        if (sampleCount >= minimumSamples && nowMillis - startMillis >= captureWindowMs) {
            return true
        }
        return nowMillis - startMillis >= 2_000L
    }

    fun captureCell(
        sessionId: String,
        row: Int,
        col: Int,
        samples: List<MagneticSample>,
        capturedAtMillis: Long = System.currentTimeMillis()
    ): GridCellMeasurement {
        val usable = samples.filter {
            it.xMicroTesla.isFinite() &&
                it.yMicroTesla.isFinite() &&
                it.zMicroTesla.isFinite() &&
                it.magnitudeMicroTesla.isFinite() &&
                it.baselineVectorDeltaMicroTesla.isFinite()
        }
        val xs = usable.map { it.xMicroTesla }
        val ys = usable.map { it.yMicroTesla }
        val zs = usable.map { it.zMicroTesla }
        val magnitudes = usable.map { it.magnitudeMicroTesla }
        val vectorDeltas = usable.map { it.baselineVectorDeltaMicroTesla }
        val magnitudeDeltas = usable.map { it.baselineDeltaMicroTesla }
        val accuracies = usable.map { it.accuracy }.ifEmpty {
            listOf(SensorManager.SENSOR_STATUS_UNRELIABLE)
        }
        return GridCellMeasurement(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            row = row,
            col = col,
            sampleCount = usable.size,
            capturedAtMillis = capturedAtMillis,
            xMean = finiteMeanOrZero(xs),
            yMean = finiteMeanOrZero(ys),
            zMean = finiteMeanOrZero(zs),
            magnitudeMean = finiteMeanOrZero(magnitudes),
            magnitudeMedian = finiteMedianOrZero(magnitudes),
            magnitudeMin = magnitudes.minOrNull() ?: 0f,
            magnitudeMax = magnitudes.maxOrNull() ?: 0f,
            magnitudeStdDev = finiteStdOrZero(magnitudes),
            vectorDeltaMean = finiteMeanOrZero(vectorDeltas),
            vectorDeltaMedian = finiteMedianOrZero(vectorDeltas),
            vectorDeltaMin = vectorDeltas.minOrNull() ?: 0f,
            vectorDeltaMax = vectorDeltas.maxOrNull() ?: 0f,
            vectorDeltaStdDev = finiteStdOrZero(vectorDeltas),
            magnitudeDeltaMean = finiteMeanOrZero(magnitudeDeltas),
            accuracy = accuracies.minOrNull() ?: SensorManager.SENSOR_STATUS_UNRELIABLE
        )
    }

    fun skippedCell(sessionId: String, row: Int, col: Int): GridCellMeasurement {
        return GridCellMeasurement(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            row = row,
            col = col,
            sampleCount = 0,
            capturedAtMillis = System.currentTimeMillis(),
            xMean = 0f,
            yMean = 0f,
            zMean = 0f,
            magnitudeMean = 0f,
            magnitudeMedian = 0f,
            magnitudeMin = 0f,
            magnitudeMax = 0f,
            magnitudeStdDev = 0f,
            vectorDeltaMean = 0f,
            vectorDeltaMedian = 0f,
            vectorDeltaMin = 0f,
            vectorDeltaMax = 0f,
            vectorDeltaStdDev = 0f,
            magnitudeDeltaMean = 0f,
            accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
        )
    }

    private fun finiteMeanOrZero(values: List<Float>): Float {
        return MagneticMath.mean(values).takeIf { it.isFinite() } ?: 0f
    }

    private fun finiteMedianOrZero(values: List<Float>): Float {
        return MagneticMath.median(values).takeIf { it.isFinite() } ?: 0f
    }

    private fun finiteStdOrZero(values: List<Float>): Float {
        return MagneticMath.stdDev(values).takeIf { it.isFinite() } ?: 0f
    }
}
