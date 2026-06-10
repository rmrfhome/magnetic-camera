package com.example.magneticcamera

import android.hardware.Sensor
import android.hardware.SensorManager
import com.example.magneticcamera.core.export.CsvExporter
import com.example.magneticcamera.core.export.JsonExporter
import com.example.magneticcamera.core.graphics.HeatmapGenerator
import com.example.magneticcamera.core.graphics.HeatmapInput
import com.example.magneticcamera.core.math.LowPassFilter
import com.example.magneticcamera.core.math.MagneticMath
import com.example.magneticcamera.core.math.MedianFilter
import com.example.magneticcamera.core.sensors.FakeMagneticSensorReader
import com.example.magneticcamera.core.sensors.MagneticSample
import com.example.magneticcamera.core.sensors.MagneticSampleValidator
import com.example.magneticcamera.core.sensors.MagneticSensorInfo
import com.example.magneticcamera.core.sensors.SensorReadConfig
import com.example.magneticcamera.core.sensors.SensorSamplingMode
import com.example.magneticcamera.core.sensors.ValidationResult
import com.example.magneticcamera.core.sensors.magneticSensorTypeLabel
import com.example.magneticcamera.core.storage.ScanDraftCodec
import com.example.magneticcamera.data.repository.ScanSessionRepositoryImpl
import com.example.magneticcamera.domain.calibration.BaselineCalibrator
import com.example.magneticcamera.domain.model.MagneticBaseline
import com.example.magneticcamera.domain.model.NormalizedPoint
import com.example.magneticcamera.domain.model.PhotoOverlayArea
import com.example.magneticcamera.domain.scan.CaptureMode
import com.example.magneticcamera.domain.scan.GridScanController
import com.example.magneticcamera.domain.scan.GridCellMeasurement
import com.example.magneticcamera.domain.scan.NormalizationMode
import com.example.magneticcamera.domain.scan.ScanDraft
import com.example.magneticcamera.domain.scan.ScanSession
import com.example.magneticcamera.domain.scan.ScanSetup
import com.example.magneticcamera.ui.scan.ScanUiState
import kotlin.math.sqrt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MagneticCoreTest {
    @Test
    fun magnitudeAndVectorDeltaUseEuclideanDistance() {
        assertEquals(13f, MagneticMath.magnitude(3f, 4f, 12f), 0.0001f)
        assertEquals(5f, MagneticMath.vectorDelta(4f, 6f, 8f, 1f, 2f, 8f), 0.0001f)
        assertEquals(2.5f, MagneticMath.magnitudeDelta(42.5f, 40f), 0.0001f)
    }

    @Test
    fun sensorReadConfigMapsSamplingModesToAndroidDelays() {
        assertEquals(SensorManager.SENSOR_DELAY_GAME, SensorReadConfig().sensorDelay)
        assertEquals(
            SensorManager.SENSOR_DELAY_FASTEST,
            SensorReadConfig(SensorSamplingMode.Fastest).sensorDelay
        )
    }

    @Test
    fun magneticSensorTypeLabelsAreUserReadable() {
        assertEquals(
            "uncalibrated magnetic field",
            magneticSensorTypeLabel(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED)
        )
        assertEquals(
            "calibrated magnetic field",
            magneticSensorTypeLabel(Sensor.TYPE_MAGNETIC_FIELD)
        )
        assertEquals("unavailable", magneticSensorTypeLabel(-1))
    }

    @Test
    fun fakeSensorCanEmitConstantUncalibratedBaselineSamples() = runBlocking {
        val reader = FakeMagneticSensorReader(
            baselineX = 1f,
            baselineY = 2f,
            baselineZ = 3f,
            noiseMicroTesla = 0f,
            hotspotMicroTesla = 0f
        )

        try {
            reader.start(SensorReadConfig(SensorSamplingMode.Fastest))
            val sample = withTimeoutOrNull(1_000L) { reader.samples.first() }
            assertNotNull(sample)
            sample!!

            assertEquals(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED, sample.sensorType)
            assertEquals(SensorManager.SENSOR_STATUS_ACCURACY_HIGH, sample.accuracy)
            assertEquals(1f, sample.xMicroTesla, 0.0001f)
            assertEquals(2f, sample.yMicroTesla, 0.0001f)
            assertEquals(3f, sample.zMicroTesla, 0.0001f)
            assertEquals(0.2f, sample.biasXMicroTesla ?: 0f, 0.0001f)
            assertEquals(-0.1f, sample.biasYMicroTesla ?: 0f, 0.0001f)
            assertEquals(0.1f, sample.biasZMicroTesla ?: 0f, 0.0001f)
        } finally {
            reader.stop()
        }
    }

    @Test
    fun fakeSensorSupportsMissingSensorAndUnreliableAccuracyStates() = runBlocking {
        val unavailable = FakeMagneticSensorReader(available = false)
        assertEquals(false, unavailable.sensorInfo.value?.isAvailable)
        unavailable.start()
        assertNull(withTimeoutOrNull(80L) { unavailable.samples.first() })

        val unreliable = FakeMagneticSensorReader(
            unreliableAccuracy = true,
            noiseMicroTesla = 0f,
            hotspotMicroTesla = 0f
        )
        try {
            unreliable.start()
            val sample = withTimeoutOrNull(1_000L) { unreliable.samples.first() }
            assertNotNull(sample)
            sample!!
            assertEquals(SensorManager.SENSOR_STATUS_UNRELIABLE, sample.accuracy)
        } finally {
            unreliable.stop()
        }
    }

    @Test
    fun magneticSampleValidatorRejectsInvalidAndOutOfRangeSamples() {
        val accepted = MagneticSampleValidator.validate(
            xMicroTesla = 20f,
            yMicroTesla = -5f,
            zMicroTesla = 40f,
            maximumRangeMicroTesla = 2_000f
        )
        assertTrue(accepted is ValidationResult.Accepted)

        val invalid = MagneticSampleValidator.validate(
            xMicroTesla = Float.NaN,
            yMicroTesla = 0f,
            zMicroTesla = 0f,
            maximumRangeMicroTesla = 2_000f
        )
        assertEquals(
            MagneticSampleValidator.INVALID_SAMPLE_WARNING,
            (invalid as ValidationResult.Rejected).warning
        )

        val outOfRange = MagneticSampleValidator.validate(
            xMicroTesla = 3_000f,
            yMicroTesla = 0f,
            zMicroTesla = 0f,
            maximumRangeMicroTesla = 2_000f
        )
        assertEquals(
            MagneticSampleValidator.OUT_OF_RANGE_WARNING,
            (outOfRange as ValidationResult.Rejected).warning
        )
    }

    @Test
    fun baselineCalibratorComputesMeanAndStdDev() {
        val samples = listOf(
            sample(x = 10f, y = 20f, z = 30f),
            sample(x = 12f, y = 22f, z = 32f),
            sample(x = 14f, y = 24f, z = 34f)
        )

        val baseline = requireNotNull(BaselineCalibrator().calculate(samples, createdAtMillis = 123L))

        assertEquals(123L, baseline.createdAtMillis)
        assertEquals(3, baseline.sampleCount)
        assertEquals(12f, baseline.xMean, 0.0001f)
        assertEquals(22f, baseline.yMean, 0.0001f)
        assertEquals(32f, baseline.zMean, 0.0001f)
        assertEquals(sqrt(8.0 / 3.0).toFloat(), baseline.xStdDev, 0.0001f)
        assertTrue(baseline.magnitudeMean > 37f)
    }

    @Test
    fun lowPassFilterFollowsConfiguredAlpha() {
        val filter = LowPassFilter(alpha = 0.25f)

        assertEquals(10f, filter.filter(10f), 0.0001f)
        assertEquals(12.5f, filter.filter(20f), 0.0001f)

        filter.setAlpha(0.5f)
        assertEquals(16.25f, filter.filter(20f), 0.0001f)
    }

    @Test
    fun medianFilterMaintainsRollingWindow() {
        val filter = MedianFilter(windowSize = 3)

        assertEquals(10f, filter.filter(10f), 0.0001f)
        assertEquals(55f, filter.filter(100f), 0.0001f)
        assertEquals(10f, filter.filter(0f), 0.0001f)
        assertEquals(5f, filter.filter(5f), 0.0001f)
    }

    @Test
    fun gridCellCaptureComputesStatistics() {
        val baseline = MagneticBaseline(
            createdAtMillis = 1L,
            sampleCount = 3,
            xMean = 10f,
            yMean = 0f,
            zMean = 0f,
            magnitudeMean = 10f,
            xStdDev = 0f,
            yStdDev = 0f,
            zStdDev = 0f,
            magnitudeStdDev = 0.2f
        )
        val samples = listOf(
            sample(x = 12f, y = 0f, z = 0f).withBaseline(baseline),
            sample(x = 14f, y = 0f, z = 0f).withBaseline(baseline),
            sample(x = 16f, y = 0f, z = 0f).withBaseline(baseline)
        )

        val measurement = GridScanController().captureCell(
            sessionId = "session-1",
            row = 1,
            col = 2,
            samples = samples,
            capturedAtMillis = 456L
        )

        assertEquals("session-1", measurement.sessionId)
        assertEquals(1, measurement.row)
        assertEquals(2, measurement.col)
        assertEquals(3, measurement.sampleCount)
        assertEquals(14f, measurement.xMean, 0.0001f)
        assertEquals(14f, measurement.magnitudeMedian, 0.0001f)
        assertEquals(12f, measurement.magnitudeMin, 0.0001f)
        assertEquals(16f, measurement.magnitudeMax, 0.0001f)
        assertEquals(4f, measurement.vectorDeltaMean, 0.0001f)
        assertEquals(4f, measurement.vectorDeltaMedian, 0.0001f)
        assertEquals(2f, measurement.vectorDeltaMin, 0.0001f)
        assertEquals(6f, measurement.vectorDeltaMax, 0.0001f)
        assertEquals(4f, measurement.magnitudeDeltaMean, 0.0001f)
        assertTrue(measurement.magnitudeStdDev > 1.6f)
        assertTrue(measurement.vectorDeltaStdDev > 1.6f)
    }

    @Test
    fun gridCellCaptureKeepsWorstAccuracyInWindow() {
        val baseline = MagneticBaseline(
            createdAtMillis = 1L,
            sampleCount = 3,
            xMean = 10f,
            yMean = 0f,
            zMean = 0f,
            magnitudeMean = 10f,
            xStdDev = 0f,
            yStdDev = 0f,
            zStdDev = 0f,
            magnitudeStdDev = 0.2f
        )
        val samples = listOf(
            sample(x = 12f, y = 0f, z = 0f, accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH).withBaseline(baseline),
            sample(x = 14f, y = 0f, z = 0f, accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE).withBaseline(baseline),
            sample(x = 16f, y = 0f, z = 0f, accuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM).withBaseline(baseline)
        )

        val measurement = GridScanController().captureCell(
            sessionId = "session-1",
            row = 0,
            col = 0,
            samples = samples
        )

        assertEquals(SensorManager.SENSOR_STATUS_UNRELIABLE, measurement.accuracy)
    }

    @Test
    fun gridCellCaptureTimingUsesWindowMinimumAndHardTimeout() {
        val controller = GridScanController()

        assertEquals(false, controller.shouldStopCapture(startMillis = 1_000L, nowMillis = 1_699L, sampleCount = 15))
        assertEquals(true, controller.shouldStopCapture(startMillis = 1_000L, nowMillis = 1_700L, sampleCount = 15))
        assertEquals(false, controller.shouldStopCapture(startMillis = 1_000L, nowMillis = 2_999L, sampleCount = 14))
        assertEquals(true, controller.shouldStopCapture(startMillis = 1_000L, nowMillis = 3_000L, sampleCount = 14))
    }

    @Test
    fun skippedCellRecordsNoSamplesAndUnreliableAccuracy() {
        val skipped = GridScanController().skippedCell(sessionId = "session-1", row = 2, col = 3)

        assertEquals("session-1", skipped.sessionId)
        assertEquals(2, skipped.row)
        assertEquals(3, skipped.col)
        assertEquals(0, skipped.sampleCount)
        assertEquals(0f, skipped.vectorDeltaMean, 0.0001f)
        assertTrue(skipped.valueForHeatmap(NormalizationMode.AutoLocal).isNaN())
        assertTrue(skipped.valueForHeatmap(NormalizationMode.AbsoluteField).isNaN())
        assertEquals(SensorManager.SENSOR_STATUS_UNRELIABLE, skipped.accuracy)
    }

    @Test
    fun heatmapGeneratorNormalizesAndInterpolatesGridValues() {
        val generator = HeatmapGenerator()
        val values = listOf(0f, 10f, 20f, 30f)

        assertEquals(15f, generator.interpolate(values, 2, 2, 0.5f, 0.5f), 0.0001f)
        assertEquals(0.5f, generator.normalize(15f, 0f, 30f), 0.0001f)
        assertEquals(0f to 50f, generator.normalizationRange(values, NormalizationMode.BaselineDeltaFixedScale(50f)))

        val render = generator.generate(
            input = HeatmapInput(gridWidth = 2, gridHeight = 2, values = values),
            outputWidth = 4,
            outputHeight = 4,
            normalizationMode = NormalizationMode.AutoLocal
        )

        assertEquals(4, render.width)
        assertEquals(4, render.height)
        assertEquals(16, render.pixels.size)
        assertEquals(0f, render.legend.minValue, 0.0001f)
        assertEquals(30f, render.legend.maxValue, 0.0001f)
    }

    @Test
    fun heatmapGeneratorIgnoresInvalidMissingCellValuesForRange() {
        val generator = HeatmapGenerator()
        val render = generator.generate(
            input = HeatmapInput(
                gridWidth = 2,
                gridHeight = 2,
                values = listOf(8f, Float.NaN, 16f, Float.POSITIVE_INFINITY)
            ),
            outputWidth = 4,
            outputHeight = 4,
            normalizationMode = NormalizationMode.AutoLocal
        )

        assertEquals(8f, render.legend.minValue, 0.0001f)
        assertEquals(16f, render.legend.maxValue, 0.0001f)
    }

    @Test
    fun scanDraftCodecRoundTripsInterruptedScanState() {
        val baseline = MagneticBaseline(
            createdAtMillis = 123L,
            sampleCount = 4,
            xMean = 1f,
            yMean = 2f,
            zMean = 3f,
            magnitudeMean = 4f,
            xStdDev = 0.1f,
            yStdDev = 0.2f,
            zStdDev = 0.3f,
            magnitudeStdDev = 0.4f
        )
        val overlayArea = PhotoOverlayArea(
            topLeft = NormalizedPoint(0.1f, 0.2f),
            topRight = NormalizedPoint(0.9f, 0.2f),
            bottomRight = NormalizedPoint(0.8f, 0.95f),
            bottomLeft = NormalizedPoint(0.12f, 0.92f)
        )
        val draft = ScanDraft(
            setup = ScanSetup(
                name = "Interrupted scan",
                gridWidth = 5,
                gridHeight = 7,
                shouldTakePhoto = true,
                captureMode = CaptureMode.AutoWhenStable
            ),
            currentSessionId = "session-draft",
            baseline = baseline,
            photoUri = "content://magnetic-camera/photo/session-draft",
            overlayArea = overlayArea,
            isScanStarted = true,
            cells = listOf(cell("session-draft").copy(row = 2, col = 3))
        )

        val restored = ScanDraftCodec.decode(ScanDraftCodec.encode(draft))

        assertEquals(draft.setup, restored.setup)
        assertEquals(draft.currentSessionId, restored.currentSessionId)
        assertEquals(draft.baseline, restored.baseline)
        assertEquals(draft.photoUri, restored.photoUri)
        assertEquals(draft.overlayArea, restored.overlayArea)
        assertEquals(draft.isScanStarted, restored.isScanStarted)
        assertEquals(draft.cells, restored.cells)
    }

    @Test
    fun scanDraftCodecRejectsCorruptedDrafts() {
        assertTrue(runCatching { ScanDraftCodec.decode("{not-json") }.isFailure)
        assertTrue(runCatching { ScanDraftCodec.decode("""{"setup":{}}""") }.isFailure)
    }

    @Test
    fun scanDraftCodecRestoresLegacyCellsWithoutDerivedStats() {
        val legacyJson = """
            {
              "setup": {
                "name": "Legacy interrupted scan",
                "gridWidth": 5,
                "gridHeight": 5,
                "shouldTakePhoto": false,
                "captureMode": "Manual"
              },
              "currentSessionId": "legacy-session",
              "isScanStarted": true,
              "cells": [
                {
                  "id": "legacy-cell",
                  "sessionId": "legacy-session",
                  "row": 1,
                  "col": 2,
                  "sampleCount": 10,
                  "capturedAtMillis": 42,
                  "xMean": 1.0,
                  "yMean": 2.0,
                  "zMean": 3.0,
                  "magnitudeMean": 4.0,
                  "vectorDeltaMean": 6.0,
                  "vectorDeltaStdDev": 0.7,
                  "accuracy": 3
                }
              ]
            }
        """.trimIndent()

        val restored = ScanDraftCodec.decode(legacyJson)
        val cell = restored.cells.single()

        assertEquals(true, restored.isScanStarted)
        assertEquals(5, restored.setup.gridWidth)
        assertEquals(4f, cell.magnitudeMedian, 0.0001f)
        assertEquals(4f, cell.magnitudeMin, 0.0001f)
        assertEquals(4f, cell.magnitudeMax, 0.0001f)
        assertEquals(0f, cell.magnitudeStdDev, 0.0001f)
        assertEquals(6f, cell.vectorDeltaMedian, 0.0001f)
        assertEquals(6f, cell.vectorDeltaMin, 0.0001f)
        assertEquals(6f, cell.vectorDeltaMax, 0.0001f)
        assertEquals(0f, cell.magnitudeDeltaMean, 0.0001f)
    }

    @Test
    fun jsonExporterContainsRequiredSchemaFieldsAndCells() {
        val baseline = MagneticBaseline(
            createdAtMillis = 1L,
            sampleCount = 4,
            xMean = 1f,
            yMean = 2f,
            zMean = 3f,
            magnitudeMean = 4f,
            xStdDev = 0.1f,
            yStdDev = 0.2f,
            zStdDev = 0.3f,
            magnitudeStdDev = 0.4f
        )
        val session = ScanSession(
            id = "scan-1",
            name = "Test scan",
            createdAtMillis = 1_700_000_000_000L,
            gridWidth = 1,
            gridHeight = 1,
            baseline = baseline,
            photoUri = null,
            cells = listOf(cell("scan-1"))
        )
        val sensorInfo = MagneticSensorInfo(
            isAvailable = true,
            name = "Pixel magnetometer",
            vendor = "Google",
            sensorType = Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED,
            typeLabel = "uncalibrated magnetic field",
            resolution = 0.1f,
            maximumRange = 2000f,
            minimumDelayMicros = 10000,
            isUncalibrated = true
        )

        val json = JsonExporter().export(
            session = session,
            deviceManufacturer = "Google",
            deviceModel = "Pixel 8",
            androidVersion = "15",
            sensorInfo = sensorInfo
        )

        assertTrue(json.contains("\"app\": \"Magnetic Camera\""))
        assertTrue(json.contains("\"schemaVersion\": 1"))
        assertTrue(json.contains("\"id\": \"scan-1\""))
        assertTrue(json.contains("\"sessionId\": \"scan-1\""))
        assertTrue(json.contains("\"type\": ${Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED}"))
        assertTrue(json.contains("\"width\": 1"))
        assertTrue(json.contains("\"row\": 0"))
        assertTrue(json.contains("\"sampleCount\": 12"))
        assertTrue(json.contains("\"magnitudeMedian\": 4.0"))
        assertTrue(json.contains("\"magnitudeStdDev\": 0.5"))
        assertTrue(json.contains("\"vectorDeltaMax\": 7.0"))
        assertTrue(json.contains("\"vectorDeltaStdDev\": 0.7"))
        assertTrue(json.contains("\"accuracy\": ${SensorManager.SENSOR_STATUS_ACCURACY_HIGH}"))
    }

    @Test
    fun jsonExporterEscapesTextFields() {
        val baseline = MagneticBaseline(
            createdAtMillis = 1L,
            sampleCount = 4,
            xMean = 1f,
            yMean = 2f,
            zMean = 3f,
            magnitudeMean = 4f,
            xStdDev = 0.1f,
            yStdDev = 0.2f,
            zStdDev = 0.3f,
            magnitudeStdDev = 0.4f
        )
        val session = ScanSession(
            id = "scan\\1",
            name = "Quote \" scan\nnext",
            createdAtMillis = 1_700_000_000_000L,
            gridWidth = 1,
            gridHeight = 1,
            baseline = baseline,
            photoUri = null,
            cells = listOf(cell("scan\\1").copy(id = "cell\t1"))
        )
        val sensorInfo = MagneticSensorInfo(
            isAvailable = true,
            name = "Pixel \"mag\"",
            vendor = "Vendor\tName",
            sensorType = Sensor.TYPE_MAGNETIC_FIELD,
            typeLabel = "calibrated magnetic field",
            resolution = 0.1f,
            maximumRange = 2000f,
            minimumDelayMicros = 10000,
            isUncalibrated = false
        )

        val json = JsonExporter().export(
            session = session,
            deviceManufacturer = "G\\oogle",
            deviceModel = "Pixel\n8",
            androidVersion = "15",
            sensorInfo = sensorInfo
        )

        assertTrue(json.contains("\"id\": \"scan\\\\1\""))
        assertTrue(json.contains("\"name\": \"Quote \\\" scan\\nnext\""))
        assertTrue(json.contains("\"manufacturer\": \"G\\\\oogle\""))
        assertTrue(json.contains("\"model\": \"Pixel\\n8\""))
        assertTrue(json.contains("\"name\": \"Pixel \\\"mag\\\"\""))
        assertTrue(json.contains("\"vendor\": \"Vendor\\tName\""))
        assertTrue(json.contains("\"id\": \"cell\\t1\""))
        assertTrue(json.contains("\"sessionId\": \"scan\\\\1\""))
    }

    @Test
    fun csvExporterContainsRequiredColumnsAndCells() {
        val baseline = MagneticBaseline(
            createdAtMillis = 1L,
            sampleCount = 4,
            xMean = 1f,
            yMean = 2f,
            zMean = 3f,
            magnitudeMean = 4f,
            xStdDev = 0.1f,
            yStdDev = 0.2f,
            zStdDev = 0.3f,
            magnitudeStdDev = 0.4f
        )
        val session = ScanSession(
            id = "scan-1",
            name = "Test scan",
            createdAtMillis = 1_700_000_000_000L,
            gridWidth = 1,
            gridHeight = 1,
            baseline = baseline,
            photoUri = null,
            cells = listOf(cell("scan-1"))
        )

        val csv = CsvExporter().export(session).lines()

        assertEquals(
            "session_id,cell_id,row,col,sample_count,captured_at_millis,x_mean,y_mean,z_mean,magnitude_mean,magnitude_median,magnitude_min,magnitude_max,magnitude_stddev,magnitude_delta_mean,vector_delta_mean,vector_delta_median,vector_delta_min,vector_delta_max,vector_delta_stddev,accuracy",
            csv.first()
        )
        assertEquals("scan-1,cell-1,0,0,12,2,1.0,2.0,3.0,4.0,4.0,3.0,5.0,0.5,2.0,6.0,6.0,5.0,7.0,0.7,3", csv[1])
    }

    @Test
    fun exportersSanitizeNonFiniteCellValues() {
        val baseline = MagneticBaseline(
            createdAtMillis = 1L,
            sampleCount = 4,
            xMean = 1f,
            yMean = 2f,
            zMean = 3f,
            magnitudeMean = 4f,
            xStdDev = 0.1f,
            yStdDev = 0.2f,
            zStdDev = 0.3f,
            magnitudeStdDev = 0.4f
        )
        val session = ScanSession(
            id = "scan-1",
            name = "Test scan",
            createdAtMillis = 1_700_000_000_000L,
            gridWidth = 1,
            gridHeight = 1,
            baseline = baseline,
            photoUri = null,
            cells = listOf(
                cell("scan-1").copy(
                    xMean = Float.NaN,
                    yMean = Float.POSITIVE_INFINITY,
                    magnitudeMean = Float.NEGATIVE_INFINITY,
                    vectorDeltaMean = Float.NaN
                )
            )
        )

        val json = JsonExporter().export(
            session = session,
            deviceManufacturer = "Google",
            deviceModel = "Pixel 8",
            androidVersion = "15",
            sensorInfo = null
        )
        val csv = CsvExporter().export(session)

        assertFalse(json.contains("NaN"))
        assertFalse(json.contains("Infinity"))
        assertEquals(0.0, JSONObject(json).getJSONArray("cells").getJSONObject(0).getDouble("xMean"), 0.0)
        assertFalse(csv.contains("NaN"))
        assertFalse(csv.contains("Infinity"))
    }

    @Test
    fun savedSessionSummaryIgnoresNonFiniteVectorDeltas() {
        val cells = listOf(
            cell("scan-1").copy(vectorDeltaMean = Float.NaN),
            cell("scan-1").copy(id = "cell-2", vectorDeltaMean = 12f),
            cell("scan-1").copy(id = "cell-3", vectorDeltaMean = Float.POSITIVE_INFINITY)
        )

        assertEquals(12f, ScanSessionRepositoryImpl.maxFiniteVectorDelta(cells), 0.0001f)
        assertEquals(
            0f,
            ScanSessionRepositoryImpl.maxFiniteVectorDelta(
                listOf(
                    cell("scan-1").copy(vectorDeltaMean = Float.NaN),
                    cell("scan-1").copy(id = "cell-2", vectorDeltaMean = Float.NEGATIVE_INFINITY)
                )
            ),
            0.0001f
        )
    }

    @Test
    fun scanUiStateOnlyExposesUnsavedScansForHomeResume() {
        val completeUnsaved = ScanUiState(
            setup = ScanSetup(gridWidth = 1, gridHeight = 1),
            currentSessionId = "scan-1",
            isScanStarted = true,
            cells = listOf(cell("scan-1"))
        )

        assertTrue(completeUnsaved.isComplete)
        assertTrue(completeUnsaved.hasUnsavedScan)
        assertFalse(completeUnsaved.copy(savedSessionId = "scan-1").hasUnsavedScan)
    }

    private fun sample(
        x: Float,
        y: Float,
        z: Float,
        timestampNanos: Long = 1L,
        accuracy: Int = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
    ): MagneticSample {
        return MagneticSample(
            timestampNanos = timestampNanos,
            sensorType = Sensor.TYPE_MAGNETIC_FIELD,
            accuracy = accuracy,
            xMicroTesla = x,
            yMicroTesla = y,
            zMicroTesla = z,
            magnitudeMicroTesla = MagneticMath.magnitude(x, y, z)
        )
    }

    private fun MagneticSample.withBaseline(baseline: MagneticBaseline): MagneticSample {
        return copy(
            baselineDeltaMicroTesla = MagneticMath.magnitudeDelta(magnitudeMicroTesla, baseline.magnitudeMean),
            baselineVectorDeltaMicroTesla = MagneticMath.vectorDelta(
                xMicroTesla,
                yMicroTesla,
                zMicroTesla,
                baseline.xMean,
                baseline.yMean,
                baseline.zMean
            )
        )
    }

    private fun cell(sessionId: String): GridCellMeasurement {
        return GridCellMeasurement(
            id = "cell-1",
            sessionId = sessionId,
            row = 0,
            col = 0,
            sampleCount = 12,
            capturedAtMillis = 2L,
            xMean = 1f,
            yMean = 2f,
            zMean = 3f,
            magnitudeMean = 4f,
            magnitudeMedian = 4f,
            magnitudeMin = 3f,
            magnitudeMax = 5f,
            magnitudeStdDev = 0.5f,
            vectorDeltaMean = 6f,
            vectorDeltaMedian = 6f,
            vectorDeltaMin = 5f,
            vectorDeltaMax = 7f,
            vectorDeltaStdDev = 0.7f,
            magnitudeDeltaMean = 2f,
            accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
        )
    }
}
