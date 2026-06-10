package com.example.magneticcamera.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class SessionWithCells(
    @androidx.room.Embedded val session: ScanSessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId"
    )
    val cells: List<GridCellMeasurementEntity>
)

@Dao
interface ScanDao {
    @Query("SELECT * FROM ScanSessionEntity ORDER BY createdAtMillis DESC")
    fun observeSessions(): Flow<List<ScanSessionEntity>>

    @Transaction
    @Query("SELECT * FROM ScanSessionEntity WHERE id = :id")
    suspend fun getSession(id: String): SessionWithCells?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ScanSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCells(cells: List<GridCellMeasurementEntity>)
}
