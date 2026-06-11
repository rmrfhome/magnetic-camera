package com.example.magneticcamera.core.graphics

import android.graphics.Bitmap
import androidx.compose.ui.graphics.toArgb
import com.example.magneticcamera.domain.scan.HeatmapMetric
import com.example.magneticcamera.domain.scan.NormalizationMode
import kotlin.math.floor

data class HeatmapInput(
    val gridWidth: Int,
    val gridHeight: Int,
    val values: List<Float>,
    val metric: HeatmapMetric = HeatmapMetric.VectorDelta,
    val unit: String = "µT"
)

data class HeatmapLegend(
    val minValue: Float,
    val maxValue: Float,
    val unit: String,
    val metricName: String,
    val gradientColors: List<Int> = emptyList()
)

data class HeatmapRender(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
    val legend: HeatmapLegend
) {
    init {
        require(width > 0 && height > 0)
        require(pixels.size == width * height)
    }

    fun toBitmap(): Bitmap {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HeatmapRender) return false
        return width == other.width &&
            height == other.height &&
            pixels.contentEquals(other.pixels) &&
            legend == other.legend
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + pixels.contentHashCode()
        result = 31 * result + legend.hashCode()
        return result
    }
}

class HeatmapGenerator {
    fun generate(
        input: HeatmapInput,
        outputWidth: Int,
        outputHeight: Int,
        normalizationMode: NormalizationMode = NormalizationMode.AutoLocal,
        palette: HeatmapPalette = ScientificHeatmapPalette
    ): HeatmapRender {
        require(input.gridWidth > 0)
        require(input.gridHeight > 0)
        require(input.values.size == input.gridWidth * input.gridHeight)
        require(outputWidth > 0)
        require(outputHeight > 0)

        val sanitized = sanitizeValues(input.values)
        val range = normalizationRange(sanitized, normalizationMode)
        val pixels = IntArray(outputWidth * outputHeight)

        for (y in 0 until outputHeight) {
            for (x in 0 until outputWidth) {
                val value = interpolate(
                    values = sanitized,
                    gridWidth = input.gridWidth,
                    gridHeight = input.gridHeight,
                    normalizedX = if (outputWidth == 1) 0f else x.toFloat() / (outputWidth - 1),
                    normalizedY = if (outputHeight == 1) 0f else y.toFloat() / (outputHeight - 1)
                )
                val normalized = normalize(value, range.first, range.second)
                pixels[y * outputWidth + x] = palette.colorFor(normalized).toArgb()
            }
        }

        return HeatmapRender(
            width = outputWidth,
            height = outputHeight,
            pixels = pixels,
            legend = HeatmapLegend(
                minValue = range.first,
                maxValue = range.second,
                unit = input.unit,
                metricName = input.metric.label,
                gradientColors = palette.gradientColors()
            )
        )
    }

    fun interpolate(
        values: List<Float>,
        gridWidth: Int,
        gridHeight: Int,
        normalizedX: Float,
        normalizedY: Float
    ): Float {
        require(values.size == gridWidth * gridHeight)
        if (gridWidth == 1 && gridHeight == 1) return values.first()

        val gx = normalizedX.coerceIn(0f, 1f) * (gridWidth - 1).coerceAtLeast(0)
        val gy = normalizedY.coerceIn(0f, 1f) * (gridHeight - 1).coerceAtLeast(0)
        val x0 = floor(gx).toInt().coerceIn(0, gridWidth - 1)
        val y0 = floor(gy).toInt().coerceIn(0, gridHeight - 1)
        val x1 = (x0 + 1).coerceAtMost(gridWidth - 1)
        val y1 = (y0 + 1).coerceAtMost(gridHeight - 1)
        val tx = gx - x0
        val ty = gy - y0

        val top = lerp(values[y0 * gridWidth + x0], values[y0 * gridWidth + x1], tx)
        val bottom = lerp(values[y1 * gridWidth + x0], values[y1 * gridWidth + x1], tx)
        return lerp(top, bottom, ty)
    }

    fun normalize(value: Float, min: Float, max: Float): Float {
        if (!value.isFinite()) return 0f
        val span = (max - min).takeIf { it > 0f } ?: 1f
        return ((value - min) / span).coerceIn(0f, 1f)
    }

    fun normalizationRange(
        values: List<Float>,
        normalizationMode: NormalizationMode
    ): Pair<Float, Float> {
        val finite = values.filter { it.isFinite() }
        val dataMin = finite.minOrNull() ?: 0f
        val dataMax = finite.maxOrNull() ?: 1f
        return when (normalizationMode) {
            NormalizationMode.AutoLocal,
            NormalizationMode.AbsoluteField -> {
                if (dataMin == dataMax) dataMin to dataMin + 1f else dataMin to dataMax
            }
            is NormalizationMode.BaselineDeltaFixedScale -> {
                0f to normalizationMode.maxDeltaMicroTesla.coerceAtLeast(1f)
            }
        }
    }

    private fun sanitizeValues(values: List<Float>): List<Float> {
        val fallback = values.filter { it.isFinite() }.minOrNull() ?: 0f
        return values.map { value -> value.takeIf { it.isFinite() } ?: fallback }
    }

    private fun lerp(start: Float, end: Float, t: Float): Float {
        return start + (end - start) * t
    }

    private fun HeatmapPalette.gradientColors(segments: Int = 160): List<Int> {
        return (0 until segments).map { index ->
            val fraction = index / (segments - 1).toFloat()
            colorFor(fraction).toArgb()
        }
    }
}
