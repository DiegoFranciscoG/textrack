package io.github.diegofranciscog.textrack.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.diegofranciscog.textrack.domain.Role;
import io.github.diegofranciscog.textrack.domain.TicketInfo;
import io.github.diegofranciscog.textrack.repository.CutRepository;
import io.github.diegofranciscog.textrack.service.calc.TicketPayload;
import io.github.diegofranciscog.textrack.service.calc.TicketSigner;
import io.github.diegofranciscog.textrack.support.Fixtures;
import io.github.diegofranciscog.textrack.support.IntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

@DisplayName("OP → corte → bultos → tickets QR firmados → PDF")
class ProductionFlowIT extends IntegrationTest {

    @Autowired
    private CutRepository cuts;
    @Autowired
    private TicketSigner signer;

    private Fixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new Fixtures(mvc, bearer(Role.ADMIN), jdbc, cuts);
    }

    @Test
    void cutGeneratesBundlesAndOneSignedTicketPerBundleAndOperation() throws Exception {
        long styleId = fixtures.styleWithRoute();
        long rollId = fixtures.roll("Negro", true);
        long orderId = fixtures.order(styleId, "Negro", 10, 10);

        long cutId = fixtures.cut(orderId, "Negro", rollId);

        mvc.perform(get("/api/v1/cuts/" + cutId).header("Authorization", bearer(Role.VIEWER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pieces").value(20))
                .andExpect(jsonPath("$.bundles.length()").value(4))
                .andExpect(jsonPath("$.tickets").value(12))
                .andExpect(jsonPath("$.bundles[0].dyeLot").value("LT-01"));
        List<TicketInfo> tickets = fixtures.tickets(cutId);
        assertThat(tickets).hasSize(12);
        assertThat(tickets).allSatisfy(ticket -> assertThat(signer.verify(new TicketPayload(ticket.keyId(),
                ticket.id(), ticket.bundleCode(), ticket.operationCode(), ticket.quantity(), ticket.signature())))
                .isTrue());

        BigDecimal remaining = jdbc.sql("SELECT remaining_m FROM fabric_rolls WHERE id = :id").param("id", rollId)
                .query(BigDecimal.class).single();
        assertThat(remaining).isEqualByComparingTo("87.50");

        mvc.perform(get("/api/v1/production-orders/" + orderId).header("Authorization", bearer(Role.VIEWER)))
                .andExpect(jsonPath("$.status").value("CUTTING"))
                .andExpect(jsonPath("$.cutQuantity").value(20));
    }

    @Test
    void ticketsPdfIsPrintable() throws Exception {
        long styleId = fixtures.styleWithRoute();
        long rollId = fixtures.roll("Gris", true);
        long cutId = fixtures.cut(fixtures.order(styleId, "Gris", 10, 10), "Gris", rollId);

        byte[] pdf = mvc.perform(get("/api/v1/cuts/" + cutId + "/tickets.pdf").header("Authorization", bearer(Role.PLANNER)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("tickets-CT-")))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(2_000);
    }

    @Test
    void rejectedRollCannotBeCut() throws Exception {
        long styleId = fixtures.styleWithRoute();
        long rejected = fixtures.roll("Rojo", false);
        long orderId = fixtures.order(styleId, "Rojo", 10, 10);

        mvc.perform(get("/api/v1/fabric-rolls").header("Authorization", bearer(Role.VIEWER)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/production-orders/" + orderId + "/cuts").header("Authorization", bearer(Role.PLANNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"color":"Rojo","maxBundleSize":5,"sizeRatios":[{"sizeCode":"S","piecesPerPly":1}],
                                 "rolls":[{"rollId":%d,"plies":5,"metersUsed":5}]}""".formatted(rejected)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("4 puntos")));
    }

    @Test
    void cutCannotExceedOrderedQuantityPlusTolerance() throws Exception {
        long styleId = fixtures.styleWithRoute();
        long rollId = fixtures.roll("Verde", true);
        long orderId = fixtures.order(styleId, "Verde", 10, 10);

        mvc.perform(post("/api/v1/production-orders/" + orderId + "/cuts").header("Authorization", bearer(Role.PLANNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"color":"Verde","maxBundleSize":5,"sizeRatios":[{"sizeCode":"S","piecesPerPly":2}],
                                 "rolls":[{"rollId":%d,"plies":10,"metersUsed":10}]}""".formatted(rollId)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("excede")));
    }

    @Test
    void rollOfAnotherColorCannotBeUsed() throws Exception {
        long styleId = fixtures.styleWithRoute();
        long blue = fixtures.roll("Celeste", true);
        long orderId = fixtures.order(styleId, "Blanco", 10, 10);

        mvc.perform(post("/api/v1/production-orders/" + orderId + "/cuts").header("Authorization", bearer(Role.PLANNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"color":"Blanco","maxBundleSize":5,"sizeRatios":[{"sizeCode":"S","piecesPerPly":1}],
                                 "rolls":[{"rollId":%d,"plies":5,"metersUsed":5}]}""".formatted(blue)))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void orderRequiresValidSizesAndNoDuplicates() throws Exception {
        long styleId = fixtures.styleWithRoute();
        mvc.perform(post("/api/v1/production-orders").header("Authorization", bearer(Role.PLANNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"styleId":%d,"customer":"Cliente","dueDate":"2099-01-01",
                                 "lines":[{"sizeCode":"S","color":"Azul","quantity":5},
                                          {"sizeCode":"S","color":"azul","quantity":5}]}""".formatted(styleId)))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/v1/production-orders").header("Authorization", bearer(Role.PLANNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"styleId":%d,"customer":"Cliente","dueDate":"2099-01-01",
                                 "lines":[{"sizeCode":"XXXL","color":"Azul","quantity":5}]}""".formatted(styleId)))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void rateChangeClosesPreviousRate() throws Exception {
        long styleId = fixtures.styleWithRoute();
        long operationId = jdbc.sql("SELECT id FROM operations WHERE style_id = :s AND code = 'OP20'")
                .param("s", styleId).query(Long.class).single();

        mvc.perform(post("/api/v1/operations/" + operationId + "/rates").header("Authorization", bearer(Role.PLANNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rateUsd\":0.0500,\"validFrom\":\"2026-03-01\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/operations/" + operationId + "/rates").header("Authorization", bearer(Role.PLANNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rateUsd\":0.0600,\"validFrom\":\"2026-02-01\"}"))
                .andExpect(status().isUnprocessableContent());

        assertThat(jdbc.sql("SELECT COUNT(*) FROM piece_rates WHERE operation_id = :o").param("o", operationId)
                .query(Integer.class).single()).isEqualTo(2);
    }
}
