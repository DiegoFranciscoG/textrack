-- Calidad: inspección por atributos indexada por AQL (ISO 2859-1 / MIL-STD-105E, R5).

CREATE TABLE aql_inspections (
    id                  BIGSERIAL PRIMARY KEY,
    production_order_id BIGINT        NOT NULL REFERENCES production_orders (id),
    line_id             BIGINT        REFERENCES production_lines (id),
    inspection_level    VARCHAR(4)    NOT NULL,
    lot_size            INTEGER       NOT NULL,
    aql_major           NUMERIC(5, 3) NOT NULL,
    aql_minor           NUMERIC(5, 3) NOT NULL,
    code_letter         CHAR(1)       NOT NULL,
    sample_size         INTEGER       NOT NULL,
    major_accept        INTEGER       NOT NULL,
    major_reject        INTEGER       NOT NULL,
    minor_accept        INTEGER       NOT NULL,
    minor_reject        INTEGER       NOT NULL,
    critical_found      INTEGER       NOT NULL DEFAULT 0,
    major_found         INTEGER       NOT NULL DEFAULT 0,
    minor_found         INTEGER       NOT NULL DEFAULT 0,
    defective_units     INTEGER       NOT NULL DEFAULT 0,
    result              VARCHAR(10)   NOT NULL,
    standard_edition    VARCHAR(80)   NOT NULL,
    inspected_by        BIGINT        REFERENCES app_users (id),
    inspected_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_aql_inspections_level CHECK (inspection_level IN ('S1', 'S2', 'S3', 'S4', 'I', 'II', 'III')),
    CONSTRAINT ck_aql_inspections_lot CHECK (lot_size >= 2),
    CONSTRAINT ck_aql_inspections_sample CHECK (sample_size > 0 AND sample_size <= lot_size),
    CONSTRAINT ck_aql_inspections_found CHECK (critical_found >= 0 AND major_found >= 0 AND minor_found >= 0),
    CONSTRAINT ck_aql_inspections_defective CHECK (defective_units >= 0 AND defective_units <= sample_size),
    CONSTRAINT ck_aql_inspections_result CHECK (result IN ('ACCEPTED', 'REJECTED'))
);

CREATE TABLE aql_defects (
    id               BIGSERIAL PRIMARY KEY,
    inspection_id    BIGINT      NOT NULL REFERENCES aql_inspections (id) ON DELETE CASCADE,
    defect_type_code VARCHAR(20) NOT NULL REFERENCES defect_types (code),
    severity         VARCHAR(10) NOT NULL,
    quantity         INTEGER     NOT NULL,
    bundle_id        BIGINT      REFERENCES bundles (id),
    operation_id     BIGINT      REFERENCES operations (id),
    CONSTRAINT ck_aql_defects_severity CHECK (severity IN ('CRITICAL', 'MAJOR', 'MINOR')),
    CONSTRAINT ck_aql_defects_quantity CHECK (quantity > 0)
);

CREATE INDEX ix_aql_inspections_order ON aql_inspections (production_order_id);
CREATE INDEX ix_aql_inspections_date ON aql_inspections (inspected_at);
CREATE INDEX ix_aql_defects_inspection ON aql_defects (inspection_id);
