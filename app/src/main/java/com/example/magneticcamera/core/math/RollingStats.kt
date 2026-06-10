package com.example.magneticcamera.core.math

import java.util.ArrayDeque

data class SampleStats(
    val count: Int,
    val mean: Float,
    val median: Float,
    val min: Float,
    val max: Float,
    val stdDev: Float
)

class RollingStats(
    private val maxSize: Int
) {
    private val values = ArrayDeque<Float>()

    fun add(value: Float): SampleStats {
        if (value.isFinite()) {
            values.addLast(value)
            while (values.size > maxSize.coerceAtLeast(1)) {
                values.removeFirst()
            }
        }
        return current()
    }

    fun current(): SampleStats {
        val list = values.toList()
        if (list.isEmpty()) {
            return SampleStats(0, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN)
        }
        return SampleStats(
            count = list.size,
            mean = MagneticMath.mean(list),
            median = MagneticMath.median(list),
            min = list.min(),
            max = list.max(),
            stdDev = MagneticMath.stdDev(list)
        )
    }

    fun clear() {
        values.clear()
    }
}
