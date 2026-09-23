-- Catálogos y parámetros legales (ver docs/investigacion.md).

-- R9: el SBU se fija cada año por acuerdo ministerial (Código del Trabajo, Art. 117).
CREATE TABLE payroll_parameters (
    year            INTEGER       PRIMARY KEY,
    sbu             NUMERIC(10, 2) NOT NULL,
    legal_reference VARCHAR(200)  NOT NULL,
    CONSTRAINT ck_payroll_parameters_year CHECK (year BETWEEN 2000 AND 2100),
    CONSTRAINT ck_payroll_parameters_sbu CHECK (sbu > 0)
);

CREATE TABLE sizes (
    code       VARCHAR(5) PRIMARY KEY,
    sort_order SMALLINT   NOT NULL,
    CONSTRAINT uk_sizes_sort UNIQUE (sort_order)
);

CREATE TABLE defect_types (
    code             VARCHAR(20)  PRIMARY KEY,
    name             VARCHAR(100) NOT NULL,
    category         VARCHAR(20)  NOT NULL,
    default_severity VARCHAR(10)  NOT NULL,
    CONSTRAINT ck_defect_types_category CHECK (category IN ('SEWING', 'FABRIC', 'MEASUREMENT', 'FINISHING', 'LABELING', 'STAIN')),
    CONSTRAINT ck_defect_types_severity CHECK (default_severity IN ('CRITICAL', 'MAJOR', 'MINOR'))
);

INSERT INTO payroll_parameters (year, sbu, legal_reference) VALUES
    (2026, 482.00, 'Acuerdo Ministerial MDT-2025-195, R.O. S. 187 de 18-dic-2025 (SBU 2026)');

INSERT INTO sizes (code, sort_order) VALUES
    ('XS', 1), ('S', 2), ('M', 3), ('L', 4), ('XL', 5), ('XXL', 6), ('3XL', 7);

INSERT INTO defect_types (code, name, category, default_severity) VALUES
    ('BROKEN_NEEDLE', 'Aguja rota o fragmento metálico en la prenda', 'SEWING', 'CRITICAL'),
    ('OPEN_SEAM', 'Costura abierta', 'SEWING', 'MAJOR'),
    ('SKIPPED_STITCH', 'Puntada saltada', 'SEWING', 'MAJOR'),
    ('UNEVEN_SEAM', 'Costura desnivelada o torcida', 'SEWING', 'MINOR'),
    ('PUCKERING', 'Fruncido en costura', 'SEWING', 'MINOR'),
    ('RAW_EDGE', 'Orilla sin rematar', 'SEWING', 'MAJOR'),
    ('SHADE_VARIATION', 'Diferencia de tono entre piezas', 'FABRIC', 'MAJOR'),
    ('FABRIC_HOLE', 'Agujero en la tela', 'FABRIC', 'MAJOR'),
    ('SLUB', 'Mota o engrosamiento de hilo', 'FABRIC', 'MINOR'),
    ('OUT_OF_TOLERANCE', 'Medida fuera de tolerancia', 'MEASUREMENT', 'MAJOR'),
    ('LOOSE_THREAD', 'Hilo suelto sin cortar', 'FINISHING', 'MINOR'),
    ('POOR_PRESSING', 'Planchado deficiente', 'FINISHING', 'MINOR'),
    ('WRONG_LABEL', 'Etiqueta errónea o faltante', 'LABELING', 'MAJOR'),
    ('OIL_STAIN', 'Mancha de aceite', 'STAIN', 'MAJOR'),
    ('DIRT_MARK', 'Marca de suciedad leve', 'STAIN', 'MINOR');
