package com.example.magneticcamera.app

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.magneticcamera.core.sensors.AndroidMagneticSensorReader
import com.example.magneticcamera.core.sensors.MagneticSensorReader
import com.example.magneticcamera.core.storage.AppFileStore
import com.example.magneticcamera.core.storage.ScanDraftStore
import com.example.magneticcamera.data.db.AppDatabase
import com.example.magneticcamera.data.repository.ScanSessionRepository
import com.example.magneticcamera.data.repository.ScanSessionRepositoryImpl

class AppContainer(
    context: Context
) {
    private val appContext = context.applicationContext

    val sensorReader: MagneticSensorReader = AndroidMagneticSensorReader(appContext)
    val fileStore: AppFileStore = AppFileStore(appContext)
    val scanDraftStore: ScanDraftStore = ScanDraftStore(appContext)
    val database: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        "magnetic-camera.db"
    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    val repository: ScanSessionRepository = ScanSessionRepositoryImpl(
        dao = database.scanDao(),
        fileStore = fileStore
    )
    val firstLaunchStore = FirstLaunchStore(appContext)

    private companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ScanSessionEntity ADD COLUMN csvDataUri TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE GridCellMeasurementEntity ADD COLUMN magnitudeMedian REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE GridCellMeasurementEntity ADD COLUMN magnitudeMin REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE GridCellMeasurementEntity ADD COLUMN magnitudeMax REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE GridCellMeasurementEntity ADD COLUMN magnitudeStdDev REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE GridCellMeasurementEntity ADD COLUMN vectorDeltaMedian REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE GridCellMeasurementEntity ADD COLUMN vectorDeltaMin REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE GridCellMeasurementEntity ADD COLUMN vectorDeltaMax REAL NOT NULL DEFAULT 0")
            }
        }
    }
}
