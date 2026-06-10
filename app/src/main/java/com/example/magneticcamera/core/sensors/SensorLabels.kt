package com.example.magneticcamera.core.sensors

import android.hardware.Sensor

fun magneticSensorTypeLabel(sensorType: Int): String {
    return when (sensorType) {
        Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "uncalibrated magnetic field"
        Sensor.TYPE_MAGNETIC_FIELD -> "calibrated magnetic field"
        -1 -> "unavailable"
        else -> "sensor type $sensorType"
    }
}
