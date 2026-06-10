package com.example.magneticcamera.ui.scan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.magneticcamera.ui.common.InstrumentPanel
import com.example.magneticcamera.ui.common.MessagePanel
import com.example.magneticcamera.ui.common.MetricReadout
import com.example.magneticcamera.ui.common.SensorLifecycleEffect
import com.example.magneticcamera.ui.common.StatusText

@Composable
fun ScanCaptureScreen(
    state: ScanUiState,
    onStartSensor: () -> Unit,
    onStopSensor: () -> Unit,
    onBack: () -> Unit,
    onCapture: () -> Unit,
    onRedo: () -> Unit,
    onSkip: () -> Unit,
    onDiscard: () -> Unit,
    onViewResult: () -> Unit
) {
    SensorLifecycleEffect(onStart = onStartSensor, onStop = onStopSensor)

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
                Text("Scan Capture", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Cell ${(state.currentCellIndex + 1).coerceAtMost(state.totalCells)} of ${state.totalCells}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        MessagePanel(message = state.message, errorMessage = state.errorMessage)

        InstrumentPanel(title = "Grid") {
            CaptureGrid(state = state)
            LinearProgressIndicator(
                progress = { state.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        InstrumentPanel(title = "Current Reading") {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetricReadout(
                    label = "Vector delta",
                    value = state.latestSample?.baselineVectorDeltaMicroTesla?.format(1) ?: "--",
                    unit = "µT",
                    modifier = Modifier.weight(1f),
                    emphasized = true
                )
                StabilityRing(
                    isStable = state.isStable,
                    stdDev = state.stabilityStdDev,
                    modifier = Modifier.weight(1f)
                )
            }
            StatusText("Mode", state.setup.captureMode.name)
            StatusText("Captured", "${state.cells.size}/${state.totalCells}")
        }

        InstrumentPanel(title = "Capture") {
            if (state.isComplete) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.heatmap != null,
                    onClick = onViewResult
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Text(if (state.heatmap == null) "Generating Heatmap..." else "View Heatmap Result")
                }
            } else {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isCapturing,
                    onClick = onCapture
                ) {
                    Icon(Icons.Default.RadioButtonChecked, contentDescription = null)
                    Text(if (state.isCapturing) "Capturing..." else "Capture Cell")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(modifier = Modifier.weight(1f), enabled = state.cells.isNotEmpty(), onClick = onRedo) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null)
                    Text("Redo")
                }
                OutlinedButton(modifier = Modifier.weight(1f), enabled = !state.isComplete, onClick = onSkip) {
                    Icon(Icons.Default.SkipNext, contentDescription = null)
                    Text("Skip")
                }
            }
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onDiscard) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Text("Discard Scan")
            }
        }
    }
}

@Composable
private fun CaptureGrid(state: ScanUiState) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val completed = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val border = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .padding(6.dp)
    ) {
        val cellWidth = size.width / state.setup.gridWidth
        val cellHeight = size.height / state.setup.gridHeight
        val capturedIndexes = state.cells.map { it.row * state.setup.gridWidth + it.col }.toSet()
        for (row in 0 until state.setup.gridHeight) {
            for (col in 0 until state.setup.gridWidth) {
                val index = row * state.setup.gridWidth + col
                val left = col * cellWidth
                val top = row * cellHeight
                val isCurrent = index == state.currentCellIndex && !state.isComplete
                val color = when {
                    isCurrent -> secondary.copy(alpha = 0.72f)
                    index in capturedIndexes -> completed
                    else -> surface.copy(alpha = 0.38f)
                }
                drawRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(cellWidth, cellHeight)
                )
                drawRect(
                    color = if (isCurrent) primary else border,
                    topLeft = Offset(left, top),
                    size = Size(cellWidth, cellHeight),
                    style = Stroke(width = if (isCurrent) 4f else 1f)
                )
            }
        }
    }
}

@Composable
private fun StabilityRing(
    isStable: Boolean,
    stdDev: Float,
    modifier: Modifier = Modifier
) {
    val color = if (isStable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Column(modifier = modifier) {
        Text("Stability", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.8f)
        ) {
            val radius = size.minDimension / 3f
            drawCircle(
                color = color.copy(alpha = 0.2f),
                radius = radius,
                center = center
            )
            drawCircle(
                color = color,
                radius = radius,
                center = center,
                style = Stroke(width = 8f)
            )
        }
        Text(
            text = if (isStable) "Stable ${stdDev.format(2)} µT" else "Moving ${stdDev.format(2)} µT",
            color = color,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun Float.format(decimals: Int): String {
    return "%.${decimals}f".format(this)
}
