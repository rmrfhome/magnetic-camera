package com.example.magneticcamera.core.math

import java.util.ArrayDeque

class MedianFilter(
    private val windowSize: Int = 5
) {
    private val values = ArrayDeque<Float>()

    fun filter(current: Float): Float {
        values.addLast(current)
        while (values.size > windowSize.coerceAtLeast(1)) {
            values.removeFirst()
        }
        return MagneticMath.median(values.toList())
    }

    fun reset() {
        values.clear()
    }
}
