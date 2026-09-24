package io.github.diegofranciscog.textrack.controller;

import io.github.diegofranciscog.textrack.dto.ReportDtos.BottleneckReport;
import io.github.diegofranciscog.textrack.dto.ReportDtos.BundleTraceResponse;
import io.github.diegofranciscog.textrack.dto.ReportDtos.DashboardSnapshot;
import io.github.diegofranciscog.textrack.dto.ReportDtos.RollTraceResponse;
import io.github.diegofranciscog.textrack.service.DashboardService;
import io.github.diegofranciscog.textrack.service.PlantTime;
import io.github.diegofranciscog.textrack.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Reportes", description = "Tablero OEE, cuellos de botella y trazabilidad")
public class ReportController {

    private final DashboardService dashboard;
    private final ReportService reports;
    private final PlantTime time;

    public ReportController(DashboardService dashboard, ReportService reports, PlantTime time) {
        this.dashboard = dashboard;
        this.reports = reports;
        this.time = time;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Foto del tablero (la misma que se publica por WebSocket en /topic/dashboard)")
    public DashboardSnapshot dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return dashboard.snapshot(date != null ? date : time.today());
    }

    @GetMapping("/reports/bottlenecks/{orderId}")
    @Operation(summary = "Carga restante por operación y cuello de botella de la orden")
    public BottleneckReport bottlenecks(@PathVariable long orderId) {
        return reports.bottlenecks(orderId);
    }

    @GetMapping("/traceability/bundles/{code}")
    public BundleTraceResponse bundle(@PathVariable @Size(max = 40) String code) {
        return reports.traceBundle(code);
    }

    @GetMapping("/traceability/garments/{serial}")
    @Operation(summary = "Trazabilidad de una prenda: rollo y lote de teñido, corte, operarios por operación y defectos")
    public BundleTraceResponse garment(@PathVariable @Size(max = 44) String serial) {
        return reports.traceGarment(serial);
    }

    @GetMapping("/traceability/rolls/{code}")
    public RollTraceResponse roll(@PathVariable @Size(max = 30) String code) {
        return reports.traceRoll(code);
    }
}
