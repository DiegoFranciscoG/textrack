package io.github.diegofranciscog.textrack.scanner.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Lectura guardada en el dispositivo (fuente de verdad local, patrón offline-first).
 * `clientReadingId` es la clave de idempotencia que viaja al servidor; `ticketId` es único para no escanear
 * dos veces el mismo ticket en el mismo dispositivo.
 */
@Entity(tableName = "readings", indices = [Index(value = ["ticketId"], unique = true), Index("status")])
data class ReadingEntity(
    @PrimaryKey val clientReadingId: String,
    val ticketId: String,
    val payload: String,
    val operatorCode: String,
    val bundleCode: String,
    val operationCode: String,
    val quantity: Int,
    val scannedAtMillis: Long,
    val status: String,
    val message: String? = null,
    val attempts: Int = 0,
    val syncedAtMillis: Long? = null,
)

@Entity(tableName = "operators")
data class OperatorEntity(
    @PrimaryKey val code: String,
    val fullName: String,
    val lineCode: String?,
)

data class StatusCount(val status: String, val total: Int)
