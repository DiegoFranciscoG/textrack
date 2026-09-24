package io.github.diegofranciscog.textrack.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Tablero en vivo, cuellos de botella y trazabilidad. */
public final class ReportDtos {

    private ReportDtos() {
    }

    // ---------------------------------------------------------------- tablero

    public record Kpi(BigDecimal attendedMinutes, BigDecimal plannedBusyMinutes, BigDecimal actualProductionMinutes,
                      BigDecimal earnedMinutes, BigDecimal availability, BigDecimal performance, BigDecimal quality,
                      BigDecimal oee, BigDecimal efficiency, boolean qualityMeasured, int pieces) {
    }

    public record LineKpi(long lineId, String lineCode, String lineName, int operatorsPresent, Kpi kpi) {
    }

    public record OperatorKpi(long operatorId, String operatorCode, String operatorName, String lineCode, int pieces,
                              BigDecimal earnedMinutes, BigDecimal attendedMinutes, BigDecimal efficiency,
                              BigDecimal totalPay, BigDecimal floor, boolean belowFloor) {
    }

    public record ActiveStop(long id, String machineCode, String lineCode, String reason, boolean planned,
                             OffsetDateTime startedAt, long minutes) {
    }

    public record DashboardSnapshot(LocalDate workDate, OffsetDateTime generatedAt, Kpi plant, List<LineKpi> lines,
                                    List<OperatorKpi> operators, List<ReadingDtos.RecentReadingResponse> recentReadings,
                                    List<ActiveStop> activeStops, int forgedTicketsToday, int operatorsBelowFloor) {
    }

    // ---------------------------------------------------------------- cuellos de botella

    public record OperationLoadResponse(int sequence, String code, String name, BigDecimal samMinutes, int operators,
                                        int completedPieces, int pendingPieces, int wipBefore,
                                        BigDecimal capacityPerHour, BigDecimal remainingMinutes, boolean bottleneck) {
    }

    public record BottleneckReport(long orderId, String orderCode, String styleCode, int totalPieces,
                                   String bottleneckCode, BigDecimal estimatedHoursToFinish,
                                   BigDecimal balanceEfficiency, List<OperationLoadResponse> operations) {
    }

    // ---------------------------------------------------------------- trazabilidad

    public record RollInfo(String code, String dyeLot, String supplier, BigDecimal pointsPer100SqYd,
                           Boolean accepted) {
    }

    public record OperationStep(UUID ticketId, int sequence, String operationCode, String operationName,
                                OffsetDateTime scannedAt, String operatorCode, String operatorName) {
    }

    public record DefectRecord(String defectTypeCode, String defectName, String severity, int quantity,
                               String operationCode, OffsetDateTime inspectedAt) {
    }

    public record BundleTraceResponse(String bundleCode, Integer garmentNumber, String garmentSerial, String sizeCode,
                                      String color, int quantity, String cutCode, OffsetDateTime cutAt,
                                      String orderCode, String customer, String styleCode, String styleName,
                                      RollInfo roll, List<OperationStep> operations, List<DefectRecord> defects) {
    }

    public record RollBundle(String bundleCode, String cutCode, String sizeCode, String color, int quantity) {
    }

    public record RollTraceResponse(RollInfo roll, String color, BigDecimal lengthM, BigDecimal remainingM,
                                    String status, int bundles, int pieces, List<RollBundle> bundleList) {
    }
}
