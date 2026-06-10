package com.example.magneticcamera.domain.scan

import com.example.magneticcamera.domain.model.MagneticBaseline
import com.example.magneticcamera.domain.model.PhotoOverlayArea

enum class CaptureMode {
    Manual,
    AutoWhenStable
}

enum class PaletteMode {
    Scientific,
    MonochromeGlow
}

enum class HeatmapMetric(
    val label: String
) {
    VectorDelta("Vector delta from baseline"),
    AbsoluteField("Absolute magnetic field")
}

sealed interface NormalizationMode {
    data object AutoLocal : NormalizationMode
    data class BaselineDeltaFixedScale(val maxDeltaMicroTesla: Float) : NormalizationMode
    data object AbsoluteField : NormalizationMode
}

data class ScanSetup(
    val name: String = "Surface scan",
    val gridWidth: Int = 7,
    val gridHeight: Int = 7,
    val shouldTakePhoto: Boolean = true,
    val captureMode: CaptureMode = CaptureMode.Manual
)

data class GridCellMeasurement(
    val id: String,
    val sessionId: String,
    val row: Int,
    val col: Int,
    val sampleCount: Int,
    val capturedAtMillis: Long,
    val xMean: Float,
    val yMean: Float,
    val zMean: Float,
    val magnitudeMean: Float,
    val magnitudeMedian: Float,
    val magnitudeMin: Float,
    val magnitudeMax: Float,
    val magnitudeStdDev: Float,
    val vectorDeltaMean: Float,
    val vectorDeltaMedian: Float,
    val vectorDeltaMin: Float,
    val vectorDeltaMax: Float,
    val vectorDeltaStdDev: Float,
    val magnitudeDeltaMean: Float,
    val accuracy: Int
) {
    fun valueForHeatmap(normalizationMode: NormalizationMode): Float {
        if (sampleCount <= 0) return Float.NaN
        return when (normalizationMode) {
            NormalizationMode.AbsoluteField -> magnitudeMean
            else -> vectorDeltaMean
        }
    }
}

data class ScanSession(
    val id: String,
    val name: String,
    val createdAtMillis: Long,
    val gridWidth: Int,
    val gridHeight: Int,
    val baseline: MagneticBaseline,
    val photoUri: String?,
    val overlayArea: PhotoOverlayArea = PhotoOverlayArea.FullFrame,
    val cells: List<GridCellMeasurement>,
    val heatmapImageUri: String? = null,
    val overlayImageUri: String? = null,
    val rawDataUri: String? = null,
    val notes: String? = null
)

data class ScanDraft(
    val setup: ScanSetup,
    val currentSessionId: String,
    val baseline: MagneticBaseline?,
    val photoUri: String?,
    val overlayArea: PhotoOverlayArea,
    val isScanStarted: Boolean,
    val cells: List<GridCellMeasurement>
)
