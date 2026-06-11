package com.example.magneticcamera.core.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class AndroidMagneticSensorReader(
    context: Context
) : MagneticSensorReader, SensorEventListener {
    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val _samples = MutableSharedFlow<MagneticSample>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val samples: SharedFlow<MagneticSample> = _samples

    private val _sensorInfo = MutableStateFlow(detectPreferredSensor()?.toInfo() ?: MagneticSensorInfo.Unavailable)
    override val sensorInfo: StateFlow<MagneticSensorInfo?> = _sensorInfo

    private val _latestAccuracy = MutableStateFlow<Int?>(null)
    override val latestAccuracy: StateFlow<Int?> = _latestAccuracy

    private val _diagnosticMessage = MutableStateFlow<String?>(null)
    override val diagnosticMessage: StateFlow<String?> = _diagnosticMessage

    private var activeSensor: Sensor? = null
    private var activeSensorDelay: Int? = null

    override fun start(config: SensorReadConfig) {
        val sensor = detectPreferredSensor()
        _sensorInfo.value = sensor?.toInfo() ?: MagneticSensorInfo.Unavailable
        if (sensor == null) {
            stop()
            return
        }

        if (activeSensor == sensor && activeSensorDelay == config.sensorDelay) return
        stop()
        activeSensor = sensor
        activeSensorDelay = config.sensorDelay
        sensorManager.registerListener(this, sensor, config.sensorDelay)
    }

    override fun stop() {
        sensorManager.unregisterListener(this)
        activeSensor = null
        activeSensorDelay = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        val values = event.values
        if (values.size < 3) return
        val x = values[0]
        val y = values[1]
        val z = values[2]
        val validation = MagneticSampleValidator.validate(
            xMicroTesla = x,
            yMicroTesla = y,
            zMicroTesla = z,
            maximumRangeMicroTesla = event.sensor.maximumRange
        )
        if (validation is ValidationResult.Rejected) {
            _diagnosticMessage.value = validation.warning
            return
        }
        _latestAccuracy.value = event.accuracy
        _diagnosticMessage.value = null

        val sample = MagneticSample(
            timestampNanos = event.timestamp,
            sensorType = event.sensor.type,
            accuracy = event.accuracy,
            xMicroTesla = x,
            yMicroTesla = y,
            zMicroTesla = z,
            biasXMicroTesla = values.getOrNull(3)?.takeIf { it.isFinite() },
            biasYMicroTesla = values.getOrNull(4)?.takeIf { it.isFinite() },
            biasZMicroTesla = values.getOrNull(5)?.takeIf { it.isFinite() },
            magnitudeMicroTesla = (validation as ValidationResult.Accepted).magnitudeMicroTesla
        )
        _samples.tryEmit(sample)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        _latestAccuracy.value = accuracy
    }

    private fun detectPreferredSensor(): Sensor? {
        return sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }

    private fun Sensor.toInfo(): MagneticSensorInfo {
        val uncalibrated = type == Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED
        return MagneticSensorInfo(
            isAvailable = true,
            name = name,
            vendor = vendor,
            sensorType = type,
            typeLabel = magneticSensorTypeLabel(type),
            resolution = resolution,
            maximumRange = maximumRange,
            minimumDelayMicros = minDelay,
            isUncalibrated = uncalibrated
        )
    }
}
