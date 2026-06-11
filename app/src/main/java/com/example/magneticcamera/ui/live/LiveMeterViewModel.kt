package com.example.magneticcamera.ui.live

import android.hardware.SensorManager
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.magneticcamera.core.sensors.MagneticSample
import com.example.magneticcamera.core.sensors.MagneticSensorInfo
import com.example.magneticcamera.core.sensors.MagneticSensorReader
import com.example.magneticcamera.core.sensors.SensorReadConfig
import com.example.magneticcamera.core.sensors.SensorSamplingMode
import com.example.magneticcamera.domain.calibration.BaselineCalibrator
import com.example.magneticcamera.domain.scan.MagneticSampleProcessor
import java.util.ArrayDeque
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class LiveMeterViewModel(
    private val sensorReader: MagneticSensorReader
) : ViewModel() {
    private val processor = MagneticSampleProcessor()
    private val calibrator = BaselineCalibrator()
    private val recentSamples = ArrayDeque<MagneticSample>()
    private val sampleTimestamps = ArrayDeque<Long>()

    private val _uiState = MutableStateFlow(LiveMeterUiState(sensorInfo = sensorReader.sensorInfo.value))
    val uiState: StateFlow<LiveMeterUiState> = _uiState.asStateFlow()

    private var sampleJob: Job? = null
    private var sensorInfoJob: Job? = null
    private var diagnosticJob: Job? = null
    private var lastUiUpdateNanos = 0L
    private var samplingMode = SensorSamplingMode.Game
    private var diagnosticMessage: String? = null
    private var diagnosticExpiresAtMillis = 0L

    init {
        sensorInfoJob = viewModelScope.launch {
            sensorReader.sensorInfo.collect { info ->
                _uiState.value = _uiState.value.copy(
                    sensorInfo = info,
                    sensorAvailable = info?.isAvailable != false,
                    errorMessage = if (info?.isAvailable == false) {
                        "This device does not expose a magnetic field sensor. Magnetic Camera cannot scan magnetic fields on this device."
                    } else {
                        _uiState.value.errorMessage
                    }
                )
            }
        }
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

    fun start() {
        if (sampleJob?.isActive == true) return
        sensorReader.start(SensorReadConfig(samplingMode))
        sampleJob = viewModelScope.launch {
            sensorReader.samples.collect { raw ->
                val processed = processor.process(raw, _uiState.value.baseline)
                rememberSample(processed)
                if (!_uiState.value.isFrozen && shouldUpdateUi(processed.timestampNanos)) {
                    _uiState.value = _uiState.value.copy(
                        latestSample = processed,
                        recentSamples = recentSamples.toList(),
                        samplingRateHz = calculateSampleRate(),
                        accuracyLabel = accuracyLabel(processed.accuracy),
                        errorMessage = activeDiagnosticMessage() ?: unreliableAccuracyMessage(processed.accuracy)
                    )
                }
            }
        }
    }

    fun stop() {
        sampleJob?.cancel()
        sampleJob = null
        sensorReader.stop()
    }

    fun toggleFreeze() {
        _uiState.value = _uiState.value.copy(isFrozen = !_uiState.value.isFrozen)
    }

    fun saveSnapshot() {
        val sample = _uiState.value.latestSample
        _uiState.value = _uiState.value.copy(
            snapshotSample = sample ?: _uiState.value.snapshotSample,
            snapshotCreatedAtMillis = if (sample == null) _uiState.value.snapshotCreatedAtMillis else System.currentTimeMillis(),
            message = if (sample == null) "No live sample is available yet."
            else "Snapshot held at ${"%.1f".format(sample.magnitudeMicroTesla)} µT."
        )
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun updateFilterAlpha(alpha: Float) {
        processor.setAlpha(alpha)
    }

    fun updateSamplingMode(mode: SensorSamplingMode) {
        if (samplingMode == mode) return
        val wasActive = sampleJob?.isActive == true
        samplingMode = mode
        if (wasActive) {
            stop()
            start()
        }
    }

    fun setBaseline() {
        if (_uiState.value.isCalibrating) return
        viewModelScope.launch {
            sensorReader.start(SensorReadConfig(samplingMode))
            if (sensorReader.sensorInfo.value?.isAvailable == false) {
                _uiState.value = _uiState.value.copy(
                    isCalibrating = false,
                    errorMessage = noMagnetometerMessage()
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
            _uiState.value = _uiState.value.copy(
                baseline = baseline ?: _uiState.value.baseline,
                isCalibrating = false,
                message = if (baseline == null) {
                    "Baseline failed. Keep the phone still and try again."
                } else {
                    "Baseline set: ${"%.1f".format(baseline.magnitudeMean)} µT, noise ${"%.2f".format(baseline.magnitudeStdDev)} µT."
                }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        sensorInfoJob?.cancel()
        diagnosticJob?.cancel()
        stop()
    }

    private suspend fun collectBaselineSamples(): List<MagneticSample> {
        sensorReader.start(SensorReadConfig(samplingMode))
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

    private fun rememberSample(sample: MagneticSample) {
        recentSamples.addLast(sample)
        while (recentSamples.size > 300) {
            recentSamples.removeFirst()
        }
        sampleTimestamps.addLast(sample.timestampNanos)
        while (sampleTimestamps.size > 90) {
            sampleTimestamps.removeFirst()
        }
    }

    private fun shouldUpdateUi(timestampNanos: Long): Boolean {
        if (timestampNanos - lastUiUpdateNanos < 33_000_000L) return false
        lastUiUpdateNanos = timestampNanos
        return true
    }

    private fun calculateSampleRate(): Float {
        if (sampleTimestamps.size < 2) return 0f
        val first = sampleTimestamps.first()
        val last = sampleTimestamps.last()
        val seconds = (last - first) / 1_000_000_000f
        if (seconds <= 0f) return 0f
        return (sampleTimestamps.size - 1) / seconds
    }

    private fun accuracyLabel(accuracy: Int): String {
        return when (accuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "High"
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium"
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low"
            SensorManager.SENSOR_STATUS_UNRELIABLE -> "Unreliable"
            else -> "Unknown"
        }
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

    private fun noMagnetometerMessage(): String {
        return "This device does not expose a magnetic field sensor. Magnetic Camera cannot scan magnetic fields on this device."
    }

    private companion object {
        const val DIAGNOSTIC_MESSAGE_DURATION_MS = 4_000L
    }
}
