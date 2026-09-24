package io.github.diegofranciscog.textrack.service;

import io.github.diegofranciscog.textrack.config.AppProperties;
import io.github.diegofranciscog.textrack.domain.Operator;
import io.github.diegofranciscog.textrack.domain.ScanSource;
import io.github.diegofranciscog.textrack.domain.ScanStatus;
import io.github.diegofranciscog.textrack.domain.TicketInfo;
import io.github.diegofranciscog.textrack.dto.ReadingDtos.BatchScanResponse;
import io.github.diegofranciscog.textrack.dto.ReadingDtos.RecentReadingResponse;
import io.github.diegofranciscog.textrack.dto.ReadingDtos.ScanRequest;
import io.github.diegofranciscog.textrack.dto.ReadingDtos.ScanResult;
import io.github.diegofranciscog.textrack.repository.CutRepository;
import io.github.diegofranciscog.textrack.repository.PlantRepository;
import io.github.diegofranciscog.textrack.repository.ProductionOrderRepository;
import io.github.diegofranciscog.textrack.repository.ReadingRepository;
import io.github.diegofranciscog.textrack.repository.ReadingRepository.StoredReading;
import io.github.diegofranciscog.textrack.service.calc.TicketPayload;
import io.github.diegofranciscog.textrack.service.calc.TicketSigner;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Registro de lecturas de tickets (R11, R12).
 *
 * <p>Garantías:</p>
 * <ol>
 *   <li>Un ticket se registra una sola vez: restricción única {@code scan_readings.ticket_id}.</li>
 *   <li>Reenviar una lectura (mismo {@code clientReadingId}) devuelve el mismo resultado sin crear otra:
 *       restricción única {@code client_reading_id}. Así, la sincronización offline puede reintentar sin límite.</li>
 *   <li>Los tickets con firma HMAC inválida se rechazan y quedan auditados (anti-falsificación).</li>
 * </ol>
 * <p>La inserción usa {@code INSERT … ON CONFLICT DO NOTHING}: con dos dispositivos subiendo el mismo ticket a la
 * vez, la base de datos decide quién gana sin bloqueos ni excepciones.</p>
 */
@Service
public class ReadingService {

    private static final Logger log = LoggerFactory.getLogger(ReadingService.class);

    private final ReadingRepository readings;
    private final CutRepository cuts;
    private final ProductionOrderRepository orders;
    private final PlantRepository plant;
    private final TicketSigner signer;
    private final PlantTime time;
    private final ApplicationEventPublisher events;
    private final AppProperties.Production settings;

    public ReadingService(ReadingRepository readings, CutRepository cuts, ProductionOrderRepository orders,
                          PlantRepository plant, TicketSigner signer, PlantTime time, ApplicationEventPublisher events,
                          AppProperties properties) {
        this.readings = readings;
        this.cuts = cuts;
        this.orders = orders;
        this.plant = plant;
        this.signer = signer;
        this.time = time;
        this.events = events;
        this.settings = properties.production();
    }

