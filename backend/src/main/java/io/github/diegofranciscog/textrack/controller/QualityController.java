package io.github.diegofranciscog.textrack.controller;

import io.github.diegofranciscog.textrack.domain.InspectionLevel;
import io.github.diegofranciscog.textrack.dto.QualityDtos.AqlInspectionResponse;
import io.github.diegofranciscog.textrack.dto.QualityDtos.AqlPlanResponse;
import io.github.diegofranciscog.textrack.dto.QualityDtos.CreateAqlInspectionRequest;
import io.github.diegofranciscog.textrack.dto.QualityDtos.DefectTypeResponse;
import io.github.diegofranciscog.textrack.security.CurrentUser;
import io.github.diegofranciscog.textrack.service.QualityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quality")
@Tag(name = "Calidad", description = "Muestreo AQL (ISO 2859-1) e inspecciones de lote")
public class QualityController {

    private final QualityService service;

    public QualityController(QualityService service) {
        this.service = service;
    }

    @GetMapping("/aql-plan")
    @Operation(summary = "Plan de muestreo simple normal: letra código, n, Ac y Re")
    public AqlPlanResponse plan(@RequestParam @Min(2) @Max(10_000_000) int lotSize,
                                @RequestParam(defaultValue = "II") InspectionLevel level,
                                @RequestParam BigDecimal aql) {
        return service.plan(lotSize, level, aql);
    }

    @GetMapping("/defect-types")
    public List<DefectTypeResponse> defectTypes() {
        return service.defectTypes();
    }

    @GetMapping("/aql-inspections")
    public List<AqlInspectionResponse> inspections() {
        return service.recent();
    }

    @PostMapping("/aql-inspections")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra una inspección AQL; el resultado lo decide el servidor con la Tabla 2-A")
    public AqlInspectionResponse inspect(@Valid @RequestBody CreateAqlInspectionRequest request,
                                         @AuthenticationPrincipal Jwt jwt) {
        return service.inspect(request, CurrentUser.id(jwt));
    }
}
