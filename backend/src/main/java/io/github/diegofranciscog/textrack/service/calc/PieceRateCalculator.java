package io.github.diegofranciscog.textrack.service.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Pago a destajo diario según el Código del Trabajo del Ecuador.
 *
 * <ul>
 *   <li>Art. 16: se paga por unidad de obra (piezas × tarifa de la operación).</li>
 *   <li>Art. 49: trabajo ordinario entre 19h00 y 06h00 → +25 %.</li>
 *   <li>Art. 55 num. 3: unidades hechas después de las 8 horas obligatorias → +50 % (hasta 24h00) o +100 % (24h00–06h00).</li>
 *   <li>Art. 55 num. 4: sábado o domingo → +100 %.</li>
 *   <li>Arts. 81, 82 y 117: el pago ordinario no puede quedar bajo el SBU prorrateado (SBU/240 por hora ordinaria).</li>
 *   <li>Art. 53: el descanso semanal se paga con el promedio de lunes a viernes, nunca menos que el mínimo.</li>
 * </ul>
 */
public final class PieceRateCalculator {

    public static final int ORDINARY_HOURS_PER_DAY = 8;
    /** Mes comercial de 30 días × 8 horas (SUPUESTO S1 en docs/investigacion.md). */
    public static final BigDecimal HOURS_PER_MONTH = new BigDecimal("240");
    public static final BigDecimal DAYS_PER_MONTH = new BigDecimal("30");
    private static final int MONEY_SCALE = 2;
    private static final LocalTime NIGHT_START = LocalTime.of(19, 0);
    private static final LocalTime NIGHT_END = LocalTime.of(6, 0);
    private static final BigDecimal WORKING_DAYS_PER_WEEK = new BigDecimal("5");
    private static final BigDecimal REST_DAYS_PER_WEEK = new BigDecimal("2");

    private PieceRateCalculator() {
    }

    public static DailyPay daily(DailyInput input) {
        boolean weekend = isWeekend(input.workDate());
        Instant ordinaryEnd = input.shift() == null ? null
                : input.shift().checkIn().plus(Duration.ofHours(ORDINARY_HOURS_PER_DAY))
                        .plus(Duration.ofMinutes(input.shift().breakMinutes()));

        BigDecimal ordinaryBase = BigDecimal.ZERO;
        BigDecimal extraBase = BigDecimal.ZERO;
        BigDecimal premiumPay = BigDecimal.ZERO;
        BigDecimal earnedMinutes = BigDecimal.ZERO;
        int pieces = 0;
        List<PayLine> lines = new ArrayList<>();

        for (PieceWork work : input.pieces()) {
            Premium premium = premiumFor(work.scannedAt(), input.zone(), weekend, ordinaryEnd);
            BigDecimal base = work.rateUsd().multiply(BigDecimal.valueOf(work.quantity()));
            BigDecimal premiumAmount = base.multiply(premium.percent()).movePointLeft(2);
            if (premium.isOrdinaryTime()) {
                ordinaryBase = ordinaryBase.add(base);
            } else {
                extraBase = extraBase.add(base);
            }
            premiumPay = premiumPay.add(premiumAmount);
            earnedMinutes = earnedMinutes.add(work.samMinutes().multiply(BigDecimal.valueOf(work.quantity())));
            pieces += work.quantity();
            lines.add(new PayLine(work.scannedAt(), work.operationCode(), work.quantity(), work.rateUsd(),
                    money(base), premium, money(premiumAmount)));
        }

        BigDecimal attendedMinutes = input.shift() == null ? null : workedMinutes(input.shift(), input.now());
        BigDecimal ordinaryHours = attendedMinutes == null
                ? BigDecimal.valueOf(ORDINARY_HOURS_PER_DAY)
                : attendedMinutes.divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP)
                        .min(BigDecimal.valueOf(ORDINARY_HOURS_PER_DAY));
        BigDecimal floor = weekend ? BigDecimal.ZERO : money(hourlyFloor(input.sbu()).multiply(ordinaryHours));
        BigDecimal ordinaryPay = money(ordinaryBase);
        boolean belowFloor = !weekend && ordinaryPay.compareTo(floor) < 0;
        BigDecimal topUp = belowFloor ? floor.subtract(ordinaryPay) : BigDecimal.ZERO.setScale(MONEY_SCALE);
        BigDecimal extraPay = money(extraBase);
        BigDecimal premium = money(premiumPay);
        BigDecimal total = ordinaryPay.add(extraPay).add(premium).add(topUp);

        BigDecimal efficiency = attendedMinutes == null || attendedMinutes.signum() == 0 ? null
                : earnedMinutes.divide(attendedMinutes, 4, RoundingMode.HALF_UP);

