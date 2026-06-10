package com.example.magneticcamera.ui.live

import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.magneticcamera.core.sensors.MagneticSample
import com.example.magneticcamera.ui.common.InstrumentPanel
import com.example.magneticcamera.ui.common.MagneticGraph
import com.example.magneticcamera.ui.common.MessagePanel
import com.example.magneticcamera.ui.common.MetricReadout
import com.example.magneticcamera.ui.common.SensorLifecycleEffect
import com.example.magneticcamera.ui.common.StatusText
import java.text.DateFormat
import java.util.Date

@Composable
fun LiveMeterScreen(
    state: LiveMeterUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    onSetBaseline: () -> Unit,
    onFreeze: () -> Unit,
    onSaveSnapshot: () -> Unit,
    onStartScan: () -> Unit
) {
    SensorLifecycleEffect(onStart = onStart, onStop = onStop)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Live Meter", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Magnetic field vector", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        MessagePanel(message = state.message, errorMessage = state.errorMessage)

        InstrumentPanel(title = "Field Strength") {
            MetricReadout(
                label = "Magnitude",
                value = state.latestSample?.magnitudeMicroTesla?.format(1) ?: "--",
                unit = "µT",
                emphasized = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetricReadout("Delta", state.latestSample?.baselineDeltaMicroTesla?.format(1) ?: "--", "µT", Modifier.weight(1f))
                MetricReadout("Vector", state.latestSample?.baselineVectorDeltaMicroTesla?.format(1) ?: "--", "µT", Modifier.weight(1f))
            }
            MagneticIntensityBlob(sample = state.latestSample)
        }

        InstrumentPanel(title = "Vector Components") {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetricReadout("X", state.latestSample?.xMicroTesla?.format(1) ?: "--", "µT", Modifier.weight(1f))
                MetricReadout("Y", state.latestSample?.yMicroTesla?.format(1) ?: "--", "µT", Modifier.weight(1f))
                MetricReadout("Z", state.latestSample?.zMicroTesla?.format(1) ?: "--", "µT", Modifier.weight(1f))
            }
            StatusText("Accuracy", state.accuracyLabel, alert = state.accuracyLabel == "Unreliable")
            StatusText("Sampling", "${state.samplingRateHz.format(1)} Hz")
        }

        InstrumentPanel(title = "Trace") {
            MagneticGraph(samples = state.recentSamples)
        }

        state.snapshotSample?.let { snapshot ->
            InstrumentPanel(title = "Saved Snapshot") {
                StatusText("Captured", state.snapshotCreatedAtMillis?.formatTime() ?: "--")
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MetricReadout(
                        "Magnitude",
                        snapshot.magnitudeMicroTesla.format(1),
                        "µT",
                        Modifier.weight(1f)
                    )
                    MetricReadout(
                        "Vector",
                        snapshot.baselineVectorDeltaMicroTesla.format(1),
                        "µT",
                        Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MetricReadout("X", snapshot.xMicroTesla.format(1), "µT", Modifier.weight(1f))
                    MetricReadout("Y", snapshot.yMicroTesla.format(1), "µT", Modifier.weight(1f))
                    MetricReadout("Z", snapshot.zMicroTesla.format(1), "µT", Modifier.weight(1f))
                }
                val snapshotAccuracy = snapshot.accuracy.toAccuracyLabel()
                StatusText("Accuracy", snapshotAccuracy, alert = snapshotAccuracy == "Unreliable")
            }
        }

        InstrumentPanel(title = "Actions") {
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isCalibrating,
                onClick = onSetBaseline
            ) {
                Icon(Icons.Default.FiberManualRecord, contentDescription = null)
                Text(if (state.isCalibrating) "Calibrating..." else "Set Baseline")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(modifier = Modifier.weight(1f), onClick = onFreeze) {
                    Icon(Icons.Default.Pause, contentDescription = null)
                    Text(if (state.isFrozen) "Resume" else "Freeze")
                }
                OutlinedButton(modifier = Modifier.weight(1f), onClick = onSaveSnapshot) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Text("Snapshot")
                }
            }
            Button(modifier = Modifier.fillMaxWidth(), onClick = onStartScan) {
                Icon(Icons.Default.Camera, contentDescription = null)
                Text("Start Surface Scan")
            }
        }

        InstrumentPanel(title = "Sensor Diagnostics") {
            StatusText("Name", state.sensorInfo?.name ?: "Unavailable")
            StatusText("Vendor", state.sensorInfo?.vendor ?: "--")
            StatusText("Type", state.sensorInfo?.typeLabel ?: "--")
            StatusText("Resolution", "${state.sensorInfo?.resolution?.format(3) ?: "--"} µT")
            StatusText("Range", "${state.sensorInfo?.maximumRange?.format(1) ?: "--"} µT")
            StatusText("Minimum delay", "${state.sensorInfo?.minimumDelayMicros ?: 0} us")
            val latestSample = state.latestSample
            if (latestSample?.biasXMicroTesla != null ||
                latestSample?.biasYMicroTesla != null ||
                latestSample?.biasZMicroTesla != null
            ) {
                StatusText("Hard-iron bias", "Uncalibrated sensor estimate")
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MetricReadout(
                        "Bias X",
                        latestSample.biasXMicroTesla?.format(1) ?: "--",
                        "µT",
                        Modifier.weight(1f)
                    )
                    MetricReadout(
                        "Bias Y",
                        latestSample.biasYMicroTesla?.format(1) ?: "--",
                        "µT",
                        Modifier.weight(1f)
                    )
                    MetricReadout(
                        "Bias Z",
                        latestSample.biasZMicroTesla?.format(1) ?: "--",
                        "µT",
                        Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private fun Float.format(decimals: Int): String {
    return "%.${decimals}f".format(this)
}

@Composable
private fun MagneticIntensityBlob(
    sample: MagneticSample?,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val intensity = sample?.baselineVectorDeltaMicroTesla
        ?.takeIf { it > 0f }
        ?: sample?.magnitudeMicroTesla
        ?: 0f
    val normalized = (intensity / 80f).coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(132.dp)
    ) {
        val radius = size.minDimension * (0.16f + normalized * 0.28f)
        drawCircle(
            color = primary.copy(alpha = 0.08f),
            radius = size.minDimension * 0.48f,
            center = center
        )
        drawCircle(
            color = secondary.copy(alpha = 0.18f + normalized * 0.24f),
            radius = radius * 1.7f,
            center = center
        )
        drawCircle(
            color = primary.copy(alpha = 0.42f + normalized * 0.38f),
            radius = radius,
            center = center
        )
        drawCircle(
            color = onSurface.copy(alpha = 0.75f),
            radius = size.minDimension * 0.025f,
            center = center
        )
    }
}

private fun Long.formatTime(): String {
    return DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(this))
}

private fun Int.toAccuracyLabel(): String {
    return when (this) {
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "High"
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium"
        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low"
        SensorManager.SENSOR_STATUS_UNRELIABLE -> "Unreliable"
        else -> "Unknown"
    }
}
