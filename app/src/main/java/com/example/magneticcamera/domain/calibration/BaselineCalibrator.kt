package com.example.magneticcamera.domain.calibration

import com.example.magneticcamera.core.math.MagneticMath
import com.example.magneticcamera.core.sensors.MagneticSample
import com.example.magneticcamera.domain.model.MagneticBaseline

class BaselineCalibrator {
    fun calculate(samples: List<MagneticSample>, createdAtMillis: Long): MagneticBaseline? {
        val usable = samples.filter {
            it.xMicroTesla.isFinite() &&
                it.yMicroTesla.isFinite() &&
                it.zMicroTesla.isFinite() &&
                it.magnitudeMicroTesla.isFinite()
        }
        if (usable.isEmpty()) return null
        val xs = usable.map { it.xMicroTesla }
        val ys = usable.map { it.yMicroTesla }
        val zs = usable.map { it.zMicroTesla }
        val magnitudes = usable.map { it.magnitudeMicroTesla }
        return MagneticBaseline(
            createdAtMillis = createdAtMillis,
            sampleCount = usable.size,
            xMean = MagneticMath.mean(xs),
            yMean = MagneticMath.mean(ys),
            zMean = MagneticMath.mean(zs),
            magnitudeMean = MagneticMath.mean(magnitudes),
            xStdDev = MagneticMath.stdDev(xs),
            yStdDev = MagneticMath.stdDev(ys),
            zStdDev = MagneticMath.stdDev(zs),
            magnitudeStdDev = MagneticMath.stdDev(magnitudes)
        )
    }
}
