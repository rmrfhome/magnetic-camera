package com.example.magneticcamera.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.SensorManager
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.magneticcamera.core.graphics.HeatmapGenerator
import com.example.magneticcamera.core.graphics.HeatmapInput
import com.example.magneticcamera.core.graphics.MonochromeGlowPalette
import com.example.magneticcamera.core.graphics.OverlayRenderer
import com.example.magneticcamera.core.graphics.ScientificHeatmapPalette
import com.example.magneticcamera.core.math.MagneticMath
import com.example.magneticcamera.core.sensors.MagneticSample
import com.example.magneticcamera.core.sensors.MagneticSensorReader
import com.example.magneticcamera.core.sensors.SensorReadConfig
import com.example.magneticcamera.core.sensors.SensorSamplingMode
import com.example.magneticcamera.core.storage.AppFileStore
import com.example.magneticcamera.core.storage.ScanDraftStore
import com.example.magneticcamera.data.repository.ScanSessionRepository
import com.example.magneticcamera.domain.calibration.BaselineCalibrator
import com.example.magneticcamera.domain.model.PhotoOverlayArea
import com.example.magneticcamera.domain.scan.CaptureMode
import com.example.magneticcamera.domain.scan.GridScanController
import com.example.magneticcamera.domain.scan.HeatmapMetric
import com.example.magneticcamera.domain.scan.MagneticSampleProcessor
import com.example.magneticcamera.domain.scan.NormalizationMode
import com.example.magneticcamera.domain.scan.PaletteMode
import com.example.magneticcamera.domain.scan.ScanDraft
import com.example.magneticcamera.domain.scan.ScanSession
import com.example.magneticcamera.domain.scan.ScanSetup
import java.io.File
import java.util.ArrayDeque
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class ScanWorkflowViewModel(
    context: Context,
    private val sensorReader: MagneticSensorReader,
    private val repository: ScanSessionRepository,
    private val fileStore: AppFileStore,
    private val draftStore: ScanDraftStore
) : ViewModel() {
    private val appContext = context.applicationContext
    private val processor = MagneticSampleProcessor()
    private val captureProcessor = MagneticSampleProcessor()
    private val calibrator = BaselineCalibrator()
    private val gridController = GridScanController()
    private val heatmapGenerator = HeatmapGenerator()
    private val overlayRenderer = OverlayRenderer()
    private val recentProcessed = ArrayDeque<MagneticSample>()

    private val _uiState = MutableStateFlow(
        ScanUiState(currentSessionId = UUID.randomUUID().toString())
    )
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private var sensorJob: Job? = null
    private var diagnosticJob: Job? = null
    private var lastAutoCaptureMillis = 0L
    private var lastUiUpdateNanos = 0L
    private var samplingMode = SensorSamplingMode.Game
    private var diagnosticMessage: String? = null
    private var diagnosticExpiresAtMillis = 0L

    init {
        restoreDraft()
        diagnosticJob = viewModelScope.launch {
            sensorReader.diagnosticMessage.collect { message ->
                if (message != null) {
                    diagnosticMessage = message
                    diagnosticExpiresAtMillis = SystemClock.elapsedRealtime() + DIAGNOSTIC_MESSAGE_DURATION_MS
                    _uiState.value = _uiState.value.copy(errorMessage = message)
                }
            }
        }
    }

    fun startSensor() {
        if (sensorJob?.isActive == true) return
        sensorReader.start(SensorReadConfig(samplingMode))
        val sensorAvailable = sensorReader.sensorInfo.value?.isAvailable != false
        _uiState.value = _uiState.value.copy(
            isSensorActive = sensorAvailable,
            errorMessage = if (sensorAvailable) _uiState.value.errorMessage else sensorUnavailableMessage()
        )
        if (!sensorAvailable) return
        sensorJob = viewModelScope.launch {
            sensorReader.samples.collect { raw ->
                val processed = processor.process(raw, _uiState.value.baseline)
                rememberProcessed(processed)
                val stability = recentVectorDeltaStdDev(processed.timestampNanos)
                val threshold = stabilityThreshold()
                val stable = stability <= threshold
                if (shouldUpdateUi(processed.timestampNanos)) {
                    _uiState.value = _uiState.value.copy(
                        latestSample = processed,
                        stabilityStdDev = stability,
                        isStable = stable,
                        errorMessage = activeDiagnosticMessage() ?: unreliableAccuracyMessage(processed.accuracy)
                    )
                }
                maybeAutoCapture(stable)
            }
        }
    }

    fun stopSensor() {
        sensorJob?.cancel()
        sensorJob = null
        sensorReader.stop()
        _uiState.value = _uiState.value.copy(isSensorActive = false)
    }

    fun updateName(name: String) {
        val state = _uiState.value
        _uiState.value = state.copy(
            setup = state.setup.copy(name = name),
            savedSessionId = if (state.isScanStarted) null else state.savedSessionId
        )
        persistDraftIfStarted()
    }

    fun updateGridSize(size: Int) {
        val state = _uiState.value
        val sanitizedSize = size.coerceIn(1, 20)
        if (state.setup.gridWidth == sanitizedSize && state.setup.gridHeight == sanitizedSize) return
        val shouldResetStartedScan = state.isScanStarted
        _uiState.value = state.copy(
            currentSessionId = if (shouldResetStartedScan) UUID.randomUUID().toString() else state.currentSessionId,
            setup = state.setup.copy(gridWidth = sanitizedSize, gridHeight = sanitizedSize),
            isScanStarted = if (shouldResetStartedScan) false else state.isScanStarted,
            cells = emptyList(),
            heatmap = null,
            savedSessionId = null,
            message = if (shouldResetStartedScan) {
                "Grid changed to ${sanitizedSize}x$sanitizedSize. Begin the scan again so all cells match the new layout."
            } else {
                state.message
            }
        )
        persistDraftIfStarted()
    }

    fun updateGridDimensions(width: Int, height: Int) {
        val state = _uiState.value
        val sanitizedWidth = width.coerceIn(1, 20)
        val sanitizedHeight = height.coerceIn(1, 20)
        if (state.setup.gridWidth == sanitizedWidth && state.setup.gridHeight == sanitizedHeight) return
        val shouldResetStartedScan = state.isScanStarted
        _uiState.value = state.copy(
            currentSessionId = if (shouldResetStartedScan) UUID.randomUUID().toString() else state.currentSessionId,
            setup = state.setup.copy(
                gridWidth = sanitizedWidth,
                gridHeight = sanitizedHeight
            ),
            isScanStarted = if (shouldResetStartedScan) false else state.isScanStarted,
            cells = emptyList(),
            heatmap = null,
            savedSessionId = null,
            message = if (shouldResetStartedScan) {
                "Grid changed to ${sanitizedWidth}x$sanitizedHeight. Begin the scan again so all cells match the new layout."
            } else {
                state.message
            }
        )
        persistDraftIfStarted()
    }

    fun updatePhotoChoice(shouldTakePhoto: Boolean) {
        val state = _uiState.value
        _uiState.value = if (shouldTakePhoto) {
            state.copy(
                setup = state.setup.copy(shouldTakePhoto = true),
                savedSessionId = null
            )
        } else {
            state.copy(
                setup = state.setup.copy(shouldTakePhoto = false),
                photoUri = null,
                overlayArea = PhotoOverlayArea.FullFrame,
                savedSessionId = null
            )
        }
        persistDraftIfStarted()
    }

    fun updateCaptureMode(captureMode: CaptureMode) {
        _uiState.value = _uiState.value.copy(setup = _uiState.value.setup.copy(captureMode = captureMode))
        persistDraftIfStarted()
    }

    fun updatePalette(paletteMode: PaletteMode) {
        val state = _uiState.value
        if (state.isSaving) return
        if (state.paletteMode == paletteMode) return
        _uiState.value = state.copy(paletteMode = paletteMode, savedSessionId = null)
        persistDraftIfStarted()
        regenerateHeatmapIfComplete()
    }

    fun updateNormalization(normalizationMode: NormalizationMode) {
        val state = _uiState.value
        if (state.isSaving) return
        if (state.normalizationMode == normalizationMode) return
        _uiState.value = state.copy(normalizationMode = normalizationMode, savedSessionId = null)
        persistDraftIfStarted()
        regenerateHeatmapIfComplete()
    }

    fun updateOpacity(opacity: Float) {
        val state = _uiState.value
        if (state.isSaving) return
        val sanitized = opacity.coerceIn(0f, 1f)
        if (state.opacity == sanitized) return
        _uiState.value = state.copy(opacity = sanitized, savedSessionId = null)
        persistDraftIfStarted()
    }

    fun updateShowGrid(showGrid: Boolean) {
        val state = _uiState.value
        if (state.isSaving) return
        if (state.showGrid == showGrid) return
        _uiState.value = state.copy(showGrid = showGrid, savedSessionId = null)
        persistDraftIfStarted()
    }

    fun updateShowLegend(showLegend: Boolean) {
        val state = _uiState.value
        if (state.isSaving) return
        if (state.showLegend == showLegend) return
        _uiState.value = state.copy(showLegend = showLegend, savedSessionId = null)
        persistDraftIfStarted()
    }

    fun updateOverlayArea(area: PhotoOverlayArea) {
        val state = _uiState.value
        if (state.overlayArea == area) return
        _uiState.value = state.copy(overlayArea = area, savedSessionId = null)
        persistDraftIfStarted()
    }

    fun setPhotoUri(uri: String?) {
        val state = _uiState.value
        _uiState.value = state.copy(
            photoUri = uri,
            overlayArea = PhotoOverlayArea.FullFrame,
            setup = state.setup.copy(shouldTakePhoto = uri != null),
            savedSessionId = null
        )
        persistDraftIfStarted()
    }

    fun importPhoto(sourceUriString: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val outputFile = preparePhotoFile()
                    appContext.contentResolver.openInputStream(Uri.parse(sourceUriString)).use { input ->
                        if (input == null) error("Could not open selected image.")
                        outputFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    fileStore.uriString(outputFile)
                }
            }
            result
                .onSuccess { importedUri ->
                    _uiState.value = _uiState.value.copy(
                        photoUri = importedUri,
                        overlayArea = PhotoOverlayArea.FullFrame,
                        setup = _uiState.value.setup.copy(shouldTakePhoto = true),
                        savedSessionId = null,
                        message = "Imported reference photo."
                    )
                    persistDraftIfStarted()
                }
                .onFailure { error ->
                    error.rethrowIfCancellation()
                    _uiState.value = _uiState.value.copy(
                        errorMessage = error.message ?: "Could not import the selected photo."
                    )
                }
        }
    }

    fun preparePhotoFile(): File {
        return fileStore.photoFile(_uiState.value.currentSessionId)
    }

    fun calibrateBaseline() {
        if (_uiState.value.isCalibrating) return
        viewModelScope.launch {
            startSensor()
            if (sensorReader.sensorInfo.value?.isAvailable == false) {
                _uiState.value = _uiState.value.copy(
                    isCalibrating = false,
                    errorMessage = sensorUnavailableMessage()
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                isCalibrating = true,
                message = "Hold still for 2 seconds away from magnets, speakers, laptop hinges, chargers, metal tables, and magnetic cases."
            )
            val samples = collectBaselineSamples()
            val baseline = calibrator.calculate(samples, System.currentTimeMillis())
            processor.reset()
            captureProcessor.reset()
            val current = _uiState.value
            val shouldResetStartedScan = baseline != null && current.isScanStarted
            _uiState.value = current.copy(
                currentSessionId = if (shouldResetStartedScan) UUID.randomUUID().toString() else current.currentSessionId,
                baseline = baseline ?: current.baseline,
                isCalibrating = false,
                isScanStarted = if (shouldResetStartedScan) false else current.isScanStarted,
                cells = if (shouldResetStartedScan) emptyList() else current.cells,
                heatmap = if (shouldResetStartedScan) null else current.heatmap,
                savedSessionId = if (shouldResetStartedScan) null else current.savedSessionId,
                message = if (baseline == null) {
                    "Baseline failed. Keep the phone still and try again."
                } else if (shouldResetStartedScan) {
                    "Baseline reset: ${"%.1f".format(baseline.magnitudeMean)} µT. Begin the scan again so all cells use the same baseline."
                } else {
                    "Baseline set: ${"%.1f".format(baseline.magnitudeMean)} µT, noise ${"%.2f".format(baseline.magnitudeStdDev)} µT."
                }
            )
            persistDraftIfStarted()
        }
    }

    fun adoptBaseline(baseline: com.example.magneticcamera.domain.model.MagneticBaseline?) {
        if (baseline == null) return
        _uiState.value = _uiState.value.copy(baseline = baseline)
        processor.reset()
        captureProcessor.reset()
        persistDraftIfStarted()
    }

    fun beginScan(): Boolean {
        if (_uiState.value.baseline == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Set a baseline before scanning.")
            return false
        }
        startSensor()
        val state = _uiState.value
        if (sensorReader.sensorInfo.value?.isAvailable == false) {
            _uiState.value = state.copy(errorMessage = sensorUnavailableMessage())
            return false
        }
        _uiState.value = state.copy(
            currentSessionId = if (state.savedSessionId != null) UUID.randomUUID().toString() else state.currentSessionId,
            isScanStarted = true,
            cells = emptyList(),
            heatmap = null,
            savedSessionId = null,
            errorMessage = null,
            message = "Move the same point of the phone over each highlighted cell."
        )
        persistDraftIfStarted()
        return true
    }

    fun discardScan() {
        _uiState.value = ScanUiState(
            setup = _uiState.value.setup,
            currentSessionId = UUID.randomUUID().toString(),
            baseline = _uiState.value.baseline
        )
        processor.reset()
        captureProcessor.reset()
        recentProcessed.clear()
        draftStore.clear()
    }

    fun redoPreviousCell() {
        val state = _uiState.value
        if (state.cells.isEmpty() || state.isCapturing) return
        _uiState.value = state.copy(
            cells = state.cells.dropLast(1),
            heatmap = null,
            savedSessionId = null
        )
        persistDraftIfStarted()
    }

    fun skipCurrentCell() {
        val state = _uiState.value
        if (state.isCapturing || state.isComplete) return
        val row = state.currentRow
        val col = state.currentCol
        val skipped = gridController.skippedCell(state.currentSessionId, row, col)
        _uiState.value = state.copy(cells = state.cells + skipped)
        persistDraftIfStarted()
        finishIfComplete()
    }

    fun captureCurrentCell() {
        val state = _uiState.value
        if (state.isCapturing || state.isComplete) return
        val baseline = state.baseline
        if (baseline == null) {
            _uiState.value = state.copy(errorMessage = "Set a baseline before capturing cells.")
            return
        }
        val sessionId = state.currentSessionId
        val row = state.currentRow
        val col = state.currentCol
        _uiState.value = state.copy(isCapturing = true, errorMessage = null)
        viewModelScope.launch {
            startSensor()
            if (sensorReader.sensorInfo.value?.isAvailable == false) {
                if (_uiState.value.currentSessionId == sessionId) {
                    _uiState.value = _uiState.value.copy(
                        isCapturing = false,
                        errorMessage = sensorUnavailableMessage()
                    )
                }
                return@launch
            }
            val startMillis = SystemClock.elapsedRealtime()
            val samples = mutableListOf<MagneticSample>()
            captureProcessor.reset()
            withTimeoutOrNull(2_100L) {
                while (true) {
                    val raw = sensorReader.samples.first()
                    samples.add(captureProcessor.process(raw, baseline))
                    val now = SystemClock.elapsedRealtime()
                    if (gridController.shouldStopCapture(startMillis, now, samples.size)) {
                        return@withTimeoutOrNull
                    }
                }
            }
            val measurement = gridController.captureCell(
                sessionId = sessionId,
                row = row,
                col = col,
                samples = samples
            )
            val latest = _uiState.value
            if (latest.currentSessionId != sessionId || !latest.isScanStarted) {
                return@launch
            }
            if (measurement.sampleCount <= 0) {
                _uiState.value = latest.copy(
                    isCapturing = false,
                    errorMessage = "No usable magnetic samples were captured. Keep the scan screen open and try this cell again."
                )
                return@launch
            }
            _uiState.value = latest.copy(
                isCapturing = false,
                cells = latest.cells + measurement,
                message = "Cell ${latest.cells.size + 1} captured with ${measurement.sampleCount} samples."
            )
            persistDraftIfStarted()
            finishIfComplete()
        }
    }

    fun saveResult() {
        val state = _uiState.value
        val heatmap = state.heatmap
        val baseline = state.baseline
        if (state.isSaving) return
        if (heatmap == null || baseline == null || !state.isComplete) {
            _uiState.value = state.copy(errorMessage = "Complete a scan before saving.")
            return
        }
        viewModelScope.launch {
            _uiState.value = state.copy(
                isSaving = true,
                message = "Saving scan and exports...",
                errorMessage = null
            )
            val result = runCatching {
                val photoUri = state.photoUri.takeIf { state.setup.shouldTakePhoto }
                val (exportHeatmap, overlayBitmap) = withContext(Dispatchers.Default) {
                    val render = buildHeatmap(state, 2048, 2048)
                    render to buildOverlayBitmap(state, render)
                }
                if (photoUri != null && overlayBitmap == null) {
                    error("Could not load the reference photo for overlay export. Reimport the photo or continue without one.")
                }
                val session = ScanSession(
                    id = state.currentSessionId,
                    name = state.setup.name.ifBlank { "Surface scan" },
                    createdAtMillis = System.currentTimeMillis(),
                    gridWidth = state.setup.gridWidth,
                    gridHeight = state.setup.gridHeight,
                    baseline = baseline,
                    photoUri = photoUri,
                    overlayArea = state.overlayArea,
                    cells = state.cells
                )
                repository.saveSession(
                    session = session,
                    sensorInfo = sensorReader.sensorInfo.value,
                    heatmapRender = exportHeatmap,
                    overlayBitmap = overlayBitmap,
                    includeGrid = state.showGrid,
                    includeLegend = state.showLegend
                )
            }
            result
                .onSuccess { savedId ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        savedSessionId = savedId,
                        message = "Saved scan and exported PNG, JSON, and CSV.",
                        errorMessage = null
                    )
                    draftStore.clear()
                }
                .onFailure { error ->
                    error.rethrowIfCancellation()
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        message = null,
                        errorMessage = error.message ?: "Could not save scan exports."
                    )
                }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null, errorMessage = null)
    }

    fun updateFilterAlpha(alpha: Float) {
        processor.setAlpha(alpha)
        captureProcessor.setAlpha(alpha)
    }

    fun updateSamplingMode(mode: SensorSamplingMode) {
        if (samplingMode == mode) return
        val wasActive = sensorJob?.isActive == true
        samplingMode = mode
        if (wasActive) {
            stopSensor()
            startSensor()
        }
    }

    fun prepareNewScan() {
        val state = _uiState.value
        if (state.hasUnsavedScan) return
        _uiState.value = ScanUiState(
            currentSessionId = UUID.randomUUID().toString(),
            baseline = state.baseline
        )
        processor.reset()
        captureProcessor.reset()
        recentProcessed.clear()
        draftStore.clear()
    }

    override fun onCleared() {
        super.onCleared()
        diagnosticJob?.cancel()
        stopSensor()
    }

    private suspend fun collectBaselineSamples(): List<MagneticSample> {
        val startedAt = SystemClock.elapsedRealtime()
        val samples = mutableListOf<MagneticSample>()
        withTimeoutOrNull(2_300L) {
            while (SystemClock.elapsedRealtime() - startedAt < 2_200L) {
                val sample = sensorReader.samples.first()
                if (SystemClock.elapsedRealtime() - startedAt >= 200L) {
                    samples.add(sample)
                }
            }
        }
        return samples
    }

    private fun maybeAutoCapture(stable: Boolean) {
        val state = _uiState.value
        val now = SystemClock.elapsedRealtime()
        if (state.setup.captureMode != CaptureMode.AutoWhenStable) return
        if (!state.isScanStarted || state.isComplete || state.isCapturing || !stable) return
        if (now - lastAutoCaptureMillis < 1_200L) return
        lastAutoCaptureMillis = now
        captureCurrentCell()
    }

    private fun shouldUpdateUi(timestampNanos: Long): Boolean {
        if (timestampNanos - lastUiUpdateNanos < 33_000_000L) return false
        lastUiUpdateNanos = timestampNanos
        return true
    }

    private fun finishIfComplete() {
        if (_uiState.value.isComplete) {
            regenerateHeatmapIfComplete()
            _uiState.value = _uiState.value.copy(message = "Scan complete. Heatmap generated.")
            persistDraftIfStarted()
        }
    }

    private fun restoreDraft() {
        val draft = draftStore.load() ?: return
        _uiState.value = ScanUiState(
            setup = draft.setup,
            currentSessionId = draft.currentSessionId,
            baseline = draft.baseline,
            photoUri = draft.photoUri,
            overlayArea = draft.overlayArea,
            isScanStarted = draft.isScanStarted || draft.cells.isNotEmpty(),
            cells = draft.cells,
            paletteMode = draft.paletteMode,
            normalizationMode = draft.normalizationMode,
            opacity = draft.opacity,
            showGrid = draft.showGrid,
            showLegend = draft.showLegend,
            message = when {
                draft.cells.isNotEmpty() -> "Restored partial scan with ${draft.cells.size} captured cells."
                draft.isScanStarted -> "Restored in-progress scan."
                else -> "Restored scan setup."
            }
        )
        regenerateHeatmapIfComplete()
    }

    private fun persistDraftIfStarted() {
        val state = _uiState.value
        if (state.baseline == null && state.photoUri == null && state.cells.isEmpty() && !state.isScanStarted) {
            draftStore.clear()
            return
        }
        draftStore.save(
            ScanDraft(
                setup = state.setup,
                currentSessionId = state.currentSessionId,
                baseline = state.baseline,
                photoUri = state.photoUri,
                overlayArea = state.overlayArea,
                isScanStarted = state.isScanStarted,
                cells = state.cells,
                paletteMode = state.paletteMode,
                normalizationMode = state.normalizationMode,
                opacity = state.opacity,
                showGrid = state.showGrid,
                showLegend = state.showLegend
            )
        )
    }

    private fun regenerateHeatmapIfComplete() {
        val state = _uiState.value
        if (!state.isComplete) return
        viewModelScope.launch {
            val heatmap = withContext(Dispatchers.Default) {
                buildHeatmap(state, 512, 512)
            }
            val latest = _uiState.value
            if (
                latest.currentSessionId == state.currentSessionId &&
                latest.cells == state.cells &&
                latest.paletteMode == state.paletteMode &&
                latest.normalizationMode == state.normalizationMode
            ) {
                _uiState.value = latest.copy(heatmap = heatmap)
            }
        }
    }

    private fun buildHeatmap(state: ScanUiState, width: Int, height: Int) = heatmapGenerator.generate(
        input = HeatmapInput(
            gridWidth = state.setup.gridWidth,
            gridHeight = state.setup.gridHeight,
            values = heatmapValues(state),
            metric = if (state.normalizationMode == NormalizationMode.AbsoluteField) {
                HeatmapMetric.AbsoluteField
            } else {
                HeatmapMetric.VectorDelta
            }
        ),
        outputWidth = width,
        outputHeight = height,
        normalizationMode = state.normalizationMode,
        palette = when (state.paletteMode) {
            PaletteMode.Scientific -> ScientificHeatmapPalette
            PaletteMode.MonochromeGlow -> MonochromeGlowPalette
        }
    )

    private fun heatmapValues(state: ScanUiState): List<Float> {
        val byIndex = state.cells.associateBy { it.row * state.setup.gridWidth + it.col }
        return (0 until state.totalCells).map { index ->
            byIndex[index]?.valueForHeatmap(state.normalizationMode) ?: Float.NaN
        }
    }

    private fun rememberProcessed(sample: MagneticSample) {
        recentProcessed.addLast(sample)
        while (recentProcessed.size > 200) {
            recentProcessed.removeFirst()
        }
    }

    private fun recentVectorDeltaStdDev(nowNanos: Long): Float {
        val recent = recentProcessed
            .filter { nowNanos - it.timestampNanos <= 500_000_000L }
            .map { it.baselineVectorDeltaMicroTesla }
        return MagneticMath.stdDev(recent).takeIf { it.isFinite() } ?: 0f
    }

    private fun stabilityThreshold(): Float {
        val baselineNoise = _uiState.value.baseline?.magnitudeStdDev ?: 0f
        return maxOf(1.5f * baselineNoise, 1.0f)
    }

    private fun noMagnetometerMessage(): String {
        return "This device does not expose a magnetic field sensor. Magnetic Camera cannot scan magnetic fields on this device."
    }

    private fun sensorUnavailableMessage(): String {
        return sensorReader.diagnosticMessage.value ?: activeDiagnosticMessage() ?: noMagnetometerMessage()
    }

    private fun unreliableAccuracyMessage(accuracy: Int): String? {
        return if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            "Sensor accuracy is currently unreliable. Move the phone in a figure-eight motion or recalibrate baseline."
        } else {
            null
        }
    }

    private fun activeDiagnosticMessage(): String? {
        if (diagnosticMessage == null) return null
        if (SystemClock.elapsedRealtime() <= diagnosticExpiresAtMillis) return diagnosticMessage
        diagnosticMessage = null
        return null
    }

    private fun buildOverlayBitmap(
        state: ScanUiState,
        heatmap: com.example.magneticcamera.core.graphics.HeatmapRender
    ): Bitmap? {
        val photoUri = state.photoUri.takeIf { state.setup.shouldTakePhoto } ?: return null
        val photo = loadBitmap(photoUri) ?: return null
        return overlayRenderer.renderRectangularOverlay(
            photo = photo,
            heatmap = heatmap.toBitmap(),
            area = state.overlayArea,
            opacity = state.opacity,
            showGrid = state.showGrid,
            gridWidth = state.setup.gridWidth,
            gridHeight = state.setup.gridHeight
        )
    }

    private fun loadBitmap(uriString: String): Bitmap? {
        return runCatching {
            appContext.contentResolver.openInputStream(Uri.parse(uriString)).use { input ->
                if (input == null) null else BitmapFactory.decodeStream(input)
            }
        }.getOrNull()
    }

    private fun Throwable.rethrowIfCancellation() {
        if (this is CancellationException) throw this
    }

    private companion object {
        const val DIAGNOSTIC_MESSAGE_DURATION_MS = 4_000L
    }
}
