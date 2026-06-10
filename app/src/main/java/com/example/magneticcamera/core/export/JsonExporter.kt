package com.example.magneticcamera.core.export

import com.example.magneticcamera.core.sensors.MagneticSensorInfo
import com.example.magneticcamera.domain.scan.ScanSession
import java.time.Instant

class JsonExporter {
    fun export(
        session: ScanSession,
        deviceManufacturer: String,
        deviceModel: String,
        androidVersion: String,
        sensorInfo: MagneticSensorInfo?
    ): String {
        val sensorType = sensorInfo?.sensorType ?: -1
        val sensorName = sensorInfo?.name.orEmpty()
        val sensorVendor = sensorInfo?.vendor.orEmpty()
        val cells = session.cells.sortedWith(compareBy({ it.row }, { it.col })).joinToString(",\n") { cell ->
            """
            |    {
            |      "id": "${cell.id.escapeJson()}",
            |      "sessionId": "${cell.sessionId.escapeJson()}",
            |      "row": ${cell.row},
            |      "col": ${cell.col},
            |      "sampleCount": ${cell.sampleCount},
            |      "capturedAtMillis": ${cell.capturedAtMillis},
            |      "xMean": ${cell.xMean.jsonNumber()},
            |      "yMean": ${cell.yMean.jsonNumber()},
            |      "zMean": ${cell.zMean.jsonNumber()},
            |      "magnitudeMean": ${cell.magnitudeMean.jsonNumber()},
            |      "magnitudeMedian": ${cell.magnitudeMedian.jsonNumber()},
            |      "magnitudeMin": ${cell.magnitudeMin.jsonNumber()},
            |      "magnitudeMax": ${cell.magnitudeMax.jsonNumber()},
            |      "magnitudeStdDev": ${cell.magnitudeStdDev.jsonNumber()},
            |      "magnitudeDeltaMean": ${cell.magnitudeDeltaMean.jsonNumber()},
            |      "vectorDeltaMean": ${cell.vectorDeltaMean.jsonNumber()},
            |      "vectorDeltaMedian": ${cell.vectorDeltaMedian.jsonNumber()},
            |      "vectorDeltaMin": ${cell.vectorDeltaMin.jsonNumber()},
            |      "vectorDeltaMax": ${cell.vectorDeltaMax.jsonNumber()},
            |      "vectorDeltaStdDev": ${cell.vectorDeltaStdDev.jsonNumber()},
            |      "accuracy": ${cell.accuracy}
            |    }
            """.trimMargin()
        }

        return """
            |{
            |  "app": "Magnetic Camera",
            |  "schemaVersion": 1,
            |  "session": {
            |    "id": "${session.id.escapeJson()}",
            |    "name": "${session.name.escapeJson()}",
            |    "createdAt": "${Instant.ofEpochMilli(session.createdAtMillis)}",
            |    "device": {
            |      "manufacturer": "${deviceManufacturer.escapeJson()}",
            |      "model": "${deviceModel.escapeJson()}",
            |      "androidVersion": "${androidVersion.escapeJson()}"
            |    },
            |    "sensor": {
            |      "type": $sensorType,
            |      "name": "${sensorName.escapeJson()}",
            |      "vendor": "${sensorVendor.escapeJson()}"
            |    },
            |    "baseline": {
            |      "x": ${session.baseline.xMean.jsonNumber()},
            |      "y": ${session.baseline.yMean.jsonNumber()},
            |      "z": ${session.baseline.zMean.jsonNumber()},
            |      "magnitude": ${session.baseline.magnitudeMean.jsonNumber()}
            |    },
            |    "grid": {
            |      "width": ${session.gridWidth},
            |      "height": ${session.gridHeight}
            |    }
            |  },
            |  "cells": [
            |$cells
            |  ]
            |}
        """.trimMargin()
    }

    private fun String.escapeJson(): String {
        return buildString {
            for (char in this@escapeJson) {
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }
    }

    private fun Float.jsonNumber(): String {
        return if (isFinite()) toString() else "0.0"
    }
}
