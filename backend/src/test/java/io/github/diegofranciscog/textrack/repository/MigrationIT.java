package io.github.diegofranciscog.textrack.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.diegofranciscog.textrack.support.PostgresContainer;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Verifica que las migraciones Flyway crean el esquema y que las reglas críticas viven en la base de datos. */
class MigrationIT {

    private static JdbcClient jdbc;

    @BeforeAll
    static void migrate() {
        PostgreSQLContainer postgres = PostgresContainer.instance();
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        Flyway.configure().dataSource(dataSource).schemas("migration_it").createSchemas(true).load().migrate();
        dataSource.setCurrentSchema("migration_it,public");
        jdbc = JdbcClient.create(dataSource);
    }

    @Test
    void seedsOfficialCatalogs() {
        assertThat(jdbc.sql("SELECT sbu FROM payroll_parameters WHERE year = 2026").query(String.class).single())
                .isEqualTo("482.00");
        assertThat(jdbc.sql("SELECT count(*) FROM sizes").query(Integer.class).single()).isEqualTo(7);
        assertThat(jdbc.sql("SELECT count(*) FROM defect_types WHERE default_severity = 'CRITICAL'")
                .query(Integer.class).single()).isPositive();
    }

    @Test
    void pieceRatesCannotOverlapForTheSameOperation() {
        long styleId = jdbc.sql("INSERT INTO styles (code, name, garment_type) VALUES ('ST-MIG', 'Polo', 'POLO') RETURNING id")
                .query(Long.class).single();
        long operationId = jdbc.sql("""
                INSERT INTO operations (style_id, sequence, code, name, machine_type, sam_minutes)
                VALUES (:style, 10, 'OP10', 'Unir hombros', 'OVERLOCK', 0.35) RETURNING id""")
                .param("style", styleId).query(Long.class).single();
        jdbc.sql("INSERT INTO piece_rates (operation_id, rate_usd, valid_from, valid_to) VALUES (:op, 0.014, '2026-01-01', '2026-07-01')")
                .param("op", operationId).update();
        jdbc.sql("INSERT INTO piece_rates (operation_id, rate_usd, valid_from) VALUES (:op, 0.015, '2026-07-01')")
                .param("op", operationId).update();

        assertThatThrownBy(() -> jdbc.sql(
                "INSERT INTO piece_rates (operation_id, rate_usd, valid_from) VALUES (:op, 0.020, '2026-08-01')")
                .param("op", operationId).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_piece_rates_no_overlap");
    }

    @Test
    void aTicketCanOnlyBeReadOnceAndAClientIdOnlyOnce() {
        long styleId = jdbc.sql("INSERT INTO styles (code, name, garment_type) VALUES ('ST-READ', 'Camiseta', 'TSHIRT') RETURNING id")
                .query(Long.class).single();
        long operationId = jdbc.sql("""
                INSERT INTO operations (style_id, sequence, code, name, machine_type, sam_minutes)
                VALUES (:style, 10, 'OP10', 'Unir hombros', 'OVERLOCK', 0.30) RETURNING id""")
                .param("style", styleId).query(Long.class).single();
        long orderId = jdbc.sql("INSERT INTO production_orders (code, style_id, customer, due_date) VALUES ('ORD-MIG', :style, 'Cliente', '2026-12-01') RETURNING id")
                .param("style", styleId).query(Long.class).single();
        long rollId = jdbc.sql("""
                INSERT INTO fabric_rolls (code, supplier, dye_lot, color, length_m, width_cm, remaining_m, status)
                VALUES ('R-MIG', 'Proveedor', 'L-1', 'Blanco', 100, 160, 100, 'APPROVED') RETURNING id""")
                .query(Long.class).single();
        long cutId = jdbc.sql("INSERT INTO cuts (code, production_order_id, color, max_bundle_size) VALUES ('CT-MIG', :order, 'Blanco', 20) RETURNING id")
                .param("order", orderId).query(Long.class).single();
        long bundleId = jdbc.sql("""
                INSERT INTO bundles (code, cut_id, roll_id, bundle_number, size_code, color, quantity)
                VALUES ('CT-MIG-001', :cut, :roll, 1, 'M', 'Blanco', 20) RETURNING id""")
                .param("cut", cutId).param("roll", rollId).query(Long.class).single();
        UUID ticketId = UUID.randomUUID();
        jdbc.sql("INSERT INTO tickets (id, bundle_id, operation_id, quantity, key_id, signature) VALUES (:id, :b, :op, 20, 'k1', 'x')")
                .param("id", ticketId).param("b", bundleId).param("op", operationId).update();
        long operatorId = jdbc.sql("INSERT INTO operators (code, full_name) VALUES ('OPR-MIG', 'Operaria Prueba') RETURNING id")
                .query(Long.class).single();

        String insert = """
                INSERT INTO scan_readings (client_reading_id, ticket_id, operator_id, scanned_at, work_date, source)
                VALUES (:client, :ticket, :operator, now(), current_date, 'APP')
                ON CONFLICT DO NOTHING""";
        UUID clientId = UUID.randomUUID();
        int first = jdbc.sql(insert).param("client", clientId).param("ticket", ticketId).param("operator", operatorId).update();
        int retry = jdbc.sql(insert).param("client", clientId).param("ticket", ticketId).param("operator", operatorId).update();
        int otherDevice = jdbc.sql(insert).param("client", UUID.randomUUID()).param("ticket", ticketId)
                .param("operator", operatorId).update();

        assertThat(first).isEqualTo(1);
        assertThat(retry).isZero();
        assertThat(otherDevice).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM scan_readings WHERE ticket_id = :t").param("t", ticketId)
                .query(Integer.class).single()).isEqualTo(1);
    }
}
