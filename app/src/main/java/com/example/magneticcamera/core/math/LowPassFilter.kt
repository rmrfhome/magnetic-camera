package com.example.magneticcamera.core.math

class LowPassFilter(
    alpha: Float = 0.25f
) {
    private var alpha: Float = alpha.coerceIn(0f, 1f)
    private var previous: Float? = null

    fun filter(current: Float): Float {
        val last = previous
        val filtered = if (last == null || !last.isFinite()) {
            current
        } else {
            last + alpha * (current - last)
        }
        previous = filtered
        return filtered
    }

    fun setAlpha(value: Float) {
        alpha = value.coerceIn(0f, 1f)
    }

    fun reset() {
        previous = null
    }
}

class VectorLowPassFilter(
    alpha: Float = 0.25f
) {
    private val xFilter = LowPassFilter(alpha)
    private val yFilter = LowPassFilter(alpha)
    private val zFilter = LowPassFilter(alpha)

    fun filter(x: Float, y: Float, z: Float): Triple<Float, Float, Float> {
        return Triple(xFilter.filter(x), yFilter.filter(y), zFilter.filter(z))
    }

    fun reset() {
        xFilter.reset()
        yFilter.reset()
        zFilter.reset()
    }

    fun setAlpha(value: Float) {
        xFilter.setAlpha(value)
        yFilter.setAlpha(value)
        zFilter.setAlpha(value)
    }
}