    public ScanResult ingest(ScanRequest request, ScanSource source) {
        Optional<TicketPayload> parsed = TicketPayload.parse(request.payload());
        if (parsed.isEmpty()) {
            return reject(request, ScanStatus.INVALID_FORMAT, "El QR no es un ticket de textrack");
        }
        TicketPayload payload = parsed.get();
        if (!signer.verify(payload)) {
            log.warn("Ticket con firma inválida desde el dispositivo {}", request.deviceId());
            return reject(request, ScanStatus.INVALID_SIGNATURE, "Firma inválida: el ticket no fue emitido por textrack");
        }

        Optional<StoredReading> replay = readings.findByClientId(request.clientReadingId());
        if (replay.isPresent()) {
            return replayResult(request, payload, replay.get());
        }

        Optional<TicketInfo> ticket = cuts.findTicket(payload.ticketId())
                .filter(t -> t.quantity() == payload.quantity() && t.bundleCode().equals(payload.bundleCode())
                        && t.operationCode().equals(payload.operationCode()));
        if (ticket.isEmpty()) {
            return reject(request, ScanStatus.UNKNOWN_TICKET, "El ticket no existe");
        }
        Optional<Operator> operator = plant.findOperatorByCode(request.operatorCode()).filter(Operator::active);
        if (operator.isEmpty()) {
            return reject(request, ScanStatus.UNKNOWN_OPERATOR, "Operario desconocido o inactivo");
        }
        OffsetDateTime now = time.now();
        if (request.scannedAt().isAfter(now.plusMinutes(settings.maxClockSkewMinutes()))
                || request.scannedAt().isBefore(now.minusDays(settings.maxScanAgeDays()))) {
            return reject(request, ScanStatus.INVALID_TIMESTAMP, "Hora de lectura fuera del rango permitido");
        }

        LocalDate workDate = plant.findAttendanceCovering(operator.get().id(), request.scannedAt())
                .map(attendance -> attendance.workDate())
                .orElseGet(() -> time.dateOf(request.scannedAt().toInstant()));
        Optional<Long> inserted = readings.insertIfAbsent(request.clientReadingId(), payload.ticketId(),
                operator.get().id(), request.scannedAt(), workDate, request.deviceId(), source.name());
        if (inserted.isPresent()) {
            orders.markInProgressByTicket(payload.ticketId());
            events.publishEvent(new ReadingAcceptedEvent(workDate, inserted.get()));
            return result(request, ScanStatus.ACCEPTED, "Lectura registrada", inserted.get(), ticket.get(),
                    operator.get().code(), request.scannedAt());
        }

        // Perdimos una carrera: o es el mismo clientReadingId (reintento concurrente) o el ticket ya tenía lectura.
        Optional<StoredReading> sameClient = readings.findByClientId(request.clientReadingId());
        if (sameClient.isPresent()) {
            return replayResult(request, payload, sameClient.get());
        }
        StoredReading existing = readings.findByTicketId(payload.ticketId()).orElseThrow();
        return result(request, ScanStatus.ALREADY_SCANNED,
                "El ticket ya fue registrado por " + existing.operatorCode(), existing.id(), ticket.get(),
                existing.operatorCode(), existing.scannedAt());
    }

    /** Cada lectura del lote se procesa de forma independiente; el resultado de una no afecta a las demás. */
    public BatchScanResponse ingestBatch(List<ScanRequest> requests, ScanSource source) {
        List<ScanResult> results = new ArrayList<>(requests.size());
        int accepted = 0;
        int duplicates = 0;
        int rejected = 0;
        for (ScanRequest request : requests) {
            ScanResult result = ingest(request, source);
            results.add(result);
            switch (result.status()) {
                case ACCEPTED -> accepted++;
                case DUPLICATE -> duplicates++;
                default -> rejected++;
            }
        }
        return new BatchScanResponse(results, accepted, duplicates, rejected);
    }

    public List<RecentReadingResponse> recent(int limit) {
        return readings.findRecent(Math.min(Math.max(limit, 1), 100)).stream()
                .map(r -> new RecentReadingResponse(r.id(), r.scannedAt(), r.operatorCode(), r.operatorName(),
                        r.lineCode(), r.bundleCode(), r.operationCode(), r.quantity(), r.source()))
                .toList();
    }

    private ScanResult replayResult(ScanRequest request, TicketPayload payload, StoredReading stored) {
        if (!stored.ticketId().equals(payload.ticketId())) {
            return new ScanResult(request.clientReadingId(), ScanStatus.KEY_REUSED,
                    "El identificador de lectura ya se usó para otro ticket", null, null, null, null, null, null, null);
        }
        TicketInfo ticket = cuts.findTicket(stored.ticketId()).orElseThrow();
        return result(request, ScanStatus.DUPLICATE, "Lectura ya sincronizada", stored.id(), ticket,
                stored.operatorCode(), stored.scannedAt());
    }

    private ScanResult reject(ScanRequest request, ScanStatus status, String message) {
        readings.insertRejected(request.clientReadingId(), request.payload(), status.name(), request.operatorCode(),
                request.deviceId());
        return new ScanResult(request.clientReadingId(), status, message, null, null, null, null, null, null, null);
    }

    private static ScanResult result(ScanRequest request, ScanStatus status, String message, Long readingId,
                                     TicketInfo ticket, String registeredBy, OffsetDateTime registeredAt) {
        return new ScanResult(request.clientReadingId(), status, message, readingId, ticket.id(), ticket.bundleCode(),
                ticket.operationCode(), ticket.quantity(), registeredBy, registeredAt);
    }

    /** Evento para refrescar el tablero en vivo. */
    public record ReadingAcceptedEvent(LocalDate workDate, long readingId) {
    }
}
