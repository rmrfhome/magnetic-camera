package com.example.magneticcamera.ui.result

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.magneticcamera.core.math.MagneticMath
import com.example.magneticcamera.domain.scan.NormalizationMode
import com.example.magneticcamera.domain.scan.PaletteMode
import com.example.magneticcamera.domain.model.PhotoOverlayArea
import com.example.magneticcamera.ui.common.HeatmapPreview
import com.example.magneticcamera.ui.common.InstrumentPanel
import com.example.magneticcamera.ui.common.MessagePanel
import com.example.magneticcamera.ui.common.StatusText
import com.example.magneticcamera.ui.common.rememberBitmap
import com.example.magneticcamera.ui.scan.ScanUiState

@Composable
fun HeatmapResultScreen(
    state: ScanUiState,
    onBack: () -> Unit,
    onPaletteChange: (PaletteMode) -> Unit,
    onNormalizationChange: (NormalizationMode) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onShowGridChange: (Boolean) -> Unit,
    onShowLegendChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onGallery: () -> Unit
) {
    val heatmap = state.heatmap
    val photo = rememberBitmap(state.photoUri)
    val values = state.cells.mapNotNull { cell ->
        cell.valueForHeatmap(state.normalizationMode).takeIf { it.isFinite() }
    }
    val metricLabel = when (state.normalizationMode) {
        NormalizationMode.AbsoluteField -> "field"
        else -> "delta"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Heatmap Result", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Reconstructed from grid samples", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        MessagePanel(message = state.message, errorMessage = state.errorMessage)

        if (heatmap == null) {
            InstrumentPanel(title = "No Heatmap") {
                Text("Complete a scan to generate a heatmap.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            InstrumentPanel(title = if (photo == null) "Heatmap" else "Magnetic Photo") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                ) {
                    if (photo != null) {
                        Image(
                            bitmap = photo.asImageBitmap(),
                            contentDescription = "Reference photo",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.matchParentSize()
                        )
                        RectangularHeatmapOverlay(
                            render = heatmap,
                            area = state.overlayArea,
                            opacity = state.opacity,
                            modifier = Modifier.matchParentSize()
                        )
                    } else {
                        HeatmapPreview(render = heatmap, modifier = Modifier.matchParentSize())
                    }
                    if (state.showGrid) {
                        if (photo != null) {
                            RectangularGridOverlay(
                                rows = state.setup.gridHeight,
                                cols = state.setup.gridWidth,
                                area = state.overlayArea,
                                modifier = Modifier.matchParentSize()
                            )
                        } else {
                            GridOverlay(
                                rows = state.setup.gridHeight,
                                cols = state.setup.gridWidth,
                                modifier = Modifier.matchParentSize()
                            )
                        }
                    }
                }
                if (state.showLegend) {
                    StatusText("Metric", heatmap.legend.metricName)
                    StatusText("Min", "${heatmap.legend.minValue.format(2)} ${heatmap.legend.unit}")
                    StatusText("Max", "${heatmap.legend.maxValue.format(2)} ${heatmap.legend.unit}")
                }
                Text(
                    if (state.normalizationMode == NormalizationMode.AbsoluteField) {
                        "Brighter areas show stronger magnetic field magnitude. The image is reconstructed from grid samples, not captured by the camera sensor."
                    } else {
                        "Brighter areas show stronger deviation from the measured baseline. The image is reconstructed from grid samples, not captured by the camera sensor."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            InstrumentPanel(title = "Statistics") {
                StatusText("Mean $metricLabel", formatMicroTesla(MagneticMath.mean(values)))
                StatusText("Std dev", formatMicroTesla(MagneticMath.stdDev(values)))
                StatusText("Max $metricLabel", formatMicroTesla(values.maxOrNull()))
                StatusText("Cells", "${state.cells.count { it.sampleCount > 0 }}/${state.totalCells}")
            }

            InstrumentPanel(title = "Overlay Controls") {
                Text("Opacity ${(state.opacity * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(value = state.opacity, onValueChange = onOpacityChange)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Show grid")
                    Switch(checked = state.showGrid, onCheckedChange = onShowGridChange)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Show legend")
                    Switch(checked = state.showLegend, onCheckedChange = onShowLegendChange)
                }
            }

            InstrumentPanel(title = "Palette") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.paletteMode == PaletteMode.Scientific,
                        onClick = { onPaletteChange(PaletteMode.Scientific) },
                        label = { Text("Scientific") }
                    )
                    FilterChip(
                        selected = state.paletteMode == PaletteMode.MonochromeGlow,
                        onClick = { onPaletteChange(PaletteMode.MonochromeGlow) },
                        label = { Text("Glow") }
                    )
                }
            }

            InstrumentPanel(title = "Normalization") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.normalizationMode == NormalizationMode.AutoLocal,
                        onClick = { onNormalizationChange(NormalizationMode.AutoLocal) },
                        label = { Text("Auto") }
                    )
                    FilterChip(
                        selected = state.normalizationMode == NormalizationMode.BaselineDeltaFixedScale(10f),
                        onClick = { onNormalizationChange(NormalizationMode.BaselineDeltaFixedScale(10f)) },
                        label = { Text("10 µT") }
                    )
                    FilterChip(
                        selected = state.normalizationMode == NormalizationMode.BaselineDeltaFixedScale(50f),
                        onClick = { onNormalizationChange(NormalizationMode.BaselineDeltaFixedScale(50f)) },
                        label = { Text("50 µT") }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.normalizationMode == NormalizationMode.BaselineDeltaFixedScale(100f),
                        onClick = { onNormalizationChange(NormalizationMode.BaselineDeltaFixedScale(100f)) },
                        label = { Text("100 µT") }
                    )
                    FilterChip(
                        selected = state.normalizationMode == NormalizationMode.BaselineDeltaFixedScale(500f),
                        onClick = { onNormalizationChange(NormalizationMode.BaselineDeltaFixedScale(500f)) },
                        label = { Text("500 µT") }
                    )
                    FilterChip(
                        selected = state.normalizationMode == NormalizationMode.AbsoluteField,
                        onClick = { onNormalizationChange(NormalizationMode.AbsoluteField) },
                        label = { Text("Absolute") }
                    )
                }
            }

            InstrumentPanel(title = "Save and Export") {
                Button(modifier = Modifier.fillMaxWidth(), onClick = onSave) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Text("Save PNG, JSON, and CSV")
                }
                Button(modifier = Modifier.fillMaxWidth(), onClick = onGallery) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Text("Open Gallery")
                }
            }
        }
    }
}

