-- Producción: órdenes, rollos (4 puntos), cortes, bultos y tickets firmados.

CREATE SEQUENCE production_order_code_seq;
CREATE SEQUENCE cut_code_seq;

CREATE TABLE production_orders (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(30)  NOT NULL,
    style_id   BIGINT       NOT NULL REFERENCES styles (id),
    customer   VARCHAR(120) NOT NULL,
    due_date   DATE         NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'PLANNED',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by BIGINT       REFERENCES app_users (id),
    CONSTRAINT uk_production_orders_code UNIQUE (code),
    CONSTRAINT ck_production_orders_status CHECK (status IN ('PLANNED', 'CUTTING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'))
);

CREATE TABLE production_order_lines (
    id                  BIGSERIAL PRIMARY KEY,
    production_order_id BIGINT      NOT NULL REFERENCES production_orders (id) ON DELETE CASCADE,
    size_code           VARCHAR(5)  NOT NULL REFERENCES sizes (code),
    color               VARCHAR(40) NOT NULL,
    quantity            INTEGER     NOT NULL,
    CONSTRAINT uk_production_order_lines UNIQUE (production_order_id, size_code, color),
    CONSTRAINT ck_production_order_lines_qty CHECK (quantity > 0 AND quantity <= 100000)
);

-- R1: la tela llega teñida; el lote de teñido se conserva para trazabilidad y control de tono.
CREATE TABLE fabric_rolls (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(30)    NOT NULL,
    supplier    VARCHAR(120)   NOT NULL,
    dye_lot     VARCHAR(40)    NOT NULL,
    color       VARCHAR(40)    NOT NULL,
    length_m    NUMERIC(10, 2) NOT NULL,
    width_cm    NUMERIC(6, 2)  NOT NULL,
    remaining_m NUMERIC(10, 2) NOT NULL,
    status      VARCHAR(20)    NOT NULL DEFAULT 'RECEIVED',
    received_at TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uk_fabric_rolls_code UNIQUE (code),
    CONSTRAINT ck_fabric_rolls_length CHECK (length_m > 0),
    CONSTRAINT ck_fabric_rolls_width CHECK (width_cm > 0),
    CONSTRAINT ck_fabric_rolls_remaining CHECK (remaining_m >= 0 AND remaining_m <= length_m),
    CONSTRAINT ck_fabric_rolls_status CHECK (status IN ('RECEIVED', 'APPROVED', 'REJECTED', 'EXHAUSTED'))
);

-- R6: sistema de 4 puntos (ASTM D5430).
CREATE TABLE fabric_inspections (
    id                   BIGSERIAL PRIMARY KEY,
    roll_id              BIGINT         NOT NULL REFERENCES fabric_rolls (id),
    inspected_length_m   NUMERIC(10, 2) NOT NULL,
    width_cm             NUMERIC(6, 2)  NOT NULL,
    total_points         INTEGER        NOT NULL,
    points_per_100_sq_yd NUMERIC(8, 2)  NOT NULL,
    max_points_allowed   NUMERIC(8, 2)  NOT NULL,
    accepted             BOOLEAN        NOT NULL,
    inspected_by         BIGINT         REFERENCES app_users (id),
    inspected_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uk_fabric_inspections_roll UNIQUE (roll_id),
    CONSTRAINT ck_fabric_inspections_length CHECK (inspected_length_m > 0),
    CONSTRAINT ck_fabric_inspections_points CHECK (total_points >= 0)
);

CREATE TABLE fabric_defects (
    id                    BIGSERIAL PRIMARY KEY,
    fabric_inspection_id  BIGINT         NOT NULL REFERENCES fabric_inspections (id) ON DELETE CASCADE,
    position_m            NUMERIC(10, 2) NOT NULL,
    length_mm             INTEGER        NOT NULL,
    is_hole               BOOLEAN        NOT NULL DEFAULT FALSE,
    defect_type_code      VARCHAR(20)    REFERENCES defect_types (code),
    points                SMALLINT       NOT NULL,
    CONSTRAINT ck_fabric_defects_position CHECK (position_m >= 0),
    CONSTRAINT ck_fabric_defects_length CHECK (length_mm > 0),
    CONSTRAINT ck_fabric_defects_points CHECK (points BETWEEN 1 AND 4)
);

