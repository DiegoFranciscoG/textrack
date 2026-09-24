package io.github.diegofranciscog.textrack.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Consultas de agregación para el tablero, cuellos de botella y trazabilidad. */
@Repository
public class ReportRepository {

    private final JdbcClient jdbc;

    public ReportRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- tablero

    /** Minutos estándar ganados y piezas por operario en la fecha de jornada. */
    public List<OperatorOutput> outputByOperator(LocalDate workDate) {
        return jdbc.sql("""
                        SELECT r.operator_id, SUM(t.quantity * o.sam_minutes) AS earned_minutes,
                               SUM(t.quantity) AS pieces, COUNT(*) AS tickets
                          FROM scan_readings r
                          JOIN tickets t ON t.id = r.ticket_id
                          JOIN operations o ON o.id = t.operation_id
                         WHERE r.work_date = :date
                         GROUP BY r.operator_id""")
                .param("date", workDate).query(OperatorOutput.class).list();
    }

    /** Unidades muestreadas y defectuosas por línea en el intervalo (estimación de Q, SUPUESTO S7). */
    public List<LineQuality> qualityByLine(OffsetDateTime from, OffsetDateTime to) {
        return jdbc.sql("""
                        SELECT line_id, SUM(sample_size) AS sampled, SUM(defective_units) AS defective
                          FROM aql_inspections
                         WHERE inspected_at >= :from AND inspected_at < :to AND line_id IS NOT NULL
                         GROUP BY line_id""")
                .param("from", from).param("to", to).query(LineQuality.class).list();
    }

    // ---------------------------------------------------------------- cuellos de botella

    public int cutPieces(long orderId) {
        return jdbc.sql("""
                        SELECT COALESCE(SUM(b.quantity), 0) FROM bundles b JOIN cuts c ON c.id = b.cut_id
                         WHERE c.production_order_id = :order""")
                .param("order", orderId).query(Integer.class).single();
    }

    /**
     * Avance por operación de la ruta del estilo de la orden. La dotación actual se estima con los operarios
     * distintos que registraron esa operación del estilo en los últimos 3 días.
     */
    public List<OperationProgressRow> operationProgress(long orderId, LocalDate since) {
        return jdbc.sql("""
                        SELECT o.id, o.sequence, o.code, o.name, o.sam_minutes,
                               COALESCE((SELECT SUM(t.quantity)
                                           FROM tickets t
                                           JOIN scan_readings r ON r.ticket_id = t.id
                                           JOIN bundles b ON b.id = t.bundle_id
                                           JOIN cuts c ON c.id = b.cut_id
                                          WHERE t.operation_id = o.id AND c.production_order_id = po.id), 0)
                                   AS completed_pieces,
                               (SELECT COUNT(DISTINCT r.operator_id)
                                  FROM scan_readings r JOIN tickets t ON t.id = r.ticket_id
                                 WHERE t.operation_id = o.id AND r.work_date >= :since) AS operators
                          FROM production_orders po
                          JOIN operations o ON o.style_id = po.style_id
                         WHERE po.id = :order
                         ORDER BY o.sequence""")
                .param("order", orderId).param("since", since).query(OperationProgressRow.class).list();
    }

    // ---------------------------------------------------------------- trazabilidad

    public Optional<BundleTrace> bundleTrace(String bundleCode) {
        return jdbc.sql("""
                        SELECT b.id AS bundle_id, b.code AS bundle_code, b.size_code, b.color, b.quantity,
                               c.code AS cut_code, c.created_at AS cut_at, po.id AS order_id, po.code AS order_code,
                               po.customer, s.code AS style_code, s.name AS style_name,
                               r.id AS roll_id, r.code AS roll_code, r.dye_lot, r.supplier,
                               i.points_per_100_sq_yd AS points_per100_sq_yd, i.accepted AS roll_accepted
                          FROM bundles b
                          JOIN cuts c ON c.id = b.cut_id
                          JOIN production_orders po ON po.id = c.production_order_id
                          JOIN styles s ON s.id = po.style_id
                          JOIN fabric_rolls r ON r.id = b.roll_id
                          LEFT JOIN fabric_inspections i ON i.roll_id = r.id
                         WHERE b.code = :code""")
                .param("code", bundleCode).query(BundleTrace.class).optional();
    }

    public List<OperationTrace> operationTrace(long bundleId) {
        return jdbc.sql("""
                        SELECT t.id AS ticket_id, o.sequence, o.code AS operation_code, o.name AS operation_name,
                               r.scanned_at, op.code AS operator_code, op.full_name AS operator_name
                          FROM tickets t
                          JOIN operations o ON o.id = t.operation_id
                          LEFT JOIN scan_readings r ON r.ticket_id = t.id
                          LEFT JOIN operators op ON op.id = r.operator_id
                         WHERE t.bundle_id = :bundle
                         ORDER BY o.sequence""")
                .param("bundle", bundleId).query(OperationTrace.class).list();
    }

    public List<DefectTrace> defectTrace(long bundleId) {
        return jdbc.sql("""
                        SELECT d.defect_type_code, dt.name AS defect_name, d.severity, d.quantity,
                               o.code AS operation_code, i.inspected_at
                          FROM aql_defects d
                          JOIN defect_types dt ON dt.code = d.defect_type_code
                          JOIN aql_inspections i ON i.id = d.inspection_id
                          LEFT JOIN operations o ON o.id = d.operation_id
                         WHERE d.bundle_id = :bundle
                         ORDER BY i.inspected_at""")
                .param("bundle", bundleId).query(DefectTrace.class).list();
    }

    public record OperatorOutput(long operatorId, BigDecimal earnedMinutes, int pieces, int tickets) {
    }

    public record LineQuality(long lineId, int sampled, int defective) {
    }

    public record OperationProgressRow(long id, int sequence, String code, String name, BigDecimal samMinutes,
                                       int completedPieces, int operators) {
    }

    public record BundleTrace(long bundleId, String bundleCode, String sizeCode, String color, int quantity,
                              String cutCode, OffsetDateTime cutAt, long orderId, String orderCode, String customer,
                              String styleCode, String styleName, long rollId, String rollCode, String dyeLot,
                              String supplier, BigDecimal pointsPer100SqYd, Boolean rollAccepted) {
    }

    public record OperationTrace(UUID ticketId, int sequence, String operationCode, String operationName,
                                 OffsetDateTime scannedAt, String operatorCode, String operatorName) {
    }

    public record DefectTrace(String defectTypeCode, String defectName, String severity, int quantity,
                              String operationCode, OffsetDateTime inspectedAt) {
    }
}
