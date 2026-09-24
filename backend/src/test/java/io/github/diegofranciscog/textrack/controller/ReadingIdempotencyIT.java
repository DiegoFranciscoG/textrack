package io.github.diegofranciscog.textrack.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.diegofranciscog.textrack.domain.Role;
import io.github.diegofranciscog.textrack.domain.ScanSource;
import io.github.diegofranciscog.textrack.domain.ScanStatus;
import io.github.diegofranciscog.textrack.domain.TicketInfo;
import io.github.diegofranciscog.textrack.dto.ReadingDtos.ScanRequest;
import io.github.diegofranciscog.textrack.dto.ReadingDtos.ScanResult;
import io.github.diegofranciscog.textrack.repository.CutRepository;
import io.github.diegofranciscog.textrack.service.ReadingService;
import io.github.diegofranciscog.textrack.service.calc.TicketSigner;
import io.github.diegofranciscog.textrack.support.Fixtures;
import io.github.diegofranciscog.textrack.support.Fixtures.Scenario;
import io.github.diegofranciscog.textrack.support.IntegrationTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

@DisplayName("Criterios de aceptación: un ticket no se registra dos veces y la sincronización offline no duplica")
class ReadingIdempotencyIT extends IntegrationTest {

    @Autowired
    private CutRepository cuts;
    @Autowired
    private ReadingService readingService;

    private Scenario scenario;

    @BeforeEach
    void setUp() throws Exception {
        scenario = new Fixtures(mvc, bearer(Role.ADMIN), jdbc, cuts).scenario();
    }

    private String scanJson(UUID clientId, String payload, String operatorCode, OffsetDateTime at) {
        return """
                {"clientReadingId":"%s","payload":"%s","operatorCode":"%s","scannedAt":"%s","deviceId":"tablet-01"}"""
                .formatted(clientId, payload, operatorCode, at);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
    }

    private int readingsFor(UUID ticketId) {
        return jdbc.sql("SELECT COUNT(*) FROM scan_readings WHERE ticket_id = :t").param("t", ticketId)
                .query(Integer.class).single();
    }

