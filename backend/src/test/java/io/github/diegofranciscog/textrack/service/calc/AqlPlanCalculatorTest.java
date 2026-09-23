package io.github.diegofranciscog.textrack.service.calc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.diegofranciscog.textrack.domain.InspectionLevel;
import io.github.diegofranciscog.textrack.service.calc.AqlPlanCalculator.AqlPlan;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AqlPlanCalculatorTest {

    @ParameterizedTest(name = "lote {0}, nivel {1} -> letra {2}")
    @CsvSource({
        "2, II, A", "8, II, A", "9, II, B", "25, II, C", "50, II, D", "90, II, E", "150, II, F",
        "280, II, G", "500, II, H", "1200, II, J", "3200, II, K", "10000, II, L", "35000, II, M",
        "150000, II, N", "500000, II, P", "500001, II, Q",
        "1000, I, G", "1000, III, K", "1000, S1, C", "1000, S2, C", "1000, S3, E", "1000, S4, F",
        "51, I, C", "51, III, F"
    })
    void codeLetterFollowsTableI(int lotSize, InspectionLevel level, char expected) {
        assertThat(AqlPlanCalculator.codeLetter(lotSize, level)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "lote {0}, nivel {1}, AQL {2} -> n={4} Ac={5} Re={6}")
    @CsvSource({
        // plan directo de la Tabla II-A
        "1000, II, 2.5, J, 80, 5, 6",
        "1000, II, 4.0, J, 80, 7, 8",
        "1000, II, 1.5, J, 80, 3, 4",
        "100, II, 2.5, F, 20, 1, 2",
        "30, II, 1.5, D, 8, 0, 1",
        "3000, II, 6.5, K, 125, 14, 15",
        "100000, II, 0.65, N, 500, 7, 8",
        // flecha ↓: se usa el primer plan de abajo y su tamaño de muestra
        "60, II, 2.5, F, 20, 1, 2",
        "10, II, 2.5, C, 5, 0, 1",
        "500, II, 0.65, J, 80, 1, 2",
        "1000, II, 0.10, K, 125, 0, 1",
        // flecha ↑: se usa el primer plan de arriba
        "50, II, 2.5, C, 5, 0, 1",
        "20000, II, 6.5, L, 200, 21, 22"
    })
    void planFollowsTableIIA(int lotSize, InspectionLevel level, String aql, char planLetter, int n, int ac, int re) {
        AqlPlan plan = AqlPlanCalculator.plan(lotSize, level, new BigDecimal(aql));

        assertThat(plan.planLetter()).isEqualTo(planLetter);
        assertThat(plan.sampleSize()).isEqualTo(n);
        assertThat(plan.acceptNumber()).isEqualTo(ac);
        assertThat(plan.rejectNumber()).isEqualTo(re);
        assertThat(plan.fullInspection()).isFalse();
    }

    @Test
    void sampleLargerThanLotMeansFullInspection() {
        AqlPlan plan = AqlPlanCalculator.plan(4, InspectionLevel.II, new BigDecimal("2.5"));

        assertThat(plan.initialLetter()).isEqualTo('A');
        assertThat(plan.planLetter()).isEqualTo('C');
        assertThat(plan.sampleSize()).isEqualTo(4);
        assertThat(plan.fullInspection()).isTrue();
        assertThat(plan.acceptNumber()).isZero();
    }

    @Test
    void acceptsUpToAcceptanceNumber() {
        AqlPlan plan = AqlPlanCalculator.plan(1000, InspectionLevel.II, new BigDecimal("2.5"));

        assertThat(plan.accepts(5)).isTrue();
        assertThat(plan.accepts(6)).isFalse();
    }

    @Test
    void equalAqlValuesWithDifferentScaleAreAccepted() {
        assertThat(AqlPlanCalculator.plan(1000, InspectionLevel.II, new BigDecimal("2.50")).sampleSize()).isEqualTo(80);
    }

    @Test
    void rejectsUnsupportedAqlAndLotSize() {
        assertThatThrownBy(() -> AqlPlanCalculator.plan(1000, InspectionLevel.II, new BigDecimal("10")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AQL no soportado");
        assertThatThrownBy(() -> AqlPlanCalculator.codeLetter(1, InspectionLevel.II))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(AqlPlanCalculator.supportedAqls()).hasSize(10).first().isEqualTo(new BigDecimal("0.10"));
    }
}
