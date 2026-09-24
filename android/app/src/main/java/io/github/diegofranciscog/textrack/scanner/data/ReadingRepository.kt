package io.github.diegofranciscog.textrack.scanner.data

import io.github.diegofranciscog.textrack.scanner.data.local.ReadingDao
import io.github.diegofranciscog.textrack.scanner.data.local.ReadingEntity
import io.github.diegofranciscog.textrack.scanner.data.remote.ApiService
import io.github.diegofranciscog.textrack.scanner.data.remote.BatchScanRequest
import io.github.diegofranciscog.textrack.scanner.data.remote.ScanRequestDto
import io.github.diegofranciscog.textrack.scanner.domain.SyncStatus
import io.github.diegofranciscog.textrack.scanner.domain.TicketPayload
import java.time.Instant
import java.util.UUID

/**
 * Lecturas offline-first:
 * 1. Escanear guarda la lectura en Room con un `clientReadingId` nuevo (nunca depende de la red).
 * 2. Sincronizar envía lotes PENDING; el servidor responde por cada `clientReadingId`.
 * 3. Si la respuesta se pierde, las lecturas siguen PENDING y se reenvían con el MISMO id: el servidor devuelve
 *    DUPLICATE y no crea otra lectura. Por eso reintentar es siempre seguro.
 */
class ReadingRepository(
    private val dao: ReadingDao,
    private val api: ApiService,
    private val deviceId: () -> String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    suspend fun registerScan(raw: String, operatorCode: String): ScanOutcome {
        val payload = TicketPayload.parse(raw) ?: return ScanOutcome.Invalid
        val reading = ReadingEntity(
            clientReadingId = newId(),
            ticketId = payload.ticketId.toString(),
            payload = raw.trim(),
            operatorCode = operatorCode,
            bundleCode = payload.bundleCode,
            operationCode = payload.operationCode,
            quantity = payload.quantity,
            scannedAtMillis = clock(),
            status = SyncStatus.PENDING.name,
        )
        if (dao.insert(reading) == -1L) {
            val existing = dao.findByTicket(reading.ticketId)
            return ScanOutcome.AlreadyScanned(payload, existing?.operatorCode)
        }
        return ScanOutcome.Queued(payload)
    }

    /** Envía todas las lecturas pendientes en lotes. Lanza IOException/HttpException si falla la red. */
    suspend fun syncPending(batchSize: Int = BATCH_SIZE): SyncReport {
        var report = SyncReport()
        while (true) {
            val pending = dao.pending(batchSize)
            if (pending.isEmpty()) {
                return report
            }
            dao.incrementAttempts(pending.map { it.clientReadingId })
            val response = api.syncBatch(BatchScanRequest(pending.map { it.toRequest() }))
            val byId = response.results.associateBy { it.clientReadingId }
            val now = clock()
            for (reading in pending) {
                val result = byId[reading.clientReadingId] ?: continue
                val status = SyncStatus.fromServer(result.status)
                val message = when (status) {
                    SyncStatus.CONFLICT -> "Ya registrado por ${result.registeredBy ?: "otro operario"}"
                    SyncStatus.REJECTED -> result.message ?: result.status
                    else -> null
                }
                dao.updateStatus(reading.clientReadingId, status.name, message, now.takeIf { status == SyncStatus.SYNCED })
                report = report.add(status)
            }
            if (pending.size < batchSize) {
                return report
            }
        }
    }

    private fun ReadingEntity.toRequest() = ScanRequestDto(
        clientReadingId = clientReadingId,
        payload = payload,
        operatorCode = operatorCode,
        scannedAt = Instant.ofEpochMilli(scannedAtMillis).toString(),
        deviceId = deviceId(),
    )

    companion object {
        const val BATCH_SIZE = 100
    }
}

sealed interface ScanOutcome {
    data class Queued(val payload: TicketPayload) : ScanOutcome
    data class AlreadyScanned(val payload: TicketPayload, val operatorCode: String?) : ScanOutcome
    data object Invalid : ScanOutcome
}

data class SyncReport(val synced: Int = 0, val conflicts: Int = 0, val rejected: Int = 0) {
    fun add(status: SyncStatus): SyncReport = when (status) {
        SyncStatus.SYNCED -> copy(synced = synced + 1)
        SyncStatus.CONFLICT -> copy(conflicts = conflicts + 1)
        SyncStatus.REJECTED -> copy(rejected = rejected + 1)
        SyncStatus.PENDING -> this
    }
}
