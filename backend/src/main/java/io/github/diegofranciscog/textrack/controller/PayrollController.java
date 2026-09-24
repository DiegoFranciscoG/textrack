package io.github.diegofranciscog.textrack.controller;

import io.github.diegofranciscog.textrack.dto.PayrollDtos.DailyPayrollResponse;
import io.github.diegofranciscog.textrack.dto.PayrollDtos.OperatorDayDetail;
import io.github.diegofranciscog.textrack.dto.PayrollDtos.WeeklyPayResponse;
import io.github.diegofranciscog.textrack.service.PayrollService;
import io.github.diegofranciscog.textrack.service.PlantTime;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payroll")
@Tag(name = "Destajo", description = "Pago a destajo con recargos del Código del Trabajo y alerta de piso SBU")
public class PayrollController {

    private final PayrollService service;
    private final PlantTime time;

    public PayrollController(PayrollService service, PlantTime time) {
        this.service = service;
        this.time = time;
    }

    @GetMapping("/daily")
    @Operation(summary = "Destajo del día por operario, con piso SBU prorrateado y complemento")
    public DailyPayrollResponse daily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.daily(date != null ? date : time.today());
    }

    @GetMapping("/daily/{operatorId}")
    @Operation(summary = "Detalle por lectura: tarifa, recargo aplicado y monto")
    public OperatorDayDetail detail(@PathVariable long operatorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.detail(operatorId, date != null ? date : time.today());
    }

    @GetMapping("/weekly/{operatorId}")
    @Operation(summary = "Semana (lunes a domingo) con el pago del descanso semanal del Art. 53")
    public WeeklyPayResponse weekly(@PathVariable long operatorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        return service.weekly(operatorId, weekStart);
    }
}