        return new DailyPay(input.workDate(), pieces, earnedMinutes.setScale(2, RoundingMode.HALF_UP),
                ordinaryPay, extraPay, premium, floor, topUp, total, belowFloor, weekend,
                ordinaryHours.setScale(2, RoundingMode.HALF_UP), attendedMinutes, efficiency,
                input.shift() != null, List.copyOf(lines));
    }

    /** Piso por hora ordinaria: SBU / 240 (30 días × 8 horas). */
    public static BigDecimal hourlyFloor(BigDecimal sbu) {
        return sbu.divide(HOURS_PER_MONTH, 6, RoundingMode.HALF_UP);
    }

    /** Art. 53: descanso semanal = 2 días al promedio de lunes a viernes, nunca menos que el SBU diario. */
    public static RestPay weeklyRest(List<DailyPay> week, BigDecimal sbu) {
        BigDecimal weekdayTotal = week.stream()
                .filter(day -> !day.weekend())
                .map(DailyPay::totalPay)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = weekdayTotal.divide(WORKING_DAYS_PER_WEEK, MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal minimumDaily = sbu.divide(DAYS_PER_MONTH, MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal dailyRate = average.max(minimumDaily);
        return new RestPay(average, minimumDaily, dailyRate, dailyRate.multiply(REST_DAYS_PER_WEEK));
    }

    static Premium premiumFor(Instant scannedAt, ZoneId zone, boolean weekend, Instant ordinaryEnd) {
        if (weekend) {
            return Premium.WEEKEND;
        }
        LocalTime time = scannedAt.atZone(zone).toLocalTime();
        boolean night = !time.isBefore(NIGHT_START) || time.isBefore(NIGHT_END);
        boolean supplementary = ordinaryEnd != null && scannedAt.isAfter(ordinaryEnd);
        if (supplementary) {
            return time.isBefore(NIGHT_END) ? Premium.SUPPLEMENTARY_NIGHT : Premium.SUPPLEMENTARY;
        }
        return night ? Premium.NIGHT : Premium.NONE;
    }

    private static BigDecimal workedMinutes(Shift shift, Instant now) {
        Instant end = shift.checkOut() != null ? shift.checkOut() : now;
        long gross = Math.max(0, Duration.between(shift.checkIn(), end).toMinutes());
        long net = gross > shift.breakMinutes() ? gross - shift.breakMinutes() : gross;
        return BigDecimal.valueOf(net);
    }

    private static boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** Recargo aplicable a una pieza. Solo se aplica uno: el mayor (SUPUESTO S12). */
    public enum Premium {
        NONE(0, true),
        NIGHT(25, true),
        SUPPLEMENTARY(50, false),
        SUPPLEMENTARY_NIGHT(100, false),
        WEEKEND(100, false);

        private final BigDecimal percent;
        private final boolean ordinaryTime;

        Premium(int percent, boolean ordinaryTime) {
            this.percent = BigDecimal.valueOf(percent);
            this.ordinaryTime = ordinaryTime;
        }

        public BigDecimal percent() {
            return percent;
        }

        public boolean isOrdinaryTime() {
            return ordinaryTime;
        }
    }

    public record Shift(Instant checkIn, Instant checkOut, int breakMinutes) {
    }

    public record PieceWork(Instant scannedAt, String operationCode, int quantity, BigDecimal rateUsd,
                            BigDecimal samMinutes) {
    }

    /**
     * @param shift asistencia del día; {@code null} si no se registró (se asume jornada completa de 8 h)
     * @param now   instante de cálculo: para una jornada abierta el piso se prorratea hasta este momento
     */
    public record DailyInput(LocalDate workDate, ZoneId zone, BigDecimal sbu, Shift shift, List<PieceWork> pieces,
                             Instant now) {
    }

    public record PayLine(Instant scannedAt, String operationCode, int quantity, BigDecimal rateUsd,
                          BigDecimal baseAmount, Premium premium, BigDecimal premiumAmount) {
    }

    public record DailyPay(LocalDate workDate, int pieces, BigDecimal earnedMinutes, BigDecimal ordinaryPay,
                           BigDecimal extraPay, BigDecimal premiumPay, BigDecimal floor, BigDecimal topUp,
                           BigDecimal totalPay, boolean belowFloor, boolean weekend, BigDecimal ordinaryHours,
                           BigDecimal attendedMinutes, BigDecimal efficiency, boolean attendanceRecorded,
                           List<PayLine> lines) {
    }

    public record RestPay(BigDecimal weekdayAverage, BigDecimal minimumDaily, BigDecimal dailyRate,
                          BigDecimal amount) {
    }
}
