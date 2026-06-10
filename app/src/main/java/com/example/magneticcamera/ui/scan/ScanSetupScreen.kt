package com.example.magneticcamera.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.magneticcamera.camera.CameraPreview
import com.example.magneticcamera.camera.PhotoCaptureController
import com.example.magneticcamera.domain.scan.CaptureMode
import com.example.magneticcamera.ui.common.InstrumentPanel
import com.example.magneticcamera.ui.common.MessagePanel
import com.example.magneticcamera.ui.common.PhotoAreaSelector
import com.example.magneticcamera.ui.common.SensorLifecycleEffect
import com.example.magneticcamera.ui.common.StatusText

@Composable
fun ScanSetupScreen(
    state: ScanUiState,
    cameraAvailable: Boolean,
    onStartSensor: () -> Unit,
    onStopSensor: () -> Unit,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onGridSizeChange: (Int) -> Unit,
    onGridDimensionsChange: (Int, Int) -> Unit,
    onPhotoChoiceChange: (Boolean) -> Unit,
    onCaptureModeChange: (CaptureMode) -> Unit,
    onCalibrateBaseline: () -> Unit,
    onTakePhoto: () -> Unit,
    onImportPhoto: (String) -> Unit,
    onOverlayAreaChange: (com.example.magneticcamera.domain.model.PhotoOverlayArea) -> Unit,
    onBeginScan: () -> Unit
) {
    val importPhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onImportPhoto(uri.toString())
        }
    }

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
                Text("New Surface Scan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Manual grid magnetic reconstruction", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        MessagePanel(message = state.message, errorMessage = state.errorMessage)

        InstrumentPanel(title = "Setup") {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.setup.name,
                onValueChange = onNameChange,
                label = { Text("Scan name") },
                singleLine = true
            )
            Text("Grid size", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 7, 10).forEach { size ->
                    FilterChip(
                        selected = state.setup.gridWidth == size,
                        onClick = { onGridSizeChange(size) },
                        label = { Text("${size}x$size") }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = state.setup.gridWidth.toString(),
                    onValueChange = { value ->
                        value.toIntOrNull()?.let { onGridDimensionsChange(it, state.setup.gridHeight) }
                    },
                    label = { Text("Columns") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = state.setup.gridHeight.toString(),
                    onValueChange = { value ->
                        value.toIntOrNull()?.let { onGridDimensionsChange(state.setup.gridWidth, it) }
                    },
                    label = { Text("Rows") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Reference photo", color = MaterialTheme.colorScheme.onSurface)
                Switch(checked = state.setup.shouldTakePhoto, onCheckedChange = onPhotoChoiceChange)
            }
            if (state.setup.shouldTakePhoto) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = cameraAvailable,
                    onClick = onTakePhoto
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Text(
                        when {
                            !cameraAvailable -> "Camera Unavailable"
                            state.photoUri == null -> "Take Reference Photo"
                            else -> "Retake Reference Photo"
                        }
                    )
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { importPhotoLauncher.launch("image/*") }
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Text(if (state.photoUri == null) "Import Reference Photo" else "Replace With Imported Photo")
                }
                if (!cameraAvailable) {
                    Text(
                        "Camera hardware is unavailable on this device. You can import a reference image or scan without a photo.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (state.setup.shouldTakePhoto && state.photoUri != null) {
            InstrumentPanel(title = "Scan Area") {
                Text(
                    "Drag the four corners to cover the physical area you will scan.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PhotoAreaSelector(
                    photoUri = state.photoUri,
                    area = state.overlayArea,
                    onAreaChange = onOverlayAreaChange
                )
            }
        }

        InstrumentPanel(title = "Baseline") {
            Text(
                "First measure the local background field. Hold the phone still away from magnets, speakers, laptops, chargers, and metal surfaces.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isCalibrating,
                onClick = onCalibrateBaseline
            ) {
                Text(
                    when {
                        state.isCalibrating -> "Calibrating..."
                        state.baseline == null -> "Set Baseline"
                        else -> "Recalibrate Baseline"
                    }
                )
            }
            StatusText("Current baseline", state.baseline?.let { "${"%.1f".format(it.magnitudeMean)} µT" } ?: "Not set")
            StatusText("Noise", state.baseline?.let { "${"%.2f".format(it.magnitudeStdDev)} µT" } ?: "--")
            StatusText("Samples", state.baseline?.sampleCount?.toString() ?: "--")
        }

        InstrumentPanel(title = "Capture Mode") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.setup.captureMode == CaptureMode.Manual,
                    onClick = { onCaptureModeChange(CaptureMode.Manual) },
                    label = { Text("Manual") }
                )
                FilterChip(
                    selected = state.setup.captureMode == CaptureMode.AutoWhenStable,
                    onClick = { onCaptureModeChange(CaptureMode.AutoWhenStable) },
                    label = { Text("Auto stable") }
                )
            }
            Text(
                "Move the same point of the phone over each highlighted cell. Keep height and orientation steady.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = state.baseline != null,
            onClick = onBeginScan
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Text("Begin ${state.setup.gridWidth}x${state.setup.gridHeight} Scan")
        }
    }
}

@Composable
fun CameraCaptureScreen(
    cameraAvailable: Boolean,
    photoFileProvider: () -> java.io.File,
    onPhotoSaved: (String) -> Unit,
    onBack: () -> Unit,
    onSkipPhoto: () -> Unit
) {
    val context = LocalContext.current
    val controller = remember { PhotoCaptureController() }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var hasPermission by remember {
        mutableStateOf(
            cameraAvailable &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) {
            error = "Camera permission is needed only for Magnetic Photo overlays. You can still run a grid scan without a reference photo."
        }
    }

    LaunchedEffect(cameraAvailable) {
        if (cameraAvailable && !hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

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
                Text("Reference Photo", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Photo overlay is optional", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        MessagePanel(message = null, errorMessage = error)

        if (cameraAvailable && hasPermission) {
            CameraPreview(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                onImageCaptureReady = { imageCapture = it }
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = imageCapture != null,
                onClick = {
                    controller.capture(
                        context = context,
                        imageCapture = imageCapture ?: return@Button,
                        outputFile = photoFileProvider()
                    ) { result ->
                        result
                            .onSuccess(onPhotoSaved)
                            .onFailure { error = it.message ?: "Photo capture failed." }
                    }
                }
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Text("Capture Photo")
            }
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onSkipPhoto) {
                Text("Continue Without Photo")
            }
        } else {
            InstrumentPanel(title = "Camera Unavailable") {
                Text(
                    if (cameraAvailable) {
                        "Surface scanning still works without a photo. You can generate a heatmap and export JSON/CSV."
                    } else {
                        "This device does not expose camera hardware. You can still run a grid scan without a reference photo."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onSkipPhoto) {
                    Text("Continue Without Photo")
                }
            }
        }
    }
}
