package com.example.magneticcamera.ui.gallery

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.magneticcamera.core.sensors.magneticSensorTypeLabel
import com.example.magneticcamera.data.db.SessionWithCells
import com.example.magneticcamera.ui.common.InstrumentPanel
import com.example.magneticcamera.ui.common.MessagePanel
import com.example.magneticcamera.ui.common.StatusText
import com.example.magneticcamera.ui.common.rememberBitmap
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun SessionDetailScreen(
    sessionWithCells: SessionWithCells?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var exportMessage by remember(sessionWithCells?.session?.id) { mutableStateOf<String?>(null) }
    var exportError by remember(sessionWithCells?.session?.id) { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Session Detail", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(sessionWithCells?.session?.name ?: "Loading", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        MessagePanel(message = exportMessage, errorMessage = exportError)

        if (sessionWithCells == null) {
            Text("Loading session...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val session = sessionWithCells.session
            val resultImage = rememberBitmap(session.overlayImageUri ?: session.heatmapImageUri)
            val referencePhoto = rememberBitmap(session.photoUri)

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    SessionImagePanel(
                        title = "Saved Result",
                        image = resultImage,
                        contentDescription = "Saved scan result image"
                    )
                }
                if (session.photoUri != null) {
                    item {
                        SessionImagePanel(
                            title = "Reference Photo",
                            image = referencePhoto,
                            contentDescription = "Reference photo"
                        )
                    }
                }
                item {
                    InstrumentPanel(title = "Metadata") {
                        StatusText("Created", formatDate(session.createdAtMillis))
                        StatusText("Grid", "${session.gridWidth}x${session.gridHeight}")
                        StatusText("Device", "${session.deviceManufacturer} ${session.deviceModel}")
                        StatusText("Android", session.androidVersion)
                        StatusText("Sensor", session.sensorName ?: "Unknown")
                        StatusText("Sensor vendor", session.sensorVendor ?: "Unknown")
                        StatusText("Sensor type", magneticSensorTypeLabel(session.sensorType))
                        StatusText("Baseline", "${"%.1f".format(session.baselineMagnitude)} µT")
                        StatusText("Heatmap PNG", session.heatmapImageUri ?: "--")
                        StatusText("Overlay PNG", session.overlayImageUri ?: "--")
                        StatusText("JSON", session.rawDataUri ?: "--")
                        StatusText("CSV", session.csvDataUri ?: "--")
                    }
                }
                item {
                    InstrumentPanel(title = "Export Files") {
                        ExportButton(
                            label = "Share Heatmap PNG",
                            uriString = session.heatmapImageUri,
                            mimeType = "image/png",
                            context = context,
                            onResult = { message, error ->
                                exportMessage = message
                                exportError = error
                            }
                        )
                        ExportButton(
                            label = "Share Overlay PNG",
                            uriString = session.overlayImageUri,
                            mimeType = "image/png",
                            context = context,
                            onResult = { message, error ->
                                exportMessage = message
                                exportError = error
                            }
                        )
                        ExportButton(
                            label = "Share JSON",
                            uriString = session.rawDataUri,
                            mimeType = "application/json",
                            context = context,
                            onResult = { message, error ->
                                exportMessage = message
                                exportError = error
                            }
                        )
                        ExportButton(
                            label = "Share CSV",
                            uriString = session.csvDataUri,
                            mimeType = "text/csv",
                            context = context,
                            onResult = { message, error ->
                                exportMessage = message
                                exportError = error
                            }
                        )
                    }
                }
                item {
                    InstrumentPanel(title = "Raw Grid Values") {
                        Text(
                            "session_id,cell_id,row,col,sample_count,captured_at_millis,x_mean,y_mean,z_mean,magnitude_mean,magnitude_median,magnitude_min,magnitude_max,magnitude_stddev,magnitude_delta_mean,vector_delta_mean,vector_delta_median,vector_delta_min,vector_delta_max,vector_delta_stddev,accuracy",
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                items(sessionWithCells.cells.sortedWith(compareBy({ it.row }, { it.col }))) { cell ->
                    Text(
                        listOf(
                            cell.sessionId,
                            cell.id,
                            cell.row,
                            cell.col,
                            cell.sampleCount,
                            cell.capturedAtMillis,
                            cell.xMean.format(3),
                            cell.yMean.format(3),
                            cell.zMean.format(3),
                            cell.magnitudeMean.format(3),
                            cell.magnitudeMedian.format(3),
                            cell.magnitudeMin.format(3),
                            cell.magnitudeMax.format(3),
                            cell.magnitudeStdDev.format(3),
                            cell.magnitudeDeltaMean.format(3),
                            cell.vectorDeltaMean.format(3),
                            cell.vectorDeltaMedian.format(3),
                            cell.vectorDeltaMin.format(3),
                            cell.vectorDeltaMax.format(3),
                            cell.vectorDeltaStdDev.format(3),
                            cell.accuracy
                        ).joinToString(","),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .padding(horizontal = 14.dp)
                            .horizontalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionImagePanel(
    title: String,
    image: android.graphics.Bitmap?,
    contentDescription: String
) {
    InstrumentPanel(title = title) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
        ) {
            if (image != null) {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.matchParentSize()
                )
            } else {
                Text("No image file is available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ExportButton(
    label: String,
    uriString: String?,
    mimeType: String,
    context: Context,
    onResult: (message: String?, error: String?) -> Unit
) {
    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = uriString != null,
        onClick = {
            val result = shareExport(context, uriString, mimeType, label)
            onResult(result.getOrNull(), result.exceptionOrNull()?.message)
        }
    ) {
        Icon(Icons.Default.Share, contentDescription = null)
        Text(label)
    }
}

private fun shareExport(
    context: Context,
    uriString: String?,
    mimeType: String,
    label: String
): Result<String> = runCatching {
    val uri = uriString?.let(Uri::parse) ?: error("No export file is available.")
    val file = File(uri.path ?: error("Export path is invalid."))
    if (!file.exists()) error("Export file is missing.")
    val contentUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, contentUri)
        clipData = ClipData.newUri(context.contentResolver, label, contentUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(sendIntent, label))
    "Opened share sheet for $label."
}

private fun Float.format(decimals: Int): String {
    return "%.${decimals}f".format(this)
}

private fun formatDate(millis: Long): String {
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
}
