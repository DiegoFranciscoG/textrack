package io.github.diegofranciscog.textrack.seed;

import io.github.diegofranciscog.textrack.config.AppProperties;
import io.github.diegofranciscog.textrack.domain.InspectionLevel;
import io.github.diegofranciscog.textrack.domain.MachineStop;
import io.github.diegofranciscog.textrack.domain.Role;
import io.github.diegofranciscog.textrack.domain.StopReason;
import io.github.diegofranciscog.textrack.dto.EngineeringDtos.CreateOperationRequest;
import io.github.diegofranciscog.textrack.dto.EngineeringDtos.CreateStyleRequest;
import io.github.diegofranciscog.textrack.dto.PlantDtos.CreateOperatorRequest;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.CreateCutRequest;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.CreateOrderRequest;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.CreateRollRequest;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.FabricDefectRequest;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.FabricInspectionRequest;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.OrderLineRequest;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.RollLayRequest;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.SizeRatioRequest;
import io.github.diegofranciscog.textrack.dto.QualityDtos.AqlDefectRequest;
import io.github.diegofranciscog.textrack.dto.QualityDtos.CreateAqlInspectionRequest;
import io.github.diegofranciscog.textrack.domain.ScanSource;
import io.github.diegofranciscog.textrack.domain.ScanStatus;
import io.github.diegofranciscog.textrack.domain.TicketInfo;
import io.github.diegofranciscog.textrack.dto.ReadingDtos.ScanRequest;
import io.github.diegofranciscog.textrack.dto.ReadingDtos.ScanResult;
import io.github.diegofranciscog.textrack.repository.CutRepository;
import io.github.diegofranciscog.textrack.repository.PlantRepository;
import io.github.diegofranciscog.textrack.repository.UserRepository;
import io.github.diegofranciscog.textrack.seed.DemoPlan.LinePlan;
import io.github.diegofranciscog.textrack.seed.DemoPlan.OperationPlan;
import io.github.diegofranciscog.textrack.seed.DemoPlan.WorkerPlan;
import io.github.diegofranciscog.textrack.service.CutService;
import io.github.diegofranciscog.textrack.service.EngineeringService;
import io.github.diegofranciscog.textrack.service.FabricService;
import io.github.diegofranciscog.textrack.service.PlantService;
import io.github.diegofranciscog.textrack.service.PlantTime;
import io.github.diegofranciscog.textrack.service.ProductionOrderService;
import io.github.diegofranciscog.textrack.service.QualityService;
import io.github.diegofranciscog.textrack.service.ReadingService;
import io.github.diegofranciscog.textrack.service.calc.TicketPayload;
import io.github.diegofranciscog.textrack.service.calc.AqlPlanCalculator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Datos de demostración 100 % ficticios (perfil {@code demo}). La primera vez crea la planta, las órdenes y los
 * cortes y simula dos jornadas pasadas más la actual con un modelo de eventos (cada operaria toma el siguiente
 * bulto disponible de sus operaciones, respetando la secuencia de la ruta, el almuerzo y los paros de su máquina).
 * En arranques posteriores solo completa el día actual para que el tablero siempre tenga actividad.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final LocalTime SHIFT_START = LocalTime.of(7, 0);
    private static final LocalTime LUNCH_START = LocalTime.of(12, 0);
    private static final LocalTime LUNCH_END = LocalTime.of(12, 30);
    private static final LocalTime SHIFT_END = LocalTime.of(15, 30);
    private static final int BREAK_MINUTES = 30;
    private static final int REPLENISH_BELOW_TICKETS = 220;
    private static final String DEMO_DOMAIN = "@textrack.demo";

    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transaction;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final EngineeringService engineering;
    private final PlantService plantService;
    private final PlantRepository plant;
    private final FabricService fabric;
    private final ProductionOrderService orders;
    private final CutService cuts;
    private final QualityService quality;
    private final ReadingService readingService;
    private final CutRepository cutRepository;
    private final PlantTime time;
    private final AppProperties properties;
    private final Random random = new Random(20_260_923L);

    public DemoDataSeeder(JdbcClient jdbc, JdbcTemplate jdbcTemplate, TransactionTemplate transaction,
                          UserRepository users, PasswordEncoder passwordEncoder, EngineeringService engineering,
                          PlantService plantService, PlantRepository plant, FabricService fabric,
                          ProductionOrderService orders, CutService cuts, QualityService quality,
                          ReadingService readingService, CutRepository cutRepository, PlantTime time,
                          AppProperties properties) {
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
        this.transaction = transaction;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.engineering = engineering;
        this.plantService = plantService;
        this.plant = plant;
        this.fabric = fabric;
        this.orders = orders;
        this.cuts = cuts;
        this.quality = quality;
        this.readingService = readingService;
        this.cutRepository = cutRepository;
        this.time = time;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        AppProperties.Seed seed = properties.seed();
        if (seed == null || isBlank(seed.staffPassword()) || seed.staffPassword().length() < 12
                || isBlank(seed.viewerPassword()) || seed.viewerPassword().length() < 8) {
            throw new IllegalStateException("El perfil demo necesita SEED_ADMIN_PASSWORD (≥ 12 caracteres) y "
                    + "DEMO_VIEWER_PASSWORD (≥ 8 caracteres)");
        }
        transaction.executeWithoutResult(status -> {
            if (!hasData()) {
                log.info("Creando datos de demostración ficticios…");
                seedUsers(seed);
                seedPlantAndProduction();
                List<LocalDate> pastDays = previousWorkingDays(2);
                for (int i = 0; i < pastDays.size(); i++) {
                    simulateDay(pastDays.get(i), i);
                }
            }
        });
        ensureToday();
    }

    /** Completa la jornada de hoy (asistencia, paros, lecturas hasta ahora) si aún no existe. */
    public synchronized void ensureToday() {
        transaction.executeWithoutResult(status -> {
            LocalDate today = time.today();
            boolean started = jdbc.sql("SELECT EXISTS (SELECT 1 FROM attendances WHERE work_date = :d)")
                    .param("d", today).query(Boolean.class).single();
            if (!started && time.now().toInstant().isAfter(at(today, SHIFT_START).toInstant())) {
                replenishIfNeeded(today);
                simulateDay(today, 2);
            }
        });
    }

    // ---------------------------------------------------------------- maestros y producción

    private boolean hasData() {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM styles)").query(Boolean.class).single();
    }

    private void seedUsers(AppProperties.Seed seed) {
        String staff = passwordEncoder.encode(seed.staffPassword());
        users.insert("admin" + DEMO_DOMAIN, "Administración (demo)", staff, Role.ADMIN);
        users.insert("planificacion" + DEMO_DOMAIN, "Planificación (demo)", staff, Role.PLANNER);
        users.insert("supervision" + DEMO_DOMAIN, "Supervisión de planta (demo)", staff, Role.SUPERVISOR);
        users.insert("calidad" + DEMO_DOMAIN, "Control de calidad (demo)", staff, Role.QUALITY);
        users.insert("tablet" + DEMO_DOMAIN, "Tablet de planta (demo)", staff, Role.SCANNER);
        users.insert("visor" + DEMO_DOMAIN, "Visitante (solo lectura)", passwordEncoder.encode(seed.viewerPassword()),
                Role.VIEWER);
    }

    private void seedPlantAndProduction() {
        LocalDate rateStart = LocalDate.of(time.today().getYear(), 1, 1);
        Map<String, Long> styleIds = new HashMap<>();
        for (LinePlan line : DemoPlan.LINES) {
            long styleId = engineering.createStyle(new CreateStyleRequest(line.style().code(), line.style().name(),
                    line.style().garmentType())).id();
            styleIds.put(line.code(), styleId);
            for (OperationPlan op : line.style().operations()) {
                BigDecimal rate = op.samMinutes().multiply(DemoPlan.RATE_PER_STANDARD_MINUTE)
                        .setScale(4, RoundingMode.HALF_UP);
                engineering.addOperation(styleId, new CreateOperationRequest(op.sequence(), op.code(), op.name(),
                        op.machineType(), op.samMinutes(), rate, rateStart));
            }
            long lineId = plant.insertLine(line.code(), line.name());
            for (WorkerPlan worker : line.workers()) {
                plantService.createOperator(new CreateOperatorRequest(worker.code(), worker.name(), lineId));
                plant.insertMachine(worker.machineCode(), worker.machineType().name(), lineId);
            }
        }

        long navy1 = roll("R-24091", "Tejidos Andinos Demo", "L-2409-A", "Azul marino", "120", "170", 3);
        long navy2 = roll("R-24092", "Tejidos Andinos Demo", "L-2409-A", "Azul marino", "118", "170", 5);
        long navy3 = roll("R-24093", "Tejidos Andinos Demo", "L-2409-B", "Azul marino", "115", "170", 2);
        roll("R-24094", "Tejidos Andinos Demo", "L-2409-B", "Azul marino", "112", "170", 1);
        long white1 = roll("R-24101", "Hilandería del Valle Demo", "L-2410-C", "Blanco", "140", "180", 4);
        long white2 = roll("R-24102", "Hilandería del Valle Demo", "L-2410-C", "Blanco", "138", "180", 1);
        long white3 = roll("R-24103", "Hilandería del Valle Demo", "L-2410-D", "Blanco", "135", "180", 6);
        roll("R-24104", "Hilandería del Valle Demo", "L-2410-D", "Blanco", "90", "180", -1);
        fabric.receive(new CreateRollRequest("R-24105", "Hilandería del Valle Demo", "L-2410-E", "Blanco",
                new BigDecimal("130"), new BigDecimal("180")));

        LocalDate due = time.today().plusDays(12);
        long polo = order(styleIds.get("L1"), "Cliente Demo Andes", due, "Azul marino",
                Map.of("S", 200, "M", 400, "L", 400, "XL", 200));
        cut(polo, "Azul marino", 20, Map.of("S", 1, "M", 2, "L", 2, "XL", 1), List.of(navy1, navy2), 100, "110");
        long tshirt = order(styleIds.get("L2"), "Cliente Demo Pacífico", due.plusDays(3), "Blanco",
                Map.of("M", 600, "L", 600));
        cut(tshirt, "Blanco", 25, Map.of("M", 2, "L", 2), List.of(white1, white2, white3), 100, "125");
        long polo2 = order(styleIds.get("L1"), "Cliente Demo Sierra", due.plusDays(6), "Azul marino",
                Map.of("S", 100, "M", 200, "L", 200, "XL", 100));
        cut(polo2, "Azul marino", 20, Map.of("S", 1, "M", 2, "L", 2, "XL", 1), List.of(navy3), 100, "110");
        // Orden planificada sin cortar: la demo permite registrar el corte con el rollo R-24094.
        order(styleIds.get("L1"), "Cliente Demo Amazonía", due.plusDays(15), "Azul marino",
                Map.of("S", 60, "M", 120, "L", 120, "XL", 60));
    }

    /** Rollo inspeccionado con 4 puntos. {@code defects < 0} crea un rollo que se rechaza. */
    private long roll(String code, String supplier, String dyeLot, String color, String length, String width,
                      int defects) {
        long id = fabric.receive(new CreateRollRequest(code, supplier, dyeLot, color, new BigDecimal(length),
                new BigDecimal(width))).id();
        List<FabricDefectRequest> list = new ArrayList<>();
        if (defects < 0) {
            for (int i = 0; i < 40; i++) {
                list.add(new FabricDefectRequest(BigDecimal.valueOf(i * 0.95).setScale(2, RoundingMode.HALF_UP), 260,
                        i % 5 == 0, i % 5 == 0 ? "FABRIC_HOLE" : "SLUB"));
            }
        } else {
            for (int i = 0; i < defects; i++) {
                list.add(new FabricDefectRequest(BigDecimal.valueOf(5 + i * 17L), 40 + random.nextInt(200), false,
                        "SLUB"));
            }
        }
        BigDecimal inspected = defects < 0 ? new BigDecimal("40") : new BigDecimal(length);
        fabric.inspect(id, new FabricInspectionRequest(inspected, null, list), null);
        return id;
    }

    private long order(long styleId, String customer, LocalDate due, String color, Map<String, Integer> sizes) {
        List<OrderLineRequest> lines = sizes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparingInt(DemoDataSeeder::sizeOrder)))
                .map(e -> new OrderLineRequest(e.getKey(), color, e.getValue()))
                .toList();
        return orders.create(new CreateOrderRequest(styleId, customer, due, lines), null).id();
    }

    private void cut(long orderId, String color, int bundleSize, Map<String, Integer> ratio, List<Long> rolls,
                     int plies, String meters) {
        List<SizeRatioRequest> ratios = ratio.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparingInt(DemoDataSeeder::sizeOrder)))
                .map(e -> new SizeRatioRequest(e.getKey(), e.getValue()))
                .toList();
        List<RollLayRequest> lays = rolls.stream()
                .map(roll -> new RollLayRequest(roll, plies, new BigDecimal(meters)))
                .toList();
        cuts.create(orderId, new CreateCutRequest(color, bundleSize, ratios, lays), null);
    }

    /** Si una línea se queda sin trabajo, llega tela nueva y se abre otra orden con su corte. */
    private void replenishIfNeeded(LocalDate day) {
        String suffix = day.format(DateTimeFormatter.ofPattern("yyMMdd"));
        for (LinePlan line : DemoPlan.LINES) {
            long styleId = styleId(line);
            int pending = jdbc.sql("""
                            SELECT COUNT(*) FROM tickets t
                              JOIN bundles b ON b.id = t.bundle_id JOIN cuts c ON c.id = b.cut_id
                              JOIN production_orders po ON po.id = c.production_order_id
                             WHERE po.style_id = :style
                               AND NOT EXISTS (SELECT 1 FROM scan_readings r WHERE r.ticket_id = t.id)""")
                    .param("style", styleId).query(Integer.class).single();
            if (pending >= REPLENISH_BELOW_TICKETS) {
                continue;
            }
            boolean polo = line == DemoPlan.POLO_LINE;
            long roll = roll("R-" + suffix + (polo ? "-P" : "-C"), polo ? "Tejidos Andinos Demo" : "Hilandería del Valle Demo",
                    "L-" + suffix, line.color(), polo ? "120" : "140", polo ? "170" : "180", 2);
            long order = order(styleId, "Cliente Demo Reposición", day.plusDays(10), line.color(),
                    polo ? Map.of("S", 100, "M", 200, "L", 200, "XL", 100) : Map.of("M", 200, "L", 200));
            cut(order, line.color(), polo ? 20 : 25, polo ? Map.of("S", 1, "M", 2, "L", 2, "XL", 1)
                    : Map.of("M", 2, "L", 2), List.of(roll), 100, polo ? "110" : "125");
            log.info("Demo: nueva orden de reposición para {}", line.code());
        }
    }

    // ---------------------------------------------------------------- simulación de una jornada

    private void simulateDay(LocalDate day, int dayIndex) {
        Instant now = time.nowInstant();
        Instant shiftEnd = at(day, SHIFT_END).toInstant();
        boolean shiftOpen = day.equals(time.today()) && now.isBefore(shiftEnd);
        seedStops(day, dayIndex, now);
        ensureAttendance(day, shiftOpen);
        List<SimReading> events = simulate(day, shiftOpen ? now : shiftEnd);
        jdbcTemplate.batchUpdate("""
                INSERT INTO scan_readings (client_reading_id, ticket_id, operator_id, scanned_at, received_at,
                                           work_date, device_id, source)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'DEMO') ON CONFLICT DO NOTHING""",
                events.stream().map(e -> new Object[] {UUID.randomUUID(), e.ticketId(), e.operatorId(),
                        e.at().atOffset(ZoneOffset.UTC), e.at().plusSeconds(random.nextInt(90)).atOffset(ZoneOffset.UTC),
                        day, "demo-tablet-" + e.lineCode()}).toList());
        jdbc.sql("""
                UPDATE production_orders SET status = 'IN_PROGRESS'
                 WHERE status = 'CUTTING'
                   AND EXISTS (SELECT 1 FROM cuts c JOIN bundles b ON b.cut_id = c.id
                                 JOIN tickets t ON t.bundle_id = b.id JOIN scan_readings r ON r.ticket_id = t.id
                                WHERE c.production_order_id = production_orders.id)""").update();
        for (LinePlan line : DemoPlan.LINES) {
            inspectLot(line, day, dayIndex, shiftOpen ? now : shiftEnd);
        }
        log.info("Demo: jornada {} simulada con {} lecturas", day, events.size());
    }

    /**
     * Avance en vivo de la demo: continúa la simulación de hoy desde la última lectura de cada operaria hasta
     * ahora y registra las lecturas por {@link ReadingService} (así el tablero se actualiza por WebSocket).
     * Fuera del turno no genera lecturas y cierra la asistencia al terminar la jornada.
     */
    public synchronized int advanceLive() {
        LocalDate today = time.today();
        Instant now = time.nowInstant();
        if (now.isBefore(at(today, SHIFT_START).plusMinutes(5).toInstant())) {
            return 0;
        }
        ensureToday();
        Instant shiftEnd = at(today, SHIFT_END).toInstant();
        if (!now.isBefore(shiftEnd)) {
            jdbc.sql("UPDATE attendances SET check_out = :out WHERE work_date = :day AND check_out IS NULL")
                    .param("out", at(today, SHIFT_END).plusMinutes(random.nextInt(8))).param("day", today).update();
            return 0;
        }
        int registered = 0;
        for (SimReading event : simulate(today, now)) {
            TicketInfo ticket = cutRepository.findTicket(event.ticketId()).orElseThrow();
            String payload = new TicketPayload(ticket.keyId(), ticket.id(), ticket.bundleCode(), ticket.operationCode(),
                    ticket.quantity(), ticket.signature()).encode();
            ScanResult result = readingService.ingest(new ScanRequest(UUID.randomUUID(), payload, event.operatorCode(),
                    event.at().atOffset(ZoneOffset.UTC), "demo-tablet-" + event.lineCode()), ScanSource.DEMO);
            if (result.status() == ScanStatus.ACCEPTED) {
                registered++;
            }
        }
        return registered;
    }

    private void ensureAttendance(LocalDate day, boolean shiftOpen) {
        for (LinePlan line : DemoPlan.LINES) {
            for (WorkerPlan plan : line.workers()) {
                long operatorId = plant.findOperatorByCode(plan.code()).orElseThrow().id();
                if (plant.findAttendance(operatorId, day).isEmpty()) {
                    Instant checkIn = at(day, SHIFT_START).toInstant().plusSeconds(random.nextInt(360));
                    OffsetDateTime checkOut = shiftOpen ? null : at(day, SHIFT_END).plusMinutes(random.nextInt(10));
                    plant.insertAttendance(operatorId, day, checkIn.atOffset(ZoneOffset.UTC), checkOut, BREAK_MINUTES);
                }
            }
        }
    }

    /**
     * Modelo de eventos: cada operaria toma el siguiente ticket disponible de sus operaciones (el paso anterior
     * del bulto ya está hecho), tarda SAM × piezas ÷ eficiencia (±10 %) y respeta almuerzo y paros de su máquina.
     * El reloj de cada operaria continúa desde su última lectura del día, así se puede avanzar por tramos.
     */
    private List<SimReading> simulate(LocalDate day, Instant until) {
        Map<Long, List<Interval>> stopsByMachine = new HashMap<>();
        for (MachineStop stop : plant.findStopsOverlapping(time.startOf(day), time.endOf(day))) {
            Instant stopEnd = stop.endedAt() != null ? stop.endedAt().toInstant() : until;
            stopsByMachine.computeIfAbsent(stop.machineId(), k -> new ArrayList<>())
                    .add(new Interval(stop.startedAt().toInstant(), stopEnd));
        }
        Map<Long, Instant> lastReading = new HashMap<>();
        jdbc.sql("""
                        SELECT operator_id, MAX(scanned_at) AS last FROM scan_readings
                         WHERE work_date = :day GROUP BY operator_id""")
                .param("day", day)
                .query((rs, n) -> Map.entry(rs.getLong("operator_id"), rs.getObject("last", OffsetDateTime.class)))
                .list()
                .forEach(e -> lastReading.put(e.getKey(), e.getValue().toInstant()));

        List<SimReading> events = new ArrayList<>();
        Instant lunchStart = at(day, LUNCH_START).toInstant();
        Instant lunchEnd = at(day, LUNCH_END).toInstant();
        for (LinePlan line : DemoPlan.LINES) {
            List<SimTicket> tickets = tickets(styleId(line));
            Map<Long, List<SimTicket>> byBundle = new HashMap<>();
            tickets.forEach(t -> byBundle.computeIfAbsent(t.bundleId, k -> new ArrayList<>()).add(t));
            byBundle.values().forEach(list -> list.sort(Comparator.comparingInt(t -> t.sequence)));
            for (List<SimTicket> list : byBundle.values()) {
                for (int i = 1; i < list.size(); i++) {
                    list.get(i).predecessor = list.get(i - 1);
                }
            }

            PriorityQueue<SimWorker> queue = new PriorityQueue<>(Comparator.comparing(w -> w.clock));
            for (WorkerPlan plan : line.workers()) {
                long operatorId = plant.findOperatorByCode(plan.code()).orElseThrow().id();
                Instant start = plant.findAttendance(operatorId, day).map(a -> a.checkIn().toInstant())
                        .orElse(at(day, SHIFT_START).toInstant());
                Instant last = lastReading.get(operatorId);
                Instant clock = last != null && last.isAfter(start) ? last : start;
                long machineId = machineId(plan.machineCode());
                queue.add(new SimWorker(plan, operatorId, clock, stopsByMachine.getOrDefault(machineId, List.of())));
            }

            while (!queue.isEmpty()) {
                SimWorker worker = queue.poll();
                if (!worker.clock.isBefore(lunchStart) && worker.clock.isBefore(lunchEnd)) {
                    worker.clock = lunchEnd;
                }
                for (Interval stop : worker.stops) {
                    if (!worker.clock.isBefore(stop.start) && worker.clock.isBefore(stop.end)) {
                        worker.clock = stop.end;
                    }
                }
                if (!worker.clock.isBefore(until)) {
                    continue;
                }
                SimTicket next = null;
                Instant wakeUp = null;
                for (SimTicket ticket : tickets) {
                    if (ticket.readAt != null || !worker.plan.operations().contains(ticket.operationCode)) {
                        continue;
                    }
                    Instant ready = ticket.predecessor == null ? Instant.MIN : ticket.predecessor.readAt;
                    if (ready != null && !ready.isAfter(worker.clock)) {
                        next = ticket;
                        break;
                    }
                    if (ready != null && (wakeUp == null || ready.isBefore(wakeUp))) {
                        wakeUp = ready;
                    }
                }
                if (next == null) {
                    Instant idle = worker.clock.plus(Duration.ofMinutes(4));
                    worker.clock = wakeUp != null && wakeUp.isBefore(idle) ? wakeUp : idle;
                    queue.add(worker);
                    continue;
                }
                double noise = 0.9 + random.nextDouble() * 0.2;
                double minutes = next.samMinutes * next.quantity / worker.plan.efficiency() * noise;
                Instant finish = worker.clock.plusSeconds(Math.round(minutes * 60));
                if (finish.isAfter(until)) {
                    continue;
                }
                next.readAt = finish;
                worker.clock = finish;
                events.add(new SimReading(next.id, worker.operatorId, worker.plan.code(), line.code(), finish));
                queue.add(worker);
            }
        }
        events.sort(Comparator.comparing(SimReading::at));
        return events;
    }

    private void seedStops(LocalDate day, int dayIndex, Instant now) {
        switch (dayIndex) {
            case 0 -> {
                stop("M-102", StopReason.MECHANICAL, false, at(day, LocalTime.of(10, 15)), at(day, LocalTime.of(10, 50)),
                        "Rotura de la barra de agujas");
                stop("M-201", StopReason.MEETING, true, at(day, LocalTime.of(7, 0)), at(day, LocalTime.of(7, 15)),
                        "Charla de seguridad");
            }
            case 1 -> {
                stop("M-204", StopReason.NO_MATERIAL, false, at(day, LocalTime.of(13, 10)), at(day, LocalTime.of(13, 40)),
                        "Falta de hilo blanco");
                stop("M-105", StopReason.PLANNED_MAINTENANCE, true, at(day, LocalTime.of(14, 30)),
                        at(day, LocalTime.of(14, 50)), "Lubricación programada");
            }
            default -> {
                OffsetDateTime start = at(day, LocalTime.of(9, 30));
                OffsetDateTime planned = at(day, LocalTime.of(9, 55));
                if (now.isAfter(start.toInstant())) {
                    stop("M-204", StopReason.NO_MATERIAL, false, start,
                            now.isAfter(planned.toInstant()) ? planned : null, "Espera de cortes");
                }
            }
        }
    }

    private void stop(String machineCode, StopReason reason, boolean planned, OffsetDateTime start,
                      OffsetDateTime end, String notes) {
        plant.insertStop(machineId(machineCode), reason, planned, start, end, notes, null);
    }

    /** Inspección AQL del lote terminado en la jornada (nivel II, AQL 2,5 mayores / 4,0 menores). */
    private void inspectLot(LinePlan line, LocalDate day, int dayIndex, Instant end) {
        long styleId = styleId(line);
        String lastOp = line.style().operations().getLast().code();
        record Finished(long orderId, int pieces, String bundleCode) {
        }
        List<Finished> finished = jdbc.sql("""
                        SELECT c.production_order_id AS order_id, SUM(t.quantity) AS pieces, MIN(b.code) AS bundle_code
                          FROM scan_readings r JOIN tickets t ON t.id = r.ticket_id
                          JOIN operations o ON o.id = t.operation_id JOIN bundles b ON b.id = t.bundle_id
                          JOIN cuts c ON c.id = b.cut_id
                         WHERE r.work_date = :day AND o.style_id = :style AND o.code = :op
                         GROUP BY c.production_order_id ORDER BY pieces DESC LIMIT 1""")
                .param("day", day).param("style", styleId).param("op", lastOp)
                .query(Finished.class).list();
        if (finished.isEmpty() || finished.getFirst().pieces() < 100) {
            return;
        }
        Finished lot = finished.getFirst();
        BigDecimal major = new BigDecimal("2.5");
        BigDecimal minor = new BigDecimal("4.0");
        var plan = AqlPlanCalculator.plan(lot.pieces(), InspectionLevel.II, major);
        boolean reject = dayIndex == 0 && line == DemoPlan.TSHIRT_LINE;
        int majorFound = reject ? plan.rejectNumber() : Math.min(1, plan.acceptNumber());
        List<AqlDefectRequest> defects = new ArrayList<>();
        if (majorFound > 0) {
            defects.add(new AqlDefectRequest(reject ? "SKIPPED_STITCH" : "OPEN_SEAM", null, majorFound,
                    lot.bundleCode(), "OP50"));
        }
        defects.add(new AqlDefectRequest("LOOSE_THREAD", null, 2, null, null));
        long lineId = plant.findLines().stream().filter(l -> l.code().equals(line.code())).findFirst().orElseThrow().id();
        long id = quality.inspect(new CreateAqlInspectionRequest(lot.orderId(), lineId, InspectionLevel.II,
                lot.pieces(), major, minor, majorFound + 1, defects), null).id();
        Instant inspectedAt = end.minus(Duration.ofMinutes(20));
        jdbc.sql("UPDATE aql_inspections SET inspected_at = :at WHERE id = :id")
                .param("at", inspectedAt.atOffset(ZoneOffset.UTC)).param("id", id).update();
    }

    // ---------------------------------------------------------------- utilidades

    private List<SimTicket> tickets(long styleId) {
        return jdbc.sql("""
                        SELECT t.id, b.id AS bundle_id, o.code AS operation_code, o.sequence, t.quantity,
                               o.sam_minutes, r.scanned_at
                          FROM tickets t
                          JOIN bundles b ON b.id = t.bundle_id
                          JOIN cuts c ON c.id = b.cut_id
                          JOIN operations o ON o.id = t.operation_id
                          LEFT JOIN scan_readings r ON r.ticket_id = t.id
                         WHERE o.style_id = :style
                         ORDER BY c.id, b.bundle_number, o.sequence""")
                .param("style", styleId)
                .query((rs, n) -> new SimTicket(rs.getObject("id", UUID.class), rs.getLong("bundle_id"),
                        rs.getString("operation_code"), rs.getInt("sequence"), rs.getInt("quantity"),
                        rs.getBigDecimal("sam_minutes").doubleValue(),
                        rs.getObject("scanned_at", OffsetDateTime.class)))
                .list();
    }

    private long styleId(LinePlan line) {
        return jdbc.sql("SELECT id FROM styles WHERE code = :code").param("code", line.style().code())
                .query(Long.class).single();
    }

    private long machineId(String code) {
        return jdbc.sql("SELECT id FROM machines WHERE code = :code").param("code", code).query(Long.class).single();
    }

    private List<LocalDate> previousWorkingDays(int count) {
        List<LocalDate> days = new ArrayList<>();
        LocalDate day = time.today().minusDays(1);
        while (days.size() < count) {
            if (day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY) {
                days.addFirst(day);
            }
            day = day.minusDays(1);
        }
        return days;
    }

    private OffsetDateTime at(LocalDate day, LocalTime localTime) {
        return day.atTime(localTime).atZone(time.zone()).toOffsetDateTime();
    }

    private static int sizeOrder(String size) {
        return List.of("XS", "S", "M", "L", "XL", "XXL", "3XL").indexOf(size);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record Interval(Instant start, Instant end) {
    }

    private record SimReading(UUID ticketId, long operatorId, String operatorCode, String lineCode, Instant at) {
    }

    private static final class SimTicket {
        private final UUID id;
        private final long bundleId;
        private final String operationCode;
        private final int sequence;
        private final int quantity;
        private final double samMinutes;
        private Instant readAt;
        private SimTicket predecessor;

        SimTicket(UUID id, long bundleId, String operationCode, int sequence, int quantity, double samMinutes,
                  OffsetDateTime readAt) {
            this.id = id;
            this.bundleId = bundleId;
            this.operationCode = operationCode;
            this.sequence = sequence;
            this.quantity = quantity;
            this.samMinutes = samMinutes;
            this.readAt = readAt == null ? null : readAt.toInstant();
        }
    }

    private static final class SimWorker {
        private final WorkerPlan plan;
        private final long operatorId;
        private final List<Interval> stops;
        private Instant clock;

        SimWorker(WorkerPlan plan, long operatorId, Instant clock, List<Interval> stops) {
            this.plan = plan;
            this.operatorId = operatorId;
            this.clock = clock;
            this.stops = stops;
        }
    }
}
