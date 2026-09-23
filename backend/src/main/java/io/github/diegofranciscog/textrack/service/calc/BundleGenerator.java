package io.github.diegofranciscog.textrack.service.calc;

import java.util.ArrayList;
import java.util.List;

/**
 * Divide un corte en bultos. Cada bulto sale de un único rollo (mismo lote de teñido, SUPUESTO S10) y de una
 * sola talla; las piezas de cada talla por rollo son {@code capas × piezas por capa} y se reparten en bultos
 * de como máximo {@code maxBundleSize} piezas.
 */
public final class BundleGenerator {

    private BundleGenerator() {
    }

    public static List<BundlePlan> generate(List<RollLay> rolls, List<SizeRatio> ratios, int maxBundleSize) {
        if (maxBundleSize < 1) {
            throw new IllegalArgumentException("El tamaño máximo de bulto debe ser al menos 1");
        }
        if (rolls.isEmpty() || ratios.isEmpty()) {
            throw new IllegalArgumentException("El corte necesita al menos un rollo y una talla");
        }
        List<BundlePlan> bundles = new ArrayList<>();
        int number = 1;
        for (RollLay roll : rolls) {
            for (SizeRatio ratio : ratios) {
                int remaining = roll.plies() * ratio.piecesPerPly();
                while (remaining > 0) {
                    int quantity = Math.min(remaining, maxBundleSize);
                    bundles.add(new BundlePlan(number++, roll.rollId(), ratio.sizeCode(), quantity));
                    remaining -= quantity;
                }
            }
        }
        return List.copyOf(bundles);
    }

    public record RollLay(long rollId, int plies) {
    }

    public record SizeRatio(String sizeCode, int piecesPerPly) {
    }

    public record BundlePlan(int bundleNumber, long rollId, String sizeCode, int quantity) {
    }
}
