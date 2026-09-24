package io.github.diegofranciscog.textrack.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.diegofranciscog.textrack.domain.Role;
import io.github.diegofranciscog.textrack.domain.ScanSource;
import io.github.diegofranciscog.textrack.domain.TicketInfo;
import io.github.diegofranciscog.textrack.dto.ReadingDtos.ScanRequest;
import io.github.diegofranciscog.textrack.repository.CutRepository;
import io.github.diegofranciscog.textrack.service.ReadingService;
import io.github.diegofranciscog.textrack.support.Fixtures;
import io.github.diegofranciscog.textrack.support.Fixtures.Scenario;
import io.github.diegofranciscog.textrack.support.IntegrationTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

@DisplayName("Tablero OEE, cuellos de botella y trazabilidad rollo → bulto → prenda")
class ReportsIT extends IntegrationTest {

    @Autowired
    private CutRepository cuts;
    @Autowired
    private ReadingService readings;

    private Scenario scenario;

    @BeforeEach
    void setUp() throws Exception {
        scenario = new Fixtures(mvc, bearer(Role.ADMIN), jdbc, cuts).scenario();
        mvc.perform(post("/api/v1/attendances").header("Authorization", bearer(Role.SUPERVISOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operatorId\":%d,\"checkIn\":\"%s\"}".formatted(scenario.operatorId(),
                                OffsetDateTime.now(ZoneOffset.UTC).minusHours(2))))
                .andExpect(status().isCreated());
        // Se completan todas las operaciones del bulto 1 y solo OP10 del bulto 2.
        List<TicketInfo> tickets = scenario.tickets();
        for (TicketInfo ticket : List.of(tickets.get(0), tickets.get(1), tickets.get(2), tickets.get(3))) {
            readings.ingest(new ScanRequest(UUID.randomUUID(), Fixtures.payload(ticket), scenario.operatorCode(),
                    OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5), "tablet"), ScanSource.APP);
        }
    }

    @Test
    void dashboardReportsLineKpis() throws Exception {
        String body = mvc.perform(get("/api/v1/dashboard").header("Authorization", bearer(Role.VIEWER)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Integer> pieces = JsonPath.read(body, "$.lines[?(@.lineId == " + scenario.lineId() + ")].kpi.pieces");
        assertThat(pieces).containsExactly(20);
        List<Double> availability = JsonPath.read(body,
                "$.lines[?(@.lineId == " + scenario.lineId() + ")].kpi.availability");
        assertThat(availability.getFirst()).isEqualTo(1.0);
        List<String> recent = JsonPath.read(body, "$.recentReadings[*].operatorCode");
        assertThat(recent).contains(scenario.operatorCode());
    }

    @Test
    void bottleneckReportFindsTheOperationWithMostRemainingWork() throws Exception {
        mvc.perform(get("/api/v1/reports/bottlenecks/" + scenario.orderId()).header("Authorization", bearer(Role.VIEWER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPieces").value(20))
                .andExpect(jsonPath("$.operations.length()").value(3))
                .andExpect(jsonPath("$.operations[0].completedPieces").value(10))
                .andExpect(jsonPath("$.operations[1].wipBefore").value(5))
                .andExpect(jsonPath("$.bottleneckCode").exists());
    }

    @Test
    void garmentIsTracedBackToRollDyeLotAndOperators() throws Exception {
        String bundle = scenario.tickets().getFirst().bundleCode();
        mvc.perform(get("/api/v1/traceability/garments/" + bundle + "-03").header("Authorization", bearer(Role.VIEWER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.garmentSerial").value(bundle + "-03"))
                .andExpect(jsonPath("$.roll.dyeLot").value("LT-01"))
                .andExpect(jsonPath("$.operations.length()").value(3))
                .andExpect(jsonPath("$.operations[0].operatorCode").value(scenario.operatorCode()));
        mvc.perform(get("/api/v1/traceability/garments/" + bundle + "-99").header("Authorization", bearer(Role.VIEWER)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/traceability/garments/no-valido").header("Authorization", bearer(Role.VIEWER)))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void rollShowsEveryBundleCutFromIt() throws Exception {
        String rollCode = jdbc.sql("SELECT code FROM fabric_rolls WHERE id = :id").param("id", scenario.rollId())
                .query(String.class).single();
        mvc.perform(get("/api/v1/traceability/rolls/" + rollCode).header("Authorization", bearer(Role.VIEWER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bundles").value(4))
                .andExpect(jsonPath("$.pieces").value(20));
    }
}
