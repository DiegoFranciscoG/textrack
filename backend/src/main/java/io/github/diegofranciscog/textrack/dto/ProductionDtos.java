package io.github.diegofranciscog.textrack.dto;

import io.github.diegofranciscog.textrack.domain.OrderStatus;
import io.github.diegofranciscog.textrack.domain.RollStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class ProductionDtos {

    private ProductionDtos() {
    }

    // ---------------------------------------------------------------- órdenes

    public record OrderLineRequest(
            @NotBlank @Size(max = 5) String sizeCode,
            @NotBlank @Size(max = 40) String color,
            @NotNull @Min(1) @Max(100_000) Integer quantity) {
    }

    public record CreateOrderRequest(
            @NotNull Long styleId,
            @NotBlank @Size(max = 120) String customer,
            @NotNull @FutureOrPresent LocalDate dueDate,
            @NotEmpty @Size(max = 100) List<@Valid OrderLineRequest> lines) {
    }

    public record OrderLineResponse(String sizeCode, String color, int quantity, int cutQuantity) {
    }

    public record OrderSummary(long id, String code, String styleCode, String styleName, String customer,
                               LocalDate dueDate, OrderStatus status, OffsetDateTime createdAt) {
    }

    public record CutSummary(long id, String code, String color, int bundles, int pieces, OffsetDateTime createdAt) {
    }

    public record OrderResponse(long id, String code, long styleId, String styleCode, String styleName,
                                String customer, LocalDate dueDate, OrderStatus status, OffsetDateTime createdAt,
                                int totalQuantity, int cutQuantity, int finishedQuantity,
                                List<OrderLineResponse> lines, List<CutSummary> cuts) {
    }

    // ---------------------------------------------------------------- cortes

    public record SizeRatioRequest(
            @NotBlank @Size(max = 5) String sizeCode,
            @NotNull @Min(1) @Max(50) Integer piecesPerPly) {
    }

    public record RollLayRequest(
            @NotNull Long rollId,
            @NotNull @Min(1) @Max(500) Integer plies,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 6, fraction = 2) BigDecimal metersUsed) {
    }

    public record CreateCutRequest(
            @NotBlank @Size(max = 40) String color,
            @NotNull @Min(1) @Max(100) Integer maxBundleSize,
            @NotEmpty @Size(max = 10) List<@Valid SizeRatioRequest> sizeRatios,
            @NotEmpty @Size(max = 20) List<@Valid RollLayRequest> rolls) {
    }

    public record BundleResponse(long id, String code, int bundleNumber, String sizeCode, String color, int quantity,
                                 String rollCode, String dyeLot) {
    }

    public record RollUsageResponse(String rollCode, String dyeLot, int plies, BigDecimal metersUsed) {
    }

    public record CutResponse(long id, String code, String orderCode, String color, int maxBundleSize,
                              OffsetDateTime createdAt, int pieces, int tickets, List<RollUsageResponse> rolls,
                              List<BundleResponse> bundles) {
    }

    // ---------------------------------------------------------------- rollos (4 puntos)

    public record CreateRollRequest(
            @NotBlank @Pattern(regexp = "[A-Z0-9][A-Z0-9-]{1,29}") String code,
            @NotBlank @Size(max = 120) String supplier,
            @NotBlank @Size(max = 40) String dyeLot,
            @NotBlank @Size(max = 40) String color,
            @NotNull @DecimalMin("1") @DecimalMax("5000") @Digits(integer = 4, fraction = 2) BigDecimal lengthM,
            @NotNull @DecimalMin("30") @DecimalMax("400") @Digits(integer = 3, fraction = 2) BigDecimal widthCm) {
    }

    public record RollResponse(long id, String code, String supplier, String dyeLot, String color, BigDecimal lengthM,
                               BigDecimal widthCm, BigDecimal remainingM, RollStatus status, OffsetDateTime receivedAt,
                               Integer totalPoints, BigDecimal pointsPer100SqYd) {
    }

    public record FabricDefectRequest(
            @NotNull @DecimalMin("0") @Digits(integer = 4, fraction = 2) BigDecimal positionM,
            @NotNull @Min(1) @Max(10_000) Integer lengthMm,
            boolean hole,
            @Size(max = 20) String defectTypeCode) {
    }

    public record FabricInspectionRequest(
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 4, fraction = 2)
            BigDecimal inspectedLengthM,
            @DecimalMin("30") @DecimalMax("400") BigDecimal widthCm,
            @NotNull @Size(max = 500) List<@Valid FabricDefectRequest> defects) {
    }

    public record FabricInspectionResponse(long rollId, String rollCode, int totalPoints, BigDecimal pointsPer100SqYd,
                                           BigDecimal maxPointsAllowed, boolean accepted, RollStatus rollStatus) {
    }
}
