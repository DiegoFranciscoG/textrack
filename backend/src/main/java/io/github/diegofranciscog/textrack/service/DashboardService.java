package io.github.diegofranciscog.textrack.service;

import io.github.diegofranciscog.textrack.domain.Attendance;
import io.github.diegofranciscog.textrack.domain.MachineStop;
import io.github.diegofranciscog.textrack.domain.Operator;
import io.github.diegofranciscog.textrack.domain.ProductionLine;
import io.github.diegofranciscog.textrack.dto.PayrollDtos.DailyPayrollResponse;
import io.github.diegofranciscog.textrack.dto.PayrollDtos.OperatorPay;
import io.github.diegofranciscog.textrack.dto.ReportDtos.ActiveStop;
import io.github.diegofranciscog.textrack.dto.ReportDtos.DashboardSnapshot;
import io.github.diegofranciscog.textrack.dto.ReportDtos.Kpi;
import io.github.diegofranciscog.textrack.dto.ReportDtos.LineKpi;
import io.github.diegofranciscog.textrack.dto.ReportDtos.OperatorKpi;
import io.github.diegofranciscog.textrack.repository.PlantRepository;
import io.github.diegofranciscog.textrack.repository.ReadingRepository;
import io.github.diegofranciscog.textrack.repository.ReportRepository;
import io.github.diegofranciscog.textrack.repository.ReportRepository.LineQuality;
import io.github.diegofranciscog.textrack.repository.ReportRepository.OperatorOutput;
import io.github.diegofranciscog.textrack.service.calc.OeeCalculator;
import io.github.diegofranciscog.textrack.service.calc.OeeCalculator.Result;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** KPI de ISO 22400-2 por línea y planta para una jornada (R3, R4). */
@Service
public class DashboardService {

    private final PlantRepository plant;
    private final ReportRepository reports;
    private final ReadingService readingService;
    private final ReadingRepository readings;
    private final PayrollService payroll;
    private final PlantTime time;

    public DashboardService(PlantRepository plant, ReportRepository reports, ReadingService readingService,
                            ReadingRepository readings, PayrollService payroll, PlantTime time) {
        this.plant = plant;
        this.reports = reports;
        this.readingService = readingService;
        this.readings = readings;
        this.payroll = payroll;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public DashboardSnapshot snapshot(LocalDate date) {
        OffsetDateTime now = time.now();
        OffsetDateTime dayStart = time.startOf(date);
        OffsetDateTime dayEnd = time.endOf(date);
        OffsetDateTime windowEnd = now.isBefore(dayEnd) ? now : dayEnd;

        Map<Long, Operator> operators = plant.findOperators(false).stream()
                .collect(Collectors.toMap(Operator::id, Function.identity()));
        Map<Long, Attendance> attendance = plant.findAttendances(date).stream()
                .collect(Collectors.toMap(Attendance::operatorId, Function.identity()));
        Map<Long, OperatorOutput> output = reports.outputByOperator(date).stream()
                .collect(Collectors.toMap(OperatorOutput::operatorId, Function.identity()));
        Map<Long, LineQuality> quality = reports.qualityByLine(dayStart, dayEnd).stream()
                .collect(Collectors.toMap(LineQuality::lineId, Function.identity()));
        List<MachineStop> stops = windowEnd.isAfter(dayStart)
                ? plant.findStopsOverlapping(dayStart, windowEnd) : List.of();

        List<LineKpi> lines = new ArrayList<>();
        Accumulator plantTotals = new Accumulator();
        for (ProductionLine line : plant.findLines()) {
            Accumulator acc = new Accumulator();
            for (Operator operator : operators.values()) {
                if (!Objects.equals(operator.lineId(), line.id())) {
                    continue;
                }
                Attendance att = attendance.get(operator.id());
                if (att != null) {
                    acc.attended = acc.attended.add(WorkTime.attendedMinutes(att, now));
                    acc.present++;
                }
                OperatorOutput out = output.get(operator.id());
                if (out != null) {
                    acc.earned = acc.earned.add(out.earnedMinutes());
                    acc.pieces += out.pieces();
                }
            }
            for (MachineStop stop : stops) {
                if (stop.lineId() == line.id()) {
                    BigDecimal minutes = clippedMinutes(stop, dayStart, windowEnd);
                    if (stop.planned()) {
                        acc.planned = acc.planned.add(minutes);
                    } else {
                        acc.unplanned = acc.unplanned.add(minutes);
                    }
                }
            }
            LineQuality q = quality.get(line.id());
            if (q != null) {
                acc.sampled += q.sampled();
                acc.defective += q.defective();
            }
            plantTotals.add(acc);
            lines.add(new LineKpi(line.id(), line.code(), line.name(), acc.present, acc.toKpi()));
        }

        DailyPayrollResponse pay = payroll.daily(date);
        List<OperatorKpi> operatorKpis = pay.operators().stream()
                .map(p -> toOperatorKpi(p, output.get(p.operatorId())))
                .toList();
        List<ActiveStop> activeStops = stops.stream()
                .filter(stop -> stop.endedAt() == null)
                .map(stop -> new ActiveStop(stop.id(), stop.machineCode(), lineCode(stop.lineId()), stop.reason().name(),
                        stop.planned(), stop.startedAt(), Duration.between(stop.startedAt(), now).toMinutes()))
                .toList();

        return new DashboardSnapshot(date, now, plantTotals.toKpi(), lines, operatorKpis, readingService.recent(15),
                activeStops, readings.countRejectedSince(dayStart), pay.operatorsBelowFloor());
    }

    private String lineCode(long lineId) {
        return plant.findLine(lineId).map(ProductionLine::code).orElse(null);
    }

    private static OperatorKpi toOperatorKpi(OperatorPay pay, OperatorOutput output) {
        return new OperatorKpi(pay.operatorId(), pay.operatorCode(), pay.operatorName(), pay.lineCode(), pay.pieces(),
                pay.earnedMinutes(), pay.attendedMinutes(), pay.efficiency(), pay.totalPay(), pay.floor(),
                pay.belowFloor());
    }

    static BigDecimal clippedMinutes(MachineStop stop, OffsetDateTime from, OffsetDateTime to) {
        OffsetDateTime start = stop.startedAt().isBefore(from) ? from : stop.startedAt();
        OffsetDateTime end = stop.endedAt() == null || stop.endedAt().isAfter(to) ? to : stop.endedAt();
        long minutes = Duration.between(start, end).toMinutes();
        return BigDecimal.valueOf(Math.max(0, minutes));
    }

    private static final class Accumulator {
        private BigDecimal attended = BigDecimal.ZERO;
        private BigDecimal planned = BigDecimal.ZERO;
        private BigDecimal unplanned = BigDecimal.ZERO;
        private BigDecimal earned = BigDecimal.ZERO;
        private int sampled;
        private int defective;
        private int pieces;
        private int present;

        void add(Accumulator other) {
            attended = attended.add(other.attended);
            planned = planned.add(other.planned);
            unplanned = unplanned.add(other.unplanned);
            earned = earned.add(other.earned);
            sampled += other.sampled;
            defective += other.defective;
            pieces += other.pieces;
            present += other.present;
        }

        Kpi toKpi() {
            Result r = OeeCalculator.calculate(new OeeCalculator.Input(attended, planned, unplanned, earned, sampled,
                    defective));
            return new Kpi(attended, r.plannedBusyMinutes(), r.actualProductionMinutes(), earned, r.availability(),
                    r.performance(), r.quality(), r.oee(), r.efficiency(), r.qualityMeasured(), pieces);
        }
    }
}
