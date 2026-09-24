package io.github.diegofranciscog.textrack.service;

import io.github.diegofranciscog.textrack.domain.Bundle;
import io.github.diegofranciscog.textrack.domain.InspectionLevel;
import io.github.diegofranciscog.textrack.domain.Operation;
import io.github.diegofranciscog.textrack.domain.ProductionOrder;
import io.github.diegofranciscog.textrack.domain.Severity;
import io.github.diegofranciscog.textrack.dto.QualityDtos.AqlDefectRequest;
import io.github.diegofranciscog.textrack.dto.QualityDtos.AqlInspectionResponse;
import io.github.diegofranciscog.textrack.dto.QualityDtos.AqlPlanResponse;
import io.github.diegofranciscog.textrack.dto.QualityDtos.CreateAqlInspectionRequest;
import io.github.diegofranciscog.textrack.dto.QualityDtos.DefectTypeResponse;
import io.github.diegofranciscog.textrack.exception.BusinessRuleException;
import io.github.diegofranciscog.textrack.exception.NotFoundException;
import io.github.diegofranciscog.textrack.repository.CatalogRepository;
import io.github.diegofranciscog.textrack.repository.CatalogRepository.DefectType;
import io.github.diegofranciscog.textrack.repository.CutRepository;
import io.github.diegofranciscog.textrack.repository.EngineeringRepository;
import io.github.diegofranciscog.textrack.repository.PlantRepository;
import io.github.diegofranciscog.textrack.repository.ProductionOrderRepository;
import io.github.diegofranciscog.textrack.repository.QualityRepository;
import io.github.diegofranciscog.textrack.repository.QualityRepository.InspectionRow;
import io.github.diegofranciscog.textrack.repository.QualityRepository.NewInspection;
import io.github.diegofranciscog.textrack.service.calc.AqlPlanCalculator;
import io.github.diegofranciscog.textrack.service.calc.AqlPlanCalculator.AqlPlan;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inspección por atributos con AQL (R5). Defectos mayores y menores se evalúan cada uno con su plan; se
 * inspecciona el mayor tamaño de muestra y cualquier defecto crítico rechaza el lote (SUPUESTO S8).
 */
@Service
public class QualityService {

    private final QualityRepository quality;
    private final ProductionOrderRepository orders;
    private final PlantRepository plant;
    private final CatalogRepository catalogs;
    private final CutRepository cuts;
    private final EngineeringRepository engineering;
    private final PlantTime time;

    public QualityService(QualityRepository quality, ProductionOrderRepository orders, PlantRepository plant,
                          CatalogRepository catalogs, CutRepository cuts, EngineeringRepository engineering,
                          PlantTime time) {
        this.quality = quality;
        this.orders = orders;
        this.plant = plant;
        this.catalogs = catalogs;
        this.cuts = cuts;
        this.engineering = engineering;
        this.time = time;
    }

    public AqlPlanResponse plan(int lotSize, InspectionLevel level, BigDecimal aql) {
        AqlPlan plan = aqlPlan(lotSize, level, aql);
        return new AqlPlanResponse(lotSize, level, aql, plan.initialLetter(), plan.planLetter(), plan.sampleSize(),
                plan.acceptNumber(), plan.rejectNumber(), plan.fullInspection(), AqlPlanCalculator.STANDARD_EDITION);
    }

