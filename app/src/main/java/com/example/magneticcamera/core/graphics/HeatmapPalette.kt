package com.example.magneticcamera.core.graphics

import androidx.compose.ui.graphics.Color

interface HeatmapPalette {
    fun colorFor(normalizedValue: Float): Color
}

object ScientificHeatmapPalette : HeatmapPalette {
    private val stops = listOf(
        0.00f to Color(0xFF06080D),
        0.20f to Color(0xFF163C8C),
        0.45f to Color(0xFF16B36D),
        0.72f to Color(0xFFFFD447),
        1.00f to Color(0xFFFF3B30)
    )

    override fun colorFor(normalizedValue: Float): Color {
        return interpolateStops(normalizedValue.coerceIn(0f, 1f), stops)
    }
}

object MonochromeGlowPalette : HeatmapPalette {
    override fun colorFor(normalizedValue: Float): Color {
        val value = normalizedValue.coerceIn(0f, 1f)
        return Color(value, value, value, 1f)
    }
}

private fun interpolateStops(value: Float, stops: List<Pair<Float, Color>>): Color {
    val nextIndex = stops.indexOfFirst { it.first >= value }.takeIf { it >= 0 } ?: stops.lastIndex
    if (nextIndex == 0) return stops.first().second
    val previous = stops[nextIndex - 1]
    val next = stops[nextIndex]
    val span = (next.first - previous.first).takeIf { it > 0f } ?: 1f
    val t = ((value - previous.first) / span).coerceIn(0f, 1f)
    return Color(
        red = previous.second.red + (next.second.red - previous.second.red) * t,
        green = previous.second.green + (next.second.green - previous.second.green) * t,
        blue = previous.second.blue + (next.second.blue - previous.second.blue) * t,
        alpha = previous.second.alpha + (next.second.alpha - previous.second.alpha) * t
    )
}
