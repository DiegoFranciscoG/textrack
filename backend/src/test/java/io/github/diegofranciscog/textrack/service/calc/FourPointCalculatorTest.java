package io.github.diegofranciscog.textrack.service.calc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.diegofranciscog.textrack.service.calc.FourPointCalculator.Defect;
import io.github.diegofranciscog.textrack.service.calc.FourPointCalculator.Result;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FourPointCalculatorTest {

    private static final BigDecimal MAX_40 = new BigDecimal("40");

    @ParameterizedTest(name = "{0} mm, agujero={1} -> {2} puntos")
    @CsvSource({"1, false, 1", "75, false, 1", "76, false, 2", "150, false, 2", "151, false, 3",
        "230, false, 3", "231, false, 4", "900, false, 4", "10, true, 4"})
    void pointsByDefectLength(int lengthMm, boolean hole, int expected) {
        assertThat(FourPointCalculator.pointsFor(lengthMm, hole)).isEqualTo(expected);
    }

    @Test
    void capsFourPointsPerLinearYardAndNormalizesTo100SquareYards() {
        List<Defect> defects = List.of(
                new Defect(new BigDecimal("5.00"), 300, false),  // yarda 5 -> 4 puntos
                new Defect(new BigDecimal("5.30"), 50, false),   // misma yarda -> tope de 4
                new Defect(new BigDecimal("50.00"), 100, false)); // 2 puntos

        Result result = FourPointCalculator.grade(new BigDecimal("100"), new BigDecimal("150"), defects, MAX_40);

        assertThat(result.totalPoints()).isEqualTo(6);
        // 6 × 3600 / (109,3613 yd × 59,0551 in) = 3,34
        assertThat(result.pointsPer100SqYd()).isEqualByComparingTo("3.34");
        assertThat(result.accepted()).isTrue();
    }

    @Test
    void rejectsRollAboveAgreedThreshold() {
        List<Defect> defects = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            defects.add(new Defect(new BigDecimal("0.95").multiply(BigDecimal.valueOf(i)), 400, false));
        }

        Result result = FourPointCalculator.grade(new BigDecimal("10"), new BigDecimal("100"), defects, MAX_40);

        assertThat(result.totalPoints()).isEqualTo(40);
        assertThat(result.pointsPer100SqYd()).isGreaterThan(MAX_40);
        assertThat(result.accepted()).isFalse();
    }

    @Test
    void cleanRollHasZeroPoints() {
        Result result = FourPointCalculator.grade(new BigDecimal("80"), new BigDecimal("160"), List.of(), MAX_40);

        assertThat(result.totalPoints()).isZero();
        assertThat(result.pointsPer100SqYd()).isEqualByComparingTo("0");
        assertThat(result.accepted()).isTrue();
    }

    @Test
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> FourPointCalculator.pointsFor(0, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FourPointCalculator.grade(BigDecimal.ZERO, BigDecimal.TEN, List.of(), MAX_40))
                .isInstanceOf(IllegalArgumentException.class);
        List<Defect> outside = List.of(new Defect(new BigDecimal("11"), 10, false));
        assertThatThrownBy(() -> FourPointCalculator.grade(BigDecimal.TEN, BigDecimal.TEN, outside, MAX_40))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