CREATE TABLE cuts (
    id                  BIGSERIAL PRIMARY KEY,
    code                VARCHAR(30) NOT NULL,
    production_order_id BIGINT      NOT NULL REFERENCES production_orders (id),
    color               VARCHAR(40) NOT NULL,
    max_bundle_size     INTEGER     NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          BIGINT      REFERENCES app_users (id),
    CONSTRAINT uk_cuts_code UNIQUE (code),
    CONSTRAINT ck_cuts_bundle_size CHECK (max_bundle_size BETWEEN 1 AND 100)
);

-- Trazo: piezas de cada talla por capa de tela.
CREATE TABLE cut_size_ratios (
    cut_id         BIGINT     NOT NULL REFERENCES cuts (id) ON DELETE CASCADE,
    size_code      VARCHAR(5) NOT NULL REFERENCES sizes (code),
    pieces_per_ply INTEGER    NOT NULL,
    PRIMARY KEY (cut_id, size_code),
    CONSTRAINT ck_cut_size_ratios_pieces CHECK (pieces_per_ply > 0)
);

-- Tendido: rollos usados y capas por rollo.
CREATE TABLE cut_rolls (
    cut_id      BIGINT         NOT NULL REFERENCES cuts (id) ON DELETE CASCADE,
    roll_id     BIGINT         NOT NULL REFERENCES fabric_rolls (id),
    plies       INTEGER        NOT NULL,
    meters_used NUMERIC(10, 2) NOT NULL,
    PRIMARY KEY (cut_id, roll_id),
    CONSTRAINT ck_cut_rolls_plies CHECK (plies > 0),
    CONSTRAINT ck_cut_rolls_meters CHECK (meters_used > 0)
);

-- S10: cada bulto sale de un solo rollo (mismo lote de teñido).
CREATE TABLE bundles (
    id            BIGSERIAL PRIMARY KEY,
    code          VARCHAR(40) NOT NULL,
    cut_id        BIGINT      NOT NULL REFERENCES cuts (id),
    roll_id       BIGINT      NOT NULL REFERENCES fabric_rolls (id),
    bundle_number INTEGER     NOT NULL,
    size_code     VARCHAR(5)  NOT NULL REFERENCES sizes (code),
    color         VARCHAR(40) NOT NULL,
    quantity      INTEGER     NOT NULL,
    CONSTRAINT uk_bundles_code UNIQUE (code),
    CONSTRAINT uk_bundles_cut_number UNIQUE (cut_id, bundle_number),
    CONSTRAINT ck_bundles_quantity CHECK (quantity BETWEEN 1 AND 100)
);

-- R12: un ticket por bulto y operación; el QR lleva una firma HMAC-SHA-256.
CREATE TABLE tickets (
    id           UUID        PRIMARY KEY,
    bundle_id    BIGINT      NOT NULL REFERENCES bundles (id),
    operation_id BIGINT      NOT NULL REFERENCES operations (id),
    quantity     INTEGER     NOT NULL,
    key_id       VARCHAR(10) NOT NULL,
    signature    VARCHAR(64) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_tickets_bundle_operation UNIQUE (bundle_id, operation_id),
    CONSTRAINT ck_tickets_quantity CHECK (quantity > 0)
);

CREATE INDEX ix_production_orders_style ON production_orders (style_id);
CREATE INDEX ix_cuts_order ON cuts (production_order_id);
CREATE INDEX ix_bundles_cut ON bundles (cut_id);
CREATE INDEX ix_bundles_roll ON bundles (roll_id);
CREATE INDEX ix_tickets_operation ON tickets (operation_id);
