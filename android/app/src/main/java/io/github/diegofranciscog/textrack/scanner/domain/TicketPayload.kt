package io.github.diegofranciscog.textrack.scanner.domain

import java.util.UUID

/**
 * Contenido del QR de un ticket: `TT1.<keyId>.<ticketId>.<bundle>.<operation>.<qty>.<firma>`.
 *
 * La app solo lo interpreta para mostrar el bulto y la operación sin conexión. La firma HMAC NO se verifica aquí
 * (la clave nunca sale del servidor); el servidor la valida al sincronizar y rechaza los tickets falsificados.
 */
data class TicketPayload(
    val keyId: String,
    val ticketId: UUID,
    val bundleCode: String,
    val operationCode: String,
    val quantity: Int,
    val signature: String,
) {
    companion object {
        private const val VERSION = "TT1"
        private const val MAX_LENGTH = 300
        private val KEY_ID = Regex("[A-Za-z0-9]{1,10}")
        private val CODE = Regex("[A-Z0-9-]{1,40}")
        private val SIGNATURE = Regex("[A-Za-z0-9_-]{22}")

        fun parse(raw: String?): TicketPayload? {
            if (raw.isNullOrBlank() || raw.length > MAX_LENGTH) return null
            val parts = raw.trim().split('.')
            if (parts.size != 7 || parts[0] != VERSION) return null
            if (!KEY_ID.matches(parts[1]) || !CODE.matches(parts[3]) || !CODE.matches(parts[4]) ||
                !SIGNATURE.matches(parts[6])
            ) {
                return null
            }
            val ticketId = runCatching { UUID.fromString(parts[2]) }.getOrNull() ?: return null
            if (ticketId.toString() != parts[2].lowercase()) return null
            val quantity = parts[5].toIntOrNull()?.takeIf { it in 1..100 } ?: return null
            return TicketPayload(parts[1], ticketId, parts[3], parts[4], quantity, parts[6])
        }
    }
}
