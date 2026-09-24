package io.github.diegofranciscog.textrack.scanner.data

import io.github.diegofranciscog.textrack.scanner.data.local.ReadingDao
import io.github.diegofranciscog.textrack.scanner.data.local.ReadingEntity
import io.github.diegofranciscog.textrack.scanner.data.local.StatusCount
import io.github.diegofranciscog.textrack.scanner.data.remote.ApiService
import io.github.diegofranciscog.textrack.scanner.data.remote.BatchScanRequest
import io.github.diegofranciscog.textrack.scanner.data.remote.BatchScanResponse
import io.github.diegofranciscog.textrack.scanner.data.remote.LoginRequest
import io.github.diegofranciscog.textrack.scanner.data.remote.OperatorDto
import io.github.diegofranciscog.textrack.scanner.data.remote.RefreshRequest
import io.github.diegofranciscog.textrack.scanner.data.remote.ScanResultDto
import io.github.diegofranciscog.textrack.scanner.data.remote.TokenResponse
import io.github.diegofranciscog.textrack.scanner.domain.SyncStatus
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Call

class ReadingRepositoryTest {

    private val dao = FakeReadingDao()
    private val server = FakeServer()
    private val repository = ReadingRepository(dao, server, deviceId = { "tablet-test" })

    private fun ticket(bundle: String = "CT-00001-001", op: String = "OP10") =
        "TT1.k1.${UUID.randomUUID()}.$bundle.$op.20.AbCdEfGhIjKlMnOpQrStUv"

    @Test
    fun scanIsStoredOfflineAsPending() = runTest {
        val outcome = repository.registerScan(ticket(), "OPR-101")

        assertTrue(outcome is ScanOutcome.Queued)
        assertEquals(SyncStatus.PENDING.name, dao.rows.values.single().status)
    }

    @Test
    fun sameTicketTwiceOnTheDeviceIsNotStoredAgain() = runTest {
        val raw = ticket()
        repository.registerScan(raw, "OPR-101")

        val second = repository.registerScan(raw, "OPR-102")

        assertTrue(second is ScanOutcome.AlreadyScanned)
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun invalidQrIsNotStored() = runTest {
        assertEquals(ScanOutcome.Invalid, repository.registerScan("hola", "OPR-101"))
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun lostResponseIsRetriedWithTheSameIdsAndDoesNotDuplicate() = runTest {
        repeat(3) { repository.registerScan(ticket(bundle = "CT-00001-00$it"), "OPR-101") }
        server.dropNextResponse = true

        try {
            repository.syncPending()
            fail("debía fallar la red")
        } catch (expected: IOException) {
            // el servidor guardó las lecturas pero la respuesta no llegó
        }
        assertTrue(dao.rows.values.all { it.status == SyncStatus.PENDING.name })

        val report = repository.syncPending()

        assertEquals(3, report.synced)
        assertEquals(3, server.stored.size)
        assertEquals(2, server.batchesReceived)
        assertTrue(dao.rows.values.all { it.status == SyncStatus.SYNCED.name })
    }

    @Test
    fun ticketRegisteredByAnotherDeviceIsMarkedAsConflict() = runTest {
        val raw = ticket()
        server.ticketsTakenByOthers += raw.split('.')[2]
        repository.registerScan(raw, "OPR-101")

        val report = repository.syncPending()

        assertEquals(1, report.conflicts)
        val row = dao.rows.values.single()
        assertEquals(SyncStatus.CONFLICT.name, row.status)
        assertEquals("Ya registrado por OPR-999", row.message)
    }

    @Test
    fun pendingReadingsAreSentInBatches() = runTest {
        repeat(5) { repository.registerScan(ticket(bundle = "CT-00002-00$it"), "OPR-101") }

        repository.syncPending(batchSize = 2)

        assertEquals(3, server.batchesReceived)
        assertEquals(5, server.stored.size)
    }

    /** Servidor simulado con la misma semántica idempotente que la API real. */
    private class FakeServer : ApiService {
        val stored = mutableMapOf<String, String>() // clientReadingId -> ticketId
        val ticketsTakenByOthers = mutableSetOf<String>()
        var dropNextResponse = false
        var batchesReceived = 0

        override suspend fun syncBatch(request: BatchScanRequest): BatchScanResponse {
            batchesReceived++
            val results = request.readings.map { reading ->
                val ticketId = reading.payload.split('.')[2]
                when {
                    stored.containsKey(reading.clientReadingId) -> ScanResultDto(reading.clientReadingId, "DUPLICATE")
                    ticketId in ticketsTakenByOthers || ticketId in stored.values ->
                        ScanResultDto(reading.clientReadingId, "ALREADY_SCANNED", registeredBy = "OPR-999")
                    else -> {
                        stored[reading.clientReadingId] = ticketId
                        ScanResultDto(reading.clientReadingId, "ACCEPTED")
                    }
                }
            }
            if (dropNextResponse) {
                dropNextResponse = false
                throw IOException("conexión perdida")
            }
            return BatchScanResponse(results, results.count { it.status == "ACCEPTED" },
                results.count { it.status == "DUPLICATE" }, results.count { it.status == "ALREADY_SCANNED" })
        }

        override suspend fun login(request: LoginRequest): TokenResponse = error("no usado")
        override fun refresh(request: RefreshRequest): Call<TokenResponse> = error("no usado")
        override suspend fun operators(): List<OperatorDto> = emptyList()
    }

    /** DAO en memoria con las mismas restricciones únicas que Room (PK e índice único por ticket). */
    private class FakeReadingDao : ReadingDao {
        val rows = linkedMapOf<String, ReadingEntity>()

        override suspend fun insert(reading: ReadingEntity): Long {
            if (rows.containsKey(reading.clientReadingId) || rows.values.any { it.ticketId == reading.ticketId }) {
                return -1
            }
            rows[reading.clientReadingId] = reading
            return rows.size.toLong()
        }

        override suspend fun pending(limit: Int): List<ReadingEntity> =
            rows.values.filter { it.status == SyncStatus.PENDING.name }.sortedBy { it.scannedAtMillis }.take(limit)

        override suspend fun findByTicket(ticketId: String): ReadingEntity? = rows.values.find { it.ticketId == ticketId }

        override suspend fun updateStatus(clientReadingId: String, status: String, message: String?, syncedAt: Long?) {
            rows[clientReadingId] = rows.getValue(clientReadingId).copy(status = status, message = message,
                syncedAtMillis = syncedAt)
        }

        override suspend fun incrementAttempts(ids: List<String>) {
            ids.forEach { id -> rows[id] = rows.getValue(id).let { it.copy(attempts = it.attempts + 1) } }
        }

        override fun observeRecent(): Flow<List<ReadingEntity>> = emptyFlow()
        override fun observeCounts(): Flow<List<StatusCount>> = emptyFlow()
    }
}
