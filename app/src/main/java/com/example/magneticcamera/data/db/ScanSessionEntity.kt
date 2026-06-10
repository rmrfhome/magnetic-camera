package com.example.magneticcamera.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ScanSessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAtMillis: Long,
    val deviceManufacturer: String,
    val deviceModel: String,
    val androidVersion: String,
    val sensorName: String?,
    val sensorVendor: String?,
    val sensorType: Int,
    val gridWidth: Int,
    val gridHeight: Int,
    val maxVectorDelta: Float,
    val baselineX: Float,
    val baselineY: Float,
    val baselineZ: Float,
    val baselineMagnitude: Float,
    val photoUri: String?,
    val overlayTopLeftX: Float,
    val overlayTopLeftY: Float,
    val overlayTopRightX: Float,
    val overlayTopRightY: Float,
    val overlayBottomRightX: Float,
    val overlayBottomRightY: Float,
    val overlayBottomLeftX: Float,
    val overlayBottomLeftY: Float,
    val heatmapImageUri: String?,
    val overlayImageUri: String?,
    val rawDataUri: String?,
    val csvDataUri: String?,
    val notes: String?
)
