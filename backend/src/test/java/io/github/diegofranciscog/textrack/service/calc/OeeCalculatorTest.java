package io.github.diegofranciscog.textrack.service.calc;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.diegofranciscog.textrack.service.calc.OeeCalculator.Input;
import io.github.diegofranciscog.textrack.service.calc.OeeCalculator.Result;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OeeCalculatorTest {

    @Test
    void computesIso22400Kpis() {
        Result result = OeeCalculator.calculate(new Input(new BigDecimal("480"), new BigDecimal("30"),
                new BigDecimal("45"), new BigDecimal("324"), 50, 2));

        assertThat(result.plannedBusyMinutes()).isEqualByComparingTo("450");
        assertThat(result.actualProductionMinutes()).isEqualByComparingTo("405");
        assertThat(result.availability()).isEqualByComparingTo("0.9000");
        assertThat(result.performance()).isEqualByComparingTo("0.8000");
        assertThat(result.quality()).isEqualByComparingTo("0.9600");
        assertThat(result.oee()).isEqualByComparingTo("0.6912");
        assertThat(result.efficiency()).isEqualByComparingTo("0.6750");
        assertThat(result.qualityMeasured()).isTrue();
    }

    @Test
    void qualityDefaultsToOneWithoutInspections() {
        Result result = OeeCalculator.calculate(new Input(new BigDecimal("480"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("480"), 0, 0));

        assertThat(result.quality()).isEqualByComparingTo("1");
        assertThat(result.qualityMeasured()).isFalse();
        assertThat(result.oee()).isEqualByComparingTo("1");
    }

    @Test
    void noAttendanceYieldsZeroInsteadOfDivisionByZero() {
        Result result = OeeCalculator.calculate(new Input(BigDecimal.ZERO, new BigDecimal("10"), new BigDecimal("5"),
                BigDecimal.ZERO, 0, 0));

        assertThat(result.plannedBusyMinutes()).isEqualByComparingTo("0");
        assertThat(result.availability()).isEqualByComparingTo("0");
        assertThat(result.oee()).isEqualByComparingTo("0");
    }
}
