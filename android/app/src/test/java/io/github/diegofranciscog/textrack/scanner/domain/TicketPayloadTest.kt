package io.github.diegofranciscog.textrack.scanner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TicketPayloadTest {

    private val valid = "TT1.k1.3f1c2d7e-1a2b-4c3d-8e9f-001122334455.CT-00001-003.OP20.20.AbCdEfGhIjKlMnOpQrStUv"

    @Test
    fun parsesValidTicket() {
        val payload = TicketPayload.parse(valid)

        assertNotNull(payload)
        assertEquals("CT-00001-003", payload!!.bundleCode)
        assertEquals("OP20", payload.operationCode)
        assertEquals(20, payload.quantity)
        assertEquals("3f1c2d7e-1a2b-4c3d-8e9f-001122334455", payload.ticketId.toString())
    }

    @Test
    fun rejectsForeignOrMalformedCodes() {
        listOf(
            null,
            "",
            "https://ejemplo.com",
            valid.replace("TT1.", "TT2."),
            valid.replace(".20.", ".0."),
            valid.replace(".20.", ".101."),
            valid.replace("CT-00001-003", "ct-00001-003"),
            valid + ".extra",
            valid.dropLast(1),
        ).forEach { assertNull("debería rechazar: $it", TicketPayload.parse(it)) }
    }
}
