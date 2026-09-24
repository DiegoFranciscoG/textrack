package io.github.diegofranciscog.textrack.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.diegofranciscog.textrack.domain.Role;
import io.github.diegofranciscog.textrack.domain.ScanSource;
import io.github.diegofranciscog.textrack.domain.ScanStatus;
import io.github.diegofranciscog.textrack.domain.TicketInfo;
import io.github.diegofranciscog.textrack.dto.ReadingDtos.ScanRequest;
import io.github.diegofranciscog.textrack.repository.CutRepository;
import io.github.diegofranciscog.textrack.service.ReadingService;
import io.github.diegofranciscog.textrack.support.Fixtures;
import io.github.diegofranciscog.textrack.support.Fixtures.Scenario;
import io.github.diegofranciscog.textrack.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

@DisplayName("Destajo diario con piso SBU y recargos, calculado desde lecturas reales")
class PayrollIT extends IntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("America/Guayaquil");

    @Autowired
    private CutRepository cuts;
    @Autowired
    private ReadingService readings;

    private Scenario scenario;
    private LocalDate workDay;

    @BeforeEach
    void setUp() throws Exception {
        scenario = new Fixtures(mvc, bearer(Role.ADMIN), jdbc, cuts).scenario();
        // Un martes a jueves reciente y distinto de hoy: resultado determinista sin depender del reloj.
        LocalDate candidate = LocalDate.now(ZONE).minusDays(2);
        while (candidate.getDayOfWeek().getValue() < DayOfWeek.TUESDAY.getValue()
                || candidate.getDayOfWeek().getValue() > DayOfWeek.THURSDAY.getValue()) {
            candidate = candidate.minusDays(1);
        }
        workDay = candidate;
    }

    private OffsetDateTime at(int hour, int minute) {
        return workDay.atTime(LocalTime.of(hour, minute)).atZone(ZONE).toOffsetDateTime();
    }

    private void scan(TicketInfo ticket, OffsetDateTime when) {
        assertThat(readings.ingest(new ScanRequest(UUID.randomUUID(), Fixtures.payload(ticket), scenario.operatorCode(),
                when, "tablet"), ScanSource.APP).status()).isEqualTo(ScanStatus.ACCEPTED);
    }

    @Test
    void dailyPayAppliesSupplementaryPremiumAndSbuTopUp() throws Exception {
        String attendance = mvc.perform(post("/api/v1/attendances").header("Authorization", bearer(Role.SUPERVISOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operatorId\":%d,\"checkIn\":\"%s\",\"breakMinutes\":30}"
                                .formatted(scenario.operatorId(), at(7, 0))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long attendanceId = ((Number) JsonPath.read(attendance, "$.id")).longValue();
        mvc.perform(put("/api/v1/attendances/" + attendanceId + "/check-out").header("Authorization", bearer(Role.SUPERVISOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"checkOut\":\"%s\"}".formatted(at(17, 0))))
                .andExpect(status().isOk());

        List<TicketInfo> tickets = scenario.tickets();
        for (int i = 0; i < 11; i++) {
            scan(tickets.get(i), at(10, 0));
        }
        scan(tickets.get(11), at(16, 30)); // después de 07:00 + 8 h + 30 min → suplementaria (+50 %)

        String body = mvc.perform(get("/api/v1/payroll/daily").param("date", workDay.toString())
                        .header("Authorization", bearer(Role.VIEWER)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> mine = JsonPath.read(body,
                "$.operators[?(@.operatorCode == '" + scenario.operatorCode() + "')]");
        assertThat(mine).hasSize(1);
        Map<String, Object> pay = mine.getFirst();
        assertThat(pay.get("pieces")).isEqualTo(60);
        assertThat(decimal(pay.get("ordinaryPay"))).isEqualByComparingTo("2.20");
        assertThat(decimal(pay.get("extraPay"))).isEqualByComparingTo("0.20");
        assertThat(decimal(pay.get("premiumPay"))).isEqualByComparingTo("0.10");
        assertThat(decimal(pay.get("floor"))).isEqualByComparingTo("16.07");
        assertThat(pay.get("belowFloor")).isEqualTo(true);
        assertThat(decimal(pay.get("topUp"))).isEqualByComparingTo("13.87");
        assertThat(decimal(pay.get("totalPay"))).isEqualByComparingTo("16.37");
        assertThat(decimal(pay.get("earnedMinutes"))).isEqualByComparingTo("40.00");
        assertThat(decimal(pay.get("attendedMinutes"))).isEqualByComparingTo("570");
        assertThat(decimal(JsonPath.read(body, "$.sbu"))).isEqualByComparingTo("482.00");

        String detail = mvc.perform(get("/api/v1/payroll/daily/" + scenario.operatorId()).param("date", workDay.toString())
                        .header("Authorization", bearer(Role.SUPERVISOR)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> premiums = JsonPath.read(detail, "$.lines[*].premium");
        assertThat(premiums).containsOnly("NONE", "SUPPLEMENTARY").contains("SUPPLEMENTARY");

        LocalDate monday = workDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        String weekly = mvc.perform(get("/api/v1/payroll/weekly/" + scenario.operatorId())
                        .param("weekStart", monday.toString())
                        .header("Authorization", bearer(Role.SUPERVISOR)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(decimal(JsonPath.read(weekly, "$.weeklyRest.amount"))).isEqualByComparingTo("32.14");
        assertThat(decimal(JsonPath.read(weekly, "$.weekTotal"))).isEqualByComparingTo("48.51");
    }

    @Test
    void weekMustStartOnMonday() throws Exception {
        LocalDate tuesday = workDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.TUESDAY));
        mvc.perform(get("/api/v1/payroll/weekly/" + scenario.operatorId()).param("weekStart", tuesday.toString())
                        .header("Authorization", bearer(Role.SUPERVISOR)))
                .andExpect(status().isUnprocessableContent());
    }

    private static BigDecimal decimal(Object value) {
        return new BigDecimal(value.toString());
    }
}
