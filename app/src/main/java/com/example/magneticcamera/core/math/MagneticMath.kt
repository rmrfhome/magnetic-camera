package com.example.magneticcamera.core.math

import kotlin.math.pow
import kotlin.math.sqrt

object MagneticMath {
    fun magnitude(x: Float, y: Float, z: Float): Float {
        return sqrt(x * x + y * y + z * z)
    }

    fun magnitudeDelta(magnitude: Float, baselineMagnitude: Float): Float {
        return magnitude - baselineMagnitude
    }

    fun vectorDelta(
        x: Float,
        y: Float,
        z: Float,
        baselineX: Float,
        baselineY: Float,
        baselineZ: Float
    ): Float {
        return sqrt(
            (x - baselineX) * (x - baselineX) +
                (y - baselineY) * (y - baselineY) +
                (z - baselineZ) * (z - baselineZ)
        )
    }

    fun mean(values: List<Float>): Float {
        val finite = values.filter { it.isFinite() }
        if (finite.isEmpty()) return Float.NaN
        return finite.sum() / finite.size
    }

    fun median(values: List<Float>): Float {
        val finite = values.filter { it.isFinite() }.sorted()
        if (finite.isEmpty()) return Float.NaN
        val middle = finite.size / 2
        return if (finite.size % 2 == 0) {
            (finite[middle - 1] + finite[middle]) / 2f
        } else {
            finite[middle]
        }
    }

    fun stdDev(values: List<Float>): Float {
        val finite = values.filter { it.isFinite() }
        if (finite.isEmpty()) return Float.NaN
        val mean = finite.sum() / finite.size
        val variance = finite.sumOf { (it - mean).toDouble().pow(2.0) } / finite.size
        return sqrt(variance).toFloat()
    }
}
