package io.github.diegofranciscog.textrack.service.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Sistema de 4 puntos para inspección visual de tela (ASTM D5430).
 *
 * <p>Puntos por defecto según su longitud (≤ 3 in → 1, ≤ 6 in → 2, ≤ 9 in → 3, &gt; 9 in → 4; agujero → 4),
 * máximo 4 puntos por yarda lineal y normalización a 100 yardas cuadradas:
 * {@code puntos × 36 × 100 / (yardas inspeccionadas × ancho en pulgadas)}.</p>
 */
public final class FourPointCalculator {

    private static final BigDecimal METERS_PER_YARD = new BigDecimal("0.9144");
    private static final BigDecimal CM_PER_INCH = new BigDecimal("2.54");
    private static final BigDecimal SQ_INCHES_FACTOR = new BigDecimal("3600"); // 36 in/yd × 100 yd²
    private static final int MAX_POINTS_PER_LINEAR_YARD = 4;

    private FourPointCalculator() {
    }

    /** Equivalencias métricas: 3 in ≈ 75 mm, 6 in ≈ 150 mm, 9 in ≈ 230 mm. */
    public static int pointsFor(int lengthMm, boolean hole) {
        if (lengthMm <= 0) {
            throw new IllegalArgumentException("La longitud del defecto debe ser positiva");
        }
        if (hole) {
            return 4;
        }
        if (lengthMm <= 75) {
            return 1;
        }
        if (lengthMm <= 150) {
            return 2;
        }
        if (lengthMm <= 230) {
            return 3;
        }
        return 4;
    }

    public static Result grade(BigDecimal inspectedLengthM, BigDecimal widthCm, List<Defect> defects,
                               BigDecimal maxPointsAllowed) {
        if (inspectedLengthM.signum() <= 0 || widthCm.signum() <= 0) {
            throw new IllegalArgumentException("Largo y ancho inspeccionados deben ser positivos");
        }
        Map<Long, Integer> pointsPerYard = new TreeMap<>();
        for (Defect defect : defects) {
            if (defect.positionM().compareTo(inspectedLengthM) > 0) {
                throw new IllegalArgumentException("Defecto fuera del largo inspeccionado: " + defect.positionM() + " m");
            }
            long yard = defect.positionM().divide(METERS_PER_YARD, 0, RoundingMode.FLOOR).longValue();
            pointsPerYard.merge(yard, pointsFor(defect.lengthMm(), defect.hole()), Integer::sum);
        }
        int totalPoints = pointsPerYard.values().stream()
                .mapToInt(points -> Math.min(points, MAX_POINTS_PER_LINEAR_YARD))
                .sum();

        BigDecimal yards = inspectedLengthM.divide(METERS_PER_YARD, 6, RoundingMode.HALF_UP);
        BigDecimal widthInches = widthCm.divide(CM_PER_INCH, 6, RoundingMode.HALF_UP);
        BigDecimal per100SqYd = BigDecimal.valueOf(totalPoints)
                .multiply(SQ_INCHES_FACTOR)
                .divide(yards.multiply(widthInches), 2, RoundingMode.HALF_UP);
        return new Result(totalPoints, per100SqYd, per100SqYd.compareTo(maxPointsAllowed) <= 0);
    }

    public record Defect(BigDecimal positionM, int lengthMm, boolean hole) {
    }

    public record Result(int totalPoints, BigDecimal pointsPer100SqYd, boolean accepted) {
    }
}
