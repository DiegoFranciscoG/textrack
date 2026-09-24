package io.github.diegofranciscog.textrack.scanner.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingDao {

    /** Devuelve -1 si el ticket ya se escaneó en este dispositivo (índice único) y no inserta nada. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(reading: ReadingEntity): Long

    @Query("SELECT * FROM readings WHERE status = 'PENDING' ORDER BY scannedAtMillis LIMIT :limit")
    suspend fun pending(limit: Int): List<ReadingEntity>

    @Query("SELECT * FROM readings WHERE ticketId = :ticketId")
    suspend fun findByTicket(ticketId: String): ReadingEntity?

    @Query(
        "UPDATE readings SET status = :status, message = :message, syncedAtMillis = :syncedAt " +
            "WHERE clientReadingId = :clientReadingId",
    )
    suspend fun updateStatus(clientReadingId: String, status: String, message: String?, syncedAt: Long?)

    @Query("UPDATE readings SET attempts = attempts + 1 WHERE clientReadingId IN (:ids)")
    suspend fun incrementAttempts(ids: List<String>)

    @Query("SELECT * FROM readings ORDER BY scannedAtMillis DESC LIMIT 100")
    fun observeRecent(): Flow<List<ReadingEntity>>

    @Query("SELECT status, COUNT(*) AS total FROM readings GROUP BY status")
    fun observeCounts(): Flow<List<StatusCount>>
}

@Dao
interface OperatorDao {

    @Query("SELECT * FROM operators ORDER BY code")
    fun observeAll(): Flow<List<OperatorEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(operators: List<OperatorEntity>)

    @Query("DELETE FROM operators")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(operators: List<OperatorEntity>) {
        deleteAll()
        insertAll(operators)
    }
}