@Composable
private fun RectangularHeatmapOverlay(
    render: com.example.magneticcamera.core.graphics.HeatmapRender,
    area: PhotoOverlayArea,
    opacity: Float,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val xs = listOf(area.topLeft.x, area.topRight.x, area.bottomRight.x, area.bottomLeft.x)
        val ys = listOf(area.topLeft.y, area.topRight.y, area.bottomRight.y, area.bottomLeft.y)
        val left = xs.minOrNull()?.coerceIn(0f, 1f) ?: 0f
        val right = xs.maxOrNull()?.coerceIn(0f, 1f) ?: 1f
        val top = ys.minOrNull()?.coerceIn(0f, 1f) ?: 0f
        val bottom = ys.maxOrNull()?.coerceIn(0f, 1f) ?: 1f
        HeatmapPreview(
            render = render,
            modifier = Modifier
                .offset(x = maxWidth * left, y = maxHeight * top)
                .size(
                    width = maxWidth * (right - left).coerceAtLeast(0.05f),
                    height = maxHeight * (bottom - top).coerceAtLeast(0.05f)
                )
                .alpha(opacity)
        )
    }
}

@Composable
private fun RectangularGridOverlay(
    rows: Int,
    cols: Int,
    area: PhotoOverlayArea,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val xs = listOf(area.topLeft.x, area.topRight.x, area.bottomRight.x, area.bottomLeft.x)
        val ys = listOf(area.topLeft.y, area.topRight.y, area.bottomRight.y, area.bottomLeft.y)
        val left = xs.minOrNull()?.coerceIn(0f, 1f) ?: 0f
        val right = xs.maxOrNull()?.coerceIn(0f, 1f) ?: 1f
        val top = ys.minOrNull()?.coerceIn(0f, 1f) ?: 0f
        val bottom = ys.maxOrNull()?.coerceIn(0f, 1f) ?: 1f
        GridOverlay(
            rows = rows,
            cols = cols,
            modifier = Modifier
                .offset(x = maxWidth * left, y = maxHeight * top)
                .size(
                    width = maxWidth * (right - left).coerceAtLeast(0.05f),
                    height = maxHeight * (bottom - top).coerceAtLeast(0.05f)
                )
        )
    }
}

@Composable
private fun GridOverlay(rows: Int, cols: Int, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
    Canvas(modifier = modifier) {
        for (col in 1 until cols) {
            val x = size.width * col / cols
            drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5f)
        }
        for (row in 1 until rows) {
            val y = size.height * row / rows
            drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
        }
    }
}

private fun Float.format(decimals: Int): String {
    return "%.${decimals}f".format(this)
}

private fun formatMicroTesla(value: Float?): String {
    return value?.takeIf { it.isFinite() }?.let { "${it.format(2)} µT" } ?: "--"
}
