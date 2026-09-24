package io.github.diegofranciscog.textrack.repository;

import io.github.diegofranciscog.textrack.domain.FabricRoll;
import io.github.diegofranciscog.textrack.domain.RollStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class FabricRollRepository {

    private static final String ROLL_SELECT = """
            SELECT r.id, r.code, r.supplier, r.dye_lot, r.color, r.length_m, r.width_cm, r.remaining_m, r.status,
                   r.received_at, i.total_points, i.points_per_100_sq_yd AS points_per100_sq_yd
              FROM fabric_rolls r LEFT JOIN fabric_inspections i ON i.roll_id = r.id
            """;

    private final JdbcClient jdbc;

    public FabricRollRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<FabricRoll> findAll() {
        return jdbc.sql(ROLL_SELECT + " ORDER BY r.received_at DESC, r.id DESC").query(FabricRoll.class).list();
    }

    public Optional<FabricRoll> findById(long id) {
        return jdbc.sql(ROLL_SELECT + " WHERE r.id = :id").param("id", id).query(FabricRoll.class).optional();
    }

    public Optional<FabricRoll> findByCode(String code) {
        return jdbc.sql(ROLL_SELECT + " WHERE r.code = :code").param("code", code).query(FabricRoll.class).optional();
    }

    /** Bloquea el rollo durante la transacción para descontar metros sin condiciones de carrera. */
    public Optional<FabricRoll> findByIdForUpdate(long id) {
        return jdbc.sql("""
                        SELECT r.id, r.code, r.supplier, r.dye_lot, r.color, r.length_m, r.width_cm, r.remaining_m,
                               r.status, r.received_at, NULL::integer AS total_points,
                               NULL::numeric AS points_per100_sq_yd
                          FROM fabric_rolls r WHERE r.id = :id FOR UPDATE""")
                .param("id", id).query(FabricRoll.class).optional();
    }

    public boolean codeExists(String code) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM fabric_rolls WHERE code = :code)").param("code", code)
                .query(Boolean.class).single();
    }

    public long insert(String code, String supplier, String dyeLot, String color, BigDecimal lengthM,
                       BigDecimal widthCm) {
        return jdbc.sql("""
                        INSERT INTO fabric_rolls (code, supplier, dye_lot, color, length_m, width_cm, remaining_m)
                        VALUES (:code, :supplier, :lot, :color, :length, :width, :length) RETURNING id""")
                .param("code", code).param("supplier", supplier).param("lot", dyeLot).param("color", color)
                .param("length", lengthM).param("width", widthCm)
                .query(Long.class).single();
    }

    public void updateStatus(long rollId, RollStatus status) {
        jdbc.sql("UPDATE fabric_rolls SET status = :status WHERE id = :id")
                .param("status", status.name()).param("id", rollId).update();
    }

    public void consume(long rollId, BigDecimal meters) {
        jdbc.sql("""
                        UPDATE fabric_rolls
                           SET remaining_m = remaining_m - :meters,
                               status = CASE WHEN remaining_m - :meters = 0 THEN 'EXHAUSTED' ELSE status END
                         WHERE id = :id""")
                .param("meters", meters).param("id", rollId).update();
    }

    public boolean hasInspection(long rollId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM fabric_inspections WHERE roll_id = :id)").param("id", rollId)
                .query(Boolean.class).single();
    }

    public long insertInspection(long rollId, BigDecimal inspectedLengthM, BigDecimal widthCm, int totalPoints,
                                 BigDecimal pointsPer100SqYd, BigDecimal maxAllowed, boolean accepted, Long inspectedBy) {
        return jdbc.sql("""
                        INSERT INTO fabric_inspections (roll_id, inspected_length_m, width_cm, total_points,
                                                        points_per_100_sq_yd, max_points_allowed, accepted, inspected_by)
                        VALUES (:roll, :length, :width, :points, :per100, :max, :accepted, :by) RETURNING id""")
                .param("roll", rollId).param("length", inspectedLengthM).param("width", widthCm)
                .param("points", totalPoints).param("per100", pointsPer100SqYd).param("max", maxAllowed)
                .param("accepted", accepted).param("by", inspectedBy)
                .query(Long.class).single();
    }

    public void insertDefect(long inspectionId, BigDecimal positionM, int lengthMm, boolean hole, String defectTypeCode,
                             int points) {
        jdbc.sql("""
                        INSERT INTO fabric_defects (fabric_inspection_id, position_m, length_mm, is_hole,
                                                    defect_type_code, points)
                        VALUES (:insp, :pos, :length, :hole, :type, :points)""")
                .param("insp", inspectionId).param("pos", positionM).param("length", lengthMm).param("hole", hole)
                .param("type", defectTypeCode).param("points", points).update();
    }

    public Optional<InspectionSummary> findInspection(long rollId) {
        return jdbc.sql("""
                        SELECT i.id, i.inspected_length_m, i.width_cm, i.total_points,
                               i.points_per_100_sq_yd AS points_per100_sq_yd, i.max_points_allowed, i.accepted,
                               i.inspected_at,
                               (SELECT COUNT(*) FROM fabric_defects d WHERE d.fabric_inspection_id = i.id) AS defects
                          FROM fabric_inspections i WHERE i.roll_id = :roll""")
                .param("roll", rollId).query(InspectionSummary.class).optional();
    }

    public record InspectionSummary(long id, BigDecimal inspectedLengthM, BigDecimal widthCm, int totalPoints,
                                    BigDecimal pointsPer100SqYd, BigDecimal maxPointsAllowed, boolean accepted,
                                    java.time.OffsetDateTime inspectedAt, int defects) {
    }
}
