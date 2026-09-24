package io.github.diegofranciscog.textrack.repository;

import io.github.diegofranciscog.textrack.domain.Bundle;
import io.github.diegofranciscog.textrack.domain.Cut;
import io.github.diegofranciscog.textrack.domain.TicketInfo;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Cortes, bultos y tickets. */
@Repository
public class CutRepository {

    private static final String CUT_SELECT = """
            SELECT c.id, c.code, c.production_order_id, po.code AS order_code, c.color, c.max_bundle_size, c.created_at,
                   (SELECT COUNT(*) FROM bundles b WHERE b.cut_id = c.id) AS bundles,
                   (SELECT COALESCE(SUM(b.quantity), 0) FROM bundles b WHERE b.cut_id = c.id) AS pieces
              FROM cuts c JOIN production_orders po ON po.id = c.production_order_id
            """;
    private static final String BUNDLE_SELECT = """
            SELECT b.id, b.code, b.cut_id, b.roll_id, r.code AS roll_code, r.dye_lot, b.bundle_number, b.size_code,
                   b.color, b.quantity
              FROM bundles b JOIN fabric_rolls r ON r.id = b.roll_id
            """;
    private static final String TICKET_SELECT = """
            SELECT t.id, t.bundle_id, b.code AS bundle_code, b.size_code, b.color, t.operation_id,
                   o.sequence AS operation_sequence, o.code AS operation_code, o.name AS operation_name, t.quantity,
                   t.key_id, t.signature, c.code AS cut_code, po.code AS order_code, s.code AS style_code
              FROM tickets t
              JOIN bundles b ON b.id = t.bundle_id
              JOIN cuts c ON c.id = b.cut_id
              JOIN production_orders po ON po.id = c.production_order_id
              JOIN styles s ON s.id = po.style_id
              JOIN operations o ON o.id = t.operation_id
            """;

    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;

    public CutRepository(JdbcClient jdbc, JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
    }

    public String nextCode() {
        long next = jdbc.sql("SELECT nextval('cut_code_seq')").query(Long.class).single();
        return "CT-%05d".formatted(next);
    }

    public long insert(String code, long orderId, String color, int maxBundleSize, Long createdBy) {
        return jdbc.sql("""
                        INSERT INTO cuts (code, production_order_id, color, max_bundle_size, created_by)
                        VALUES (:code, :order, :color, :max, :by) RETURNING id""")
                .param("code", code).param("order", orderId).param("color", color).param("max", maxBundleSize)
                .param("by", createdBy).query(Long.class).single();
    }

    public void insertRatio(long cutId, String sizeCode, int piecesPerPly) {
        jdbc.sql("INSERT INTO cut_size_ratios (cut_id, size_code, pieces_per_ply) VALUES (:cut, :size, :pieces)")
                .param("cut", cutId).param("size", sizeCode).param("pieces", piecesPerPly).update();
    }

    public void insertRollUsage(long cutId, long rollId, int plies, BigDecimal metersUsed) {
        jdbc.sql("INSERT INTO cut_rolls (cut_id, roll_id, plies, meters_used) VALUES (:cut, :roll, :plies, :meters)")
                .param("cut", cutId).param("roll", rollId).param("plies", plies).param("meters", metersUsed).update();
    }

    public long insertBundle(String code, long cutId, long rollId, int bundleNumber, String sizeCode, String color,
                             int quantity) {
        return jdbc.sql("""
                        INSERT INTO bundles (code, cut_id, roll_id, bundle_number, size_code, color, quantity)
                        VALUES (:code, :cut, :roll, :number, :size, :color, :qty) RETURNING id""")
                .param("code", code).param("cut", cutId).param("roll", rollId).param("number", bundleNumber)
                .param("size", sizeCode).param("color", color).param("qty", quantity)
                .query(Long.class).single();
    }

    public void insertTickets(List<NewTicket> tickets) {
        jdbcTemplate.batchUpdate("""
                        INSERT INTO tickets (id, bundle_id, operation_id, quantity, key_id, signature)
                        VALUES (?, ?, ?, ?, ?, ?)""",
                tickets, 500, (ps, ticket) -> {
                    ps.setObject(1, ticket.id());
                    ps.setLong(2, ticket.bundleId());
                    ps.setLong(3, ticket.operationId());
                    ps.setInt(4, ticket.quantity());
                    ps.setString(5, ticket.keyId());
                    ps.setString(6, ticket.signature());
                });
    }

    public List<Cut> findByOrder(long orderId) {
        return jdbc.sql(CUT_SELECT + " WHERE c.production_order_id = :order ORDER BY c.id")
                .param("order", orderId).query(Cut.class).list();
    }

    public List<Cut> findAll() {
        return jdbc.sql(CUT_SELECT + " ORDER BY c.id DESC").query(Cut.class).list();
    }

    public Optional<Cut> findById(long id) {
        return jdbc.sql(CUT_SELECT + " WHERE c.id = :id").param("id", id).query(Cut.class).optional();
    }

    public List<Bundle> findBundles(long cutId) {
        return jdbc.sql(BUNDLE_SELECT + " WHERE b.cut_id = :cut ORDER BY b.bundle_number")
                .param("cut", cutId).query(Bundle.class).list();
    }

    public List<Bundle> findBundlesByRoll(long rollId) {
        return jdbc.sql(BUNDLE_SELECT + " WHERE b.roll_id = :roll ORDER BY b.code")
                .param("roll", rollId).query(Bundle.class).list();
    }

    public Optional<Bundle> findBundleByCode(String code) {
        return jdbc.sql(BUNDLE_SELECT + " WHERE b.code = :code").param("code", code).query(Bundle.class).optional();
    }

    public List<TicketInfo> findTicketsByCut(long cutId) {
        return jdbc.sql(TICKET_SELECT + " WHERE b.cut_id = :cut ORDER BY b.bundle_number, o.sequence")
                .param("cut", cutId).query(TicketInfo.class).list();
    }

    public List<TicketInfo> findTicketsByBundle(long bundleId) {
        return jdbc.sql(TICKET_SELECT + " WHERE t.bundle_id = :bundle ORDER BY o.sequence")
                .param("bundle", bundleId).query(TicketInfo.class).list();
    }

    public Optional<TicketInfo> findTicket(UUID ticketId) {
        return jdbc.sql(TICKET_SELECT + " WHERE t.id = :id").param("id", ticketId).query(TicketInfo.class).optional();
    }

    /** Detalle del trazo y tendido de un corte. */
    public List<RollUsage> findRollUsages(long cutId) {
        return jdbc.sql("""
                        SELECT r.id AS roll_id, r.code AS roll_code, r.dye_lot, cr.plies, cr.meters_used
                          FROM cut_rolls cr JOIN fabric_rolls r ON r.id = cr.roll_id
                         WHERE cr.cut_id = :cut ORDER BY r.code""")
                .param("cut", cutId).query(RollUsage.class).list();
    }

    public record NewTicket(UUID id, long bundleId, long operationId, int quantity, String keyId, String signature) {
    }

    public record RollUsage(long rollId, String rollCode, String dyeLot, int plies, BigDecimal metersUsed) {
    }
}
