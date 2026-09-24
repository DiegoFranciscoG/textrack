package io.github.diegofranciscog.textrack.scanner.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncStatusTest {

    @Test
    fun acceptedAndDuplicateMeanTheReadingIsOnTheServer() {
        assertEquals(SyncStatus.SYNCED, SyncStatus.fromServer("ACCEPTED"))
        assertEquals(SyncStatus.SYNCED, SyncStatus.fromServer("DUPLICATE"))
    }

    @Test
    fun alreadyScannedIsAConflictAndEverythingElseIsRejected() {
        assertEquals(SyncStatus.CONFLICT, SyncStatus.fromServer("ALREADY_SCANNED"))
        listOf("INVALID_SIGNATURE", "UNKNOWN_TICKET", "UNKNOWN_OPERATOR", "INVALID_TIMESTAMP", "KEY_REUSED")
            .forEach { assertEquals(SyncStatus.REJECTED, SyncStatus.fromServer(it)) }
    }
}
