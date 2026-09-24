package io.github.diegofranciscog.textrack.seed;

import io.github.diegofranciscog.textrack.domain.ScanSource;
import io.github.diegofranciscog.textrack.domain.TicketInfo;
import io.github.diegofranciscog.textrack.dto.ReadingDtos.ScanRequest;
import io.github.diegofranciscog.textrack.repository.CutRepository;
import io.github.diegofranciscog.textrack.seed.DemoPlan.LinePlan;
import io.github.diegofranciscog.textrack.seed.DemoPlan.WorkerPlan;
import io.github.diegofranciscog.textrack.service.PlantTime;
import io.github.diegofranciscog.textrack.service.ReadingService;
import io.github.diegofranciscog.textrack.service.calc.TicketPayload;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Simula a la tablet de planta en la demo: cada pocos segundos una operaria escanea el siguiente ticket disponible
 * de sus operaciones. Usa el mismo {@link ReadingService} que la app Android, así el tablero se actualiza en vivo
 * por WebSocket exactamente como en producción.
 */
@Component
@Profile("demo")
@ConditionalOnProperty(prefix = "app.demo", name = "simulator-enabled", havingValue = "true")
public class DemoScanSimulator {

    private static final Logger log = LoggerFactory.getLogger(DemoScanSimulator.class);
    private static final LocalTime FIRST_SCAN = LocalTime.of(7, 5);

    private final JdbcClient jdbc;
    private final CutRepository cuts;
    private final ReadingService readings;
    private final DemoDataSeeder seeder;
    private final PlantTime time;

    public DemoScanSimulator(JdbcClient jdbc, CutRepository cuts, ReadingService readings, DemoDataSeeder seeder,
                             PlantTime time) {
        this.jdbc = jdbc;
        this.cuts = cuts;
        this.readings = readings;
        this.seeder = seeder;
        this.time = time;
    }

    @Scheduled(fixedDelayString = "${app.demo.simulator-interval-seconds}", timeUnit = java.util.concurrent.TimeUnit.SECONDS,
            initialDelayString = "${app.demo.simulator-interval-seconds}")
    public void scanNext() {
        if (time.now().atZoneSameInstant(time.zone()).toLocalTime().isBefore(FIRST_SCAN)) {
            return;
        }
        try {
            seeder.ensureToday();
            closeLongStops();
            List<WorkerPlan> workers = new ArrayList<>();
            DemoPlan.LINES.forEach(line -> workers.addAll(line.workers()));
            Collections.shuffle(workers);
            for (WorkerPlan worker : workers) {
                if (machineStopped(worker)) {
                    continue;
                }
                Optional<UUID> ticket = nextTicket(lineOf(worker), worker);
                if (ticket.isPresent()) {
                    TicketInfo info = cuts.findTicket(ticket.get()).orElseThrow();
                    String payload = new TicketPayload(info.keyId(), info.id(), info.bundleCode(), info.operationCode(),
                            info.quantity(), info.signature()).encode();
                    readings.ingest(new ScanRequest(UUID.randomUUID(), payload, worker.code(), time.now(),
                            "demo-simulator"), ScanSource.DEMO);
                    return;
                }
            }
        } catch (RuntimeException e) {
            log.warn("Simulador demo: {}", e.getMessage());
        }
    }

    /** Siguiente ticket de las operaciones de la operaria cuyo paso anterior del bulto ya fue registrado. */
    private Optional<UUID> nextTicket(LinePlan line, WorkerPlan worker) {
        List<UUID> candidates = jdbc.sql("""
                        SELECT t.id
                          FROM tickets t
                          JOIN bundles b ON b.id = t.bundle_id
                          JOIN cuts c ON c.id = b.cut_id
                          JOIN operations o ON o.id = t.operation_id
                          JOIN styles s ON s.id = o.style_id
                         WHERE s.code = :style AND o.code IN (:ops)
                           AND NOT EXISTS (SELECT 1 FROM scan_readings r WHERE r.ticket_id = t.id)
                           AND NOT EXISTS (
                                 SELECT 1 FROM tickets prev
                                   JOIN operations po ON po.id = prev.operation_id
                                  WHERE prev.bundle_id = t.bundle_id AND po.sequence < o.sequence
                                    AND NOT EXISTS (SELECT 1 FROM scan_readings r2 WHERE r2.ticket_id = prev.id))
                         ORDER BY c.id, b.bundle_number, o.sequence
                         LIMIT 5""")
                .param("style", line.style().code())
                .param("ops", List.copyOf(worker.operations()))
                .query(UUID.class).list();
        return candidates.isEmpty() ? Optional.empty()
                : Optional.of(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())));
    }

    private boolean machineStopped(WorkerPlan worker) {
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM machine_stops s JOIN machines m ON m.id = s.machine_id
                                        WHERE m.code = :code AND s.ended_at IS NULL)""")
                .param("code", worker.machineCode()).query(Boolean.class).single();
    }

    /** En la demo nadie cierra los paros a mano: se cierran solos a los 25 minutos. */
    private void closeLongStops() {
        jdbc.sql("""
                        UPDATE machine_stops SET ended_at = started_at + INTERVAL '25 minutes'
                         WHERE ended_at IS NULL AND started_at < :limit""")
                .param("limit", time.now().minusMinutes(25)).update();
    }

    private static LinePlan lineOf(WorkerPlan worker) {
        return DemoPlan.LINES.stream().filter(line -> line.workers().contains(worker)).findFirst().orElseThrow();
    }
}
