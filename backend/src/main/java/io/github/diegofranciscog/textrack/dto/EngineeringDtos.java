package io.github.diegofranciscog.textrack.dto;

import io.github.diegofranciscog.textrack.domain.MachineType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class EngineeringDtos {

    public static final String CODE_PATTERN = "[A-Z0-9][A-Z0-9-]{1,29}";

    private EngineeringDtos() {
    }

    public record CreateStyleRequest(
            @NotBlank @Pattern(regexp = CODE_PATTERN, message = "Usa mayúsculas, números y guiones") String code,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 40) String garmentType) {
    }

    public record CreateOperationRequest(
            @NotNull @Min(1) @Max(9999) Integer sequence,
            @NotBlank @Pattern(regexp = "[A-Z0-9][A-Z0-9-]{0,19}") String code,
            @NotBlank @Size(max = 120) String name,
            @NotNull MachineType machineType,
            @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("60") @Digits(integer = 2, fraction = 4)
            BigDecimal samMinutes,
            @DecimalMin("0") @Digits(integer = 6, fraction = 4) BigDecimal rateUsd,
            LocalDate rateValidFrom) {
    }

    public record CreateRateRequest(
            @NotNull @DecimalMin("0") @Digits(integer = 6, fraction = 4) BigDecimal rateUsd,
            @NotNull LocalDate validFrom) {
    }

    public record OperationResponse(long id, int sequence, String code, String name, MachineType machineType,
                                    BigDecimal samMinutes, BigDecimal currentRateUsd) {
    }

    public record StyleResponse(long id, String code, String name, String garmentType, boolean active,
                                BigDecimal totalSam, List<OperationResponse> operations) {
    }
}
