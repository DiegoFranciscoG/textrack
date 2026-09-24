package io.github.diegofranciscog.textrack.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Lecturas de tickets. La inserción es idempotente gracias a las restricciones únicas (V6). */
@Repository
public class ReadingRepository {

    private static final String STORED_SELECT = """
            SELECT r.id, r.client_reading_id, r.ticket_id, r.operator_id, o.code AS operator_code, r.scanned_at,
                   r.received_at
              FROM scan_readings r JOIN operators o ON o.id = r.operator_id
            """;

    private final JdbcClient jdbc;

    public ReadingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserta la lectura si no viola ninguna restricción única. {@code ON CONFLICT DO NOTHING} sin objetivo cubre
     * ambas (ticket y clientReadingId), así que dos dispositivos que suben el mismo ticket a la vez nunca generan
     * dos lecturas: uno inserta y el otro recibe vacío.
     */
    public Optional<Long> insertIfAbsent(UUID clientReadingId, UUID ticketId, long operatorId, OffsetDateTime scannedAt,
                                         LocalDate workDate, String deviceId, String source) {
        return jdbc.sql("""
                        INSERT INTO scan_readings (client_reading_id, ticket_id, operator_id, scanned_at, work_date,
                                                   device_id, source)
                        VALUES (:client, :ticket, :operator, :scanned, :date, :device, :source)
                        ON CONFLICT DO NOTHING
                        RETURNING id""")
                .param("client", clientReadingId).param("ticket", ticketId).param("operator", operatorId)
                .param("scanned", scannedAt).param("date", workDate).param("device", deviceId)
                .param("source", source)
                .query(Long.class).optional();
    }

    public Optional<StoredReading> findByClientId(UUID clientReadingId) {
        return jdbc.sql(STORED_SELECT + " WHERE r.client_reading_id = :client").param("client", clientReadingId)
                .query(StoredReading.class).optional();
    }

    public Optional<StoredReading> findByTicketId(UUID ticketId) {
        return jdbc.sql(STORED_SELECT + " WHERE r.ticket_id = :ticket").param("ticket", ticketId)
                .query(StoredReading.class).optional();
    }

    public void insertRejected(UUID clientReadingId, String rawPayload, String reason, String operatorCode,
                               String deviceId) {
        jdbc.sql("""
                        INSERT INTO rejected_scans (client_reading_id, raw_payload, reason, operator_code, device_id)
                        VALUES (:client, :raw, :reason, :operator, :device)
                        ON CONFLICT (client_reading_id) DO NOTHING""")
                .param("client", clientReadingId).param("raw", truncate(rawPayload, 300)).param("reason", reason)
                .param("operator", truncate(operatorCode, 20)).param("device", truncate(deviceId, 64))
                .update();
    }

    /** Trabajo del día por operario con la tarifa vigente en la fecha de la jornada. */
    public List<PieceWorkRow> findPieceWork(LocalDate workDate, Long operatorId) {
        return jdbc.sql("""
                        SELECT r.operator_id, r.scanned_at, o.code AS operation_code, t.quantity, o.sam_minutes,
                               (SELECT pr.rate_usd FROM piece_rates pr
                                 WHERE pr.operation_id = o.id AND pr.valid_from <= r.work_date
                                   AND (pr.valid_to IS NULL OR pr.valid_to > r.work_date)) AS rate_usd
                          FROM scan_readings r
                          JOIN tickets t ON t.id = r.ticket_id
                          JOIN operations o ON o.id = t.operation_id
                         WHERE r.work_date = :date AND (CAST(:operator AS BIGINT) IS NULL OR r.operator_id = :operator)
                         ORDER BY r.operator_id, r.scanned_at""")
                .param("date", workDate).param("operator", operatorId)
                .query(PieceWorkRow.class).list();
    }

    public List<RecentReading> findRecent(int limit) {
        return jdbc.sql("""
                        SELECT r.id, r.scanned_at, op.code AS operator_code, op.full_name AS operator_name,
                               l.code AS line_code, b.code AS bundle_code, o.code AS operation_code, t.quantity,
                               r.source
                          FROM scan_readings r
                          JOIN operators op ON op.id = r.operator_id
                          LEFT JOIN production_lines l ON l.id = op.line_id
                          JOIN tickets t ON t.id = r.ticket_id
                          JOIN bundles b ON b.id = t.bundle_id
                          JOIN operations o ON o.id = t.operation_id
                         ORDER BY r.scanned_at DESC, r.id DESC
                         LIMIT :limit""")
                .param("limit", limit).query(RecentReading.class).list();
    }

    public int countRejectedSince(OffsetDateTime since) {
        return jdbc.sql("SELECT COUNT(*) FROM rejected_scans WHERE received_at >= :since AND reason = 'INVALID_SIGNATURE'")
                .param("since", since).query(Integer.class).single();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public record StoredReading(long id, UUID clientReadingId, UUID ticketId, long operatorId, String operatorCode,
                                OffsetDateTime scannedAt, OffsetDateTime receivedAt) {
    }

    public record PieceWorkRow(long operatorId, OffsetDateTime scannedAt, String operationCode, int quantity,
                               BigDecimal samMinutes, BigDecimal rateUsd) {
    }

    public record RecentReading(long id, OffsetDateTime scannedAt, String operatorCode, String operatorName,
                                String lineCode, String bundleCode, String operationCode, int quantity, String source) {
    }
}
