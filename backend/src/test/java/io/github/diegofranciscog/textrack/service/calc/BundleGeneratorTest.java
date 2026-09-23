package io.github.diegofranciscog.textrack.service.calc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.diegofranciscog.textrack.service.calc.BundleGenerator.BundlePlan;
import io.github.diegofranciscog.textrack.service.calc.BundleGenerator.RollLay;
import io.github.diegofranciscog.textrack.service.calc.BundleGenerator.SizeRatio;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class BundleGeneratorTest {

    private static final List<SizeRatio> RATIO = List.of(
            new SizeRatio("S", 1), new SizeRatio("M", 2), new SizeRatio("L", 2), new SizeRatio("XL", 1));

    @Test
    void splitsEachRollAndSizeIntoBundlesOfMaxSize() {
        List<BundlePlan> bundles = BundleGenerator.generate(
                List.of(new RollLay(1, 30), new RollLay(2, 30)), RATIO, 20);

        assertThat(bundles).hasSize(20);
        assertThat(bundles).extracting(BundlePlan::bundleNumber)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 20).boxed().toList());
        assertThat(bundles.stream().mapToInt(BundlePlan::quantity).sum()).isEqualTo(360);
        assertThat(bundles).allMatch(bundle -> bundle.quantity() <= 20);
        assertThat(bundles.subList(0, 10)).allMatch(bundle -> bundle.rollId() == 1);
        assertThat(bundles.get(0)).isEqualTo(new BundlePlan(1, 1, "S", 20));
        assertThat(bundles.get(1)).isEqualTo(new BundlePlan(2, 1, "S", 10));
    }

    @Test
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> BundleGenerator.generate(List.of(new RollLay(1, 1)), RATIO, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BundleGenerator.generate(List.of(), RATIO, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
