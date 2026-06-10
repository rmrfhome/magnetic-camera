package com.example.magneticcamera.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ScanSessionEntity::class,
        GridCellMeasurementEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao
}
