package com.example.magneticcamera.data.repository

import android.graphics.Bitmap
import android.os.Build
import com.example.magneticcamera.core.export.CsvExporter
import com.example.magneticcamera.core.export.JsonExporter
import com.example.magneticcamera.core.export.PngExporter
import com.example.magneticcamera.core.graphics.HeatmapRender
import com.example.magneticcamera.core.sensors.MagneticSensorInfo
import com.example.magneticcamera.core.storage.AppFileStore
import com.example.magneticcamera.data.db.GridCellMeasurementEntity
import com.example.magneticcamera.data.db.ScanDao
import com.example.magneticcamera.data.db.ScanSessionEntity
import com.example.magneticcamera.data.db.SessionWithCells
import com.example.magneticcamera.domain.scan.ScanSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ScanSessionRepositoryImpl(
    private val dao: ScanDao,
    private val fileStore: AppFileStore,
    private val jsonExporter: JsonExporter = JsonExporter(),
    private val csvExporter: CsvExporter = CsvExporter(),
    private val pngExporter: PngExporter = PngExporter()
) : ScanSessionRepository {
    override fun observeSessions(): Flow<List<ScanSessionEntity>> = dao.observeSessions()

    override suspend fun getSession(id: String): SessionWithCells? = dao.getSession(id)

    override suspend fun saveSession(
        session: ScanSession,
        sensorInfo: MagneticSensorInfo?,
        heatmapRender: HeatmapRender,
        overlayBitmap: Bitmap?,
        includeGrid: Boolean,
        includeLegend: Boolean
    ): String = withContext(Dispatchers.IO) {
        val heatmapFile = fileStore.heatmapFile(session.id)
        val rawJsonFile = fileStore.rawJsonFile(session.id)
        val csvFile = fileStore.csvExportFile(session.id)
        val overlayFile = overlayBitmap?.let { fileStore.overlayFile(session.id) }

        pngExporter.saveHeatmap(
            render = heatmapRender,
            file = heatmapFile,
            includeLegend = includeLegend,
            includeGrid = includeGrid,
            gridWidth = session.gridWidth,
            gridHeight = session.gridHeight
        )
        if (overlayBitmap != null && overlayFile != null) {
            pngExporter.saveBitmap(overlayBitmap, overlayFile, heatmapRender.legend.takeIf { includeLegend })
        }
        fileStore.writeText(
            rawJsonFile,
            jsonExporter.export(
                session = session,
                deviceManufacturer = Build.MANUFACTURER,
                deviceModel = Build.MODEL,
                androidVersion = Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString(),
                sensorInfo = sensorInfo
            )
        )
        fileStore.writeText(csvFile, csvExporter.export(session))

        dao.replaceSessionWithCells(
            session = ScanSessionEntity(
                id = session.id,
                name = session.name,
                createdAtMillis = session.createdAtMillis,
                deviceManufacturer = Build.MANUFACTURER,
                deviceModel = Build.MODEL,
                androidVersion = Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString(),
                sensorName = sensorInfo?.name,
                sensorVendor = sensorInfo?.vendor,
                sensorType = sensorInfo?.sensorType ?: -1,
                gridWidth = session.gridWidth,
                gridHeight = session.gridHeight,
                maxVectorDelta = session.cells.maxOfOrNull { it.vectorDeltaMean } ?: 0f,
                baselineX = session.baseline.xMean,
                baselineY = session.baseline.yMean,
                baselineZ = session.baseline.zMean,
                baselineMagnitude = session.baseline.magnitudeMean,
                photoUri = session.photoUri,
                overlayTopLeftX = session.overlayArea.topLeft.x,
                overlayTopLeftY = session.overlayArea.topLeft.y,
                overlayTopRightX = session.overlayArea.topRight.x,
                overlayTopRightY = session.overlayArea.topRight.y,
                overlayBottomRightX = session.overlayArea.bottomRight.x,
                overlayBottomRightY = session.overlayArea.bottomRight.y,
                overlayBottomLeftX = session.overlayArea.bottomLeft.x,
                overlayBottomLeftY = session.overlayArea.bottomLeft.y,
                heatmapImageUri = fileStore.uriString(heatmapFile),
                overlayImageUri = overlayFile?.let(fileStore::uriString),
                rawDataUri = fileStore.uriString(rawJsonFile),
                csvDataUri = fileStore.uriString(csvFile),
                notes = session.notes
            ),
            cells = session.cells.map { cell ->
                GridCellMeasurementEntity(
                    id = cell.id,
                    sessionId = session.id,
                    row = cell.row,
                    col = cell.col,
                    sampleCount = cell.sampleCount,
                    capturedAtMillis = cell.capturedAtMillis,
                    xMean = cell.xMean,
                    yMean = cell.yMean,
                    zMean = cell.zMean,
                    magnitudeMean = cell.magnitudeMean,
                    magnitudeMedian = cell.magnitudeMedian,
                    magnitudeMin = cell.magnitudeMin,
                    magnitudeMax = cell.magnitudeMax,
                    magnitudeStdDev = cell.magnitudeStdDev,
                    vectorDeltaMean = cell.vectorDeltaMean,
                    vectorDeltaMedian = cell.vectorDeltaMedian,
                    vectorDeltaMin = cell.vectorDeltaMin,
                    vectorDeltaMax = cell.vectorDeltaMax,
                    magnitudeDeltaMean = cell.magnitudeDeltaMean,
                    vectorDeltaStdDev = cell.vectorDeltaStdDev,
                    accuracy = cell.accuracy
                )
            }
        )
        session.id
    }
}
