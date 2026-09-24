package io.github.diegofranciscog.textrack.controller;

import io.github.diegofranciscog.textrack.dto.ProductionDtos.CreateCutRequest;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.CreateOrderRequest;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.CreateRollRequest;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.CutResponse;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.CutSummary;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.FabricInspectionRequest;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.FabricInspectionResponse;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.OrderResponse;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.OrderSummary;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.RollResponse;
import io.github.diegofranciscog.textrack.security.CurrentUser;
import io.github.diegofranciscog.textrack.service.CutService;
import io.github.diegofranciscog.textrack.service.FabricService;
import io.github.diegofranciscog.textrack.service.ProductionOrderService;
import io.github.diegofranciscog.textrack.service.TicketPdfService;
import io.github.diegofranciscog.textrack.service.TicketPdfService.TicketPdf;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Producción", description = "Órdenes, rollos (4 puntos), cortes, bultos y tickets QR")
public class ProductionController {

    private final ProductionOrderService orders;
    private final FabricService fabric;
    private final CutService cuts;
    private final TicketPdfService pdf;

    public ProductionController(ProductionOrderService orders, FabricService fabric, CutService cuts,
                                TicketPdfService pdf) {
        this.orders = orders;
        this.fabric = fabric;
        this.cuts = cuts;
        this.pdf = pdf;
    }

    @GetMapping("/production-orders")
    public List<OrderSummary> orders() {
        return orders.findAll();
    }

    @GetMapping("/production-orders/{id}")
    public OrderResponse order(@PathVariable long id) {
        return orders.find(id);
    }

    @PostMapping("/production-orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request, @AuthenticationPrincipal Jwt jwt) {
        return orders.create(request, CurrentUser.id(jwt));
    }

    @PostMapping("/production-orders/{id}/cuts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra un corte: genera bultos por rollo y talla y un ticket firmado por bulto × operación")
    public CutResponse createCut(@PathVariable long id, @Valid @RequestBody CreateCutRequest request,
                                 @AuthenticationPrincipal Jwt jwt) {
        return cuts.create(id, request, CurrentUser.id(jwt));
    }

    @GetMapping("/cuts")
    public List<CutSummary> cuts() {
        return cuts.findAll();
    }

    @GetMapping("/cuts/{id}")
    public CutResponse cut(@PathVariable long id) {
        return cuts.find(id);
    }

    @GetMapping(value = "/cuts/{id}/tickets.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "PDF A4 imprimible con los tickets QR firmados del corte")
    public ResponseEntity<byte[]> tickets(@PathVariable long id) {
        TicketPdf file = pdf.render(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .body(file.content());
    }

    @GetMapping("/fabric-rolls")
    public List<RollResponse> rolls() {
        return fabric.findAll();
    }

    @PostMapping("/fabric-rolls")
    @ResponseStatus(HttpStatus.CREATED)
    public RollResponse receiveRoll(@Valid @RequestBody CreateRollRequest request) {
        return fabric.receive(request);
    }

    @PostMapping("/fabric-rolls/{id}/inspection")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Inspección de 4 puntos (ASTM D5430): aprueba o rechaza el rollo")
    public FabricInspectionResponse inspect(@PathVariable long id, @Valid @RequestBody FabricInspectionRequest request,
                                            @AuthenticationPrincipal Jwt jwt) {
        return fabric.inspect(id, request, CurrentUser.id(jwt));
    }
}
