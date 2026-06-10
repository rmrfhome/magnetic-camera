package com.example.magneticcamera.data.repository

import android.graphics.Bitmap
import com.example.magneticcamera.core.graphics.HeatmapRender
import com.example.magneticcamera.core.sensors.MagneticSensorInfo
import com.example.magneticcamera.data.db.ScanSessionEntity
import com.example.magneticcamera.data.db.SessionWithCells
import com.example.magneticcamera.domain.scan.ScanSession
import kotlinx.coroutines.flow.Flow

interface ScanSessionRepository {
    fun observeSessions(): Flow<List<ScanSessionEntity>>
    suspend fun getSession(id: String): SessionWithCells?
    suspend fun saveSession(
        session: ScanSession,
        sensorInfo: MagneticSensorInfo?,
        heatmapRender: HeatmapRender,
        overlayBitmap: Bitmap?,
        includeGrid: Boolean,
        includeLegend: Boolean
    ): String
}
