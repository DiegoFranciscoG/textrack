package io.github.diegofranciscog.textrack.service.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Cuellos de botella por operación (balanceo de línea, OIT).
 *
 * <p>Para cada operación de la ruta: piezas pendientes, WIP frente a ella (lo que la operación anterior ya
 * entregó y esta aún no procesa), capacidad teórica {@code operarios × 60 / SAM} y minutos restantes con la
 * dotación actual {@code pendientes × SAM / operarios}. El cuello de botella es la operación con más minutos
 * restantes; una operación con trabajo pendiente y sin operarios asignados es cuello de botella inmediato.</p>
 */
public final class BottleneckAnalyzer {

    private static final int SCALE = 2;

    private BottleneckAnalyzer() {
    }

    public static Report analyze(int totalPieces, List<OperationProgress> route) {
        List<OperationProgress> ordered = route.stream()
                .sorted(Comparator.comparingInt(OperationProgress::sequence))
                .toList();
        List<OperationLoad> loads = new ArrayList<>();
        int previousCompleted = totalPieces;
        for (OperationProgress op : ordered) {
            int completed = Math.min(op.completedPieces(), totalPieces);
            int pending = totalPieces - completed;
            int wipBefore = Math.max(0, previousCompleted - completed);
            BigDecimal capacityPerHour = op.operators() == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(op.operators() * 60L).divide(op.samMinutes(), SCALE, RoundingMode.HALF_UP);
            BigDecimal remainingMinutes = op.operators() == 0 ? null
                    : op.samMinutes().multiply(BigDecimal.valueOf(pending))
                            .divide(BigDecimal.valueOf(op.operators()), SCALE, RoundingMode.HALF_UP);
            BigDecimal cycleMinutes = op.operators() == 0 ? null
                    : op.samMinutes().divide(BigDecimal.valueOf(op.operators()), 4, RoundingMode.HALF_UP);
            loads.add(new OperationLoad(op.sequence(), op.code(), op.name(), op.samMinutes(), op.operators(),
                    completed, pending, wipBefore, capacityPerHour, remainingMinutes, cycleMinutes, false));
            previousCompleted = completed;
        }

        OperationLoad bottleneck = loads.stream()
                .filter(load -> load.pendingPieces() > 0)
                .max(Comparator.comparing(BottleneckAnalyzer::urgency))
                .orElse(null);
        List<OperationLoad> marked = loads.stream()
                .map(load -> load == bottleneck ? load.markBottleneck() : load)
                .toList();

        BigDecimal estimatedHours = bottleneck == null || bottleneck.remainingMinutes() == null ? null
                : bottleneck.remainingMinutes().divide(BigDecimal.valueOf(60), SCALE, RoundingMode.HALF_UP);
        return new Report(totalPieces, marked, bottleneck == null ? null : bottleneck.code(),
                estimatedHours, balanceEfficiency(loads));
    }

    /** Operaciones sin operarios pesan más que cualquier carga finita. */
    private static BigDecimal urgency(OperationLoad load) {
        return load.remainingMinutes() == null ? BigDecimal.valueOf(Long.MAX_VALUE) : load.remainingMinutes();
    }

    /**
     * Eficiencia de balanceo = Σ SAM / (Σ operarios × ciclo máximo). 1,0 significa línea perfectamente balanceada.
     */
    static BigDecimal balanceEfficiency(List<OperationLoad> loads) {
        if (loads.isEmpty() || loads.stream().anyMatch(load -> load.operators() == 0)) {
            return null;
        }
        BigDecimal totalSam = loads.stream().map(OperationLoad::samMinutes).reduce(BigDecimal.ZERO, BigDecimal::add);
        int totalOperators = loads.stream().mapToInt(OperationLoad::operators).sum();
        BigDecimal maxCycle = loads.stream().map(OperationLoad::cycleMinutes).max(Comparator.naturalOrder())
                .orElseThrow();
        return totalSam.divide(maxCycle.multiply(BigDecimal.valueOf(totalOperators)), 4, RoundingMode.HALF_UP);
    }

    public record OperationProgress(int sequence, String code, String name, BigDecimal samMinutes, int operators,
                                    int completedPieces) {
    }

    public record OperationLoad(int sequence, String code, String name, BigDecimal samMinutes, int operators,
                                int completedPieces, int pendingPieces, int wipBefore, BigDecimal capacityPerHour,
                                BigDecimal remainingMinutes, BigDecimal cycleMinutes, boolean bottleneck) {

        OperationLoad markBottleneck() {
            return new OperationLoad(sequence, code, name, samMinutes, operators, completedPieces, pendingPieces,
                    wipBefore, capacityPerHour, remainingMinutes, cycleMinutes, true);
        }
    }

    public record Report(int totalPieces, List<OperationLoad> operations, String bottleneckCode,
                         BigDecimal estimatedHoursToFinish, BigDecimal balanceEfficiency) {
    }
}
