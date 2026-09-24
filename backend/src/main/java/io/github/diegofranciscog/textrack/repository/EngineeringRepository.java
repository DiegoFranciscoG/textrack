package io.github.diegofranciscog.textrack.repository;

import io.github.diegofranciscog.textrack.domain.MachineType;
import io.github.diegofranciscog.textrack.domain.Operation;
import io.github.diegofranciscog.textrack.domain.Style;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Estilos, operaciones (SAM) y tarifas a destajo. */
@Repository
public class EngineeringRepository {

    private static final String OPERATION_SELECT = """
            SELECT o.id, o.style_id, o.sequence, o.code, o.name, o.machine_type, o.sam_minutes,
                   (SELECT r.rate_usd FROM piece_rates r
                     WHERE r.operation_id = o.id AND r.valid_from <= :today
                       AND (r.valid_to IS NULL OR r.valid_to > :today)) AS current_rate_usd
              FROM operations o
            """;

    private final JdbcClient jdbc;

    public EngineeringRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Style> findStyles() {
        return jdbc.sql("SELECT id, code, name, garment_type, active FROM styles ORDER BY code").query(Style.class).list();
    }

    public Optional<Style> findStyle(long id) {
        return jdbc.sql("SELECT id, code, name, garment_type, active FROM styles WHERE id = :id")
                .param("id", id).query(Style.class).optional();
    }

    public boolean styleCodeExists(String code) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM styles WHERE code = :code)").param("code", code)
                .query(Boolean.class).single();
    }

    public long insertStyle(String code, String name, String garmentType) {
        return jdbc.sql("INSERT INTO styles (code, name, garment_type) VALUES (:code, :name, :type) RETURNING id")
                .param("code", code).param("name", name).param("type", garmentType)
                .query(Long.class).single();
    }

    public List<Operation> findOperations(long styleId, LocalDate today) {
        return jdbc.sql(OPERATION_SELECT + " WHERE o.style_id = :style ORDER BY o.sequence")
                .param("style", styleId).param("today", today)
                .query(Operation.class).list();
    }

    public Optional<Operation> findOperation(long operationId, LocalDate today) {
        return jdbc.sql(OPERATION_SELECT + " WHERE o.id = :id")
                .param("id", operationId).param("today", today)
                .query(Operation.class).optional();
    }

    public boolean operationExists(long styleId, int sequence, String code) {
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM operations
                                        WHERE style_id = :style AND (sequence = :seq OR code = :code))""")
                .param("style", styleId).param("seq", sequence).param("code", code)
                .query(Boolean.class).single();
    }

    public long insertOperation(long styleId, int sequence, String code, String name, MachineType machineType,
                                BigDecimal samMinutes) {
        return jdbc.sql("""
                        INSERT INTO operations (style_id, sequence, code, name, machine_type, sam_minutes)
                        VALUES (:style, :seq, :code, :name, :machine, :sam) RETURNING id""")
                .param("style", styleId).param("seq", sequence).param("code", code).param("name", name)
                .param("machine", machineType.name()).param("sam", samMinutes)
                .query(Long.class).single();
    }

    /** Cierra la tarifa abierta (sin fecha fin) de la operación en la fecha indicada. */
    public int closeOpenRate(long operationId, LocalDate validTo) {
        return jdbc.sql("""
                        UPDATE piece_rates SET valid_to = :validTo
                         WHERE operation_id = :op AND valid_to IS NULL AND valid_from < :validTo""")
                .param("op", operationId).param("validTo", validTo)
                .update();
    }

    public void insertRate(long operationId, BigDecimal rateUsd, LocalDate validFrom) {
        jdbc.sql("INSERT INTO piece_rates (operation_id, rate_usd, valid_from) VALUES (:op, :rate, :from)")
                .param("op", operationId).param("rate", rateUsd).param("from", validFrom)
                .update();
    }

    public boolean hasRateStartingOnOrAfter(long operationId, LocalDate date) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM piece_rates WHERE operation_id = :op AND valid_from >= :date)")
                .param("op", operationId).param("date", date)
                .query(Boolean.class).single();
    }
}
