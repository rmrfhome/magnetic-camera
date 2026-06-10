package com.example.magneticcamera.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import com.example.magneticcamera.domain.model.NormalizedPoint
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
    private var lastAutoCaptureMillis = 0L
    private var lastUiUpdateNanos = 0L
    private var samplingMode = SensorSamplingMode.Game

    init {
        restoreDraft()
    }

    fun startSensor() {
        if (sensorJob?.isActive == true) return
        sensorReader.start(SensorReadConfig(samplingMode))
        val sensorAvailable = sensorReader.sensorInfo.value?.isAvailable != false
        _uiState.value = _uiState.value.copy(
            isSensorActive = sensorAvailable,
            errorMessage = if (sensorAvailable) _uiState.value.errorMessage else noMagnetometerMessage()
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
                        errorMessage = null
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
        _uiState.value = _uiState.value.copy(setup = _uiState.value.setup.copy(name = name))
        persistDraftIfStarted()
    }

    fun updateGridSize(size: Int) {
        _uiState.value = _uiState.value.copy(
            setup = _uiState.value.setup.copy(gridWidth = size, gridHeight = size),
            cells = emptyList(),
            heatmap = null,
            savedSessionId = null
        )
        persistDraftIfStarted()
    }

    fun updateGridDimensions(width: Int, height: Int) {
        _uiState.value = _uiState.value.copy(
            setup = _uiState.value.setup.copy(
                gridWidth = width.coerceIn(1, 20),
                gridHeight = height.coerceIn(1, 20)
            ),
            cells = emptyList(),
            heatmap = null,
            savedSessionId = null
        )
        persistDraftIfStarted()
    }

    fun updatePhotoChoice(shouldTakePhoto: Boolean) {
        _uiState.value = _uiState.value.copy(setup = _uiState.value.setup.copy(shouldTakePhoto = shouldTakePhoto))
        persistDraftIfStarted()
    }

    fun updateCaptureMode(captureMode: CaptureMode) {
        _uiState.value = _uiState.value.copy(setup = _uiState.value.setup.copy(captureMode = captureMode))
        persistDraftIfStarted()
    }

    fun updatePalette(paletteMode: PaletteMode) {
        _uiState.value = _uiState.value.copy(paletteMode = paletteMode)
        regenerateHeatmapIfComplete()
    }

    fun updateNormalization(normalizationMode: NormalizationMode) {
        _uiState.value = _uiState.value.copy(normalizationMode = normalizationMode)
        regenerateHeatmapIfComplete()
    }

    fun updateOpacity(opacity: Float) {
        _uiState.value = _uiState.value.copy(opacity = opacity.coerceIn(0f, 1f))
    }

    fun updateShowLegend(showLegend: Boolean) {
        _uiState.value = _uiState.value.copy(showLegend = showLegend)
    }

    fun updateOverlayArea(area: PhotoOverlayArea) {
        _uiState.value = _uiState.value.copy(overlayArea = area)
        persistDraftIfStarted()
    }

    fun setPhotoUri(uri: String?) {
        _uiState.value = _uiState.value.copy(photoUri = uri)
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
                        setup = _uiState.value.setup.copy(shouldTakePhoto = true),
                        message = "Imported reference photo."
                    )
                    persistDraftIfStarted()
                }
                .onFailure { error ->
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
                    errorMessage = noMagnetometerMessage()
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                isCalibrating = true,
                message = "Hold still for 2 seconds away from magnets, speakers, laptops, chargers, and metal surfaces."
            )
            val samples = collectBaselineSamples()
            val baseline = calibrator.calculate(samples, System.currentTimeMillis())
            processor.reset()
            captureProcessor.reset()
            _uiState.value = _uiState.value.copy(
                baseline = baseline ?: _uiState.value.baseline,
                isCalibrating = false,
                message = if (baseline == null) {
                    "Baseline failed. Keep the phone still and try again."
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
        if (sensorReader.sensorInfo.value?.isAvailable == false) {
            _uiState.value = _uiState.value.copy(errorMessage = noMagnetometerMessage())
            return false
        }
        _uiState.value = _uiState.value.copy(
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
        viewModelScope.launch {
            startSensor()
            if (sensorReader.sensorInfo.value?.isAvailable == false) {
                _uiState.value = _uiState.value.copy(
                    isCapturing = false,
                    errorMessage = noMagnetometerMessage()
                )
                return@launch
            }
            val startMillis = SystemClock.elapsedRealtime()
            val row = _uiState.value.currentRow
            val col = _uiState.value.currentCol
            val samples = mutableListOf<MagneticSample>()
            captureProcessor.reset()
            _uiState.value = _uiState.value.copy(isCapturing = true, errorMessage = null)
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
                sessionId = _uiState.value.currentSessionId,
                row = row,
                col = col,
                samples = samples
            )
            _uiState.value = _uiState.value.copy(
                isCapturing = false,
                cells = _uiState.value.cells + measurement,
                message = "Cell ${_uiState.value.cells.size + 1} captured with ${measurement.sampleCount} samples."
            )
            persistDraftIfStarted()
            finishIfComplete()
        }
    }

    fun saveResult() {
        val state = _uiState.value
        val heatmap = state.heatmap
        val baseline = state.baseline
        if (heatmap == null || baseline == null || !state.isComplete) {
            _uiState.value = state.copy(errorMessage = "Complete a scan before saving.")
            return
        }
        viewModelScope.launch {
            val (exportHeatmap, overlayBitmap) = withContext(Dispatchers.Default) {
                val render = buildHeatmap(state, 2048, 2048)
                render to buildOverlayBitmap(state, render)
            }
            val session = ScanSession(
                id = state.currentSessionId,
                name = state.setup.name.ifBlank { "Surface scan" },
                createdAtMillis = System.currentTimeMillis(),
                gridWidth = state.setup.gridWidth,
                gridHeight = state.setup.gridHeight,
                baseline = baseline,
                photoUri = state.photoUri,
                overlayArea = state.overlayArea,
                cells = state.cells
            )
            val savedId = repository.saveSession(
                session = session,
                sensorInfo = sensorReader.sensorInfo.value,
                heatmapRender = exportHeatmap,
                overlayBitmap = overlayBitmap,
                includeLegend = state.showLegend
            )
            _uiState.value = _uiState.value.copy(
                savedSessionId = savedId,
                message = "Saved scan and exported PNG, JSON, and CSV."
            )
            draftStore.clear()
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

    override fun onCleared() {
        super.onCleared()
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
                cells = state.cells
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

    private fun buildOverlayBitmap(
        state: ScanUiState,
        heatmap: com.example.magneticcamera.core.graphics.HeatmapRender
    ): Bitmap? {
        val photoUri = state.photoUri ?: return null
        val photo = loadBitmap(photoUri) ?: return null
        return overlayRenderer.renderRectangularOverlay(
            photo = photo,
            heatmap = heatmap.toBitmap(),
            area = state.overlayArea,
            opacity = state.opacity
        )
    }

    private fun loadBitmap(uriString: String): Bitmap? {
        return runCatching {
            appContext.contentResolver.openInputStream(Uri.parse(uriString)).use { input ->
                if (input == null) null else BitmapFactory.decodeStream(input)
            }
        }.getOrNull()
    }
}
