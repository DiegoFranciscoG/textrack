package io.github.diegofranciscog.textrack.mapper;

import io.github.diegofranciscog.textrack.domain.Operation;
import io.github.diegofranciscog.textrack.domain.Style;
import io.github.diegofranciscog.textrack.dto.EngineeringDtos.OperationResponse;
import io.github.diegofranciscog.textrack.dto.EngineeringDtos.StyleResponse;
import java.math.BigDecimal;
import java.util.List;

public final class EngineeringMapper {

    private EngineeringMapper() {
    }

    public static OperationResponse toResponse(Operation operation) {
        return new OperationResponse(operation.id(), operation.sequence(), operation.code(), operation.name(),
                operation.machineType(), operation.samMinutes(), operation.currentRateUsd());
    }

    public static StyleResponse toResponse(Style style, List<Operation> operations) {
        BigDecimal totalSam = operations.stream().map(Operation::samMinutes).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new StyleResponse(style.id(), style.code(), style.name(), style.garmentType(), style.active(), totalSam,
                operations.stream().map(EngineeringMapper::toResponse).toList());
    }
}
