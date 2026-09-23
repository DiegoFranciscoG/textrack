-- Ingeniería de métodos: estilos, operaciones con SAM y tarifas a destajo.

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE styles (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(30)  NOT NULL,
    name         VARCHAR(120) NOT NULL,
    garment_type VARCHAR(40)  NOT NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_styles_code UNIQUE (code)
);

-- R2: SAM en minutos estándar (OIT, Introduction to Work Study).
CREATE TABLE operations (
    id           BIGSERIAL PRIMARY KEY,
    style_id     BIGINT        NOT NULL REFERENCES styles (id),
    sequence     INTEGER       NOT NULL,
    code         VARCHAR(20)   NOT NULL,
    name         VARCHAR(120)  NOT NULL,
    machine_type VARCHAR(30)   NOT NULL,
    sam_minutes  NUMERIC(8, 4) NOT NULL,
    CONSTRAINT uk_operations_style_sequence UNIQUE (style_id, sequence),
    CONSTRAINT uk_operations_style_code UNIQUE (style_id, code),
    CONSTRAINT ck_operations_sequence CHECK (sequence > 0),
    CONSTRAINT ck_operations_sam CHECK (sam_minutes > 0 AND sam_minutes <= 60),
    CONSTRAINT ck_operations_machine_type CHECK (machine_type IN
        ('LOCKSTITCH', 'OVERLOCK', 'COVERSTITCH', 'BARTACK', 'BUTTONHOLE', 'BUTTON', 'IRON', 'MANUAL', 'INSPECTION', 'PACKING'))
);

-- R7: tarifa por pieza (Código del Trabajo, Art. 16). Una sola tarifa vigente por día y operación.
CREATE TABLE piece_rates (
    id           BIGSERIAL PRIMARY KEY,
    operation_id BIGINT         NOT NULL REFERENCES operations (id),
    rate_usd     NUMERIC(10, 4) NOT NULL,
    valid_from   DATE           NOT NULL,
    valid_to     DATE,
    CONSTRAINT ck_piece_rates_rate CHECK (rate_usd >= 0),
    CONSTRAINT ck_piece_rates_range CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT ex_piece_rates_no_overlap EXCLUDE USING gist (
        operation_id WITH =,
        daterange(valid_from, valid_to, '[)') WITH &&
    )
);

CREATE INDEX ix_operations_style ON operations (style_id);
