package com.example.magneticcamera.core.storage

import android.content.Context
import com.example.magneticcamera.domain.model.MagneticBaseline
import com.example.magneticcamera.domain.model.NormalizedPoint
import com.example.magneticcamera.domain.model.PhotoOverlayArea
import com.example.magneticcamera.domain.scan.CaptureMode
import com.example.magneticcamera.domain.scan.GridCellMeasurement
import com.example.magneticcamera.domain.scan.NormalizationMode
import com.example.magneticcamera.domain.scan.PaletteMode
import com.example.magneticcamera.domain.scan.ScanDraft
import com.example.magneticcamera.domain.scan.ScanSetup
import org.json.JSONArray
import org.json.JSONObject

class ScanDraftStore(
    context: Context
) {
    private val prefs = context.applicationContext.getSharedPreferences("scan-draft", Context.MODE_PRIVATE)

    fun save(draft: ScanDraft) {
        prefs.edit().putString(KEY_DRAFT, ScanDraftCodec.encode(draft)).apply()
    }

    fun load(): ScanDraft? {
        val json = prefs.getString(KEY_DRAFT, null) ?: return null
        return runCatching { ScanDraftCodec.decode(json) }
            .onFailure { clear() }
            .getOrNull()
    }

    fun clear() {
        prefs.edit().remove(KEY_DRAFT).apply()
    }

    private companion object {
        const val KEY_DRAFT = "draft"
    }
}

internal object ScanDraftCodec {
    fun encode(draft: ScanDraft): String = draft.toJson().toString()

    fun decode(json: String): ScanDraft = JSONObject(json).toDraft()

    private fun ScanDraft.toJson(): JSONObject {
        return JSONObject()
            .put("setup", setup.toJson())
            .put("currentSessionId", currentSessionId)
            .put("baseline", baseline?.toJson())
            .put("photoUri", photoUri)
            .put("overlayArea", overlayArea.toJson())
            .put("isScanStarted", isScanStarted)
            .put("paletteMode", paletteMode.name)
            .put("normalizationMode", normalizationMode.toJson())
            .put("opacity", opacity.toDouble())
            .put("showGrid", showGrid)
            .put("showLegend", showLegend)
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

    private fun NormalizationMode.toJson(): JSONObject {
        return when (this) {
            NormalizationMode.AutoLocal -> JSONObject().put("type", "AutoLocal")
            is NormalizationMode.BaselineDeltaFixedScale -> JSONObject()
                .put("type", "BaselineDeltaFixedScale")
                .put("maxDeltaMicroTesla", maxDeltaMicroTesla.toDouble())
            NormalizationMode.AbsoluteField -> JSONObject().put("type", "AbsoluteField")
        }
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
            },
            paletteMode = optPaletteMode(),
            normalizationMode = optNormalizationMode(),
            opacity = optFiniteFloat("opacity", 0.72f).coerceIn(0f, 1f),
            showGrid = optBoolean("showGrid", true),
            showLegend = optBoolean("showLegend", true)
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
            createdAtMillis = getLong("createdAtMillis").coerceAtLeast(0L),
            sampleCount = getInt("sampleCount").coerceAtLeast(0),
            xMean = getFiniteFloat("xMean"),
            yMean = getFiniteFloat("yMean"),
            zMean = getFiniteFloat("zMean"),
            magnitudeMean = getFiniteFloat("magnitudeMean"),
            xStdDev = getFiniteFloat("xStdDev").coerceAtLeast(0f),
            yStdDev = getFiniteFloat("yStdDev").coerceAtLeast(0f),
            zStdDev = getFiniteFloat("zStdDev").coerceAtLeast(0f),
            magnitudeStdDev = getFiniteFloat("magnitudeStdDev").coerceAtLeast(0f)
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

    private fun JSONObject.optPaletteMode(): PaletteMode {
        return runCatching { PaletteMode.valueOf(optString("paletteMode")) }
            .getOrDefault(PaletteMode.Scientific)
    }

    private fun JSONObject.optNormalizationMode(): NormalizationMode {
        val json = optJSONObject("normalizationMode") ?: return NormalizationMode.AutoLocal
        return when (json.optString("type")) {
            "BaselineDeltaFixedScale" -> NormalizationMode.BaselineDeltaFixedScale(
                json.optFiniteFloat("maxDeltaMicroTesla", 50f).coerceAtLeast(1f)
            )
            "AbsoluteField" -> NormalizationMode.AbsoluteField
            else -> NormalizationMode.AutoLocal
        }
    }

    private fun JSONObject.toCell(): GridCellMeasurement {
        val magnitudeMean = getFiniteFloat("magnitudeMean")
        val vectorDeltaMean = getFiniteFloat("vectorDeltaMean")
        return GridCellMeasurement(
            id = getString("id"),
            sessionId = getString("sessionId"),
            row = getInt("row"),
            col = getInt("col"),
            sampleCount = getInt("sampleCount"),
            capturedAtMillis = getLong("capturedAtMillis"),
            xMean = getFiniteFloat("xMean"),
            yMean = getFiniteFloat("yMean"),
            zMean = getFiniteFloat("zMean"),
            magnitudeMean = magnitudeMean,
            magnitudeMedian = optFiniteFloat("magnitudeMedian", magnitudeMean),
            magnitudeMin = optFiniteFloat("magnitudeMin", magnitudeMean),
            magnitudeMax = optFiniteFloat("magnitudeMax", magnitudeMean),
            magnitudeStdDev = optFiniteFloat("magnitudeStdDev", 0f),
            vectorDeltaMean = vectorDeltaMean,
            vectorDeltaMedian = optFiniteFloat("vectorDeltaMedian", vectorDeltaMean),
            vectorDeltaMin = optFiniteFloat("vectorDeltaMin", vectorDeltaMean),
            vectorDeltaMax = optFiniteFloat("vectorDeltaMax", vectorDeltaMean),
            vectorDeltaStdDev = getFiniteFloat("vectorDeltaStdDev"),
            magnitudeDeltaMean = optFiniteFloat("magnitudeDeltaMean", 0f),
            accuracy = getInt("accuracy")
        )
    }

    private fun JSONObject.getFiniteFloat(name: String): Float {
        return getDouble(name).toFloat().takeIf { it.isFinite() }
            ?: error("Draft value $name must be finite.")
    }

    private fun JSONObject.optFiniteFloat(name: String, fallback: Float): Float {
        if (!has(name) || isNull(name)) return fallback
        return optDouble(name, fallback.toDouble()).toFloat().takeIf { it.isFinite() } ?: fallback
    }
}
