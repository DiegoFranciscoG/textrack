package io.github.diegofranciscog.textrack.service.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * KPI de ISO 22400-2 adaptados a confección (el <i>work unit</i> es la estación operario-máquina).
 *
 * <pre>
 *   PBT = minutos de asistencia − paros planificados         (planned busy time)
 *   APT = PBT − paros no planificados                         (actual production time)
 *   A   = APT / PBT                                           (availability)
 *   E   = Σ(SAM × piezas) / APT                               (effectiveness: PRI × PQ / APT)
 *   Q   = 1 − unidades defectuosas / unidades muestreadas     (quality ratio, estimado por AQL — S7)
 *   OEE = A × E × Q
 *   Eficiencia de línea (OIT) = Σ(SAM × piezas) / minutos de asistencia
 * </pre>
 */
public final class OeeCalculator {

    private static final int SCALE = 4;

    private OeeCalculator() {
    }

    public static Result calculate(Input input) {
        BigDecimal attended = nonNegative(input.attendedMinutes());
        BigDecimal pbt = nonNegative(attended.subtract(input.plannedStopMinutes()));
        BigDecimal apt = nonNegative(pbt.subtract(input.unplannedStopMinutes()));
        BigDecimal earned = nonNegative(input.earnedMinutes());

        BigDecimal availability = ratio(apt, pbt);
        BigDecimal performance = ratio(earned, apt);
        boolean qualityMeasured = input.sampledUnits() > 0;
        BigDecimal quality = qualityMeasured
                ? BigDecimal.ONE.subtract(ratio(BigDecimal.valueOf(input.defectiveUnits()),
                        BigDecimal.valueOf(input.sampledUnits())))
                : BigDecimal.ONE.setScale(SCALE);
        BigDecimal oee = availability.multiply(performance).multiply(quality).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal efficiency = ratio(earned, attended);
        return new Result(pbt, apt, availability, performance, quality, oee, efficiency, qualityMeasured);
    }

    private static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator.signum() == 0) {
            return BigDecimal.ZERO.setScale(SCALE);
        }
        return numerator.divide(denominator, SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        return value.signum() < 0 ? BigDecimal.ZERO : value;
    }

    public record Input(BigDecimal attendedMinutes, BigDecimal plannedStopMinutes, BigDecimal unplannedStopMinutes,
                        BigDecimal earnedMinutes, int sampledUnits, int defectiveUnits) {
    }

    public record Result(BigDecimal plannedBusyMinutes, BigDecimal actualProductionMinutes, BigDecimal availability,
                         BigDecimal performance, BigDecimal quality, BigDecimal oee, BigDecimal efficiency,
                         boolean qualityMeasured) {
    }
}