    public List<DefectTypeResponse> defectTypes() {
        return catalogs.defectTypes().stream()
                .map(type -> new DefectTypeResponse(type.code(), type.name(), type.category(), type.defaultSeverity()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AqlInspectionResponse> recent() {
        return quality.findRecent(50).stream().map(QualityService::toResponse).toList();
    }

    @Transactional
    public AqlInspectionResponse inspect(CreateAqlInspectionRequest request, Long inspectorId) {
        ProductionOrder order = orders.findById(request.productionOrderId())
                .orElseThrow(() -> new NotFoundException("Orden no encontrada"));
        if (request.lineId() != null && plant.findLine(request.lineId()).isEmpty()) {
            throw new NotFoundException("Línea no encontrada");
        }
        AqlPlan majorPlan = aqlPlan(request.lotSize(), request.level(), request.aqlMajor());
        AqlPlan minorPlan = aqlPlan(request.lotSize(), request.level(), request.aqlMinor());
        AqlPlan largest = majorPlan.sampleSize() >= minorPlan.sampleSize() ? majorPlan : minorPlan;
        int sampleSize = largest.sampleSize();

        List<ResolvedDefect> defects = new ArrayList<>();
        Map<Severity, Integer> found = new EnumMap<>(Severity.class);
        for (AqlDefectRequest defect : request.defects()) {
            ResolvedDefect resolved = resolve(defect, order);
            defects.add(resolved);
            found.merge(resolved.severity(), resolved.quantity(), Integer::sum);
        }
        int totalDefects = found.values().stream().mapToInt(Integer::intValue).sum();
        if (request.defectiveUnits() > sampleSize) {
            throw new BusinessRuleException("Las unidades defectuosas no pueden superar la muestra (" + sampleSize + ")");
        }
        if (totalDefects > 0 && request.defectiveUnits() == 0) {
            throw new BusinessRuleException("Indica cuántas unidades de la muestra tienen defectos");
        }
        if (request.defectiveUnits() > totalDefects) {
            throw new BusinessRuleException("Hay más unidades defectuosas que defectos registrados");
        }

        int critical = found.getOrDefault(Severity.CRITICAL, 0);
        int major = found.getOrDefault(Severity.MAJOR, 0);
        int minor = found.getOrDefault(Severity.MINOR, 0);
        boolean accepted = critical == 0 && majorPlan.accepts(major) && minorPlan.accepts(minor);

        long id = quality.insertInspection(new NewInspection(order.id(), request.lineId(), request.level().name(),
                request.lotSize(), request.aqlMajor(), request.aqlMinor(), String.valueOf(largest.planLetter()),
                sampleSize, majorPlan.acceptNumber(), majorPlan.rejectNumber(), minorPlan.acceptNumber(),
                minorPlan.rejectNumber(), critical, major, minor, request.defectiveUnits(),
                accepted ? "ACCEPTED" : "REJECTED", AqlPlanCalculator.STANDARD_EDITION, inspectorId, time.now()));
        for (ResolvedDefect defect : defects) {
            quality.insertDefect(id, defect.type().code(), defect.severity(), defect.quantity(), defect.bundleId(),
                    defect.operationId());
        }
        return quality.findRecent(50).stream().filter(row -> row.id() == id).findFirst()
                .map(QualityService::toResponse).orElseThrow();
    }

    private ResolvedDefect resolve(AqlDefectRequest defect, ProductionOrder order) {
        DefectType type = catalogs.defectType(defect.defectTypeCode())
                .orElseThrow(() -> new BusinessRuleException("Tipo de defecto desconocido: " + defect.defectTypeCode()));
        Severity severity = defect.severity() != null ? defect.severity() : type.defaultSeverity();
        Long bundleId = null;
        if (defect.bundleCode() != null && !defect.bundleCode().isBlank()) {
            Bundle bundle = cuts.findBundleByCode(defect.bundleCode())
                    .orElseThrow(() -> new BusinessRuleException("Bulto desconocido: " + defect.bundleCode()));
            boolean belongsToOrder = cuts.findById(bundle.cutId())
                    .map(cut -> cut.productionOrderId() == order.id()).orElse(false);
            if (!belongsToOrder) {
                throw new BusinessRuleException("El bulto " + defect.bundleCode() + " no pertenece a la orden");
            }
            bundleId = bundle.id();
        }
        Long operationId = null;
        if (defect.operationCode() != null && !defect.operationCode().isBlank()) {
            Optional<Operation> operation = engineering.findOperations(order.styleId(), time.today()).stream()
                    .filter(op -> op.code().equals(defect.operationCode()))
                    .findFirst();
            operationId = operation.map(Operation::id)
                    .orElseThrow(() -> new BusinessRuleException("Operación desconocida: " + defect.operationCode()));
        }
        return new ResolvedDefect(type, severity, defect.quantity(), bundleId, operationId);
    }

    private static AqlPlan aqlPlan(int lotSize, InspectionLevel level, BigDecimal aql) {
        try {
            return AqlPlanCalculator.plan(lotSize, level, aql);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException(e.getMessage());
        }
    }

    private static AqlInspectionResponse toResponse(InspectionRow row) {
        return new AqlInspectionResponse(row.id(), row.orderCode(), row.lineCode(), row.inspectionLevel(),
                row.lotSize(), row.aqlMajor(), row.aqlMinor(), row.codeLetter(), row.sampleSize(), row.majorAccept(),
                row.majorReject(), row.minorAccept(), row.minorReject(), row.criticalFound(), row.majorFound(),
                row.minorFound(), row.defectiveUnits(), row.result(), row.standardEdition(), row.inspectedAt());
    }

    private record ResolvedDefect(DefectType type, Severity severity, int quantity, Long bundleId, Long operationId) {
    }
}
