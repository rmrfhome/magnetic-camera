package com.example.magneticcamera.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = ScanSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId"), Index(value = ["sessionId", "row", "col"], unique = true)]
)
data class GridCellMeasurementEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val row: Int,
    val col: Int,
    val sampleCount: Int,
    val capturedAtMillis: Long,
    val xMean: Float,
    val yMean: Float,
    val zMean: Float,
    val magnitudeMean: Float,
    val magnitudeMedian: Float,
    val magnitudeMin: Float,
    val magnitudeMax: Float,
    val magnitudeStdDev: Float,
    val vectorDeltaMean: Float,
    val vectorDeltaMedian: Float,
    val vectorDeltaMin: Float,
    val vectorDeltaMax: Float,
    val magnitudeDeltaMean: Float,
    val vectorDeltaStdDev: Float,
    val accuracy: Int
)
