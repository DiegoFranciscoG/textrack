package io.github.diegofranciscog.textrack.controller;

import io.github.diegofranciscog.textrack.dto.EngineeringDtos.CreateOperationRequest;
import io.github.diegofranciscog.textrack.dto.EngineeringDtos.CreateRateRequest;
import io.github.diegofranciscog.textrack.dto.EngineeringDtos.CreateStyleRequest;
import io.github.diegofranciscog.textrack.dto.EngineeringDtos.OperationResponse;
import io.github.diegofranciscog.textrack.dto.EngineeringDtos.StyleResponse;
import io.github.diegofranciscog.textrack.service.EngineeringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Ingeniería", description = "Estilos, operaciones con SAM y tarifas a destajo")
public class EngineeringController {

    private final EngineeringService service;

    public EngineeringController(EngineeringService service) {
        this.service = service;
    }

    @GetMapping("/styles")
    public List<StyleResponse> styles() {
        return service.styles();
    }

    @GetMapping("/styles/{id}")
    public StyleResponse style(@PathVariable long id) {
        return service.style(id);
    }

    @PostMapping("/styles")
    @ResponseStatus(HttpStatus.CREATED)
    public StyleResponse createStyle(@Valid @RequestBody CreateStyleRequest request) {
        return service.createStyle(request);
    }

    @PostMapping("/styles/{id}/operations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agrega una operación a la ruta del estilo (SAM y tarifa opcional)")
    public OperationResponse addOperation(@PathVariable long id, @Valid @RequestBody CreateOperationRequest request) {
        return service.addOperation(id, request);
    }

    @PostMapping("/operations/{id}/rates")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Programa una nueva tarifa a destajo; cierra la vigente en esa fecha")
    public OperationResponse changeRate(@PathVariable long id, @Valid @RequestBody CreateRateRequest request) {
        return service.changeRate(id, request);
    }
}
