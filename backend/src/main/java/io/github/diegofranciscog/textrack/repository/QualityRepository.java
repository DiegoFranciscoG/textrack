package io.github.diegofranciscog.textrack.repository;

import io.github.diegofranciscog.textrack.domain.Severity;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Inspecciones AQL y sus defectos. */
@Repository
public class QualityRepository {

    private final JdbcClient jdbc;

    public QualityRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long insertInspection(NewInspection inspection) {
        return jdbc.sql("""
                        INSERT INTO aql_inspections (production_order_id, line_id, inspection_level, lot_size, aql_major,
                            aql_minor, code_letter, sample_size, major_accept, major_reject, minor_accept, minor_reject,
                            critical_found, major_found, minor_found, defective_units, result, standard_edition,
                            inspected_by, inspected_at)
                        VALUES (:order, :line, :level, :lot, :aqlMajor, :aqlMinor, :letter, :sample, :majorAc, :majorRe,
                            :minorAc, :minorRe, :critical, :major, :minor, :defective, :result, :edition, :by, :at)
                        RETURNING id""")
                .param("order", inspection.productionOrderId()).param("line", inspection.lineId())
                .param("level", inspection.inspectionLevel()).param("lot", inspection.lotSize())
                .param("aqlMajor", inspection.aqlMajor()).param("aqlMinor", inspection.aqlMinor())
                .param("letter", inspection.codeLetter()).param("sample", inspection.sampleSize())
                .param("majorAc", inspection.majorAccept()).param("majorRe", inspection.majorReject())
                .param("minorAc", inspection.minorAccept()).param("minorRe", inspection.minorReject())
                .param("critical", inspection.criticalFound()).param("major", inspection.majorFound())
                .param("minor", inspection.minorFound()).param("defective", inspection.defectiveUnits())
                .param("result", inspection.result()).param("edition", inspection.standardEdition())
                .param("by", inspection.inspectedBy()).param("at", inspection.inspectedAt())
                .query(Long.class).single();
    }

    public void insertDefect(long inspectionId, String defectTypeCode, Severity severity, int quantity, Long bundleId,
                             Long operationId) {
        jdbc.sql("""
                        INSERT INTO aql_defects (inspection_id, defect_type_code, severity, quantity, bundle_id,
                                                 operation_id)
                        VALUES (:insp, :type, :severity, :qty, :bundle, :op)""")
                .param("insp", inspectionId).param("type", defectTypeCode).param("severity", severity.name())
                .param("qty", quantity).param("bundle", bundleId).param("op", operationId).update();
    }

    public List<InspectionRow> findRecent(int limit) {
        return jdbc.sql("""
                        SELECT i.id, po.code AS order_code, l.code AS line_code, i.inspection_level, i.lot_size,
                               i.aql_major, i.aql_minor, i.code_letter, i.sample_size, i.major_accept, i.major_reject,
                               i.minor_accept, i.minor_reject, i.critical_found, i.major_found, i.minor_found,
                               i.defective_units, i.result, i.standard_edition, i.inspected_at
                          FROM aql_inspections i
                          JOIN production_orders po ON po.id = i.production_order_id
                          LEFT JOIN production_lines l ON l.id = i.line_id
                         ORDER BY i.inspected_at DESC, i.id DESC LIMIT :limit""")
                .param("limit", limit).query(InspectionRow.class).list();
    }

    public record NewInspection(long productionOrderId, Long lineId, String inspectionLevel, int lotSize,
                                BigDecimal aqlMajor, BigDecimal aqlMinor, String codeLetter, int sampleSize,
                                int majorAccept, int majorReject, int minorAccept, int minorReject, int criticalFound,
                                int majorFound, int minorFound, int defectiveUnits, String result,
                                String standardEdition, Long inspectedBy, OffsetDateTime inspectedAt) {
    }

    public record InspectionRow(long id, String orderCode, String lineCode, String inspectionLevel, int lotSize,
                                BigDecimal aqlMajor, BigDecimal aqlMinor, String codeLetter, int sampleSize,
                                int majorAccept, int majorReject, int minorAccept, int minorReject, int criticalFound,
                                int majorFound, int minorFound, int defectiveUnits, String result,
                                String standardEdition, OffsetDateTime inspectedAt) {
    }
}
