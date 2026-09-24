package io.github.diegofranciscog.textrack.controller;

import io.github.diegofranciscog.textrack.domain.ScanSource;
import io.github.diegofranciscog.textrack.dto.ReadingDtos.BatchScanRequest;
import io.github.diegofranciscog.textrack.dto.ReadingDtos.BatchScanResponse;
import io.github.diegofranciscog.textrack.dto.ReadingDtos.RecentReadingResponse;
import io.github.diegofranciscog.textrack.dto.ReadingDtos.ScanRequest;
import io.github.diegofranciscog.textrack.dto.ReadingDtos.ScanResult;
import io.github.diegofranciscog.textrack.exception.BusinessRuleException;
import io.github.diegofranciscog.textrack.service.ReadingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/readings")
@Tag(name = "Lecturas", description = "Registro idempotente de tickets escaneados (app offline y web)")
public class ReadingController {

    private final ReadingService service;

    public ReadingController(ReadingService service) {
        this.service = service;
    }

    /**
     * 201 lectura nueva · 200 reintento idempotente · 409 ticket ya registrado · 422 ticket inválido o falsificado.
     */
    @PostMapping
    @Operation(summary = "Registra una lectura. Idempotente por clientReadingId (o cabecera Idempotency-Key)")
    public ResponseEntity<ScanResult> scan(@Valid @RequestBody ScanRequest request,
                                           @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                           Authentication authentication) {
        if (key != null && !key.equalsIgnoreCase(request.clientReadingId().toString())) {
            throw new BusinessRuleException("Idempotency-Key debe coincidir con clientReadingId");
        }
        ScanResult result = service.ingest(request, sourceOf(authentication));
        HttpStatus status = switch (result.status()) {
            case ACCEPTED -> HttpStatus.CREATED;
            case DUPLICATE -> HttpStatus.OK;
            case ALREADY_SCANNED -> HttpStatus.CONFLICT;
            default -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
        return ResponseEntity.status(status).body(result);
    }

    @PostMapping("/batch")
    @Operation(summary = "Sincroniza un lote de lecturas offline; cada una trae su propio resultado")
    public BatchScanResponse batch(@Valid @RequestBody BatchScanRequest request, Authentication authentication) {
        return service.ingestBatch(request.readings(), sourceOf(authentication));
    }

    @GetMapping("/recent")
    public List<RecentReadingResponse> recent(@RequestParam(defaultValue = "20") int limit) {
        return service.recent(limit);
    }

    /** La app móvil usa el rol SCANNER; supervisores registran desde la web. */
    private static ScanSource sourceOf(Authentication authentication) {
        boolean scanner = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_SCANNER".equals(authority.getAuthority()));
        return scanner ? ScanSource.APP : ScanSource.WEB;
    }
}
