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
abstract class ScanDao {
    @Query("SELECT * FROM ScanSessionEntity ORDER BY createdAtMillis DESC")
    abstract fun observeSessions(): Flow<List<ScanSessionEntity>>

    @Transaction
    @Query("SELECT * FROM ScanSessionEntity WHERE id = :id")
    abstract suspend fun getSession(id: String): SessionWithCells?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertSession(session: ScanSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertCells(cells: List<GridCellMeasurementEntity>)

    @Query("DELETE FROM GridCellMeasurementEntity WHERE sessionId = :sessionId")
    protected abstract suspend fun deleteCellsForSession(sessionId: String)

    @Transaction
    open suspend fun replaceSessionWithCells(
        session: ScanSessionEntity,
        cells: List<GridCellMeasurementEntity>
    ) {
        insertSession(session)
        deleteCellsForSession(session.id)
        insertCells(cells)
    }
}
