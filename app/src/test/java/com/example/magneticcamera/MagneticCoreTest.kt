package com.example.magneticcamera

import android.hardware.Sensor
import android.hardware.SensorManager
import com.example.magneticcamera.core.export.CsvExporter
import com.example.magneticcamera.core.export.JsonExporter
import com.example.magneticcamera.core.graphics.BitmapLoader
import com.example.magneticcamera.core.graphics.HeatmapGenerator
import com.example.magneticcamera.core.graphics.HeatmapInput
import com.example.magneticcamera.core.graphics.MonochromeGlowPalette
import com.example.magneticcamera.core.graphics.ScientificHeatmapPalette
import com.example.magneticcamera.core.math.LowPassFilter
import com.example.magneticcamera.core.math.MagneticMath
import com.example.magneticcamera.core.math.MedianFilter
import com.example.magneticcamera.core.sensors.FakeMagneticSensorReader
import com.example.magneticcamera.core.sensors.MagneticSensorDiagnostics
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
import com.example.magneticcamera.domain.scan.PaletteMode
import com.example.magneticcamera.domain.scan.ScanDraft
import com.example.magneticcamera.domain.scan.ScanSession
import com.example.magneticcamera.domain.scan.ScanSetup
import com.example.magneticcamera.ui.live.LiveMeterViewModel
import com.example.magneticcamera.ui.scan.ScanUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.math.sqrt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
    fun fakeSensorReportsStartFailureWithoutEmittingSamples() = runBlocking {
        val reader = FakeMagneticSensorReader(startFails = true)

        reader.start()

        assertEquals(false, reader.sensorInfo.value?.isAvailable)
        assertEquals(
            MagneticSensorDiagnostics.SENSOR_START_FAILED_WARNING,
            reader.diagnosticMessage.value
        )
        assertNull(withTimeoutOrNull(80L) { reader.samples.first() })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun liveMeterShowsSensorStartFailureDiagnostic() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val reader = FakeMagneticSensorReader(startFails = true)
            val viewModel = LiveMeterViewModel(
                sensorReader = reader,
                elapsedRealtimeMillis = { testScheduler.currentTime }
            )

            viewModel.start()

            assertEquals(false, viewModel.uiState.value.sensorAvailable)
            assertEquals(
                MagneticSensorDiagnostics.SENSOR_START_FAILED_WARNING,
                viewModel.uiState.value.errorMessage
            )
        } finally {
            Dispatchers.resetMain()
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
    fun heatmapLegendCarriesSelectedPaletteGradientForPngExports() {
        val generator = HeatmapGenerator()
        val input = HeatmapInput(gridWidth = 2, gridHeight = 2, values = listOf(0f, 10f, 20f, 30f))

        val scientific = generator.generate(
            input = input,
            outputWidth = 4,
            outputHeight = 4,
            palette = ScientificHeatmapPalette
        )
        val monochrome = generator.generate(
            input = input,
            outputWidth = 4,
            outputHeight = 4,
            palette = MonochromeGlowPalette
        )

        assertEquals(160, scientific.legend.gradientColors.size)
        assertEquals(160, monochrome.legend.gradientColors.size)
        assertTrue(scientific.legend.gradientColors.first() != scientific.legend.gradientColors.last())
        assertTrue(monochrome.legend.gradientColors.first() != monochrome.legend.gradientColors.last())
        assertTrue(scientific.legend.gradientColors != monochrome.legend.gradientColors)
    }

    @Test
    fun bitmapLoaderSampleSizeBoundsLargePhotoDecodes() {
        assertEquals(1, BitmapLoader.calculateInSampleSize(width = 2_048, height = 1_536, maxDimension = 2_048))
        assertEquals(2, BitmapLoader.calculateInSampleSize(width = 4_000, height = 3_000, maxDimension = 2_048))
        assertEquals(4, BitmapLoader.calculateInSampleSize(width = 5_000, height = 4_000, maxDimension = 2_048))
        assertEquals(1, BitmapLoader.calculateInSampleSize(width = 0, height = 0, maxDimension = 2_048))
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
            cells = listOf(cell("session-draft").copy(row = 2, col = 3)),
            paletteMode = PaletteMode.MonochromeGlow,
            normalizationMode = NormalizationMode.BaselineDeltaFixedScale(50f),
            opacity = 0.38f,
            showGrid = false,
            showLegend = false
        )

        val restored = ScanDraftCodec.decode(ScanDraftCodec.encode(draft))

        assertEquals(draft.setup, restored.setup)
        assertEquals(draft.currentSessionId, restored.currentSessionId)
        assertEquals(draft.baseline, restored.baseline)
        assertEquals(draft.photoUri, restored.photoUri)
        assertEquals(draft.overlayArea, restored.overlayArea)
        assertEquals(draft.isScanStarted, restored.isScanStarted)
        assertEquals(draft.cells, restored.cells)
        assertEquals(draft.paletteMode, restored.paletteMode)
        assertEquals(draft.normalizationMode, restored.normalizationMode)
        assertEquals(draft.opacity, restored.opacity, 0.0001f)
        assertEquals(draft.showGrid, restored.showGrid)
        assertEquals(draft.showLegend, restored.showLegend)
    }

    @Test
    fun scanDraftCodecRejectsCorruptedDrafts() {
        assertTrue(runCatching { ScanDraftCodec.decode("{not-json") }.isFailure)
        assertTrue(runCatching { ScanDraftCodec.decode("""{"setup":{}}""") }.isFailure)
    }

    @Test
    fun scanDraftCodecRejectsNonFiniteBaselineValues() {
        val draftJson = """
            {
              "setup": {
                "name": "Invalid baseline draft",
                "gridWidth": 5,
                "gridHeight": 5,
                "shouldTakePhoto": false,
                "captureMode": "Manual"
              },
              "currentSessionId": "invalid-baseline",
              "baseline": {
                "createdAtMillis": 123,
                "sampleCount": 10,
                "xMean": 1e999,
                "yMean": 2.0,
                "zMean": 3.0,
                "magnitudeMean": 4.0,
                "xStdDev": 0.1,
                "yStdDev": 0.2,
                "zStdDev": 0.3,
                "magnitudeStdDev": 0.4
              },
              "cells": []
            }
        """.trimIndent()

        assertTrue(runCatching { ScanDraftCodec.decode(draftJson) }.isFailure)
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
        assertEquals(PaletteMode.Scientific, restored.paletteMode)
        assertEquals(NormalizationMode.AutoLocal, restored.normalizationMode)
        assertEquals(0.72f, restored.opacity, 0.0001f)
        assertTrue(restored.showGrid)
        assertTrue(restored.showLegend)
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
    fun scanDraftCodecClampsOutOfRangeRestoredUiState() {
        val draftJson = """
            {
              "setup": {
                "name": "Out of range draft",
                "gridWidth": 0,
                "gridHeight": 99,
                "shouldTakePhoto": true,
                "captureMode": "Unexpected"
              },
              "currentSessionId": "clamped-session",
              "photoUri": "file:///tmp/reference.jpg",
              "overlayArea": {
                "topLeft": { "x": -0.5, "y": -1.0 },
                "topRight": { "x": 1.5, "y": -0.25 },
                "bottomRight": { "x": 2.0, "y": 4.0 },
                "bottomLeft": { "x": -1.0, "y": 1.25 }
              },
              "paletteMode": "UnknownPalette",
              "normalizationMode": {
                "type": "BaselineDeltaFixedScale",
                "maxDeltaMicroTesla": -20.0
              },
              "opacity": 4.5,
              "showGrid": false,
              "showLegend": false,
              "cells": []
            }
        """.trimIndent()

        val restored = ScanDraftCodec.decode(draftJson)

        assertEquals(1, restored.setup.gridWidth)
        assertEquals(20, restored.setup.gridHeight)
        assertEquals(CaptureMode.Manual, restored.setup.captureMode)
        assertEquals(NormalizedPoint(0f, 0f), restored.overlayArea.topLeft)
        assertEquals(NormalizedPoint(1f, 0f), restored.overlayArea.topRight)
        assertEquals(NormalizedPoint(1f, 1f), restored.overlayArea.bottomRight)
        assertEquals(NormalizedPoint(0f, 1f), restored.overlayArea.bottomLeft)
        assertEquals(PaletteMode.Scientific, restored.paletteMode)
        assertEquals(NormalizationMode.BaselineDeltaFixedScale(1f), restored.normalizationMode)
        assertEquals(1f, restored.opacity, 0.0001f)
        assertFalse(restored.showGrid)
        assertFalse(restored.showLegend)
    }

    @Test
    fun scanDraftCodecSanitizesCorruptedOverlayPointsAndCellCounters() {
        val draftJson = """
            {
              "setup": {
                "name": "Corrupted interrupted scan",
                "gridWidth": 5,
                "gridHeight": 5,
                "shouldTakePhoto": true,
                "captureMode": "Manual"
              },
              "currentSessionId": "corrupted-session",
              "photoUri": "file:///tmp/reference.jpg",
              "overlayArea": {
                "topLeft": { "x": 1e999, "y": -1e999 },
                "topRight": { "x": 1.25, "y": -0.25 },
                "bottomRight": { "x": 0.75, "y": 0.8 },
                "bottomLeft": { "x": 0.1, "y": 0.9 }
              },
              "isScanStarted": true,
              "cells": [
                {
                  "id": "corrupted-cell",
                  "sessionId": "corrupted-session",
                  "row": -2,
                  "col": -3,
                  "sampleCount": -10,
                  "capturedAtMillis": -42,
                  "xMean": 1.0,
                  "yMean": 2.0,
                  "zMean": 3.0,
                  "magnitudeMean": 4.0,
                  "magnitudeMedian": 4.0,
                  "magnitudeMin": 3.0,
                  "magnitudeMax": 5.0,
                  "magnitudeStdDev": -0.5,
                  "vectorDeltaMean": 6.0,
                  "vectorDeltaMedian": 6.0,
                  "vectorDeltaMin": 5.0,
                  "vectorDeltaMax": 7.0,
                  "vectorDeltaStdDev": -0.7,
                  "magnitudeDeltaMean": 2.0,
                  "accuracy": -1
                }
              ]
            }
        """.trimIndent()

        val restored = ScanDraftCodec.decode(draftJson)
        val cell = restored.cells.single()

        assertEquals(NormalizedPoint(0f, 0f), restored.overlayArea.topLeft)
        assertEquals(NormalizedPoint(1f, 0f), restored.overlayArea.topRight)
        assertEquals(0, cell.row)
        assertEquals(0, cell.col)
        assertEquals(0, cell.sampleCount)
        assertEquals(0L, cell.capturedAtMillis)
        assertEquals(0f, cell.magnitudeStdDev, 0.0001f)
        assertEquals(0f, cell.vectorDeltaStdDev, 0.0001f)
        assertEquals(0, cell.accuracy)
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
        val parsed = JSONObject(json)
        val parsedSession = parsed.getJSONObject("session")
        val parsedCell = parsed.getJSONArray("cells").getJSONObject(0)

        assertEquals("Magnetic Camera", parsed.getString("app"))
        assertEquals(1, parsed.getInt("schemaVersion"))
        assertEquals("scan-1", parsedSession.getString("id"))
        assertEquals("scan-1", parsedCell.getString("sessionId"))
        assertEquals(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED, parsedSession.getJSONObject("sensor").getInt("type"))
        assertEquals(1, parsedSession.getJSONObject("grid").getInt("width"))
        assertEquals(0, parsedCell.getInt("row"))
        assertEquals(12, parsedCell.getInt("sampleCount"))
        assertEquals(4.0, parsedCell.getDouble("magnitudeMedian"), 0.0001)
        assertEquals(0.5, parsedCell.getDouble("magnitudeStdDev"), 0.0001)
        assertEquals(7.0, parsedCell.getDouble("vectorDeltaMax"), 0.0001)
        assertEquals(0.7, parsedCell.getDouble("vectorDeltaStdDev"), 0.0001)
        assertEquals(SensorManager.SENSOR_STATUS_ACCURACY_HIGH, parsedCell.getInt("accuracy"))
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
    fun jsonExporterProducesValidJsonForControlCharacters() {
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
            name = "Control\u0001scan\b",
            createdAtMillis = 1_700_000_000_000L,
            gridWidth = 1,
            gridHeight = 1,
            baseline = baseline,
            photoUri = null,
            cells = listOf(cell("scan-1").copy(id = "cell\u0002one"))
        )

        val json = JsonExporter().export(
            session = session,
            deviceManufacturer = "Google",
            deviceModel = "Pixel 8",
            androidVersion = "15",
            sensorInfo = null
        )
        val parsed = JSONObject(json)

        assertEquals("Control\u0001scan\b", parsed.getJSONObject("session").getString("name"))
        assertEquals("cell\u0002one", parsed.getJSONArray("cells").getJSONObject(0).getString("id"))
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
    fun csvExporterQuotesTextFieldsWithCommasQuotesAndLineBreaks() {
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
            id = "scan,\"A\"\rnext",
            name = "Test scan",
            createdAtMillis = 1_700_000_000_000L,
            gridWidth = 1,
            gridHeight = 1,
            baseline = baseline,
            photoUri = null,
            cells = listOf(cell("scan,\"A\"\rnext").copy(id = "cell\n1"))
        )

        val csv = CsvExporter().export(session)

        assertTrue(csv.contains("\"scan,\"\"A\"\"\rnext\""))
        assertTrue(csv.contains("\"cell\n1\""))
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
