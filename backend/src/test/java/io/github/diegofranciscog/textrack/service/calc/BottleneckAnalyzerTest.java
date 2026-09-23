package io.github.diegofranciscog.textrack.service.calc;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.diegofranciscog.textrack.service.calc.BottleneckAnalyzer.OperationLoad;
import io.github.diegofranciscog.textrack.service.calc.BottleneckAnalyzer.OperationProgress;
import io.github.diegofranciscog.textrack.service.calc.BottleneckAnalyzer.Report;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class BottleneckAnalyzerTest {

    @Test
    void operationWithMostRemainingMinutesIsTheBottleneck() {
        Report report = BottleneckAnalyzer.analyze(100, List.of(
                new OperationProgress(30, "OP30", "Dobladillo", new BigDecimal("0.60"), 1, 30),
                new OperationProgress(10, "OP10", "Unir hombros", new BigDecimal("0.50"), 1, 80),
                new OperationProgress(20, "OP20", "Pegar cuello", new BigDecimal("1.20"), 1, 40)));

        assertThat(report.bottleneckCode()).isEqualTo("OP20");
        assertThat(report.estimatedHoursToFinish()).isEqualByComparingTo("1.20");
        assertThat(report.operations()).extracting(OperationLoad::code).containsExactly("OP10", "OP20", "OP30");
        assertThat(report.operations()).extracting(OperationLoad::wipBefore).containsExactly(20, 40, 10);
        assertThat(report.operations()).extracting(OperationLoad::remainingMinutes).containsExactly(
                new BigDecimal("10.00"), new BigDecimal("72.00"), new BigDecimal("42.00"));
        assertThat(report.operations().get(1).capacityPerHour()).isEqualByComparingTo("50.00");
        assertThat(report.operations()).filteredOn(OperationLoad::bottleneck).hasSize(1);
        assertThat(report.balanceEfficiency()).isEqualByComparingTo("0.6389");
    }

    @Test
    void operationWithoutOperatorsAndPendingWorkIsTheBottleneck() {
        Report report = BottleneckAnalyzer.analyze(50, List.of(
                new OperationProgress(10, "OP10", "Unir", new BigDecimal("0.50"), 2, 50),
                new OperationProgress(20, "OP20", "Planchar", new BigDecimal("0.40"), 0, 0)));

        assertThat(report.bottleneckCode()).isEqualTo("OP20");
        assertThat(report.estimatedHoursToFinish()).isNull();
        assertThat(report.balanceEfficiency()).isNull();
    }

    @Test
    void finishedRouteHasNoBottleneck() {
        Report report = BottleneckAnalyzer.analyze(10, List.of(
                new OperationProgress(10, "OP10", "Unir", new BigDecimal("0.50"), 1, 10)));

        assertThat(report.bottleneckCode()).isNull();
        assertThat(report.operations().get(0).pendingPieces()).isZero();
    }
}
