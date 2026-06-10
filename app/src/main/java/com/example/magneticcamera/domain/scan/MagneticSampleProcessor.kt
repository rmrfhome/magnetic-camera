package com.example.magneticcamera.domain.scan

import com.example.magneticcamera.core.math.LowPassFilter
import com.example.magneticcamera.core.math.MagneticMath
import com.example.magneticcamera.core.math.MedianFilter
import com.example.magneticcamera.core.math.VectorLowPassFilter
import com.example.magneticcamera.core.sensors.MagneticSample
import com.example.magneticcamera.domain.model.MagneticBaseline

class MagneticSampleProcessor(
    alpha: Float = 0.25f,
    medianWindow: Int = 5
) {
    private val vectorFilter = VectorLowPassFilter(alpha)
    private val magnitudeMedian = MedianFilter(medianWindow)
    private val deltaMedian = MedianFilter(medianWindow)
    private val magnitudeDeltaMedian = MedianFilter(medianWindow)

    fun process(sample: MagneticSample, baseline: MagneticBaseline?): MagneticSample {
        val (x, y, z) = vectorFilter.filter(
            sample.xMicroTesla,
            sample.yMicroTesla,
            sample.zMicroTesla
        )
        val magnitude = magnitudeMedian.filter(MagneticMath.magnitude(x, y, z))
        val magnitudeDelta = if (baseline == null) {
            0f
        } else {
            magnitudeDeltaMedian.filter(MagneticMath.magnitudeDelta(magnitude, baseline.magnitudeMean))
        }
        val vectorDelta = if (baseline == null) {
            0f
        } else {
            deltaMedian.filter(
                MagneticMath.vectorDelta(
                    x = x,
                    y = y,
                    z = z,
                    baselineX = baseline.xMean,
                    baselineY = baseline.yMean,
                    baselineZ = baseline.zMean
                )
            )
        }
        return sample.copy(
            xMicroTesla = x,
            yMicroTesla = y,
            zMicroTesla = z,
            magnitudeMicroTesla = magnitude,
            baselineDeltaMicroTesla = magnitudeDelta,
            baselineVectorDeltaMicroTesla = vectorDelta
        )
    }

    fun reset() {
        vectorFilter.reset()
        magnitudeMedian.reset()
        deltaMedian.reset()
        magnitudeDeltaMedian.reset()
    }

    fun setAlpha(value: Float) {
        vectorFilter.setAlpha(value)
    }
}
