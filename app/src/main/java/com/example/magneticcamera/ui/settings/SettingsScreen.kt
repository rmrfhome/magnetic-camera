package com.example.magneticcamera.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.magneticcamera.core.sensors.MagneticSensorInfo
import com.example.magneticcamera.core.sensors.SensorSamplingMode
import com.example.magneticcamera.ui.common.InstrumentPanel
import com.example.magneticcamera.ui.common.StatusText

@Composable
fun SettingsScreen(
    sensorInfo: MagneticSensorInfo?,
    filterAlpha: Float,
    onFilterAlphaChange: (Float) -> Unit,
    samplingMode: SensorSamplingMode,
    onSamplingModeChange: (SensorSamplingMode) -> Unit,
    onBack: () -> Unit
) {
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
                Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Diagnostics and developer controls", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        InstrumentPanel(title = "Sensor") {
            val availabilityText = when (sensorInfo?.isAvailable) {
                true -> "Yes"
                false -> "No"
                null -> "Checking"
            }
            StatusText("Available", availabilityText, alert = sensorInfo?.isAvailable == false)
            StatusText("Name", sensorInfo?.name ?: "--")
            StatusText("Vendor", sensorInfo?.vendor ?: "--")
            StatusText("Type", sensorInfo?.typeLabel ?: "--")
            StatusText("Resolution", "${sensorInfo?.resolution ?: 0f} µT")
            StatusText("Maximum range", "${sensorInfo?.maximumRange ?: 0f} µT")
            StatusText("Minimum delay", "${sensorInfo?.minimumDelayMicros ?: 0} us")
        }

        InstrumentPanel(title = "Developer") {
            Text("Low-pass alpha ${"%.2f".format(filterAlpha)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = filterAlpha,
                onValueChange = onFilterAlphaChange,
                valueRange = 0.05f..0.8f
            )
            Text(
                "Lower values smooth readings more. Higher values respond faster to nearby magnets.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Sensor sampling", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SensorSamplingMode.entries.forEach { mode ->
                    FilterChip(
                        selected = samplingMode == mode,
                        onClick = { onSamplingModeChange(mode) },
                        label = { Text(mode.label) }
                    )
                }
            }
            Text(
                samplingMode.description + ". Actual sample rate is measured from sensor timestamps and varies by device.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        InstrumentPanel(title = "Safety and Limits") {
            Text(
                "The phone measures at one internal sensor point. A few centimeters of position error can matter near strong magnets.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Magnetic cases, metal surfaces, laptop hinges, chargers, speakers, and wallet plates can distort readings.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "This app cannot reliably detect electrical wires in walls and cannot infer object identity from magnetic data alone.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "It is not a safety, medical, or professional diagnostic instrument.",
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
