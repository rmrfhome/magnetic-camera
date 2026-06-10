package com.example.magneticcamera.core.storage

import android.content.Context
import com.example.magneticcamera.domain.model.MagneticBaseline
import com.example.magneticcamera.domain.model.NormalizedPoint
import com.example.magneticcamera.domain.model.PhotoOverlayArea
import com.example.magneticcamera.domain.scan.CaptureMode
import com.example.magneticcamera.domain.scan.GridCellMeasurement
import com.example.magneticcamera.domain.scan.ScanDraft
import com.example.magneticcamera.domain.scan.ScanSetup
import org.json.JSONArray
import org.json.JSONObject

class ScanDraftStore(
    context: Context
) {
    private val prefs = context.applicationContext.getSharedPreferences("scan-draft", Context.MODE_PRIVATE)

    fun save(draft: ScanDraft) {
        prefs.edit().putString(KEY_DRAFT, draft.toJson().toString()).apply()
    }

    fun load(): ScanDraft? {
        val json = prefs.getString(KEY_DRAFT, null) ?: return null
        return runCatching { JSONObject(json).toDraft() }.getOrNull()
    }

    fun clear() {
        prefs.edit().remove(KEY_DRAFT).apply()
    }

    private fun ScanDraft.toJson(): JSONObject {
        return JSONObject()
            .put("setup", setup.toJson())
            .put("currentSessionId", currentSessionId)
            .put("baseline", baseline?.toJson())
            .put("photoUri", photoUri)
            .put("overlayArea", overlayArea.toJson())
            .put("isScanStarted", isScanStarted)
            .put("cells", JSONArray().also { array ->
                cells.forEach { array.put(it.toJson()) }
            })
    }

    private fun ScanSetup.toJson(): JSONObject {
        return JSONObject()
            .put("name", name)
            .put("gridWidth", gridWidth)
            .put("gridHeight", gridHeight)
            .put("shouldTakePhoto", shouldTakePhoto)
            .put("captureMode", captureMode.name)
    }

    private fun MagneticBaseline.toJson(): JSONObject {
        return JSONObject()
            .put("createdAtMillis", createdAtMillis)
            .put("sampleCount", sampleCount)
            .put("xMean", xMean.toDouble())
            .put("yMean", yMean.toDouble())
            .put("zMean", zMean.toDouble())
            .put("magnitudeMean", magnitudeMean.toDouble())
            .put("xStdDev", xStdDev.toDouble())
            .put("yStdDev", yStdDev.toDouble())
            .put("zStdDev", zStdDev.toDouble())
            .put("magnitudeStdDev", magnitudeStdDev.toDouble())
    }

    private fun PhotoOverlayArea.toJson(): JSONObject {
        return JSONObject()
            .put("topLeft", topLeft.toJson())
            .put("topRight", topRight.toJson())
            .put("bottomRight", bottomRight.toJson())
            .put("bottomLeft", bottomLeft.toJson())
    }

    private fun NormalizedPoint.toJson(): JSONObject {
        return JSONObject().put("x", x.toDouble()).put("y", y.toDouble())
    }

    private fun GridCellMeasurement.toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("sessionId", sessionId)
            .put("row", row)
            .put("col", col)
            .put("sampleCount", sampleCount)
            .put("capturedAtMillis", capturedAtMillis)
            .put("xMean", xMean.toDouble())
            .put("yMean", yMean.toDouble())
            .put("zMean", zMean.toDouble())
            .put("magnitudeMean", magnitudeMean.toDouble())
            .put("magnitudeMedian", magnitudeMedian.toDouble())
            .put("magnitudeMin", magnitudeMin.toDouble())
            .put("magnitudeMax", magnitudeMax.toDouble())
            .put("magnitudeStdDev", magnitudeStdDev.toDouble())
            .put("vectorDeltaMean", vectorDeltaMean.toDouble())
            .put("vectorDeltaMedian", vectorDeltaMedian.toDouble())
            .put("vectorDeltaMin", vectorDeltaMin.toDouble())
            .put("vectorDeltaMax", vectorDeltaMax.toDouble())
            .put("vectorDeltaStdDev", vectorDeltaStdDev.toDouble())
            .put("magnitudeDeltaMean", magnitudeDeltaMean.toDouble())
            .put("accuracy", accuracy)
    }

    private fun JSONObject.toDraft(): ScanDraft {
        val setupJson = getJSONObject("setup")
        val baselineJson = optJSONObject("baseline")
        val cellsArray = optJSONArray("cells") ?: JSONArray()
        return ScanDraft(
            setup = setupJson.toSetup(),
            currentSessionId = getString("currentSessionId"),
            baseline = baselineJson?.toBaseline(),
            photoUri = optString("photoUri").takeIf { it.isNotBlank() && it != "null" },
            overlayArea = optJSONObject("overlayArea")?.toOverlayArea() ?: PhotoOverlayArea.FullFrame,
            isScanStarted = optBoolean("isScanStarted", cellsArray.length() > 0),
            cells = (0 until cellsArray.length()).map { index ->
                cellsArray.getJSONObject(index).toCell()
            }
        )
    }

    private fun JSONObject.toSetup(): ScanSetup {
        return ScanSetup(
            name = optString("name", "Surface scan"),
            gridWidth = optInt("gridWidth", 7).coerceIn(1, 20),
            gridHeight = optInt("gridHeight", 7).coerceIn(1, 20),
            shouldTakePhoto = optBoolean("shouldTakePhoto", true),
            captureMode = runCatching { CaptureMode.valueOf(optString("captureMode")) }
                .getOrDefault(CaptureMode.Manual)
        )
    }

    private fun JSONObject.toBaseline(): MagneticBaseline {
        return MagneticBaseline(
            createdAtMillis = getLong("createdAtMillis"),
            sampleCount = getInt("sampleCount"),
            xMean = getDouble("xMean").toFloat(),
            yMean = getDouble("yMean").toFloat(),
            zMean = getDouble("zMean").toFloat(),
            magnitudeMean = getDouble("magnitudeMean").toFloat(),
            xStdDev = getDouble("xStdDev").toFloat(),
            yStdDev = getDouble("yStdDev").toFloat(),
            zStdDev = getDouble("zStdDev").toFloat(),
            magnitudeStdDev = getDouble("magnitudeStdDev").toFloat()
        )
    }

    private fun JSONObject.toOverlayArea(): PhotoOverlayArea {
        return PhotoOverlayArea(
            topLeft = getJSONObject("topLeft").toPoint(),
            topRight = getJSONObject("topRight").toPoint(),
            bottomRight = getJSONObject("bottomRight").toPoint(),
            bottomLeft = getJSONObject("bottomLeft").toPoint()
        )
    }

    private fun JSONObject.toPoint(): NormalizedPoint {
        return NormalizedPoint(
            x = optDouble("x", 0.0).toFloat().coerceIn(0f, 1f),
            y = optDouble("y", 0.0).toFloat().coerceIn(0f, 1f)
        )
    }

    private fun JSONObject.toCell(): GridCellMeasurement {
        return GridCellMeasurement(
            id = getString("id"),
            sessionId = getString("sessionId"),
            row = getInt("row"),
            col = getInt("col"),
            sampleCount = getInt("sampleCount"),
            capturedAtMillis = getLong("capturedAtMillis"),
            xMean = getDouble("xMean").toFloat(),
            yMean = getDouble("yMean").toFloat(),
            zMean = getDouble("zMean").toFloat(),
            magnitudeMean = getDouble("magnitudeMean").toFloat(),
            magnitudeMedian = getDouble("magnitudeMedian").toFloat(),
            magnitudeMin = getDouble("magnitudeMin").toFloat(),
            magnitudeMax = getDouble("magnitudeMax").toFloat(),
            magnitudeStdDev = getDouble("magnitudeStdDev").toFloat(),
            vectorDeltaMean = getDouble("vectorDeltaMean").toFloat(),
            vectorDeltaMedian = getDouble("vectorDeltaMedian").toFloat(),
            vectorDeltaMin = getDouble("vectorDeltaMin").toFloat(),
            vectorDeltaMax = getDouble("vectorDeltaMax").toFloat(),
            vectorDeltaStdDev = getDouble("vectorDeltaStdDev").toFloat(),
            magnitudeDeltaMean = getDouble("magnitudeDeltaMean").toFloat(),
            accuracy = getInt("accuracy")
        )
    }

    private companion object {
        const val KEY_DRAFT = "draft"
    }
}
