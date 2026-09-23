-- Planta: líneas (work units), operarios, máquinas, asistencia y paros.

CREATE TABLE production_lines (
    id   BIGSERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(80) NOT NULL,
    CONSTRAINT uk_production_lines_code UNIQUE (code)
);

-- R13 (LOPDP): solo código, nombre y línea; sin cédula ni datos de contacto.
CREATE TABLE operators (
    id        BIGSERIAL PRIMARY KEY,
    code      VARCHAR(20)  NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    line_id   BIGINT       REFERENCES production_lines (id),
    active    BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_operators_code UNIQUE (code)
);

CREATE TABLE machines (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(20) NOT NULL,
    machine_type VARCHAR(30) NOT NULL,
    line_id      BIGINT      NOT NULL REFERENCES production_lines (id),
    active       BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_machines_code UNIQUE (code),
    CONSTRAINT ck_machines_type CHECK (machine_type IN
        ('LOCKSTITCH', 'OVERLOCK', 'COVERSTITCH', 'BARTACK', 'BUTTONHOLE', 'BUTTON', 'IRON', 'MANUAL', 'INSPECTION', 'PACKING'))
);

-- R3/R8/R9: minutos reloj de asistencia; base de eficiencia, recargos y piso SBU.
CREATE TABLE attendances (
    id            BIGSERIAL PRIMARY KEY,
    operator_id   BIGINT      NOT NULL REFERENCES operators (id),
    work_date     DATE        NOT NULL,
    check_in      TIMESTAMPTZ NOT NULL,
    check_out     TIMESTAMPTZ,
    break_minutes INTEGER     NOT NULL DEFAULT 30,
    CONSTRAINT uk_attendances_operator_date UNIQUE (operator_id, work_date),
    CONSTRAINT ck_attendances_range CHECK (check_out IS NULL OR check_out > check_in),
    CONSTRAINT ck_attendances_break CHECK (break_minutes BETWEEN 0 AND 120)
);

-- R4: paros planificados (PDOT) y no planificados (ADOT) de ISO 22400-2.
CREATE TABLE machine_stops (
    id          BIGSERIAL PRIMARY KEY,
    machine_id  BIGINT       NOT NULL REFERENCES machines (id),
    reason      VARCHAR(30)  NOT NULL,
    planned     BOOLEAN      NOT NULL,
    started_at  TIMESTAMPTZ  NOT NULL,
    ended_at    TIMESTAMPTZ,
    notes       VARCHAR(300),
    reported_by BIGINT       REFERENCES app_users (id),
    CONSTRAINT ck_machine_stops_range CHECK (ended_at IS NULL OR ended_at > started_at),
    CONSTRAINT ck_machine_stops_reason CHECK (reason IN
        ('MECHANICAL', 'ELECTRICAL', 'NO_MATERIAL', 'NO_OPERATOR', 'CHANGEOVER', 'PLANNED_MAINTENANCE', 'MEETING', 'OTHER'))
);

CREATE INDEX ix_operators_line ON operators (line_id);
CREATE INDEX ix_machines_line ON machines (line_id);
CREATE INDEX ix_attendances_date ON attendances (work_date);
CREATE INDEX ix_machine_stops_started ON machine_stops (started_at);
CREATE UNIQUE INDEX ux_machine_stops_one_open ON machine_stops (machine_id) WHERE ended_at IS NULL;
