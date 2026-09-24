package io.github.diegofranciscog.textrack.dto;

import io.github.diegofranciscog.textrack.domain.InspectionLevel;
import io.github.diegofranciscog.textrack.domain.Severity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class QualityDtos {

    private QualityDtos() {
    }

    public record AqlPlanResponse(int lotSize, InspectionLevel level, BigDecimal aql, char initialLetter,
                                  char planLetter, int sampleSize, int acceptNumber, int rejectNumber,
                                  boolean fullInspection, String standardEdition) {
    }

    public record AqlDefectRequest(
            @NotBlank @Size(max = 20) String defectTypeCode,
            Severity severity,
            @NotNull @Min(1) @Max(10_000) Integer quantity,
            @Size(max = 40) String bundleCode,
            @Size(max = 20) String operationCode) {
    }

    public record CreateAqlInspectionRequest(
            @NotNull Long productionOrderId,
            Long lineId,
            @NotNull InspectionLevel level,
            @NotNull @Min(2) @Max(10_000_000) Integer lotSize,
            @NotNull BigDecimal aqlMajor,
            @NotNull BigDecimal aqlMinor,
            @NotNull @Min(0) Integer defectiveUnits,
            @NotNull @Size(max = 200) List<@Valid AqlDefectRequest> defects) {
    }

    public record AqlInspectionResponse(long id, String orderCode, String lineCode, String level, int lotSize,
                                        BigDecimal aqlMajor, BigDecimal aqlMinor, String codeLetter, int sampleSize,
                                        int majorAccept, int majorReject, int minorAccept, int minorReject,
                                        int criticalFound, int majorFound, int minorFound, int defectiveUnits,
                                        String result, String standardEdition, OffsetDateTime inspectedAt) {
    }

    public record DefectTypeResponse(String code, String name, String category, Severity defaultSeverity) {
    }
}
