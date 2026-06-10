package com.example.magneticcamera.core.sensors

import com.example.magneticcamera.core.math.MagneticMath
import kotlin.math.abs

internal object MagneticSampleValidator {
    const val INVALID_SAMPLE_WARNING =
        "Discarded invalid magnetic sample. Keep scanning; the app ignores NaN or infinite sensor values."
    const val OUT_OF_RANGE_WARNING =
        "Discarded magnetic sample outside the sensor range. Move away from very strong magnets and continue scanning."

    fun validate(
        xMicroTesla: Float,
        yMicroTesla: Float,
        zMicroTesla: Float,
        maximumRangeMicroTesla: Float
    ): ValidationResult {
        if (!xMicroTesla.isFinite() || !yMicroTesla.isFinite() || !zMicroTesla.isFinite()) {
            return ValidationResult.Rejected(INVALID_SAMPLE_WARNING)
        }
        val magnitude = MagneticMath.magnitude(xMicroTesla, yMicroTesla, zMicroTesla)
        if (!magnitude.isFinite()) {
            return ValidationResult.Rejected(INVALID_SAMPLE_WARNING)
        }
        val rangeLimit = maximumRangeMicroTesla
            .takeIf { it.isFinite() && it > 0f }
            ?.times(1.25f)
        if (
            rangeLimit != null &&
            (abs(xMicroTesla) > rangeLimit ||
                abs(yMicroTesla) > rangeLimit ||
                abs(zMicroTesla) > rangeLimit ||
                magnitude > rangeLimit)
        ) {
            return ValidationResult.Rejected(OUT_OF_RANGE_WARNING)
        }
        return ValidationResult.Accepted(magnitude)
    }
}

internal sealed interface ValidationResult {
    data class Accepted(val magnitudeMicroTesla: Float) : ValidationResult
    data class Rejected(val warning: String) : ValidationResult
}
