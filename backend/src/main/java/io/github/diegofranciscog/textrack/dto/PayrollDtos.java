package io.github.diegofranciscog.textrack.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class PayrollDtos {

    private PayrollDtos() {
    }

    public record OperatorPay(long operatorId, String operatorCode, String operatorName, String lineCode,
                              LocalDate workDate, int pieces, BigDecimal earnedMinutes, BigDecimal attendedMinutes,
                              BigDecimal efficiency, BigDecimal ordinaryPay, BigDecimal extraPay, BigDecimal premiumPay,
                              BigDecimal floor, BigDecimal topUp, BigDecimal totalPay, boolean belowFloor,
                              boolean weekend, boolean attendanceRecorded) {
    }

    public record PayLineResponse(OffsetDateTime scannedAt, String operationCode, int quantity, BigDecimal rateUsd,
                                  BigDecimal baseAmount, String premium, BigDecimal premiumPercent,
                                  BigDecimal premiumAmount) {
    }

    public record DailyPayrollResponse(LocalDate workDate, BigDecimal sbu, BigDecimal hourlyFloor,
                                       String legalReference, int operatorsBelowFloor, BigDecimal totalPay,
                                       BigDecimal totalTopUp, List<OperatorPay> operators) {
    }

    public record OperatorDayDetail(OperatorPay summary, List<PayLineResponse> lines) {
    }

    public record RestPayResponse(BigDecimal weekdayAverage, BigDecimal minimumDaily, BigDecimal dailyRate,
                                  BigDecimal amount) {
    }

    public record WeeklyPayResponse(long operatorId, String operatorCode, String operatorName, LocalDate weekStart,
                                    List<OperatorPay> days, RestPayResponse weeklyRest, BigDecimal weekTotal) {
    }
}
