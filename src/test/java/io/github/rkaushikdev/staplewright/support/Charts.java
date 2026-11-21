package io.github.rkaushikdev.staplewright.support;

import io.github.rkaushikdev.staplewright.spec.DepthAndSpaceChart;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Charts the tests cut against, kept out of the library on purpose.
 *
 * <p>These are the three families the shop actually stocks, written the way the catalogue writes
 * them. Tests that want something impossible build it here with overrides rather than inventing a
 * fourth manufacturer.
 */
public final class Charts {

    private static final List<Integer> ZERO_TO_NINE = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

    private Charts() {}

    public static DepthAndSpaceChart sixPinChart() {
        return new DepthAndSpaceChart("Schlage", "C keyway, six pin", 6, ZERO_TO_NINE, 335, 15, 231, 156, 7);
    }

    /** The six pin chart with some numbers changed, checked once all of them are. */
    public static DepthAndSpaceChart sixPinChart(UnaryOperator<DepthAndSpaceChart.Builder> overrides) {
        return overrides.apply(sixPinChart().toBuilder()).build();
    }

    /** A small format interchangeable core, which is what the dorms run. */
    public static DepthAndSpaceChart sevenPinCoreChart() {
        return new DepthAndSpaceChart(
                "Best",
                "A2 seven pin core",
                7,
                ZERO_TO_NINE,
                320,
                25,
                150,
                155,
                7,
                false,
                2,
                "depths quoted in the A2 system, twenty five mil increments");
    }

    public static DepthAndSpaceChart sevenPinCoreChart(UnaryOperator<DepthAndSpaceChart.Builder> overrides) {
        return overrides.apply(sevenPinCoreChart().toBuilder()).build();
    }

    /** The old maintenance padlocks, five chambers and a tight macs. */
    public static DepthAndSpaceChart fivePinChart() {
        return new DepthAndSpaceChart(
                "Corbin", "60 keyway, five pin", 5, List.of(0, 1, 2, 3, 4, 5, 6), 328, 20, 200, 150, 4);
    }

    public static DepthAndSpaceChart fivePinChart(UnaryOperator<DepthAndSpaceChart.Builder> overrides) {
        return overrides.apply(fivePinChart().toBuilder()).build();
    }
}
