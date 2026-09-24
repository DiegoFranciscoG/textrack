package io.github.diegofranciscog.textrack.service;

import io.github.diegofranciscog.textrack.domain.Bundle;
import io.github.diegofranciscog.textrack.domain.FabricRoll;
import io.github.diegofranciscog.textrack.domain.ProductionOrder;
import io.github.diegofranciscog.textrack.dto.ReportDtos.BottleneckReport;
import io.github.diegofranciscog.textrack.dto.ReportDtos.BundleTraceResponse;
import io.github.diegofranciscog.textrack.dto.ReportDtos.DefectRecord;
import io.github.diegofranciscog.textrack.dto.ReportDtos.OperationLoadResponse;
import io.github.diegofranciscog.textrack.dto.ReportDtos.OperationStep;
import io.github.diegofranciscog.textrack.dto.ReportDtos.RollBundle;
import io.github.diegofranciscog.textrack.dto.ReportDtos.RollInfo;
import io.github.diegofranciscog.textrack.dto.ReportDtos.RollTraceResponse;
import io.github.diegofranciscog.textrack.exception.BusinessRuleException;
import io.github.diegofranciscog.textrack.exception.NotFoundException;
import io.github.diegofranciscog.textrack.repository.CutRepository;
import io.github.diegofranciscog.textrack.repository.FabricRollRepository;
import io.github.diegofranciscog.textrack.repository.ProductionOrderRepository;
import io.github.diegofranciscog.textrack.repository.ReportRepository;
import io.github.diegofranciscog.textrack.repository.ReportRepository.BundleTrace;
import io.github.diegofranciscog.textrack.service.calc.BottleneckAnalyzer;
import io.github.diegofranciscog.textrack.service.calc.BottleneckAnalyzer.OperationProgress;
import io.github.diegofranciscog.textrack.service.calc.BottleneckAnalyzer.Report;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cuellos de botella por operación y trazabilidad rollo → bulto → prenda. */
@Service
public class ReportService {

    /** Serie de prenda: código de bulto + número de pieza dentro del bulto (p. ej. CT-00001-012-07). */
    private static final Pattern GARMENT_SERIAL = Pattern.compile("^(CT-\\d{5}-\\d{3})-(\\d{1,3})$");
    private static final int STAFFING_WINDOW_DAYS = 3;

    private final ReportRepository reports;
    private final ProductionOrderRepository orders;
    private final CutRepository cuts;
    private final FabricRollRepository rolls;
    private final PlantTime time;

    public ReportService(ReportRepository reports, ProductionOrderRepository orders, CutRepository cuts,
                         FabricRollRepository rolls, PlantTime time) {
        this.reports = reports;
        this.orders = orders;
        this.cuts = cuts;
        this.rolls = rolls;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public BottleneckReport bottlenecks(long orderId) {
        ProductionOrder order = orders.findById(orderId).orElseThrow(() -> new NotFoundException("Orden no encontrada"));
        int totalPieces = reports.cutPieces(orderId);
        List<OperationProgress> route = reports.operationProgress(orderId, time.today().minusDays(STAFFING_WINDOW_DAYS))
                .stream()
                .map(row -> new OperationProgress(row.sequence(), row.code(), row.name(), row.samMinutes(),
                        row.operators(), row.completedPieces()))
                .toList();
        Report report = BottleneckAnalyzer.analyze(totalPieces, route);
        return new BottleneckReport(order.id(), order.code(), order.styleCode(), totalPieces, report.bottleneckCode(),
                report.estimatedHoursToFinish(), report.balanceEfficiency(),
                report.operations().stream().map(op -> new OperationLoadResponse(op.sequence(), op.code(), op.name(),
                        op.samMinutes(), op.operators(), op.completedPieces(), op.pendingPieces(), op.wipBefore(),
                        op.capacityPerHour(), op.remainingMinutes(), op.bottleneck())).toList());
    }

    @Transactional(readOnly = true)
    public BundleTraceResponse traceBundle(String bundleCode) {
        return trace(bundleCode, null);
    }

    @Transactional(readOnly = true)
    public BundleTraceResponse traceGarment(String serial) {
        Matcher matcher = GARMENT_SERIAL.matcher(serial);
        if (!matcher.matches()) {
            throw new BusinessRuleException("Serie de prenda inválida. Formato: CT-00001-001-01");
        }
        return trace(matcher.group(1), Integer.valueOf(matcher.group(2)));
    }

    @Transactional(readOnly = true)
    public RollTraceResponse traceRoll(String rollCode) {
        FabricRoll roll = rolls.findByCode(rollCode).orElseThrow(() -> new NotFoundException("Rollo no encontrado"));
        List<Bundle> bundles = cuts.findBundlesByRoll(roll.id());
        var inspection = rolls.findInspection(roll.id());
        RollInfo info = new RollInfo(roll.code(), roll.dyeLot(), roll.supplier(),
                inspection.map(i -> i.pointsPer100SqYd()).orElse(null),
                inspection.map(i -> i.accepted()).orElse(null));
        List<RollBundle> bundleList = bundles.stream()
                .map(b -> new RollBundle(b.code(), b.code().substring(0, 8), b.sizeCode(), b.color(), b.quantity()))
                .toList();
        return new RollTraceResponse(info, roll.color(), roll.lengthM(), roll.remainingM(), roll.status().name(),
                bundles.size(), bundles.stream().mapToInt(Bundle::quantity).sum(), bundleList);
    }

    private BundleTraceResponse trace(String bundleCode, Integer garmentNumber) {
        BundleTrace bundle = reports.bundleTrace(bundleCode)
                .orElseThrow(() -> new NotFoundException("Bulto no encontrado"));
        if (garmentNumber != null && (garmentNumber < 1 || garmentNumber > bundle.quantity())) {
            throw new NotFoundException("El bulto " + bundleCode + " tiene " + bundle.quantity() + " piezas");
        }
        List<OperationStep> steps = reports.operationTrace(bundle.bundleId()).stream()
                .map(op -> new OperationStep(op.ticketId(), op.sequence(), op.operationCode(), op.operationName(),
                        op.scannedAt(), op.operatorCode(), op.operatorName()))
                .toList();
        List<DefectRecord> defects = reports.defectTrace(bundle.bundleId()).stream()
                .map(d -> new DefectRecord(d.defectTypeCode(), d.defectName(), d.severity(), d.quantity(),
                        d.operationCode(), d.inspectedAt()))
                .toList();
        String serial = garmentNumber == null ? null : "%s-%02d".formatted(bundleCode, garmentNumber);
        return new BundleTraceResponse(bundle.bundleCode(), garmentNumber, serial, bundle.sizeCode(), bundle.color(),
                bundle.quantity(), bundle.cutCode(), bundle.cutAt(), bundle.orderCode(), bundle.customer(),
                bundle.styleCode(), bundle.styleName(),
                new RollInfo(bundle.rollCode(), bundle.dyeLot(), bundle.supplier(), bundle.pointsPer100SqYd(),
                        bundle.rollAccepted()),
                steps, defects);
    }
}
