package io.github.diegofranciscog.textrack.scanner.domain

/** Estado local de una lectura. */
enum class SyncStatus {
    /** Guardada en el dispositivo, aún no confirmada por el servidor. */
    PENDING,

    /** Registrada en el servidor (nueva o reintento idempotente). */
    SYNCED,

    /** Otro dispositivo registró antes ese ticket. */
    CONFLICT,

    /** El servidor la rechazó (firma inválida, ticket u operario desconocido, hora fuera de rango). */
    REJECTED;

    companion object {
        /**
         * Traduce el estado devuelto por `POST /api/v1/readings/batch`. ACCEPTED y DUPLICATE significan lo mismo
         * para el dispositivo: la lectura ya está en el servidor (DUPLICATE es un reintento de algo ya guardado).
         */
        fun fromServer(status: String): SyncStatus = when (status) {
            "ACCEPTED", "DUPLICATE" -> SYNCED
            "ALREADY_SCANNED" -> CONFLICT
            else -> REJECTED
        }
    }
}
