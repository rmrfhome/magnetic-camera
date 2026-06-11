package com.example.magneticcamera.core.sensors

import android.hardware.Sensor
import android.hardware.SensorManager
import com.example.magneticcamera.core.math.MagneticMath
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FakeMagneticSensorReader(
    private val available: Boolean = true,
    private val unreliableAccuracy: Boolean = false,
    private val baselineX: Float = 18f,
    private val baselineY: Float = -4f,
    private val baselineZ: Float = 43f,
    private val noiseMicroTesla: Float = 0.4f,
    private val hotspotMicroTesla: Float = 18f
) : MagneticSensorReader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _samples = MutableSharedFlow<MagneticSample>(extraBufferCapacity = 128)
    override val samples: SharedFlow<MagneticSample> = _samples

    private val _sensorInfo = MutableStateFlow(
        if (available) {
            MagneticSensorInfo(
                isAvailable = true,
                name = "Fake magnetic field sensor",
                vendor = "Magnetic Camera",
                sensorType = Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED,
                typeLabel = magneticSensorTypeLabel(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED),
                resolution = 0.1f,
                maximumRange = 2000f,
                minimumDelayMicros = 10000,
                isUncalibrated = true
            )
        } else {
            MagneticSensorInfo.Unavailable
        }
    )
    override val sensorInfo: StateFlow<MagneticSensorInfo?> = _sensorInfo

    private val _latestAccuracy = MutableStateFlow(
        if (unreliableAccuracy) SensorManager.SENSOR_STATUS_UNRELIABLE
        else SensorManager.SENSOR_STATUS_ACCURACY_HIGH
    )
    override val latestAccuracy: StateFlow<Int?> = _latestAccuracy

    private val _diagnosticMessage = MutableStateFlow<String?>(null)
    override val diagnosticMessage: StateFlow<String?> = _diagnosticMessage

    private var job: Job? = null
    private var activeSamplingMode: SensorSamplingMode? = null

    override fun start(config: SensorReadConfig) {
        if (!available) return
        if (job?.isActive == true && activeSamplingMode == config.samplingMode) return
        stop()
        activeSamplingMode = config.samplingMode
        job = scope.launch {
            var tick = 0
            val delayMillis = when (config.samplingMode) {
                SensorSamplingMode.Game -> 16L
                SensorSamplingMode.Fastest -> 5L
            }
            while (isActive) {
                val hotspot = hotspotMicroTesla * (0.5f + 0.5f * sin(tick / 24.0).toFloat())
                val x = baselineX + hotspot + Random.nextFloatCentered(noiseMicroTesla)
                val y = baselineY + Random.nextFloatCentered(noiseMicroTesla)
                val z = baselineZ + Random.nextFloatCentered(noiseMicroTesla)
                _samples.emit(
                    MagneticSample(
                        timestampNanos = System.nanoTime(),
                        sensorType = Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED,
                        accuracy = _latestAccuracy.value ?: SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
                        xMicroTesla = x,
                        yMicroTesla = y,
                        zMicroTesla = z,
                        biasXMicroTesla = 0.2f,
                        biasYMicroTesla = -0.1f,
                        biasZMicroTesla = 0.1f,
                        magnitudeMicroTesla = MagneticMath.magnitude(x, y, z)
                    )
                )
                tick++
                delay(delayMillis)
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        activeSamplingMode = null
    }

    private fun Random.nextFloatCentered(radius: Float): Float {
        return (nextFloat() * 2f - 1f) * radius
    }
}