    @Test
    @DisplayName("El mismo ticket escaneado por segunda vez se rechaza con 409 y no crea otra lectura")
    void ticketCannotBeRegisteredTwice() throws Exception {
        TicketInfo ticket = scenario.tickets().getFirst();
        String payload = Fixtures.payload(ticket);

        mvc.perform(post("/api/v1/readings").header("Authorization", bearer(Role.SCANNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scanJson(UUID.randomUUID(), payload, scenario.operatorCode(), now())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.bundleCode").value(ticket.bundleCode()));

        mvc.perform(post("/api/v1/readings").header("Authorization", bearer(Role.SCANNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scanJson(UUID.randomUUID(), payload, scenario.operatorCode(), now())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("ALREADY_SCANNED"))
                .andExpect(jsonPath("$.registeredBy").value(scenario.operatorCode()));

        assertThat(readingsFor(ticket.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("Reenviar el mismo lote offline devuelve DUPLICATE y no duplica lecturas")
    void resendingOfflineBatchIsIdempotent() throws Exception {
        List<TicketInfo> tickets = scenario.tickets().subList(0, 5);
        StringBuilder items = new StringBuilder();
        for (TicketInfo ticket : tickets) {
            if (!items.isEmpty()) {
                items.append(',');
            }
            items.append(scanJson(UUID.randomUUID(), Fixtures.payload(ticket), scenario.operatorCode(), now()));
        }
        String batch = "{\"readings\":[" + items + "]}";

        mvc.perform(post("/api/v1/readings/batch").header("Authorization", bearer(Role.SCANNER))
                        .contentType(MediaType.APPLICATION_JSON).content(batch))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(5))
                .andExpect(jsonPath("$.duplicates").value(0));

        // La respuesta se perdió (sin cobertura) y WorkManager reintenta exactamente el mismo lote.
        mvc.perform(post("/api/v1/readings/batch").header("Authorization", bearer(Role.SCANNER))
                        .contentType(MediaType.APPLICATION_JSON).content(batch))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(0))
                .andExpect(jsonPath("$.duplicates").value(5))
                .andExpect(jsonPath("$.results[0].status").value("DUPLICATE"));

        for (TicketInfo ticket : tickets) {
            assertThat(readingsFor(ticket.id())).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Dos dispositivos offline con el mismo ticket: gana uno y el otro recibe ALREADY_SCANNED")
    void twoDevicesSameTicketInOneBatch() {
        TicketInfo ticket = scenario.tickets().get(1);
        String payload = Fixtures.payload(ticket);
        List<ScanRequest> batch = List.of(
                new ScanRequest(UUID.randomUUID(), payload, scenario.operatorCode(), now(), "tablet-A"),
                new ScanRequest(UUID.randomUUID(), payload, scenario.operatorCode(), now(), "tablet-B"));

        var response = readingService.ingestBatch(batch, ScanSource.APP);

        assertThat(response.results()).extracting(ScanResult::status)
                .containsExactly(ScanStatus.ACCEPTED, ScanStatus.ALREADY_SCANNED);
        assertThat(readingsFor(ticket.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("Concurrencia real: 12 envíos simultáneos del mismo ticket generan exactamente una lectura")
    void concurrentSubmissionsCreateExactlyOneReading() throws Exception {
        TicketInfo ticket = scenario.tickets().get(2);
        String payload = Fixtures.payload(ticket);
        UUID sharedClientId = UUID.randomUUID();
        int threads = 12;
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<ScanResult>> calls = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            // la mitad son reintentos del mismo dispositivo (mismo id) y la otra mitad, otros dispositivos
            UUID clientId = i % 2 == 0 ? sharedClientId : UUID.randomUUID();
            calls.add(() -> {
                start.await();
                return readingService.ingest(
                        new ScanRequest(clientId, payload, scenario.operatorCode(), now(), "tablet"), ScanSource.APP);
            });
        }
        List<ScanStatus> statuses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<ScanResult>> futures = calls.stream().map(pool::submit).toList();
            start.countDown();
            for (Future<ScanResult> future : futures) {
                statuses.add(future.get().status());
            }
        }

        assertThat(statuses).filteredOn(s -> s == ScanStatus.ACCEPTED).hasSize(1);
        assertThat(statuses).allMatch(s -> s == ScanStatus.ACCEPTED || s == ScanStatus.DUPLICATE
                || s == ScanStatus.ALREADY_SCANNED);
        assertThat(readingsFor(ticket.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("Un ticket firmado con otra clave (falsificado) se rechaza y queda auditado")
    void forgedTicketIsRejectedAndAudited() throws Exception {
        TicketInfo ticket = scenario.tickets().get(3);
        TicketSigner attacker = new TicketSigner(randomSecret(), "k1");
        String forged = attacker.sign(ticket.id(), ticket.bundleCode(), ticket.operationCode(), ticket.quantity())
                .encode();
        UUID clientId = UUID.randomUUID();

        mvc.perform(post("/api/v1/readings").header("Authorization", bearer(Role.SCANNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scanJson(clientId, forged, scenario.operatorCode(), now())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value("INVALID_SIGNATURE"));

        assertThat(readingsFor(ticket.id())).isZero();
        assertThat(jdbc.sql("SELECT reason FROM rejected_scans WHERE client_reading_id = :c").param("c", clientId)
                .query(String.class).single()).isEqualTo("INVALID_SIGNATURE");
    }

    @Test
    @DisplayName("Alterar la cantidad impresa invalida la firma")
    void tamperedQuantityIsRejected() {
        TicketInfo ticket = scenario.tickets().get(4);
        String tampered = Fixtures.payload(ticket).replace("." + ticket.quantity() + ".", ".99.");

        ScanResult result = readingService.ingest(
                new ScanRequest(UUID.randomUUID(), tampered, scenario.operatorCode(), now(), "tablet"), ScanSource.APP);

        assertThat(result.status()).isEqualTo(ScanStatus.INVALID_SIGNATURE);
    }

    @Test
    @DisplayName("Reusar un clientReadingId para otro ticket se detecta como KEY_REUSED")
    void reusedClientIdForAnotherTicket() {
        UUID clientId = UUID.randomUUID();
        readingService.ingest(new ScanRequest(clientId, Fixtures.payload(scenario.tickets().get(5)),
                scenario.operatorCode(), now(), "tablet"), ScanSource.APP);

        ScanResult result = readingService.ingest(new ScanRequest(clientId, Fixtures.payload(scenario.tickets().get(6)),
                scenario.operatorCode(), now(), "tablet"), ScanSource.APP);

        assertThat(result.status()).isEqualTo(ScanStatus.KEY_REUSED);
        assertThat(readingsFor(scenario.tickets().get(6).id())).isZero();
    }

    @Test
    @DisplayName("Operario desconocido, hora futura o QR ajeno se rechazan sin registrar")
    void invalidInputsAreRejected() {
        String payload = Fixtures.payload(scenario.tickets().get(7));

        assertThat(readingService.ingest(new ScanRequest(UUID.randomUUID(), payload, "NO-EXISTE", now(), null),
                ScanSource.APP).status()).isEqualTo(ScanStatus.UNKNOWN_OPERATOR);
        assertThat(readingService.ingest(new ScanRequest(UUID.randomUUID(), payload, scenario.operatorCode(),
                now().plusHours(2), null), ScanSource.APP).status()).isEqualTo(ScanStatus.INVALID_TIMESTAMP);
        assertThat(readingService.ingest(new ScanRequest(UUID.randomUUID(), "https://ejemplo.com",
                scenario.operatorCode(), now(), null), ScanSource.APP).status()).isEqualTo(ScanStatus.INVALID_FORMAT);
        assertThat(readingsFor(scenario.tickets().get(7).id())).isZero();
    }

    @Test
    @DisplayName("La cabecera Idempotency-Key debe coincidir con clientReadingId")
    void idempotencyHeaderMustMatchBody() throws Exception {
        mvc.perform(post("/api/v1/readings").header("Authorization", bearer(Role.SCANNER))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scanJson(UUID.randomUUID(), Fixtures.payload(scenario.tickets().get(8)),
                                scenario.operatorCode(), now())))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("Un VIEWER no puede registrar lecturas")
    void viewerCannotScan() throws Exception {
        mvc.perform(post("/api/v1/readings").header("Authorization", bearer(Role.VIEWER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scanJson(UUID.randomUUID(), Fixtures.payload(scenario.tickets().get(9)),
                                scenario.operatorCode(), now())))
                .andExpect(status().isForbidden());
    }
}
