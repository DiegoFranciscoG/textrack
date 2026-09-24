package io.github.diegofranciscog.textrack.controller;

import io.github.diegofranciscog.textrack.dto.PlantDtos.AttendanceResponse;
import io.github.diegofranciscog.textrack.dto.PlantDtos.CheckInRequest;
import io.github.diegofranciscog.textrack.dto.PlantDtos.CheckOutRequest;
import io.github.diegofranciscog.textrack.dto.PlantDtos.CloseStopRequest;
import io.github.diegofranciscog.textrack.dto.PlantDtos.CreateOperatorRequest;
import io.github.diegofranciscog.textrack.dto.PlantDtos.CreateStopRequest;
import io.github.diegofranciscog.textrack.dto.PlantDtos.LineResponse;
import io.github.diegofranciscog.textrack.dto.PlantDtos.MachineResponse;
import io.github.diegofranciscog.textrack.dto.PlantDtos.OperatorResponse;
import io.github.diegofranciscog.textrack.dto.PlantDtos.StopResponse;
import io.github.diegofranciscog.textrack.security.CurrentUser;
import io.github.diegofranciscog.textrack.service.PlantService;
import io.github.diegofranciscog.textrack.service.PlantTime;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Planta", description = "Líneas, operarios, máquinas, asistencia y paros")
public class PlantController {

    private final PlantService service;
    private final PlantTime time;

    public PlantController(PlantService service, PlantTime time) {
        this.service = service;
        this.time = time;
    }

    @GetMapping("/lines")
    public List<LineResponse> lines() {
        return service.lines();
    }

    @GetMapping("/operators")
    public List<OperatorResponse> operators(@RequestParam(defaultValue = "true") boolean onlyActive) {
        return service.operators(onlyActive);
    }

    @PostMapping("/operators")
    @ResponseStatus(HttpStatus.CREATED)
    public OperatorResponse createOperator(@Valid @RequestBody CreateOperatorRequest request) {
        return service.createOperator(request);
    }

    @GetMapping("/machines")
    public List<MachineResponse> machines() {
        return service.machines();
    }

    @GetMapping("/attendances")
    public List<AttendanceResponse> attendances(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.attendances(date != null ? date : time.today());
    }

    @PostMapping("/attendances")
    @ResponseStatus(HttpStatus.CREATED)
    public AttendanceResponse checkIn(@Valid @RequestBody CheckInRequest request) {
        return service.checkIn(request);
    }

    @PutMapping("/attendances/{id}/check-out")
    public AttendanceResponse checkOut(@PathVariable long id, @RequestBody(required = false) CheckOutRequest request) {
        return service.checkOut(id, request == null ? null : request.checkOut());
    }

    @GetMapping("/machine-stops")
    public List<StopResponse> stops(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.stops(date != null ? date : time.today());
    }

    @PostMapping("/machine-stops")
    @ResponseStatus(HttpStatus.CREATED)
    public StopResponse reportStop(@Valid @RequestBody CreateStopRequest request, @AuthenticationPrincipal Jwt jwt) {
        return service.reportStop(request, CurrentUser.id(jwt));
    }

    @PutMapping("/machine-stops/{id}/close")
    public StopResponse closeStop(@PathVariable long id, @RequestBody(required = false) CloseStopRequest request) {
        return service.closeStop(id, request == null ? null : request.endedAt());
    }
}
