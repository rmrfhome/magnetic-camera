package com.example.magneticcamera.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.magneticcamera.core.sensors.MagneticSensorInfo
import com.example.magneticcamera.ui.common.InstrumentPanel
import com.example.magneticcamera.ui.common.StatusText

@Composable
fun HomeScreen(
    sensorInfo: MagneticSensorInfo?,
    cameraAvailable: Boolean,
    showFirstLaunchExplanation: Boolean,
    onDismissFirstLaunch: () -> Unit,
    onLiveMeter: () -> Unit,
    onNewScan: () -> Unit,
    onGallery: () -> Unit,
    onSettings: () -> Unit,
    partialScanLabel: String?,
    partialScanActionLabel: String,
    onResumeScan: () -> Unit,
    onDiscardPartialScan: () -> Unit
) {
    if (showFirstLaunchExplanation) {
        AlertDialog(
            onDismissRequest = onDismissFirstLaunch,
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            title = { Text("Magnetic Camera") },
            text = {
                Text(
                    "Magnetic Camera uses your phone's magnetometer. The phone measures the magnetic field at one sensor point inside the device. To create an image-like heatmap, you scan multiple positions over a surface.\n\n" +
                        "This app is experimental. It is not an X-ray, not a wall-wire detector, not a medical device, not a safety instrument, and not a professional diagnostic tool."
                )
            },
            confirmButton = {
                TextButton(onClick = onDismissFirstLaunch) {
                    Text("Continue")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "Magnetic Camera",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Point-sensor magnetic field reconstruction",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }

        InstrumentPanel(title = "Instrument") {
            Text(
                "Remove magnetic phone cases, MagSafe-style rings, wallet cases, and metal plates before scanning.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onLiveMeter
            ) {
                Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text("Live Meter")
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNewScan
            ) {
                Icon(Icons.Default.GridOn, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text("New Surface Scan")
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onGallery
            ) {
                Icon(Icons.Default.Collections, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text("Gallery")
            }
        }

        if (partialScanLabel != null) {
            InstrumentPanel(title = "In-Progress Scan") {
                Text(partialScanLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(modifier = Modifier.weight(1f), onClick = onResumeScan) {
                        Text(partialScanActionLabel)
                    }
                    TextButton(modifier = Modifier.weight(1f), onClick = onDiscardPartialScan) {
                        Text("Discard")
                    }
                }
            }
        }

        InstrumentPanel(title = "Device Status") {
            StatusText(
                label = "Magnetometer",
                value = when (sensorInfo?.isAvailable) {
                    true -> "Available"
                    false -> "Unavailable"
                    null -> "Checking"
                },
                alert = sensorInfo?.isAvailable == false
            )
            StatusText(
                label = "Sensor type",
                value = sensorInfo?.typeLabel ?: "Checking"
            )
            StatusText(
                label = "Camera",
                value = if (cameraAvailable) "Available" else "Unavailable",
                alert = !cameraAvailable
            )
            if (sensorInfo?.isAvailable == false) {
                Text(
                    "This device does not expose a magnetic field sensor. Magnetic Camera cannot scan magnetic fields on this device.",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        InstrumentPanel(title = "Physical Limits") {
            Text(
                "Heatmaps are reconstructed measurements, not direct camera images. The sensor point is inside the phone and may not align with the visible camera center.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Magnetic Camera is not an X-ray, wall-wire detector, safety instrument, medical device, or professional diagnostic tool.",
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(12.dp))
    }
}
