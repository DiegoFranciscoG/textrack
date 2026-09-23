-- Lecturas de tickets. La idempotencia se garantiza en la base de datos (R11):
--   * ticket_id único          -> un ticket no se registra dos veces;
--   * client_reading_id único  -> reintentar la sincronización offline no duplica lecturas.

CREATE TABLE scan_readings (
    id                BIGSERIAL PRIMARY KEY,
    client_reading_id UUID        NOT NULL,
    ticket_id         UUID        NOT NULL REFERENCES tickets (id),
    operator_id       BIGINT      NOT NULL REFERENCES operators (id),
    scanned_at        TIMESTAMPTZ NOT NULL,
    received_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    work_date         DATE        NOT NULL,
    device_id         VARCHAR(64),
    source            VARCHAR(10) NOT NULL,
    CONSTRAINT uk_scan_readings_client_id UNIQUE (client_reading_id),
    CONSTRAINT uk_scan_readings_ticket UNIQUE (ticket_id),
    CONSTRAINT ck_scan_readings_source CHECK (source IN ('APP', 'WEB', 'DEMO'))
);

-- Auditoría de lecturas rechazadas (firma inválida, ticket desconocido...). Alimenta la alerta anti-falsificación.
CREATE TABLE rejected_scans (
    id                BIGSERIAL PRIMARY KEY,
    client_reading_id UUID         NOT NULL,
    raw_payload       VARCHAR(300) NOT NULL,
    reason            VARCHAR(40)  NOT NULL,
    operator_code     VARCHAR(20),
    device_id         VARCHAR(64),
    received_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_rejected_scans_client_id UNIQUE (client_reading_id)
);

CREATE INDEX ix_scan_readings_operator_date ON scan_readings (operator_id, work_date);
CREATE INDEX ix_scan_readings_date ON scan_readings (work_date);
CREATE INDEX ix_rejected_scans_received ON rejected_scans (received_at);
