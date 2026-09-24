package io.github.diegofranciscog.textrack.repository;

import io.github.diegofranciscog.textrack.domain.OrderLine;
import io.github.diegofranciscog.textrack.domain.OrderStatus;
import io.github.diegofranciscog.textrack.domain.ProductionOrder;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ProductionOrderRepository {

    private static final String ORDER_SELECT = """
            SELECT po.id, po.code, po.style_id, s.code AS style_code, s.name AS style_name, po.customer, po.due_date,
                   po.status, po.created_at
              FROM production_orders po JOIN styles s ON s.id = po.style_id
            """;

    private final JdbcClient jdbc;

    public ProductionOrderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ProductionOrder> findAll() {
        return jdbc.sql(ORDER_SELECT + " ORDER BY po.created_at DESC, po.id DESC").query(ProductionOrder.class).list();
    }

    public Optional<ProductionOrder> findById(long id) {
        return jdbc.sql(ORDER_SELECT + " WHERE po.id = :id").param("id", id).query(ProductionOrder.class).optional();
    }

    public Optional<ProductionOrder> findByCode(String code) {
        return jdbc.sql(ORDER_SELECT + " WHERE po.code = :code").param("code", code)
                .query(ProductionOrder.class).optional();
    }

    public String nextCode(int year) {
        long next = jdbc.sql("SELECT nextval('production_order_code_seq')").query(Long.class).single();
        return "ORD-%d-%04d".formatted(year, next);
    }

    public long insert(String code, long styleId, String customer, LocalDate dueDate, Long createdBy) {
        return jdbc.sql("""
                        INSERT INTO production_orders (code, style_id, customer, due_date, created_by)
                        VALUES (:code, :style, :customer, :due, :by) RETURNING id""")
                .param("code", code).param("style", styleId).param("customer", customer).param("due", dueDate)
                .param("by", createdBy).query(Long.class).single();
    }

    public void insertLine(long orderId, String sizeCode, String color, int quantity) {
        jdbc.sql("""
                        INSERT INTO production_order_lines (production_order_id, size_code, color, quantity)
                        VALUES (:order, :size, :color, :qty)""")
                .param("order", orderId).param("size", sizeCode).param("color", color).param("qty", quantity)
                .update();
    }

    /** Líneas de la orden con la cantidad ya cortada (suma de bultos de cortes de la orden). */
    public List<OrderLine> findLines(long orderId) {
        return jdbc.sql("""
                        SELECT l.id, l.size_code, l.color, l.quantity,
                               COALESCE((SELECT SUM(b.quantity) FROM bundles b JOIN cuts c ON c.id = b.cut_id
                                          WHERE c.production_order_id = l.production_order_id
                                            AND b.size_code = l.size_code AND b.color = l.color), 0) AS cut_quantity
                          FROM production_order_lines l JOIN sizes z ON z.code = l.size_code
                         WHERE l.production_order_id = :order
                         ORDER BY l.color, z.sort_order""")
                .param("order", orderId).query(OrderLine.class).list();
    }

    public void updateStatus(long orderId, OrderStatus status) {
        jdbc.sql("UPDATE production_orders SET status = :status WHERE id = :id")
                .param("status", status.name()).param("id", orderId).update();
    }

    /** Piezas terminadas de la orden: lecturas de la última operación de la ruta. */
    public int finishedPieces(long orderId) {
        return jdbc.sql("""
                        SELECT COALESCE(SUM(t.quantity), 0)
                          FROM scan_readings r
                          JOIN tickets t ON t.id = r.ticket_id
                          JOIN bundles b ON b.id = t.bundle_id
                          JOIN cuts c ON c.id = b.cut_id
                          JOIN production_orders po ON po.id = c.production_order_id
                          JOIN operations o ON o.id = t.operation_id
                         WHERE c.production_order_id = :order
                           AND o.sequence = (SELECT MAX(sequence) FROM operations WHERE style_id = po.style_id)""")
                .param("order", orderId).query(Integer.class).single();
    }
}
