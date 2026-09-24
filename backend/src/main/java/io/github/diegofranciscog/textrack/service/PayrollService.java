package io.github.diegofranciscog.textrack.service;

import io.github.diegofranciscog.textrack.domain.Attendance;
import io.github.diegofranciscog.textrack.domain.Operator;
import io.github.diegofranciscog.textrack.dto.PayrollDtos.DailyPayrollResponse;
import io.github.diegofranciscog.textrack.dto.PayrollDtos.OperatorDayDetail;
import io.github.diegofranciscog.textrack.dto.PayrollDtos.OperatorPay;
import io.github.diegofranciscog.textrack.dto.PayrollDtos.WeeklyPayResponse;
import io.github.diegofranciscog.textrack.exception.BusinessRuleException;
import io.github.diegofranciscog.textrack.exception.NotFoundException;
import io.github.diegofranciscog.textrack.mapper.PayrollMapper;
import io.github.diegofranciscog.textrack.repository.CatalogRepository;
import io.github.diegofranciscog.textrack.repository.CatalogRepository.PayrollParameter;
import io.github.diegofranciscog.textrack.repository.PlantRepository;
import io.github.diegofranciscog.textrack.repository.ReadingRepository;
import io.github.diegofranciscog.textrack.repository.ReadingRepository.PieceWorkRow;
import io.github.diegofranciscog.textrack.service.calc.PieceRateCalculator;
import io.github.diegofranciscog.textrack.service.calc.PieceRateCalculator.DailyInput;
import io.github.diegofranciscog.textrack.service.calc.PieceRateCalculator.DailyPay;
import io.github.diegofranciscog.textrack.service.calc.PieceRateCalculator.PieceWork;
import io.github.diegofranciscog.textrack.service.calc.PieceRateCalculator.RestPay;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Destajo diario y semanal por operario con alerta de piso SBU (R7–R10). */
@Service
public class PayrollService {

    private final ReadingRepository readings;
    private final PlantRepository plant;
    private final CatalogRepository catalogs;
    private final PlantTime time;

    public PayrollService(ReadingRepository readings, PlantRepository plant, CatalogRepository catalogs,
                          PlantTime time) {
        this.readings = readings;
        this.plant = plant;
        this.catalogs = catalogs;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public DailyPayrollResponse daily(LocalDate date) {
        PayrollParameter parameter = parameter(date);
        Map<Long, Operator> operators = plant.findOperators(false).stream()
                .collect(Collectors.toMap(Operator::id, Function.identity()));
        Map<Long, List<PieceWorkRow>> work = readings.findPieceWork(date, null).stream()
                .collect(Collectors.groupingBy(PieceWorkRow::operatorId));
        Map<Long, Attendance> attendance = plant.findAttendances(date).stream()
                .collect(Collectors.toMap(Attendance::operatorId, Function.identity()));

        Set<Long> active = new TreeSet<>(work.keySet());
        active.addAll(attendance.keySet());
        OffsetDateTime now = time.now();
        List<OperatorPay> pays = active.stream()
                .filter(operators::containsKey)
                .map(id -> PayrollMapper.toResponse(operators.get(id),
                        calculate(date, parameter.sbu(), attendance.get(id), work.getOrDefault(id, List.of()), now)))
                .sorted(Comparator.comparing(OperatorPay::operatorCode))
                .toList();

        BigDecimal total = pays.stream().map(OperatorPay::totalPay).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal topUp = pays.stream().map(OperatorPay::topUp).reduce(BigDecimal.ZERO, BigDecimal::add);
        int below = (int) pays.stream().filter(OperatorPay::belowFloor).count();
        return new DailyPayrollResponse(date, parameter.sbu(), PieceRateCalculator.hourlyFloor(parameter.sbu()),
                parameter.legalReference(), below, total, topUp, pays);
    }

    @Transactional(readOnly = true)
    public OperatorDayDetail detail(long operatorId, LocalDate date) {
        Operator operator = plant.findOperator(operatorId)
                .orElseThrow(() -> new NotFoundException("Operario no encontrado"));
        DailyPay pay = calculate(operator, date);
        return new OperatorDayDetail(PayrollMapper.toResponse(operator, pay),
                pay.lines().stream().map(PayrollMapper::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public WeeklyPayResponse weekly(long operatorId, LocalDate weekStart) {
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new BusinessRuleException("La semana debe iniciar en lunes");
        }
        Operator operator = plant.findOperator(operatorId)
                .orElseThrow(() -> new NotFoundException("Operario no encontrado"));
        List<DailyPay> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            days.add(calculate(operator, weekStart.plusDays(i)));
        }
        RestPay rest = PieceRateCalculator.weeklyRest(days, parameter(weekStart).sbu());
        BigDecimal total = days.stream().map(DailyPay::totalPay).reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(rest.amount());
        return new WeeklyPayResponse(operator.id(), operator.code(), operator.fullName(), weekStart,
                days.stream().map(day -> PayrollMapper.toResponse(operator, day)).toList(),
                PayrollMapper.toResponse(rest), total);
    }

    private DailyPay calculate(Operator operator, LocalDate date) {
        return calculate(date, parameter(date).sbu(), plant.findAttendance(operator.id(), date).orElse(null),
                readings.findPieceWork(date, operator.id()), time.now());
    }

    private DailyPay calculate(LocalDate date, BigDecimal sbu, Attendance attendance, List<PieceWorkRow> rows,
                               OffsetDateTime now) {
        List<PieceWork> pieces = rows.stream()
                .map(row -> new PieceWork(row.scannedAt().toInstant(), row.operationCode(), row.quantity(),
                        row.rateUsd() != null ? row.rateUsd() : BigDecimal.ZERO, row.samMinutes()))
                .toList();
        return PieceRateCalculator.daily(new DailyInput(date, time.zone(), sbu,
                attendance == null ? null : WorkTime.shift(attendance, now), pieces, now.toInstant()));
    }

    private PayrollParameter parameter(LocalDate date) {
        return catalogs.payrollParameter(date.getYear()).orElseThrow(() -> new BusinessRuleException(
                "No hay SBU registrado para " + date.getYear() + " en payroll_parameters"));
    }
}
