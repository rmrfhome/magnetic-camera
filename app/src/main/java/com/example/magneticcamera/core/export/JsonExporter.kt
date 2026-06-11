package com.example.magneticcamera.core.export

import com.example.magneticcamera.core.sensors.MagneticSensorInfo
import com.example.magneticcamera.domain.scan.ScanSession
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

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

        val root = JSONObject()
            .put("app", "Magnetic Camera")
            .put("schemaVersion", 1)
            .put("session", session.toJson(deviceManufacturer, deviceModel, androidVersion, sensorType, sensorName, sensorVendor))
            .put("cells", session.cellsToJson())

        return root.toString(2)
    }

    private fun ScanSession.toJson(
        deviceManufacturer: String,
        deviceModel: String,
        androidVersion: String,
        sensorType: Int,
        sensorName: String,
        sensorVendor: String
    ): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("name", name)
            .put("createdAt", Instant.ofEpochMilli(createdAtMillis).toString())
            .put(
                "device",
                JSONObject()
                    .put("manufacturer", deviceManufacturer)
                    .put("model", deviceModel)
                    .put("androidVersion", androidVersion)
            )
            .put(
                "sensor",
                JSONObject()
                    .put("type", sensorType)
                    .put("name", sensorName)
                    .put("vendor", sensorVendor)
            )
            .put(
                "baseline",
                JSONObject()
                    .put("x", baseline.xMean.jsonNumber())
                    .put("y", baseline.yMean.jsonNumber())
                    .put("z", baseline.zMean.jsonNumber())
                    .put("magnitude", baseline.magnitudeMean.jsonNumber())
            )
            .put(
                "grid",
                JSONObject()
                    .put("width", gridWidth)
                    .put("height", gridHeight)
            )
    }

    private fun ScanSession.cellsToJson(): JSONArray {
        return JSONArray().also { array ->
            cells.sortedWith(compareBy({ it.row }, { it.col })).forEach { cell ->
                array.put(
                    JSONObject()
                        .put("id", cell.id)
                        .put("sessionId", cell.sessionId)
                        .put("row", cell.row)
                        .put("col", cell.col)
                        .put("sampleCount", cell.sampleCount)
                        .put("capturedAtMillis", cell.capturedAtMillis)
                        .put("xMean", cell.xMean.jsonNumber())
                        .put("yMean", cell.yMean.jsonNumber())
                        .put("zMean", cell.zMean.jsonNumber())
                        .put("magnitudeMean", cell.magnitudeMean.jsonNumber())
                        .put("magnitudeMedian", cell.magnitudeMedian.jsonNumber())
                        .put("magnitudeMin", cell.magnitudeMin.jsonNumber())
                        .put("magnitudeMax", cell.magnitudeMax.jsonNumber())
                        .put("magnitudeStdDev", cell.magnitudeStdDev.jsonNumber())
                        .put("magnitudeDeltaMean", cell.magnitudeDeltaMean.jsonNumber())
                        .put("vectorDeltaMean", cell.vectorDeltaMean.jsonNumber())
                        .put("vectorDeltaMedian", cell.vectorDeltaMedian.jsonNumber())
                        .put("vectorDeltaMin", cell.vectorDeltaMin.jsonNumber())
                        .put("vectorDeltaMax", cell.vectorDeltaMax.jsonNumber())
                        .put("vectorDeltaStdDev", cell.vectorDeltaStdDev.jsonNumber())
                        .put("accuracy", cell.accuracy)
                )
            }
        }
    }

    private fun Float.jsonNumber(): Double {
        return if (isFinite()) toDouble() else 0.0
    }
}
