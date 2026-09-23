package io.github.diegofranciscog.textrack.service.calc;

import io.github.diegofranciscog.textrack.domain.InspectionLevel;
import java.math.BigDecimal;
import java.util.List;

/**
 * Plan de muestreo simple para inspección normal indexado por AQL.
 *
 * <p>Tabla I (letra código) y Tabla II-A (n, Ac, Re) de ISO 2859-1 / MIL-STD-105E. La Tabla II-A tiene una
 * estructura diagonal: para cada AQL existe una fila «cero» donde el plan es Ac=0/Re=1; al bajar una fila
 * (muestra mayor) la secuencia de planes es siempre la misma: 0/1, ↑, ↓, 1/2, 2/3, 3/4, 5/6, 7/8, 10/11,
 * 14/15, 21/22 y luego ↑. Modelarla así evita transcribir 256 celdas a mano y permite probarla contra los
 * valores publicados.</p>
 */
public final class AqlPlanCalculator {

    public static final String STANDARD_EDITION = "ISO 2859-1 Tabla 2-A (valores de MIL-STD-105E, Tabla II-A)";

    private static final String CODE_LETTERS = "ABCDEFGHJKLMNPQR";
    private static final int[] SAMPLE_SIZES = {2, 3, 5, 8, 13, 20, 32, 50, 80, 125, 200, 315, 500, 800, 1250, 2000};

    /** Límite superior de cada rango de tamaño de lote (Tabla I). */
    private static final int[] LOT_UPPER_BOUNDS = {
        8, 15, 25, 50, 90, 150, 280, 500, 1200, 3200, 10_000, 35_000, 150_000, 500_000, Integer.MAX_VALUE
    };

    /** AQL preferentes de la Tabla II-A en orden; la fila cero del AQL de índice i es 14 - i. */
    private static final List<BigDecimal> AQL_VALUES = List.of(
            new BigDecimal("0.010"), new BigDecimal("0.015"), new BigDecimal("0.025"), new BigDecimal("0.040"),
            new BigDecimal("0.065"), new BigDecimal("0.10"), new BigDecimal("0.15"), new BigDecimal("0.25"),
            new BigDecimal("0.40"), new BigDecimal("0.65"), new BigDecimal("1.0"), new BigDecimal("1.5"),
            new BigDecimal("2.5"), new BigDecimal("4.0"), new BigDecimal("6.5"));

    /** Solo se admiten AQL cuyos planes quedan completos dentro de las filas A…R (0,10 a 6,5). */
    private static final int MIN_SUPPORTED_AQL_INDEX = 5;

    /** Ac/Re según la distancia a la fila cero (d = 0 y d = 3…10). */
    private static final int[][] ACCEPT_REJECT = {
        {0, 1}, null, null, {1, 2}, {2, 3}, {3, 4}, {5, 6}, {7, 8}, {10, 11}, {14, 15}, {21, 22}
    };

    private AqlPlanCalculator() {
    }

    public static List<BigDecimal> supportedAqls() {
        return AQL_VALUES.subList(MIN_SUPPORTED_AQL_INDEX, AQL_VALUES.size());
    }

    public static char codeLetter(int lotSize, InspectionLevel level) {
        if (lotSize < 2) {
            throw new IllegalArgumentException("El tamaño de lote debe ser al menos 2");
        }
        int range = 0;
        while (lotSize > LOT_UPPER_BOUNDS[range]) {
            range++;
        }
        return level.letters().charAt(range);
    }

    public static AqlPlan plan(int lotSize, InspectionLevel level, BigDecimal aql) {
        int aqlIndex = aqlIndex(aql);
        char initialLetter = codeLetter(lotSize, level);
        int row = CODE_LETTERS.indexOf(initialLetter);
        int zeroRow = 14 - aqlIndex;
        int distance = row - zeroRow;

        int planRow;
        if (distance < 0 || distance == 1) {
            planRow = zeroRow;              // ↓ (usar el primer plan debajo) o ↑ (usar el plan de arriba)
        } else if (distance == 2) {
            planRow = zeroRow + 3;          // ↓ hasta el plan 1/2
        } else if (distance > 10) {
            planRow = zeroRow + 10;         // ↑ hasta el plan 21/22
        } else {
            planRow = row;
        }
        int[] acRe = ACCEPT_REJECT[planRow - zeroRow];
        int sampleSize = SAMPLE_SIZES[planRow];
        boolean fullInspection = sampleSize >= lotSize;
        return new AqlPlan(
                initialLetter,
                CODE_LETTERS.charAt(planRow),
                fullInspection ? lotSize : sampleSize,
                acRe[0],
                acRe[1],
                fullInspection);
    }

    private static int aqlIndex(BigDecimal aql) {
        for (int i = MIN_SUPPORTED_AQL_INDEX; i < AQL_VALUES.size(); i++) {
            if (AQL_VALUES.get(i).compareTo(aql) == 0) {
                return i;
            }
        }
        throw new IllegalArgumentException("AQL no soportado: " + aql + ". Valores válidos: " + supportedAqls());
    }

    /**
     * @param initialLetter letra obtenida de la Tabla I
     * @param planLetter    letra del plan aplicado tras seguir las flechas (define n)
     * @param fullInspection true si n alcanzó el tamaño del lote (inspección 100 %)
     */
    public record AqlPlan(char initialLetter, char planLetter, int sampleSize, int acceptNumber, int rejectNumber,
                          boolean fullInspection) {

        public boolean accepts(int defectsFound) {
            return defectsFound <= acceptNumber;
        }
    }
}
