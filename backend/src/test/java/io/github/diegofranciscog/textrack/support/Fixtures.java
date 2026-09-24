package io.github.diegofranciscog.textrack.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.diegofranciscog.textrack.domain.TicketInfo;
import io.github.diegofranciscog.textrack.repository.CutRepository;
import io.github.diegofranciscog.textrack.service.calc.TicketPayload;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/** Crea datos de prueba a través de la API (así los tests también ejercitan validaciones y seguridad). */
public final class Fixtures {

    private static final AtomicInteger SEQ = new AtomicInteger();

    private final MockMvc mvc;
    private final String admin;
    private final JdbcClient jdbc;
    private final CutRepository cuts;

    public Fixtures(MockMvc mvc, String adminBearer, JdbcClient jdbc, CutRepository cuts) {
        this.mvc = mvc;
        this.admin = adminBearer;
        this.jdbc = jdbc;
        this.cuts = cuts;
    }

    public static String unique(String prefix) {
        return prefix + "-" + System.nanoTime() % 100_000 + SEQ.incrementAndGet();
    }

    public String postJson(String url, String body) throws Exception {
        return mvc.perform(post(url).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    /** Estilo con 3 operaciones (SAM 0,50 / 1,00 / 0,50) y tarifa 0,04 USD por pieza vigente desde 2026-01-01. */
    public long styleWithRoute() throws Exception {
        String code = unique("ST");
        long styleId = ((Number) JsonPath.read(postJson("/api/v1/styles", """
                {"code":"%s","name":"Camiseta prueba","garmentType":"TSHIRT"}""".formatted(code)), "$.id")).longValue();
        String[][] ops = {{"10", "OP10", "0.5000", "OVERLOCK"}, {"20", "OP20", "1.0000", "LOCKSTITCH"},
            {"30", "OP30", "0.5000", "COVERSTITCH"}};
        for (String[] op : ops) {
            postJson("/api/v1/styles/" + styleId + "/operations", """
                    {"sequence":%s,"code":"%s","name":"Operación %s","machineType":"%s","samMinutes":%s,
                     "rateUsd":0.0400,"rateValidFrom":"2026-01-01"}""".formatted(op[0], op[1], op[1], op[3], op[2]));
        }
        return styleId;
    }

    public long line() {
        String code = unique("L");
        return jdbc.sql("INSERT INTO production_lines (code, name) VALUES (:c, 'Línea de prueba') RETURNING id")
                .param("c", code).query(Long.class).single();
    }

    public String operator(long lineId) throws Exception {
        String code = unique("OPR").toUpperCase();
        postJson("/api/v1/operators", """
                {"code":"%s","fullName":"Operaria de prueba","lineId":%d}""".formatted(code, lineId));
        return code;
    }

    public long operatorId(String code) {
        return jdbc.sql("SELECT id FROM operators WHERE code = :c").param("c", code).query(Long.class).single();
    }

    public long roll(String color, boolean approve) throws Exception {
        String code = unique("R");
        long rollId = ((Number) JsonPath.read(postJson("/api/v1/fabric-rolls", """
                {"code":"%s","supplier":"Tejidos del Norte","dyeLot":"LT-01","color":"%s","lengthM":100,"widthCm":150}"""
                .formatted(code, color)), "$.id")).longValue();
        // Rollo rechazado: 20 defectos largos en yardas distintas = 80 puntos en 20 m (≈ 223 puntos/100 yd²).
        String defects = approve ? "[]"
                : "[" + String.join(",", IntStream.range(0, 20)
                        .mapToObj(i -> String.format(Locale.ROOT,
                                "{\"positionM\":%.2f,\"lengthMm\":400,\"hole\":false}", i * 0.95))
                        .toList()) + "]";
        postJson("/api/v1/fabric-rolls/" + rollId + "/inspection", """
                {"inspectedLengthM":%s,"defects":%s}""".formatted(approve ? "100" : "20", defects));
        return rollId;
    }

    public long order(long styleId, String color, int small, int medium) throws Exception {
        return ((Number) JsonPath.read(postJson("/api/v1/production-orders", """
                {"styleId":%d,"customer":"Cliente de prueba","dueDate":"%s",
                 "lines":[{"sizeCode":"S","color":"%s","quantity":%d},{"sizeCode":"M","color":"%s","quantity":%d}]}"""
                .formatted(styleId, LocalDate.now().plusDays(20), color, small, color, medium)), "$.id")).longValue();
    }

    /** Corte de 10 capas: S×1 y M×1 por capa, bultos de hasta 5 piezas → 4 bultos por rollo. */
    public long cut(long orderId, String color, long rollId) throws Exception {
        return ((Number) JsonPath.read(postJson("/api/v1/production-orders/" + orderId + "/cuts", """
                {"color":"%s","maxBundleSize":5,"sizeRatios":[{"sizeCode":"S","piecesPerPly":1},
                 {"sizeCode":"M","piecesPerPly":1}],"rolls":[{"rollId":%d,"plies":10,"metersUsed":12.5}]}"""
                .formatted(color, rollId)), "$.id")).longValue();
    }

    public List<TicketInfo> tickets(long cutId) {
        return cuts.findTicketsByCut(cutId);
    }

    public static String payload(TicketInfo ticket) {
        return new TicketPayload(ticket.keyId(), ticket.id(), ticket.bundleCode(), ticket.operationCode(),
                ticket.quantity(), ticket.signature()).encode();
    }

    /** Escenario completo listo para escanear: estilo, línea, operario, rollo aprobado, orden y corte. */
    public Scenario scenario() throws Exception {
        long styleId = styleWithRoute();
        long lineId = line();
        String operatorCode = operator(lineId);
        String color = "Azul";
        long rollId = roll(color, true);
        long orderId = order(styleId, color, 10, 10);
        long cutId = cut(orderId, color, rollId);
        return new Scenario(styleId, lineId, operatorCode, operatorId(operatorCode), rollId, orderId, cutId,
                tickets(cutId));
    }

    public record Scenario(long styleId, long lineId, String operatorCode, long operatorId, long rollId, long orderId,
                           long cutId, List<TicketInfo> tickets) {
    }
}
