package io.github.diegofranciscog.textrack.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.diegofranciscog.textrack.domain.Role;
import io.github.diegofranciscog.textrack.repository.CutRepository;
import io.github.diegofranciscog.textrack.support.Fixtures;
import io.github.diegofranciscog.textrack.support.Fixtures.Scenario;
import io.github.diegofranciscog.textrack.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

@DisplayName("Inspección AQL (ISO 2859-1) de extremo a extremo")
class QualityIT extends IntegrationTest {

    @Autowired
    private CutRepository cuts;

    private Scenario scenario;

    @BeforeEach
    void setUp() throws Exception {
        scenario = new Fixtures(mvc, bearer(Role.ADMIN), jdbc, cuts).scenario();
    }

    @Test
    void planEndpointFollowsTableIIA() throws Exception {
        mvc.perform(get("/api/v1/quality/aql-plan").param("lotSize", "1000").param("level", "II").param("aql", "2.5")
                        .header("Authorization", bearer(Role.QUALITY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planLetter").value("J"))
                .andExpect(jsonPath("$.sampleSize").value(80))
                .andExpect(jsonPath("$.acceptNumber").value(5))
                .andExpect(jsonPath("$.rejectNumber").value(6));
        mvc.perform(get("/api/v1/quality/aql-plan").param("lotSize", "1000").param("aql", "10")
                        .header("Authorization", bearer(Role.QUALITY)))
                .andExpect(status().isUnprocessableContent());
    }

    /** Inspección de un lote de 1000 (nivel II): mayores con AQL 2,5 (Ac 5/Re 6) y menores con 4,0 (Ac 7/Re 8). */
    private String inspection(int major, int minor, int critical, int defectiveUnits) {
        String bundle = scenario.tickets().getFirst().bundleCode();
        StringBuilder defects = new StringBuilder("""
                {"defectTypeCode":"OPEN_SEAM","quantity":%d,"bundleCode":"%s","operationCode":"OP20"},
                {"defectTypeCode":"LOOSE_THREAD","quantity":%d}""".formatted(major, bundle, minor));
        if (critical > 0) {
            defects.append(",{\"defectTypeCode\":\"BROKEN_NEEDLE\",\"quantity\":%d}".formatted(critical));
        }
        return """
                {"productionOrderId":%d,"lineId":%d,"level":"II","lotSize":1000,"aqlMajor":2.5,"aqlMinor":4.0,
                 "defectiveUnits":%d,"defects":[%s]}"""
                .formatted(scenario.orderId(), scenario.lineId(), defectiveUnits, defects);
    }

    @Test
    void lotWithinAcceptanceNumbersIsAccepted() throws Exception {
        mvc.perform(post("/api/v1/quality/aql-inspections").header("Authorization", bearer(Role.QUALITY))
                        .contentType(MediaType.APPLICATION_JSON).content(inspection(5, 7, 0, 10)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sampleSize").value(80))
                .andExpect(jsonPath("$.majorFound").value(5))
                .andExpect(jsonPath("$.minorFound").value(7))
                .andExpect(jsonPath("$.result").value("ACCEPTED"));
    }

    @Test
    void majorDefectsAtRejectionNumberRejectTheLot() throws Exception {
        mvc.perform(post("/api/v1/quality/aql-inspections").header("Authorization", bearer(Role.QUALITY))
                        .contentType(MediaType.APPLICATION_JSON).content(inspection(6, 1, 0, 7)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result").value("REJECTED"));
    }

    @Test
    void anyCriticalDefectRejectsTheLot() throws Exception {
        mvc.perform(post("/api/v1/quality/aql-inspections").header("Authorization", bearer(Role.QUALITY))
                        .contentType(MediaType.APPLICATION_JSON).content(inspection(1, 1, 1, 3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.criticalFound").value(1))
                .andExpect(jsonPath("$.result").value("REJECTED"));
    }

    @Test
    void defectiveUnitsMustBeConsistent() throws Exception {
        mvc.perform(post("/api/v1/quality/aql-inspections").header("Authorization", bearer(Role.QUALITY))
                        .contentType(MediaType.APPLICATION_JSON).content(inspection(1, 1, 0, 0)))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/v1/quality/aql-inspections").header("Authorization", bearer(Role.QUALITY))
                        .contentType(MediaType.APPLICATION_JSON).content(inspection(1, 1, 0, 81)))
                .andExpect(status().isUnprocessableContent());
    }
}
