package io.github.diegofranciscog.textrack.service;

import io.github.diegofranciscog.textrack.config.AppProperties;
import io.github.diegofranciscog.textrack.domain.FabricRoll;
import io.github.diegofranciscog.textrack.domain.RollStatus;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.CreateRollRequest;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.FabricDefectRequest;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.FabricInspectionRequest;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.FabricInspectionResponse;
import io.github.diegofranciscog.textrack.dto.ProductionDtos.RollResponse;
import io.github.diegofranciscog.textrack.exception.BusinessRuleException;
import io.github.diegofranciscog.textrack.exception.ConflictException;
import io.github.diegofranciscog.textrack.exception.NotFoundException;
import io.github.diegofranciscog.textrack.mapper.ProductionMapper;
import io.github.diegofranciscog.textrack.repository.CatalogRepository;
import io.github.diegofranciscog.textrack.repository.FabricRollRepository;
import io.github.diegofranciscog.textrack.service.calc.FourPointCalculator;
import io.github.diegofranciscog.textrack.service.calc.FourPointCalculator.Defect;
import io.github.diegofranciscog.textrack.service.calc.FourPointCalculator.Result;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Recepción de rollos e inspección con el sistema de 4 puntos (ASTM D5430). */
@Service
public class FabricService {

    private final FabricRollRepository rolls;
    private final CatalogRepository catalogs;
    private final BigDecimal maxPoints;

    public FabricService(FabricRollRepository rolls, CatalogRepository catalogs, AppProperties properties) {
        this.rolls = rolls;
        this.catalogs = catalogs;
        this.maxPoints = properties.quality().fabricMaxPointsPer100SqYd();
    }

    @Transactional(readOnly = true)
    public List<RollResponse> findAll() {
        return rolls.findAll().stream().map(ProductionMapper::toResponse).toList();
    }

    @Transactional
    public RollResponse receive(CreateRollRequest request) {
        if (rolls.codeExists(request.code())) {
            throw new ConflictException("Ya existe un rollo con el código " + request.code());
        }
        long id = rolls.insert(request.code(), request.supplier().trim(), request.dyeLot().trim(),
                request.color().trim(), request.lengthM(), request.widthCm());
        return ProductionMapper.toResponse(rolls.findById(id).orElseThrow());
    }

    /** Califica el rollo; si supera el umbral acordado queda rechazado y no puede usarse en cortes (R6). */
    @Transactional
    public FabricInspectionResponse inspect(long rollId, FabricInspectionRequest request, Long inspectorId) {
        FabricRoll roll = rolls.findById(rollId).orElseThrow(() -> new NotFoundException("Rollo no encontrado"));
        if (roll.status() != RollStatus.RECEIVED || rolls.hasInspection(rollId)) {
            throw new ConflictException("El rollo ya fue inspeccionado");
        }
        if (request.inspectedLengthM().compareTo(roll.lengthM()) > 0) {
            throw new BusinessRuleException("El largo inspeccionado supera el largo del rollo");
        }
        for (FabricDefectRequest defect : request.defects()) {
            if (defect.defectTypeCode() != null && catalogs.defectType(defect.defectTypeCode()).isEmpty()) {
                throw new BusinessRuleException("Tipo de defecto desconocido: " + defect.defectTypeCode());
            }
        }
        BigDecimal width = request.widthCm() != null ? request.widthCm() : roll.widthCm();
        List<Defect> defects = request.defects().stream()
                .map(d -> new Defect(d.positionM(), d.lengthMm(), d.hole()))
                .toList();
        Result result;
        try {
            result = FourPointCalculator.grade(request.inspectedLengthM(), width, defects, maxPoints);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException(e.getMessage());
        }
        long inspectionId = rolls.insertInspection(rollId, request.inspectedLengthM(), width, result.totalPoints(),
                result.pointsPer100SqYd(), maxPoints, result.accepted(), inspectorId);
        for (FabricDefectRequest defect : request.defects()) {
            rolls.insertDefect(inspectionId, defect.positionM(), defect.lengthMm(), defect.hole(),
                    defect.defectTypeCode(), FourPointCalculator.pointsFor(defect.lengthMm(), defect.hole()));
        }
        RollStatus status = result.accepted() ? RollStatus.APPROVED : RollStatus.REJECTED;
        rolls.updateStatus(rollId, status);
        return new FabricInspectionResponse(rollId, roll.code(), result.totalPoints(), result.pointsPer100SqYd(),
                maxPoints, result.accepted(), status);
    }
}
