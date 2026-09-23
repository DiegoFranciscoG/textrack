package io.github.diegofranciscog.textrack.domain;

/** Resultado de procesar una lectura. El mismo clientReadingId siempre produce el mismo resultado. */
public enum ScanStatus {
    /** Lectura nueva registrada. */
    ACCEPTED,
    /** Reintento de una lectura ya registrada (mismo clientReadingId): idempotente, no crea nada. */
    DUPLICATE,
    /** El ticket ya fue registrado por otra lectura. */
    ALREADY_SCANNED,
    /** El mismo clientReadingId se usó para otro ticket. */
    KEY_REUSED,
    INVALID_FORMAT,
    INVALID_SIGNATURE,
    UNKNOWN_TICKET,
    UNKNOWN_OPERATOR,
    INVALID_TIMESTAMP;

    public boolean isStoredOnServer() {
        return this == ACCEPTED || this == DUPLICATE;
    }
}
