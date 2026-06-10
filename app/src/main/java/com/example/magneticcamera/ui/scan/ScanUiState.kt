package com.example.magneticcamera.ui.scan

import com.example.magneticcamera.core.graphics.HeatmapRender
import com.example.magneticcamera.core.sensors.MagneticSample
import com.example.magneticcamera.domain.model.MagneticBaseline
import com.example.magneticcamera.domain.model.PhotoOverlayArea
import com.example.magneticcamera.domain.scan.GridCellMeasurement
import com.example.magneticcamera.domain.scan.NormalizationMode
import com.example.magneticcamera.domain.scan.PaletteMode
import com.example.magneticcamera.domain.scan.ScanSetup

data class ScanUiState(
    val setup: ScanSetup = ScanSetup(),
    val currentSessionId: String = "",
    val baseline: MagneticBaseline? = null,
    val photoUri: String? = null,
    val overlayArea: PhotoOverlayArea = PhotoOverlayArea.FullFrame,
    val isSensorActive: Boolean = false,
    val isScanStarted: Boolean = false,
    val isCalibrating: Boolean = false,
    val isCapturing: Boolean = false,
    val latestSample: MagneticSample? = null,
    val stabilityStdDev: Float = 0f,
    val isStable: Boolean = false,
    val cells: List<GridCellMeasurement> = emptyList(),
    val heatmap: HeatmapRender? = null,
    val paletteMode: PaletteMode = PaletteMode.Scientific,
    val normalizationMode: NormalizationMode = NormalizationMode.AutoLocal,
    val opacity: Float = 0.72f,
    val showLegend: Boolean = true,
    val savedSessionId: String? = null,
    val message: String? = null,
    val errorMessage: String? = null
) {
    val totalCells: Int get() = setup.gridWidth * setup.gridHeight
    val currentCellIndex: Int get() = cells.size.coerceAtMost(totalCells)
    val progress: Float get() = if (totalCells == 0) 0f else cells.size.toFloat() / totalCells
    val isComplete: Boolean get() = totalCells > 0 && cells.size >= totalCells
    val currentRow: Int get() = currentCellIndex / setup.gridWidth
    val currentCol: Int get() = currentCellIndex % setup.gridWidth
}
